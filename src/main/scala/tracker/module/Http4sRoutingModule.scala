package tracker.module

import cats.implicits._
import com.avast.sst.http4s.server.Http4sRouting
import com.avast.sst.http4s.server.micrometer.MicrometerHttp4sServerMetricsModule
import fs2.concurrent.Topic
import io.circe.syntax._
import org.http4s._
import org.http4s.circe.CirceEntityCodec._
import org.http4s.client.Client
import org.http4s.dsl.Http4sDsl
import org.http4s.server.Router
import org.http4s.server.middleware.CORS
import slog4s.LoggerFactory
import tracker.{User, _}
import tracker.Roles._
import tracker.config.Configuration
import tracker.module.routes.GPX
import tracker.module.routes.RoutesImplicits._
import tracker.security.{AuthJwtMiddleware, DefaultAccessTokenParser}
import tracker.service._
import zio.Task
import zio.interop.catz._

class Http4sRoutingModule(
    topic: Topic[Task, WebSocketMessage],
    userService: UserService,
    vehicleService: VehicleService,
    fleetService: FleetService,
    positionService: PositionService,
    trackService: TrackService,
    trackerService: TrackerService,
    loggerFactory: LoggerFactory[Task],
    client: Client[Task],
    serverMetricsModule: MicrometerHttp4sServerMetricsModule[Task],
    config: Configuration
) extends Http4sDsl[Task] {

  import serverMetricsModule._

  private val authMiddleware = AuthJwtMiddleware(DefaultAccessTokenParser, config.jwt, userService)

  private val authedRoutes: AuthedRoutes[User, Task] = AuthedRoutes.of {
    case GET -> Root / "auth" as user => Ok(s"User: $user")
    case request @ GET -> Root / "withRole" as _ =>
      request.withRoles(Admin) {
        Ok("ok")
      }
    case request @ POST -> Root / "user" / "new" as _ =>
      request.withRoles(Admin) {
        handleNewUser(request.req)
      }
    case request @ POST -> Root / "user" as _ =>
      request.withRoles(Admin) {
        handleUpdateUser(request.req)
      }
    case request @ GET -> Root / "users" :? PageQueryParamMatcher(page) +& PageSizeQueryParamMatcher(pageSize) as _ =>
      request.withRoles(Reader) {
        userService.getAll(page, pageSize).flatMap(u => Ok(u.asJson))
      }
    case request @ GET -> Root / "user" :? IdQueryParamMatcher(id) as _ =>
      request.withRoles(Admin) {
        handleGetUser(id)
      }
    case request @ GET -> Root / "vehicles" :? PageQueryParamMatcher(page) +& PageSizeQueryParamMatcher(pageSize) as _ =>
      request.withRoles(Reader) {
        vehicleService.getAll(page, pageSize).flatMap(p => Ok(p.asJson))
      }
    case request @ GET -> Root / "vehicle" :? IdQueryParamMatcher(id) as _ =>
      request.withRoles(Reader) {
        handleGetVehicle(id)
      }
    case request @ POST -> Root / "vehicle" as _ =>
      request.withRoles(Editor) {
        handleUpdateVehicle(request.req)
      }
    case request @ POST -> Root / "vehicle" / "new" as _ =>
      request.withRoles(Editor) {
        handleNewVehicle(request.req)
      }
    case request @ GET -> Root / "fleet" :? IdQueryParamMatcher(id) as _ =>
      request.withRoles(Reader) {
        handleGetFleet(id)
      }
    case request @ POST -> Root / "fleet" / "new" as _ =>
      request.withRoles(Reader) {
        handleNewFleet(request.req)
      }
    case request @ GET -> Root / "fleets" :? PageQueryParamMatcher(page) +& PageSizeQueryParamMatcher(pageSize) as _ =>
      request.withRoles(Reader) {
        fleetService.getAll(page, pageSize).flatMap(t => Ok(t.asJson))
      }
    case request @ POST -> Root / "position" / "new" as _ =>
      request.withRoles(Editor) {
        handleNewPosition(request.req)
      }
    case request @ POST -> Root / "vehicle" / "positions" as _ =>
      request.withRoles(Reader) {
        handleGetVehiclePositions(request.req)
      }
    case request @ GET -> Root / "vehicle" / "position" :? IdQueryParamMatcher(id) as _ =>
      request.withRoles(Reader) {
        handleGetLastVehiclePosition(id)
      }
    case request @ POST -> Root / "vehicle" / "history" as _ =>
      request.withRoles(Reader) {
        handleGetVehiclePositionHistory(request.req)
      }
    case request @ GET -> Root / "tracks" :? OptionalVehicleQueryParamMatcher(vehicleId) +& PageQueryParamMatcher(page) +& PageSizeQueryParamMatcher(
          pageSize
        ) as _ =>
      request.withRoles(Reader) {
        handleGetAllTracks(vehicleId, page, pageSize)
      }
    case request @ GET -> Root / "track" :? IdQueryParamMatcher(id) as _ =>
      request.withRoles(Reader) {
        trackService.get(id).flatMap(Ok(_))
      }
    case request @ GET -> Root / "track" / "positions" :? IdQueryParamMatcher(id) as _ =>
      request.withRoles(Reader) {
        trackService.getPositions(id).flatMap(_.fold(NotFound())(p => Ok(p)))
      }
    case request @ GET -> Root / "trackers" :? PageQueryParamMatcher(page) +& PageSizeQueryParamMatcher(pageSize) as _ =>
      request.withRoles(Reader) {
        trackerService.getAll(page, pageSize).flatMap(t => Ok(t.asJson))
      }
    case request @ GET -> Root / "tracker" :? IdQueryParamMatcher(id) as _ =>
      request.withRoles(Reader) {
        trackerService.get(id).flatMap(_.fold(NotFound())(Ok(_)))
      }
    case request @ POST -> Root / "tracker" / "new" as _ =>
      request.withRoles(Editor) {
        handleNewTracker(request.req)
      }
    case request @ POST -> Root / "tracker" as _ =>
      request.withRoles(Editor) {
        handleUpdateTracker(request.req)
      }
    case request @ POST -> Root / "tracker" / "delete" as _ =>
      request.withRoles(Editor) {
        handleDeleteTracker(request.req)
      }
    case request @ POST -> Root / "tracker" / "revoke" as _ =>
      request.withRoles(Editor) {
        handleRevokeTrackerToken(request.req)
      }
    case request @ GET -> Root / "vehicle" / "active"
        :? IdQueryParamMatcher(id)
        +& MonthQueryParamMatcher(month)
        +& YearQueryParamMatcher(year) as _ =>
      request.withRoles(Reader) {
        positionService.getActiveDays(id, month, year).flatMap(days => Ok(days.asJson))
      }
  }

  private val routes = HttpRoutes.of[Task] {
    case GET -> Root / "circuit-breaker"  => client.expect[String]("https://httpbin.org/status/500").flatMap(Ok(_))
    case request @ POST -> Root / "login" => handleLogin(request)
    case GET -> Root / "ws"               => WebSocketService(loggerFactory, DefaultAccessTokenParser, topic, vehicleService, config).flatMap(_.build)

  } <+> authMiddleware(authedRoutes) <+> authMiddleware(new GPX(positionService).routes)

  val router: HttpApp[Task] = Http4sRouting.make {
    serverMetrics {
      CORS(
        Router(
          "api" -> routes,
          "" -> StaticFileRoutingModule.make(config)
        )
      )
    }
  }

  private def handleLogin(req: Request[Task]): Task[Response[Task]] = {
    req.as[LoginRequest].flatMap(userService.login).flatMap {
      case Some(at) => Ok(at)
      case None     => Forbidden()
    }
  }

  private def handleNewUser(req: Request[Task]): Task[Response[Task]] = {
    req
      .as[NewUserRequest]
      .flatMap(userService.persist)
      .flatMap(Ok(_))
  }

  private def handleUpdateUser(req: Request[Task]): Task[Response[Task]] = {
    req
      .as[UpdateUserRequest]
      .flatMap(userService.persist)
      .flatMap(Ok(_))
  }

  private def handleGetVehicle(id: Long): Task[Response[Task]] = {
    vehicleService.get(id).flatMap {
      case Some(vehicle) => Ok(vehicle.asJson)
      case None          => NotFound(NotFoundResponse("Vehicle not found").asJson)
    }
  }

  private def handleUpdateVehicle(req: Request[Task]): Task[Response[Task]] = {
    val updatedVehicle = for {
      vehicle <- req.as[UpdateVehicleRequest]
      _ <- vehicleService.update(vehicle)
      res <- vehicleService.setFleets(vehicle.data)
    } yield res
    Ok(updatedVehicle)
  }

  private def handleNewVehicle(req: Request[Task]): Task[Response[Task]] = {
    val newVehicle = for {
      vehicleRequest <- req.as[NewVehicleRequest]
      newVehicle <- vehicleService.persist(vehicleRequest)
      vehicleWithFleets <- vehicleService.setFleets(newVehicle, vehicleRequest.fleetsId)
    } yield vehicleWithFleets
    Ok(newVehicle)
  }

  private def handleGetFleet(id: Long): Task[Response[Task]] = {
    fleetService.get(id).flatMap {
      case Some(fleet) => Ok(fleet.asJson)
      case None        => NotFound(NotFoundResponse("Fleet not found").asJson)
    }
  }

  private def handleNewFleet(req: Request[Task]): Task[Response[Task]] = {
    req
      .as[NewFleetRequest]
      .flatMap(fleetService.persist)
      .flatMap(Ok(_))
  }

  private def handleGetUser(id: Long): Task[Response[Task]] = {
    userService.getUserById(id).flatMap {
      case Some(user) => Ok(user.asJson)
      case None       => NotFound("User not found")
    }
  }

  private def handleNewPosition(req: Request[Task]): Task[Response[Task]] = {
    req
      .as[PositionRequest]
      .flatMap(positionService.persist)
      .flatMap(pos => topic.publish1(WebSocketMessage.position(pos)) >> Ok(pos))
  }

  private def handleGetVehiclePositions(req: Request[Task]): Task[Response[Task]] = {
    req
      .as[VehiclePositionsRequest]
      .flatMap(positionService.getByVehicle)
      .flatMap(Ok(_))
  }

  private def handleGetVehiclePositionHistory(req: Request[Task]): Task[Response[Task]] = {
    req
      .as[VehiclePositionHistoryRequest]
      .flatMap(positionService.getVehiclePositionHistory)
      .flatMap(Ok(_))
  }

  private def handleGetLastVehiclePosition(vehicleId: Long): Task[Response[Task]] = {
    positionService.getLastVehiclePosition(vehicleId).flatMap {
      case Some(position) => Ok(position.asJson)
      case None           => NotFound("Vehicle not found")
    }
  }

  private def handleGetAllTracks(vehicleId: Option[Long], page: Option[Int], pageSize: Option[Int]): Task[Response[Task]] = {
    vehicleId.fold(trackService.getAll(page, pageSize).flatMap(t => Ok(t.asJson)))(id =>
      trackService.getByVehicle(id, page, pageSize).flatMap(t => Ok(t.asJson))
    )
  }

  private def handleNewTracker(req: Request[Task]): Task[Response[Task]] = {
    req
      .as[NewTrackerRequest]
      .flatMap(trackerService.persist)
      .flatMap(t => Ok(t.asJson))
  }

  private def handleUpdateTracker(req: Request[Task]): Task[Response[Task]] = {
    req
      .as[UpdateTrackerRequest]
      .flatMap(trackerService.update)
      .flatMap(t => Ok(t.asJson))
  }

  private def handleDeleteTracker(req: Request[Task]): Task[Response[Task]] = {
    req
      .as[UpdateTrackerRequest]
      .flatMap(trackerService.delete)
      .flatMap(t => Ok(t.asJson))
  }
  private def handleRevokeTrackerToken(req: Request[Task]): Task[Response[Task]] = {
    req
      .as[UpdateTrackerRequest]
      .flatMap(trackerService.updateAccessToken)
      .flatMap(t => Ok(t.asJson))
  }
}

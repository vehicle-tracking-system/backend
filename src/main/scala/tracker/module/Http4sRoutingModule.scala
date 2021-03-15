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
import org.http4s.server.middleware._
import slog4s.LoggerFactory
import tracker.{Role, User, _}
import tracker.Roles._
import tracker.config.Configuration
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
    loggerFactory: LoggerFactory[Task],
    client: Client[Task],
    serverMetricsModule: MicrometerHttp4sServerMetricsModule[Task],
    config: Configuration
) extends Http4sDsl[Task] {

  import serverMetricsModule._

  private val helloWorldRoute = routeMetrics.wrap("hello")(Ok("Hello World!"))

  private val authMiddleware = AuthJwtMiddleware(DefaultAccessTokenParser, config.jwt, userService)

  private val authedRoutes: AuthedRoutes[User, Task] = AuthedRoutes.of {
    case GET -> Root / "auth" as user => Ok(s"User: $user")
    case request @ GET -> Root / "withRole" as _ =>
      withRoles(Admin) {
        Ok("ok")
      }(request)
    case request @ POST -> Root / "user" / "new" as _ =>
      withRoles(Admin) {
        handleNewUser(request.req)
      }(request)
    case request @ POST -> Root / "user" as _ =>
      withRoles(Admin) {
        handleUpdateUser(request.req)
      }(request)
    case request @ GET -> Root / "user" / LongVar(id) as _ =>
      withRoles(Admin) {
        handleGetUser(id)
      }(request)
    case request @ POST -> Root / "vehicles" as _ =>
      withRoles(Reader) {
        handleGetVehicles(request.req)
      }(request)
    case request @ GET -> Root / "vehicle" / LongVar(id) as _ =>
      withRoles(Reader) {
        handleGetVehicle(id)
      }(request)
    case request @ GET -> Root / "fleet" / LongVar(id) as _ =>
      withRoles(Reader) {
        handleGetFleet(id)
      }(request)
    case request @ POST -> Root / "position" / "new" as _ =>
      withRoles(Editor) {
        handleNewPosition(request.req)
      }(request)
    case request @ POST -> Root / "vehicle" / "positions" as _ =>
      withRoles(Reader) {
        handleGetVehiclePositions(request.req)
      }(request)
    case request @ POST -> Root / "vehicle" / "history" as _ =>
      withRoles(Reader) {
        handleGetVehiclePositionHistory(request.req)
      }(request)
  }

  private val routes = HttpRoutes.of[Task] {
    case GET -> Root / "hello"            => helloWorldRoute
    case GET -> Root / "circuit-breaker"  => client.expect[String]("https://httpbin.org/status/500").flatMap(Ok(_))
    case request @ POST -> Root / "login" => handleLogin(request)
    case GET -> Root / "ws"               => WebSocketService(loggerFactory, topic, vehicleService, config).flatMap(_.build)

  } <+> authMiddleware(authedRoutes)

  val router: HttpApp[Task] = Http4sRouting.make {
    serverMetrics {
      CORS(routes)
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
      case Some(vehicle) => Ok(vehicle.asJson.noSpacesSortKeys)
      case None          => NotFound("Vehicle not found")
    }
  }

  private def handleGetFleet(id: Long): Task[Response[Task]] = {
    fleetService.get(id).flatMap {
      case Some(fleet) => Ok(fleet.asJson.noSpacesSortKeys)
      case None        => NotFound("Fleet not found")
    }
  }

  private def handleGetUser(id: Long): Task[Response[Task]] = {
    userService.getUserById(id).flatMap {
      case Some(user) => Ok(user.asJson.noSpacesSortKeys)
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

  private def handleGetVehicles(req: Request[Task]): Task[Response[Task]] = {
    req
      .as[VehiclesRequest]
      .flatMap(vehicleService.getAll)
      .flatMap(Ok(_))
  }

  private def withRoles(
      roles: Role*
  )(f: Task[Response[Task]])(request: AuthedRequest[Task, User]): Task[Response[Task]] = {
    val user = request.context

    if (roles.toSet.subsetOf(user.roles) || user.roles.contains(Admin)) {
      f
    } else {
      Forbidden()
    }
  }
}

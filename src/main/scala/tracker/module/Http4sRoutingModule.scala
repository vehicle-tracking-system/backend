package tracker.module

import cats.implicits._
import com.avast.sst.http4s.server.Http4sRouting
import com.avast.sst.http4s.server.micrometer.MicrometerHttp4sServerMetricsModule
import fs2.concurrent.Topic
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
import tracker.module.routes._
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

  private val testingAuthedRoutes: AuthedRoutes[User, Task] = AuthedRoutes.of {
    case request @ POST -> Root / "position" / "new" as _ =>
      request.withRoles(Editor) {
        handleNewPosition(request.req)
      }
  }

  private val routes = HttpRoutes.of[Task] {
    case GET -> Root / "circuit-breaker"  => client.expect[String]("https://httpbin.org/status/500").flatMap(Ok(_))
    case request @ POST -> Root / "login" => handleLogin(request)
    case GET -> Root / "ws"               => WebSocketService(loggerFactory, DefaultAccessTokenParser, topic, vehicleService, config).flatMap(_.build)

  } <+> authMiddleware(testingAuthedRoutes)

  private val apiRouter = {
    Router(
      "auth" -> authMiddleware(new AuthRoutes(userService).routes),
      "user" -> authMiddleware(new UserRoutes(userService).routes),
      "vehicle" -> authMiddleware(new VehicleRoutes(vehicleService, positionService, trackService).routes),
      "fleet" -> authMiddleware(new FleetRoutes(fleetService).routes),
      "track" -> authMiddleware(new TrackRoutes(trackService).routes),
      "tracker" -> authMiddleware(new TrackerRoutes(trackerService).routes),
      "gpx" -> authMiddleware(new GPXRoutes(positionService).routes),
      "" -> routes
    )
  }

  /**
    * Application router contains all parts and static files server.
    */
  val router: HttpApp[Task] = Http4sRouting.make {
    serverMetrics {
      CORS(
        Router(
          "" -> StaticFileRoutingModule.make(config),
          "api" -> apiRouter
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

  private def handleNewPosition(req: Request[Task]): Task[Response[Task]] = {
    req
      .as[PositionRequest]
      .flatMap(positionService.persist)
      .flatMap(pos => topic.publish1(WebSocketMessage.position(pos)) >> Ok(pos))
  }
}

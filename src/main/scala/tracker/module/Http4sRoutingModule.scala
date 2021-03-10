package tracker.module

import cats.implicits._
import com.avast.sst.http4s.server.Http4sRouting
import com.avast.sst.http4s.server.micrometer.MicrometerHttp4sServerMetricsModule
import fs2.{Pipe, Stream}
import fs2.concurrent.Topic
import io.circe.syntax._
import org.http4s._
import org.http4s.circe.CirceEntityCodec._
import org.http4s.client.Client
import org.http4s.dsl.Http4sDsl
import org.http4s.server.websocket._
import org.http4s.websocket.WebSocketFrame
import org.http4s.websocket.WebSocketFrame.Text
import tracker.{Role, User, _}
import tracker.Roles._
import tracker.config.Configuration
import tracker.security.{AuthJwtMiddleware, DefaultAccessTokenParser}
import tracker.service.{FleetService, UserService, VehicleService}
import zio.{Task, ZIO}
import zio.interop.catz._
import zio.interop.catz.implicits._
import WebSocketMessage._
import org.http4s.server.middleware._

import scala.concurrent.duration._

class Http4sRoutingModule(
    topic: Topic[Task, WebSocketMessage],
    userService: UserService,
    vehicleService: VehicleService,
    fleetService: FleetService,
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
    case request @ GET -> Root / "vehicle" / LongVar(id) as _ =>
      withRoles(Reader) {
        handleGetVehicle(id)
      }(request)
    case request @ GET -> Root / "fleet" / LongVar(id) as _ =>
      withRoles(Reader) {
        handleGetFleet(id)
      }(request)
  }

  private val routes = HttpRoutes.of[Task] {
    case GET -> Root / "hello"            => helloWorldRoute
    case GET -> Root / "circuit-breaker"  => client.expect[String]("https://httpbin.org/status/500").flatMap(Ok(_))
    case request @ POST -> Root / "login" => handleLogin(request)
    case GET -> Root / "ws" =>
      val toClient: Stream[Task, WebSocketFrame] =
        topic
          .subscribe(100)
          .map(msg => Text(msg.asJson.noSpaces))
          .merge(Stream.awakeEvery[Task](5.seconds).map(_ => Text(heartbeat.asJson.noSpaces)))

      val fromClient: Pipe[Task, WebSocketFrame, Unit] =
        _.evalMap {
          case Text(txt, _) =>
            if (txt == "close")
              topic.publish1(text("Some websocket client going down"))
            else ZIO.unit
          case _ => ZIO.unit
        }

      WebSocketBuilder[Task].build(toClient, fromClient)

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
      .flatMap(_.fold(BadRequest(_), i => Ok(s"$i user successfully inserted.")))
  }

  private def handleUpdateUser(req: Request[Task]): Task[Response[Task]] = {
    req
      .as[UpdateUserRequest]
      .flatMap(userService.persist)
      .flatMap(_.fold(BadRequest(_), i => Ok(s"$i user successfully updated.")))
  }

  private def handleGetVehicle(id: Long): Task[Response[Task]] = {
    vehicleService.find(id).map(_.asJson).flatMap(Ok(_))
  }

  private def handleGetFleet(id: Long): Task[Response[Task]] = {
    fleetService.find(id).map(_.asJson).flatMap(Ok(_))
  }

  private def handleGetUser(id: Long): Task[Response[Task]] = {
    userService.getUserById(id).map(_.asJson).flatMap(Ok(_))
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

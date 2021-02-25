package tracker.module

import cats.implicits._
import com.avast.sst.http4s.server.Http4sRouting
import com.avast.sst.http4s.server.micrometer.MicrometerHttp4sServerMetricsModule
import io.circe.syntax._
import org.http4s.circe.CirceEntityCodec._
import org.http4s.client.Client
import org.http4s.dsl.Http4sDsl
import org.http4s.{AuthedRequest, AuthedRoutes, HttpApp, HttpRoutes, Request, Response}
import tracker.config.Configuration
import tracker.dao.UserDAO
import tracker.request.LoginRequest
import tracker.security.{AuthJwtMiddleware, DefaultAccessTokenParser}
import tracker.service.UserService
import tracker.{Role, Roles, User}
import zio.Task
import zio.interop.catz._

class Http4sRoutingModule(
    userDAO: UserDAO,
    userService: UserService,
    client: Client[Task],
    serverMetricsModule: MicrometerHttp4sServerMetricsModule[Task],
    config: Configuration
) extends Http4sDsl[Task] {

  import serverMetricsModule._

  private val helloWorldRoute = routeMetrics.wrap("hello")(Ok("Hello World!"))

  private val authMiddleware = AuthJwtMiddleware(DefaultAccessTokenParser, config.jwt, userDAO)

  private val authedRoutes: AuthedRoutes[User, Task] = AuthedRoutes.of {
    case GET -> Root / "auth" as user => Ok(s"User: ${user}")
    case request @ GET -> Root / "withRole" as _ =>
      withRoles(Roles.User) { _ =>
        Ok("ok")
      }(request)
  }

  private val routes = HttpRoutes.of[Task] {
    case GET -> Root / "hello"            => helloWorldRoute
    case GET -> Root / "circuit-breaker"  => client.expect[String]("https://httpbin.org/status/500").flatMap(Ok(_))
    case GET -> Root / "user" / id        => userService.getUserById(id.toLong).map(_.asJson).flatMap(Ok(_))
    case request @ POST -> Root / "login" => handleLogin(request)
  } <+> authMiddleware(authedRoutes)

  val router: HttpApp[Task] = Http4sRouting.make {
    serverMetrics {
      routes
    }
  }

  private def handleLogin(req: Request[Task]): Task[Response[Task]] = {
    req.as[LoginRequest].flatMap(loginRequest => userService.login(loginRequest)).flatMap {
      case Some(at) => Ok(at)
      case None     => Forbidden()
    }
  }

  private def withRoles(
      roles: Role*
  )(f: AuthedRequest[Task, User] => Task[Response[Task]])(request: AuthedRequest[Task, User]): Task[Response[Task]] = {
    val user = request.context
    //    ???

    if (roles.toSet.subsetOf(user.roles)) {
      f.apply(request)
    } else {
      Forbidden()
    }
  }
}

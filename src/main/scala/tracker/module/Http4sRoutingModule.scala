package tracker.module

import cats.implicits._
import com.avast.sst.http4s.server.Http4sRouting
import com.avast.sst.http4s.server.micrometer.MicrometerHttp4sServerMetricsModule
import io.circe.syntax._
import org.http4s.client.Client
import org.http4s.dsl.Http4sDsl
import org.http4s.{AuthedRoutes, HttpApp, HttpRoutes, Request, Response}
import tracker.config.Configuration
import tracker.model.User
import tracker.request.LoginRequest
import tracker.security.{AuthJwtMiddleware, DefaultAccessTokenParser}
import tracker.service.{RandomService, UserService}
import zio.Task
import zio.interop.catz._
import org.http4s.circe.CirceEntityCodec._

class Http4sRoutingModule(
                           randomService: RandomService,
                           userService: UserService,
                           client: Client[Task],
                           serverMetricsModule: MicrometerHttp4sServerMetricsModule[Task],
                           config: Configuration
                         ) extends Http4sDsl[Task] {

  import serverMetricsModule._

  private val helloWorldRoute = routeMetrics.wrap("hello")(Ok("Hello World!"))

  private val authMiddleware = AuthJwtMiddleware(DefaultAccessTokenParser, config.jwt, userService)

  val authedRoutes: AuthedRoutes[User, Task] = AuthedRoutes.of {
    case GET -> Root / "auth" as user => Ok(s"User: ${user}")
  }

  private val routes = HttpRoutes.of[Task] {
    case GET -> Root / "hello" => helloWorldRoute
    case GET -> Root / "random" => randomService.randomNumber.map(_.toString).flatMap(Ok(_))
    case GET -> Root / "circuit-breaker" => client.expect[String]("https://httpbin.org/status/500").flatMap(Ok(_))
    case GET -> Root / "user" / id => userService.find(id.toLong).map(_.asJson.toString).flatMap(Ok(_))
    case request@POST -> Root / "login" => handleLogin(request)
  } <+> authMiddleware(authedRoutes)


  val router: HttpApp[Task] = Http4sRouting.make {
    serverMetrics {
      routes
    }
  }

  private def handleLogin(req: Request[Task]): Task[Response[Task]] = {
    req.as[LoginRequest].flatMap(loginRequest => userService.login(loginRequest)).flatMap {
      case Some(at) => Ok(at)
      case None => Forbidden()
    }
  }

}

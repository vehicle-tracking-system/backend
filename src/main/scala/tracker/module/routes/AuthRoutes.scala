package tracker.module.routes
import io.circe.syntax.EncoderOps
import org.http4s.AuthedRoutes
import org.http4s.circe.CirceEntityCodec._
import tracker.NewPasswordRequest
import tracker.service.UserService
import zio.Task
import zio.interop.catz._

/**
  * Routes handling user authorization and auth user modifications.
  *
  * @param userService service providing operations with Users
  */
class AuthRoutes(userService: UserService) extends AuthedRoutesPart {
  override def routes: AuthedRoutes[tracker.User, Task] =
    AuthedRoutes.of {
      case GET -> Root as user             => Ok(user.asJson)
      case GET -> Root / "user" as user    => Ok(user.asJson)
      case GET -> Root / "refresh" as user => Ok(userService.generateToken(user).asJson)
      case request @ PUT -> Root / "password" as user =>
        request.req
          .as[NewPasswordRequest]
          .flatMap { newPass =>
            Ok(
              userService.changePassword(user.id.getOrElse(throw new IllegalStateException("Authenticate user is not in database")), newPass.password)
            )
          }
      case request @ PUT -> Root as user =>
        request.req
          .as[NewPasswordRequest]
          .flatMap { newPass =>
            Ok(
              userService.changePassword(user.id.getOrElse(throw new IllegalStateException("Authenticate user is not in database")), newPass.password)
            )
          }
    }
}

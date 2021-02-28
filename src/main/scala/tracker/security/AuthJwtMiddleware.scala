package tracker.security

import cats.data._
import org.http4s.{AuthedRoutes, Request}
import org.http4s.dsl.Http4sDsl
import org.http4s.server.AuthMiddleware
import org.http4s.util.CaseInsensitiveString
import tracker.User
import tracker.config.JwtConfig
import tracker.service.UserService
import zio.{IO, Task}
import zio.interop.catz._

class AuthJwtMiddleware

object AuthJwtMiddleware {
  private val dsl = new Http4sDsl[Task] {};

  import dsl._

  def apply(
      tokenParser: AccessTokenParser,
      jwtConfig: JwtConfig,
      userService: UserService
  ): AuthMiddleware[Task, User] = {
    val authUser: Kleisli[Task, Request[Task], Either[String, User]] = Kleisli { request =>
      (for {
        header <- IO.fromEither(
          request.headers
            .get(CaseInsensitiveString("authorization"))
            .map(_.value)
            .toRight("Authorization header not found")
        )
        token <- IO.fromEither(tokenParser.parseAccessToken(header, jwtConfig.secret))
        clientId <- IO.fromEither(token.clientId.toLongOption.toRight("Could not convert ID to number"))
        userOpt <- userService.getUserById(clientId).mapError(e => s"${e.getClass.getName}: ${e.getMessage}")
        user <- IO.fromEither(userOpt.toRight("User not found"))
      } yield {
        user
      }).either
    }

    val onFailure: AuthedRoutes[String, Task] = Kleisli(req => OptionT.liftF(Forbidden(req.context)))

    AuthMiddleware(authUser, onFailure)
  }
}

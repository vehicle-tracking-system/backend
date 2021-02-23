package tracker.security

import cats.data._
import org.http4s.dsl.Http4sDsl
import org.http4s.server.AuthMiddleware
import org.http4s.util.CaseInsensitiveString
import org.http4s.{AuthedRoutes, Request}
import tracker.config.JwtConfig
import tracker.model.User
import tracker.service.UserService
import zio.interop.catz._
import zio.{IO, Task}

object AuthJwtMiddleware {
  private val dsl = new Http4sDsl[Task] {};

  import dsl._

  def apply(tokenParser: AccessTokenParser, jwtConfig: JwtConfig, userService: UserService): AuthMiddleware[Task, User] = {
    val authUser: Kleisli[Task, Request[Task], Either[String, User]] = Kleisli { request =>
        (for {
          header <- IO.fromEither(request.headers.get(CaseInsensitiveString("authorization")).map(_.value).toRight("Authorization header not found"))
          token <- IO.fromEither(tokenParser.parseAccessToken(header, jwtConfig.secret))
          clientId <- IO.fromEither(token.clientId.toLongOption.toRight("Could not convert ID to number"))
          userOpt <- userService.find(clientId).mapError(e => s"${e.getClass.getName}: ${e.getMessage}")
          user <- IO.fromEither(userOpt.toRight("User not found"))
        } yield {
          user
        }).either

        //        val token = for {
        //          header <- request.headers.get(Authorization).map(_.value).toRight("Authorization header not found")
        //          token <- tokenParser.parseAccessToken(header, jwtConfig.secret)
        //        } yield token
        //
        //        (for {
        //          token <- IO.fromEither(token)
        //          clientId <- IO.fromEither(token.clientId.toLongOption.toRight("Could not convert ID to number"))
        //          userOpt <- userService.find(clientId).mapError(e => s"${e.getClass.getName}: ${e.getMessage}")
        //          user <- IO.fromEither(userOpt.toRight("User not found"))
        //        } yield {
        //          user
        //        }).either
        //
        //
        //        IO.fromEither(token).flatMap { token =>
        //          IO.fromEither(token.clientId.toLongOption.toRight("Could not convert ID to number"))
        //        }.flatMap { clientId =>
        //          userService.find(clientId).mapError(e => s"${e.getClass.getName}: ${e.getMessage}")
        //        }.flatMap { r =>
        //          IO.fromEither(r.toRight("User not found"))
        //        }.either


        //        val value3: IO[String, AccessToken] = IO.fromEither(token)
        //
        //        value3.flatMap { token =>
        //          val value4: Task[Option[User]] = userService.find(token.clientId.toLong) // TODO co kdz6 to nepujde
        //
        //          val value: IO[String, Option[User]] = value4.mapError(e => "error")
        //
        //          val value2: IO[ String, User] = value.flatMap { r =>
        //            val value1: IO[String, User] = IO.fromEither(r.toRight("User not found"))
        //            value1
        //          }
        //
        //          value2
        //        }.either
      }

    val onFailure: AuthedRoutes[String, Task] = Kleisli(req => OptionT.liftF(Forbidden(req.context)))

    AuthMiddleware(authUser, onFailure)
  }
}

package tracker.service

import io.circe.parser._
import tracker.User
import tracker.config.JwtConfig
import tracker.dao.UserDAO
import tracker._
import tracker.security.AccessTokenBuilder
import tracker.utils.PasswordUtility
import zio.Task

class UserService(userDAO: UserDAO, jwtConfig: JwtConfig) {

  def getUserById(id: Long): Task[Option[User]] = {
    userDAO.find(id)
  }

  def login(loginRequest: LoginRequest): Task[Option[AccessTokenResponse]] = {
    userDAO
      .findByUsername(loginRequest.username)
      .map(_.flatMap { user =>
        if (PasswordUtility.checkPassword(loginRequest.password, user.password)) {
          Some(
            AccessTokenResponse(
              AccessTokenBuilder.createToken(
                parse(s"""{"clientId":"${user.id.get}"}""")
                  .getOrElse(throw new IllegalStateException("JWT token creating error")),
                jwtConfig
              )
            )
          )
        } else None
      })
  }

  def persist(userRequest: UserRequest): Task[Either[String, Int]] = {
    userRequest.user.id match {
      case Some(_) =>
        userDAO
          .update(userRequest.user)
          .map(i =>
            if (i != 1) { Left("Saving new user failed") }
            else {
              Right(i)
            }
          )
      case None =>
        userDAO
          .persist(userRequest.user)
          .map(i =>
            if (i != 1) { Left("Updating user failed") }
            else {
              Right(i)
            }
          )
    }
  }
}

object UserService {
  def apply(userDAO: UserDAO, jwtConfig: JwtConfig) = new UserService(userDAO, jwtConfig)
}

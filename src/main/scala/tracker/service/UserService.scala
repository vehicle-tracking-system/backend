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

  def login(loginRequest: LoginRequest): Task[Option[LoginResponse]] = {
    userDAO
      .findByUsername(loginRequest.username)
      .map {
        _.flatMap { user =>
          if (PasswordUtility.checkPassword(loginRequest.password, user.password)) {
            Some {
              LoginResponse(
                AccessTokenBuilder.createToken(
                  AccessTokenPayload(user.id.get, user.roles),
                  jwtConfig
                ),
                user
              )
            }
          } else None
        }
      }
  }

  def persist(userRequest: UserRequest): Task[User] = {
    userRequest.user.id match {
      case Some(_) => userDAO.update(userRequest.user)
      case None    => userDAO.persist(userRequest.user)
    }
  }
}

object UserService {
  def apply(userDAO: UserDAO, jwtConfig: JwtConfig) = new UserService(userDAO, jwtConfig)
}

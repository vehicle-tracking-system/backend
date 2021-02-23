package tracker.service

import cats.implicits.catsSyntaxFlatMapOps
import doobie.implicits._
import doobie.implicits.javatime._
import doobie.util.transactor.Transactor
import io.circe.parser._
import org.log4s.{Logger, getLogger}
import tracker.config.JwtConfig
import tracker.model.User
import tracker.request.LoginRequest
import tracker.response.AccessTokenResponse
import tracker.security.AccessTokenBuilder
import tracker.utils.PasswordUtility
import zio.Task
import zio.interop.catz._

class UserService(transactor: Transactor[Task], jwtConfig: JwtConfig, logger: Logger = getLogger) {
  def find(id: Long): Task[Option[User]] = {
    Task {
      logger.debug(s"Selecting user with id: ${id}")
    } >> (sql"""SELECT id, name, created_at, deleted_at, password, username FROM USER WHERE ID = $id""")
      .query[User]
      .option
      .transact(transactor)
  }

  def login(loginRequest: LoginRequest): Task[Option[AccessTokenResponse]] = {
    import loginRequest._
    Task {
      logger.debug(s"Logging in user: $username")
    } >> (sql"""SELECT id, name, created_at, deleted_at, password, username FROM USER WHERE USERNAME = $username""")
      .query[User]
      .option
      .transact(transactor).map(_.flatMap(user =>
      if (PasswordUtility.checkPassword(loginRequest.password, user.password)) {
        Some(AccessTokenResponse(AccessTokenBuilder.createToken(parse(s"""{"clientId":"${user.id}"}""")
          .getOrElse(throw new IllegalStateException("JWT token creating error")), jwtConfig)))
      } else None))
  }

  def customLogin(loginRequest: LoginRequest): Task[Option[AccessTokenResponse]] = {
    import loginRequest._
    val hashedPassword = PasswordUtility.hashPassword(password)

    Task {
      logger.debug(s"Logging in user: $username")
    } >> (sql"""SELECT id, name, created_at, deleted_at, password, username FROM USER WHERE USERNAME = $username AND PASSWORD = $hashedPassword""")
      .query[User]
      .option
      .transact(transactor).map(_.map(user =>
      AccessTokenBuilder.createToken(parse(s"""{"clientId": "${user.id}" """)
        .getOrElse(throw new IllegalStateException("JWT token creating error")), jwtConfig)).map(stringToken => AccessTokenResponse(stringToken)))
  }
}

object UserService {
  def apply(transactor: Transactor[Task], jwtConfig: JwtConfig) = new UserService(transactor, jwtConfig)
}

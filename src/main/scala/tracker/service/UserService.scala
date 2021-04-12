package tracker.service

import cats.implicits.catsSyntaxFlatMapOps
import slog4s.{Logger, LoggerFactory}
import tracker.{DefaultPagination, LoginRequest, LoginResponse, Page, Pagination, User, UserRequest}
import tracker.config.JwtConfig
import tracker.dao.UserDAO
import tracker.security.{AccessTokenBuilder, AccessTokenPayload}
import tracker.utils.PasswordUtility
import zio.interop.catz._
import zio.Task

import java.time.ZonedDateTime

class UserService(userDAO: UserDAO, jwtConfig: JwtConfig, logger: Logger[Task], pagination: Pagination[User]) {

  def getUserById(id: Long): Task[Option[User]] = {
    userDAO.find(id)
  }

  def getAll(page: Option[Int], pageSize: Option[Int]): Task[Page[User]] =
    logger.info(s"Get page $page with size $pageSize from all users") >>
      pagination.getPage(page.fold(1)(identity), pageSize.fold(Int.MaxValue)(identity))

  def persist(userRequest: UserRequest): Task[User] = {
    userRequest.user.id match {
      case Some(_) => userDAO.update(userRequest.user)
      case None    => userDAO.persist(userRequest.user)
    }
  }

  def delete(userRequest: UserRequest): Task[User] =
    logger.info(s"Mark user ${userRequest.user.id} as deleted") >>
      userDAO
        .update(
          User(
            userRequest.user.id,
            userRequest.user.name,
            userRequest.user.createdAt,
            Some(ZonedDateTime.now()),
            "N/A",
            userRequest.user.username,
            userRequest.user.roles
          )
        )

  def login(loginRequest: LoginRequest): Task[Option[LoginResponse]] = {
    userDAO
      .findByUsername(loginRequest.username)
      .map {
        _.flatMap { user =>
          if (PasswordUtility.checkPassword(loginRequest.password, user.password)) {
            Some(generateToken(user))
          } else None
        }
      }
  }

  def generateToken(user: User): LoginResponse = {
    LoginResponse(
      AccessTokenBuilder.createToken(
        AccessTokenPayload(user.id.get, user.roles),
        jwtConfig
      ),
      user
    )
  }

  def changePassword(user: User, password: String): Task[User] = {
    userDAO.update(User(user.id, user.name, user.createdAt, user.deletedAt, PasswordUtility.hashPassword(password), user.username, user.roles))
  }
}

object UserService {
  def apply(userDAO: UserDAO, jwtConfig: JwtConfig, loggerFactory: LoggerFactory[Task]) =
    new UserService(userDAO, jwtConfig, loggerFactory.make("user-service"), DefaultPagination(userDAO.findAllActive, () => userDAO.count()))
}

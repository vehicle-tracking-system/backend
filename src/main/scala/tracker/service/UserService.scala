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

class UserService(userDAO: UserDAO, jwtConfig: JwtConfig, logger: Logger[Task]) {
  val pagination: Pagination[User] = DefaultPagination(userDAO.findAll, () => userDAO.count())

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
}

object UserService {
  def apply(userDAO: UserDAO, jwtConfig: JwtConfig, loggerFactory: LoggerFactory[Task]) =
    new UserService(userDAO, jwtConfig, loggerFactory.make("user-service"))
}

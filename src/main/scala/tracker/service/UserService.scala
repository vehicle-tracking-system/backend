package tracker.service

import cats.implicits.catsSyntaxFlatMapOps
import slog4s.{Logger, LoggerFactory}
import tracker._
import tracker.config.JwtConfig
import tracker.dao.UserDAO
import tracker.security.{AccessTokenBuilder, AccessTokenPayload}
import tracker.utils.PasswordUtility
import zio.interop.catz._
import zio.Task

class UserService(userDAO: UserDAO, jwtConfig: JwtConfig, logger: Logger[Task], paginationBuilder: PaginationBuilder) {
  private val getAllPagination = paginationBuilder.make((o, s) => userDAO.findAll(o, s), () => userDAO.count())
  private val getAllActivePagination = paginationBuilder.make((o, s) => userDAO.findAllActive(o, s), () => userDAO.countActive())

  def getUserById(id: Long): Task[Option[User]] = {
    userDAO.find(id)
  }

  def getAll(page: Option[Int], pageSize: Option[Int]): Task[Page[User]] = {
    logger.info(s"Get page $page with size $pageSize from all users") >>
      getAllPagination.getPage(page.fold(1)(identity), pageSize.fold(Int.MaxValue)(identity))
  }

  def getAllActive(page: Option[Int], pageSize: Option[Int]): Task[Page[User]] = {
    logger.info(s"Get page $page with size $pageSize from active users") >>
      getAllActivePagination.getPage(page.fold(1)(identity), pageSize.fold(Int.MaxValue)(identity))
  }

  def persist(userRequest: UserRequest): Task[User] = {
    userRequest.user.id match {
      case Some(_) => userDAO.update(userRequest.user)
      case None    => userDAO.persist(userRequest.user)
    }
  }

  def delete(userId: Long): Task[User] = {
    logger.info(s"Mark user ${userId} as deleted") >>
      userDAO.markAsDeleted(userId)
  }

  def login(loginRequest: LoginRequest): Task[Option[LoginResponse]] = {
    userDAO
      .findActiveByUsername(loginRequest.username)
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
    new UserService(userDAO, jwtConfig, loggerFactory.make("user-service"), DefaultPaginationBuilder)
}

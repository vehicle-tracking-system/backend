package tracker.module.routes

import io.circe.syntax.EncoderOps
import org.http4s.{AuthedRoutes, Request, Response}
import org.http4s.circe.CirceEntityCodec._
import tracker.{IdQueryParamMatcher, NewPasswordRequest, NewUserRequest, PageQueryParamMatcher, PageSizeQueryParamMatcher, UpdateUserRequest}
import tracker.Roles.{Admin, Reader}
import tracker.service.UserService
import tracker.module.routes.RoutesImplicits._
import zio.Task
import zio.interop.catz._

/**
  * Routes handling client HTTP request about Users.
  *
  * @param userService service providing API for operations with Users
  */
class UserRoutes(userService: UserService) extends AuthedRoutesPart {
  override def routes: AuthedRoutes[tracker.User, Task] =
    AuthedRoutes.of {
      case request @ GET -> Root :? IdQueryParamMatcher(id) as _ =>
        request.withRoles(Admin) {
          handleGetUser(id)
        }
      case request @ PUT -> Root as _ =>
        request.withRoles(Admin) {
          handleUpdateUser(request.req)
        }
      case request @ GET -> Root / "list" :? PageQueryParamMatcher(page) +& PageSizeQueryParamMatcher(pageSize) as _ =>
        request.withRoles(Reader) {
          userService.getAllActive(page, pageSize).flatMap(u => Ok(u.asJson))
        }
      case request @ POST -> Root / "new" as _ =>
        request.withRoles(Admin) {
          handleNewUser(request.req)
        }
      case request @ DELETE -> Root :? IdQueryParamMatcher(id) as _ =>
        request.withRoles(Admin) {
          userService.delete(id).flatMap(u => Ok(u.asJson))
        }
      case request @ PUT -> Root / "pass" :? IdQueryParamMatcher(id) as _ =>
        request.withRoles(Admin) {
          handleChangePassword(request.req, id)
        }
    }

  private def handleNewUser(req: Request[Task]): Task[Response[Task]] = {
    req
      .as[NewUserRequest]
      .flatMap(userService.persist)
      .flatMap(Ok(_))
  }

  private def handleUpdateUser(req: Request[Task]): Task[Response[Task]] = {
    req
      .as[UpdateUserRequest]
      .flatMap(userService.persist)
      .either
      .flatMap {
        case Right(user) => Ok(user.asJson)
        case Left(e)     => BadRequest(e.toString)
      }
  }

  private def handleGetUser(id: Long): Task[Response[Task]] = {
    userService.getUserById(id).flatMap {
      case Some(user) => Ok(user.asJson)
      case None       => NotFound("User not found")
    }
  }

  private def handleChangePassword(req: Request[Task], userId: Long): Task[Response[Task]] = {
    req
      .as[NewPasswordRequest]
      .flatMap { np =>
        userService
          .changePassword(userId, np.password)
          .flatMap(Ok(_))
      }
  }
}

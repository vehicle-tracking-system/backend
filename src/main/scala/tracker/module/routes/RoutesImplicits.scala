package tracker.module.routes

import org.http4s.{AuthedRequest, Response}
import org.http4s.dsl.Http4sDsl
import tracker.{Role, User}
import tracker.Roles.Admin
import zio.Task
import zio.interop.catz.monadErrorInstance

object RoutesImplicits extends Http4sDsl[Task] {

  /**
    * Check if user, that calling endpoint has right roles.
    * If roles check pass, request is normally proceeded. If roles check failed, send Forbidden response (404).
    * @param request
    */
  implicit class WithRoles(request: AuthedRequest[Task, User]) {
    def withRoles(
        roles: Role*
    )(f: Task[Response[Task]]): Task[Response[Task]] = {
      val user = request.context
      val userRoles = user.roles.flatMap(_.subRoles())
      if (roles.toSet.subsetOf(userRoles) || user.roles.contains(Admin)) {
        f
      } else {
        Forbidden()
      }
    }
  }
}

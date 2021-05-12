package tracker.module.routes

import org.http4s.{AuthedRoutes, HttpRoutes}
import org.http4s.dsl.Http4sDsl
import tracker.User
import zio.Task

/**
  * Request to this route must be called with authorization header with auth token.
  */
trait AuthedRoutesPart extends Http4sDsl[Task] {
  def routes: AuthedRoutes[User, Task]
}

trait RoutesPart extends Http4sDsl[Task] {
  def routes: HttpRoutes[Task]
}

package tracker.module.routes

import io.circe.syntax.EncoderOps
import org.http4s.circe.CirceEntityCodec._
import org.http4s.{headers, AuthedRoutes, MediaType, Request, Response}
import org.http4s.headers.`Content-Type`
import tracker.service.TrackerService
import tracker.{IdQueryParamMatcher, NewTrackerRequest, PageQueryParamMatcher, PageSizeQueryParamMatcher, UpdateTrackerRequest}
import tracker.Roles.{Editor, Reader}
import tracker.module.routes.RoutesImplicits._
import zio.Task
import zio.interop.catz._

class TrackerRoutes(trackerService: TrackerService) extends AuthedRoutesPart {
  override def routes: AuthedRoutes[tracker.User, Task] =
    AuthedRoutes.of {
      case request @ GET -> Root :? IdQueryParamMatcher(id) as _ =>
        request.withRoles(Reader) {
          trackerService.get(id).flatMap(_.fold(NotFound())(Ok(_)))
        }
      case request @ PUT -> Root as _ =>
        request.withRoles(Editor) {
          handleUpdateTracker(request.req)
        }
      case request @ GET -> Root / "list" :? PageQueryParamMatcher(page) +& PageSizeQueryParamMatcher(pageSize) as _ =>
        request.withRoles(Reader) {
          trackerService.getAll(page, pageSize).flatMap(t => Ok(t.asJson))
        }
      case request @ POST -> Root / "new" as _ =>
        request.withRoles(Editor) {
          handleNewTracker(request.req)
        }
      case request @ DELETE -> Root :? IdQueryParamMatcher(id) as _ =>
        request.withRoles(Editor) {
          trackerService.delete(id).flatMap(t => Ok(t.asJson))
        }
      case request @ PUT -> Root / "revoke" as _ =>
        request.withRoles(Editor) {
          handleRevokeTrackerToken(request.req)
        }
      case request @ GET -> Root / "config" :? IdQueryParamMatcher(tracker) as _ =>
        request.withRoles(Reader) {
          trackerService
            .configFile(tracker)
            .flatMap {
              _.fold {
                NotFound()
              } { stream =>
                {
                  Ok(stream)
                    .map(_.withContentType(`Content-Type`(MediaType.application.json)))
                    .map(_.putHeaders(headers.`Content-Disposition`("attachment", Map(("filename", s"config_$tracker.gpx")))))
                }
              }
            }
        }
    }

  private def handleNewTracker(req: Request[Task]): Task[Response[Task]] = {
    req
      .as[NewTrackerRequest]
      .flatMap(trackerService.persist)
      .flatMap(t => Ok(t.asJson))
  }

  private def handleUpdateTracker(req: Request[Task]): Task[Response[Task]] = {
    req
      .as[UpdateTrackerRequest]
      .flatMap(trackerService.update)
      .flatMap(t => Ok(t.asJson))
  }

  private def handleRevokeTrackerToken(req: Request[Task]): Task[Response[Task]] = {
    req
      .as[UpdateTrackerRequest]
      .flatMap(trackerService.updateAccessToken)
      .flatMap(t => Ok(t.asJson))
  }
}

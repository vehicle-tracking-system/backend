package tracker.module.routes

import org.http4s._
import org.http4s.headers.`Content-Type`
import tracker.{User, _}
import tracker.Roles._
import tracker.module.routes.RoutesImplicits._
import tracker.service._
import zio.Task
import zio.interop.catz._

class GPX(positionService: PositionService) extends AuthedRoutesPart {
  override def routes: AuthedRoutes[User, Task] =
    AuthedRoutes.of {
      case request @ GET -> Root :? IdQueryParamMatcher(track) as _ =>
        request.withRoles(Reader) {
          positionService
            .generateGPX(track)
            .flatMap {
              _.fold {
                NotFound()
              } { stream =>
                {
                  Ok(stream)
                    .map(_.withContentType(`Content-Type`(MediaType.application.`gpx+xml`)))
                    .map(_.putHeaders(headers.`Content-Disposition`("attachment", Map(("filename", s"track_$track.gpx")))))
                }
              }
            }
        }
    }
}

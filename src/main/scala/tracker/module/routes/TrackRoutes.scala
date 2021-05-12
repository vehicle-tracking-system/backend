package tracker.module.routes

import io.circe.syntax.EncoderOps
import org.http4s.{AuthedRoutes, Response}
import org.http4s.circe.CirceEntityCodec._
import tracker.service.TrackService
import tracker.module.routes.RoutesImplicits._
import tracker.{IdQueryParamMatcher, OptionalVehicleQueryParamMatcher, PageQueryParamMatcher, PageSizeQueryParamMatcher}
import tracker.Roles.Reader
import zio.Task
import zio.interop.catz._

/**
  * Routes handling client HTTP request about Tracks.
  *
  * @param trackService service providing API for operations with Tracks
  */
class TrackRoutes(trackService: TrackService) extends AuthedRoutesPart {
  override def routes: AuthedRoutes[tracker.User, Task] =
    AuthedRoutes.of {
      case request @ GET -> Root / "list" :? OptionalVehicleQueryParamMatcher(vehicleId) +& PageQueryParamMatcher(
            page
          ) +& PageSizeQueryParamMatcher(
            pageSize
          ) as _ =>
        request.withRoles(Reader) {
          handleGetAllTracks(vehicleId, page, pageSize)
        }
      case request @ GET -> Root :? IdQueryParamMatcher(id) as _ =>
        request.withRoles(Reader) {
          trackService.get(id).flatMap(Ok(_))
        }
      case request @ GET -> Root / "positions" :? IdQueryParamMatcher(id) as _ =>
        request.withRoles(Reader) {
          trackService.getPositions(id).flatMap(_.fold(NotFound())(p => Ok(p)))
        }
    }

  private def handleGetAllTracks(vehicleId: Option[Long], page: Option[Int], pageSize: Option[Int]): Task[Response[Task]] = {
    vehicleId.fold(trackService.getAll(page, pageSize).flatMap(t => Ok(t.asJson)))(id =>
      trackService.getByVehicle(id, page, pageSize).flatMap(t => Ok(t.asJson))
    )
  }
}

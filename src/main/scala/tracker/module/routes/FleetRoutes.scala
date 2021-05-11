package tracker.module.routes

import io.circe.syntax.EncoderOps
import org.http4s.circe.CirceEntityCodec._
import org.http4s.{AuthedRoutes, Request, Response}
import tracker.{IdQueryParamMatcher, NewFleetRequest, NotFoundResponse, PageQueryParamMatcher, PageSizeQueryParamMatcher}
import tracker.Roles.Reader
import tracker.module.routes.RoutesImplicits._
import tracker.service.FleetService
import zio.Task
import zio.interop.catz._

/**
  * Routes handling client HTTP request about Fleets.
  * @param fleetService - service providing API for operations with Fleets
  */
class FleetRoutes(fleetService: FleetService) extends AuthedRoutesPart {
  override def routes: AuthedRoutes[tracker.User, Task] =
    AuthedRoutes.of {
      case request @ GET -> Root :? IdQueryParamMatcher(id) as _ =>
        request.withRoles(Reader) {
          handleGetFleet(id)
        }
      case request @ GET -> Root / "list" :? PageQueryParamMatcher(page) +& PageSizeQueryParamMatcher(pageSize) as _ =>
        request.withRoles(Reader) {
          fleetService.getAll(page, pageSize).flatMap(t => Ok(t.asJson))
        }
      case request @ POST -> Root / "new" as _ =>
        request.withRoles(Reader) {
          handleNewFleet(request.req)
        }
    }

  private def handleGetFleet(id: Long): Task[Response[Task]] = {
    fleetService.get(id).flatMap {
      case Some(fleet) => Ok(fleet.asJson)
      case None        => NotFound(NotFoundResponse("Fleet not found").asJson)
    }
  }

  private def handleNewFleet(req: Request[Task]): Task[Response[Task]] = {
    req
      .as[NewFleetRequest]
      .flatMap(fleetService.persist)
      .flatMap(Ok(_))
  }
}

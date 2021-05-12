package tracker.module.routes

import io.circe.syntax.EncoderOps
import org.http4s.{AuthedRoutes, Request, Response}
import org.http4s.circe.CirceEntityCodec._
import tracker._
import tracker.Roles.{Editor, Reader}
import tracker.service.{PositionService, TrackService, VehicleService}
import tracker.module.routes.RoutesImplicits._
import zio.Task
import zio.interop.catz._

/**
  * Routes handling client HTTP request about Vehicles.
  *
  * @param vehicleService service providing API for operations with Vehicles
  * @param positionService service providing API for operations with Positions
  * @param trackService service providing API for operations with Tracks
  */
class VehicleRoutes(vehicleService: VehicleService, positionService: PositionService, trackService: TrackService) extends AuthedRoutesPart {
  override def routes: AuthedRoutes[tracker.User, Task] =
    AuthedRoutes.of {
      case request @ GET -> Root :? IdQueryParamMatcher(id) as _ =>
        request.withRoles(Reader) {
          handleGetVehicle(id)
        }
      case request @ PUT -> Root as _ =>
        request.withRoles(Editor) {
          handleUpdateVehicle(request.req)
        }
      case request @ GET -> Root / "list" :? PageQueryParamMatcher(page) +& PageSizeQueryParamMatcher(pageSize) as _ =>
        request.withRoles(Reader) {
          vehicleService.getAllActive(page, pageSize).flatMap(p => Ok(p.asJson))
        }
      case request @ POST -> Root / "new" as _ =>
        request.withRoles(Editor) {
          handleNewVehicle(request.req)
        }
      case request @ DELETE -> Root :? IdQueryParamMatcher(id) as _ =>
        request.withRoles(Editor) {
          vehicleService.delete(id).flatMap(Ok(_))
        }
      case request @ POST -> Root / "positions" as _ =>
        request.withRoles(Reader) {
          handleGetVehiclePositions(request.req)
        }
      case request @ GET -> Root / "position" :? IdQueryParamMatcher(id) as _ =>
        request.withRoles(Reader) {
          handleGetLastVehiclePosition(id)
        }
      case request @ POST -> Root / "history" as _ =>
        request.withRoles(Reader) {
          handleGetVehiclePositionHistory(request.req)
        }
      case request @ GET -> Root / "active"
          :? IdQueryParamMatcher(id)
          +& MonthQueryParamMatcher(month)
          +& YearQueryParamMatcher(year) as _ =>
        request.withRoles(Reader) {
          positionService.getActiveDays(id, month, year).flatMap(days => Ok(days.asJson))
        }
    }

  private def handleGetVehicle(id: Long): Task[Response[Task]] = {
    vehicleService.get(id).flatMap {
      case Some(vehicle) => Ok(vehicle.asJson)
      case None          => NotFound(NotFoundResponse("Vehicle not found").asJson)
    }
  }

  private def handleUpdateVehicle(req: Request[Task]): Task[Response[Task]] = {
    val updatedVehicle = for {
      vehicle <- req.as[UpdateVehicleRequest]
      _ <- vehicleService.update(vehicle)
      res <- vehicleService.setFleets(vehicle.data)
    } yield res
    Ok(updatedVehicle)
  }

  private def handleNewVehicle(req: Request[Task]): Task[Response[Task]] = {
    val newVehicle = for {
      vehicleRequest <- req.as[NewVehicleRequest]
      newVehicle <- vehicleService.persist(vehicleRequest)
      vehicleWithFleets <- vehicleService.setFleets(newVehicle, vehicleRequest.fleetsId)
    } yield vehicleWithFleets
    Ok(newVehicle)
  }

  private def handleGetVehiclePositions(req: Request[Task]): Task[Response[Task]] = {
    req
      .as[VehiclePositionsRequest]
      .flatMap(positionService.getByVehicle)
      .flatMap(Ok(_))
  }

  private def handleGetVehiclePositionHistory(req: Request[Task]): Task[Response[Task]] = {
    for {
      request <- req.as[VehiclePositionHistoryRequest]
      position <- positionService.getVehiclePositionHistory(request)
      track <- trackService.getInRange(request.vehicleId, request.since, request.until)
      response <- Ok(VehicleHistoryResponse(position, track).asJson)
    } yield response
  }

  private def handleGetLastVehiclePosition(vehicleId: Long): Task[Response[Task]] = {
    positionService.getLastVehiclePosition(vehicleId).flatMap {
      case Some(position) => Ok(position.asJson)
      case None           => NotFound("Vehicle not found")
    }
  }
}

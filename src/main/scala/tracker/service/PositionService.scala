package tracker.service

import cats.implicits.catsSyntaxFlatMapOps
import slog4s.{Logger, LoggerFactory}
import tracker.{Position, PositionRequest, VehiclePositionHistoryRequest, VehiclePositionsRequest}
import tracker.dao.PositionDAO
import zio.Task
import zio.interop.catz._

class PositionService(positionDAO: PositionDAO, logger: Logger[Task]) {
  def get(id: Long): Task[Option[Position]] = positionDAO.find(id)

  def getByVehicle(request: VehiclePositionsRequest): Task[List[Position]] = {
    logger.debug(s"Getting position by vehicle id: ${request.vehicleId} - page ${request.page} with size ${request.pageSize}") >>
      positionDAO.findByVehicle(request.vehicleId, (request.page - 1) * request.pageSize, request.pageSize)
  }

  def persist(request: PositionRequest): Task[Position] = {
    request.position.id match {
      case Some(_) => positionDAO.update(request.position)
      case None    => positionDAO.persist(request.position)
    }
  }

  def persist(positions: List[Position]): Task[Int] = {
    positionDAO.persistList(positions)
  }

  def getVehiclePositionHistory(request: VehiclePositionHistoryRequest): Task[List[Position]] = {
    logger.debug(s"Searching history of vehicle ${request.vehicleId}, since ${request.since} until ${request.until}") >>
      positionDAO.findVehicleHistory(request.vehicleId, request.since, request.until)
  }

  def getLastVehiclePosition(vehicleId: Long): Task[Option[Position]] = { positionDAO.findLastVehiclePosition(vehicleId) }
}

object PositionService {
  def apply(
      positionDAO: PositionDAO,
      loggerFactory: LoggerFactory[Task]
  ): PositionService =
    new PositionService(positionDAO, loggerFactory.make("position-service"))
}

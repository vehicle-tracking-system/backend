package tracker.service

import tracker.{Position, PositionRequest, VehiclePositionHistoryRequest, VehiclePositionsRequest}
import tracker.dao.PositionDAO
import zio.Task

import java.time.temporal.ChronoUnit

class PositionService(positionDAO: PositionDAO) {
  def get(id: Long): Task[Option[Position]] = positionDAO.find(id)

  def getByVehicle(request: VehiclePositionsRequest): Task[List[Position]] = {
    if (request.pageSize * request.page > 1000)
      Task { List.empty }
    else
      positionDAO.findByVehicle(request.vehicleId, (request.page - 1) * request.pageSize, request.pageSize)
  }

  def persist(request: PositionRequest): Task[Position] = {
    request.position.id match {
      case Some(_) => positionDAO.update(request.position)
      case None    => positionDAO.persist(request.position)
    }
  }

  def getVehiclePositionHistory(request: VehiclePositionHistoryRequest): Task[List[Position]] = {
    if (ChronoUnit.DAYS.between(request.since, request.until) > 30)
      Task { List.empty }
    else
      positionDAO.findVehicleHistory(request.vehicleId, request.since, request.until)
  }

  def getLastVehiclePosition(vehicleId: Long): Task[Option[Position]] = {
    positionDAO.findLastVehiclePosition(vehicleId)
  }
}

object PositionService {
  def apply(positionDAO: PositionDAO): PositionService = new PositionService(positionDAO)
}

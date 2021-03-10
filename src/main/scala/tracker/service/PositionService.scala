package tracker.service

import tracker.{Position, PositionRequest, VehiclePositionsRequest}
import tracker.dao.PositionDAO
import zio.Task

class PositionService(positionDAO: PositionDAO) {
  def find(id: Long): Task[Option[Position]] = positionDAO.find(id)

  def findByVehicle(request: VehiclePositionsRequest): Task[List[Position]] = {
    if (request.pageSize * request.page > 1000)
      Task { List.empty }
    else
      positionDAO.findByVehicle(request.vehicleId, (request.page - 1) * request.pageSize, request.pageSize)
  }

  def persist(request: PositionRequest): Task[Position] = {
    request.position.id match {
      case Some(_) =>
        positionDAO.update(request.position)
      case None =>
        positionDAO.persist(request.position)
    }
  }
}

object PositionService {
  def apply(positionDAO: PositionDAO): PositionService = new PositionService(positionDAO)
}

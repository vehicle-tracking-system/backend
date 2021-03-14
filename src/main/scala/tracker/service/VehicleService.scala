package tracker.service

import tracker.{Vehicle, VehiclesRequest}
import tracker.dao.VehicleDAO
import zio.Task

class VehicleService(vehicleDAO: VehicleDAO) {
  def get(id: Long): Task[Option[Vehicle]] = vehicleDAO.find(id)

  def getList(ids: Set[Long]): Task[List[Vehicle]] = vehicleDAO.findList(ids.toList)

  def getAll(request: VehiclesRequest): Task[List[Vehicle]] = vehicleDAO.findAll((request.page - 1) * request.pageSize, request.pageSize)
}

object VehicleService {
  def apply(vehicleDAO: VehicleDAO): VehicleService = new VehicleService(vehicleDAO)
}

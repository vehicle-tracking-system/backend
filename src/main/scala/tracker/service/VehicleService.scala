package tracker.service

import tracker.{DefaultPagination, Page, Pagination, Vehicle, VehiclesRequest}
import tracker.dao.VehicleDAO
import zio.Task

class VehicleService(vehicleDAO: VehicleDAO) {
  val pagination: Pagination[Vehicle] = DefaultPagination(vehicleDAO.findAll, () => vehicleDAO.count())

  def get(id: Long): Task[Option[Vehicle]] = vehicleDAO.find(id)

  def getList(ids: Set[Long]): Task[List[Vehicle]] = vehicleDAO.findList(ids.toList)

  def getAll(request: VehiclesRequest): Task[Page[Vehicle]] = pagination.getPage(request.page, request.pageSize)
}

object VehicleService {
  def apply(vehicleDAO: VehicleDAO): VehicleService = new VehicleService(vehicleDAO)
}

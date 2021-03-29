package tracker.service

import tracker.{DefaultPagination, Page, Pagination, Vehicle}
import tracker.dao.VehicleDAO
import zio.Task

class VehicleService(vehicleDAO: VehicleDAO) {
  val pagination: Pagination[Vehicle] = DefaultPagination(vehicleDAO.findAll, () => vehicleDAO.count())

  def get(id: Long): Task[Option[Vehicle]] = vehicleDAO.find(id)

  def getList(ids: Set[Long]): Task[List[Vehicle]] = vehicleDAO.findList(ids.toList)

  def getAll(page: Option[Int], pageSize: Option[Int]): Task[Page[Vehicle]] =
    pagination.getPage(page.fold(1)(identity), pageSize.fold(Int.MaxValue)(identity))
}

object VehicleService {
  def apply(vehicleDAO: VehicleDAO): VehicleService = new VehicleService(vehicleDAO)
}

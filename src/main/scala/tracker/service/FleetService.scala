package tracker.service

import tracker.dao.FleetDAO
import tracker.{DefaultPagination, Fleet, NewFleetRequest, Page, Pagination}
import zio.Task

class FleetService(fleetDAO: FleetDAO, pagination: Pagination[Fleet]) {
  def get(id: Long): Task[Option[Fleet]] = fleetDAO.find(id)

  def getAll(page: Option[Int], pageSize: Option[Int]): Task[Page[Fleet]] =
    pagination.getPage(page.fold(1)(identity), pageSize.fold(Int.MaxValue)(identity))

  def persist(req: NewFleetRequest): Task[Fleet] = {
    fleetDAO.persist(req.fleet)
  }
}

object FleetService {
  def apply(fleetDAO: FleetDAO): FleetService = new FleetService(fleetDAO, DefaultPagination(fleetDAO.findAll, () => fleetDAO.count()))
}

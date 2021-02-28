package tracker.service

import tracker.dao.FleetDAO
import tracker.Fleet
import zio.Task

class FleetService(fleetDAO: FleetDAO) {
  def find(id: Long): Task[Option[Fleet]] = fleetDAO.find(id)
}

object FleetService {
  def apply(fleetDAO: FleetDAO): FleetService = new FleetService(fleetDAO)
}

package tracker.service

import tracker.dao.VehicleDAO
import tracker.Vehicle
import zio.Task

class VehicleService(vehicleDAO: VehicleDAO) {
  def find(id: Long): Task[Option[Vehicle]] = vehicleDAO.find(id)
}

object VehicleService {
  def apply(vehicleDAO: VehicleDAO): VehicleService = new VehicleService(vehicleDAO)
}

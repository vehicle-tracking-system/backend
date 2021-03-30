package tracker.service

import cats.implicits._
import tracker._
import tracker.dao.{VehicleDAO, VehicleFleetDAO}
import zio.Task
import zio.interop.catz._

class VehicleService(vehicleDAO: VehicleDAO, vehicleFleetDAO: VehicleFleetDAO, pagination: Pagination[Vehicle]) {
  def get(id: Long): Task[Option[Vehicle]] = vehicleDAO.find(id)

  def getList(ids: Set[Long]): Task[List[Vehicle]] = vehicleDAO.findList(ids.toList)

  def getAll(page: Option[Int], pageSize: Option[Int]): Task[Page[Vehicle]] =
    pagination.getPage(page.fold(1)(identity), pageSize.fold(Int.MaxValue)(identity))

  def persist(req: NewVehicleRequest): Task[Vehicle] = {
    vehicleDAO.persist(req.vehicle)
  }

  def update(req: UpdateVehicleRequest): Task[Vehicle] = {
    vehicleDAO.update(req.data)
  }

  def setFleets(vehicle: Vehicle): Task[Option[Vehicle]] = {
    vehicleFleetDAO.setToVehicle(vehicle) >> vehicleDAO.find {
      vehicle.vehicle.id.getOrElse(throw new IllegalStateException("Vehicle without identifier"))
    }
  }

  def setFleets(vehicle: Vehicle, fleetsId: List[Long]): Task[Option[Vehicle]] =
    vehicleFleetDAO.setToVehicle(Vehicle(vehicle.vehicle, fleetsId.map(id => LightFleet(Some(id), "N/A")))) >>
      vehicleDAO.find(vehicle.vehicle.id.getOrElse(throw new IllegalStateException("Vehicle without identifier")))

}

object VehicleService {
  def apply(vehicleDAO: VehicleDAO, vehicleFleetDAO: VehicleFleetDAO): VehicleService =
    new VehicleService(vehicleDAO, vehicleFleetDAO, DefaultPagination(vehicleDAO.findAll, () => vehicleDAO.count()))
}

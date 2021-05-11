package tracker.service

import cats.implicits._
import tracker._
import tracker.dao.{VehicleDAO, VehicleFleetDAO}
import tracker.utils.{Clock, DefaultClock}
import zio.Task
import zio.interop.catz._

class VehicleService(vehicleDAO: VehicleDAO, vehicleFleetDAO: VehicleFleetDAO, paginationBuilder: PaginationBuilder, clock: Clock) {
  private val getAllPagination: Pagination[Vehicle] =
    paginationBuilder.make[Vehicle]((p, s) => vehicleDAO.findAll(p, s), () => vehicleDAO.count())
  private val getActivePagination: Pagination[Vehicle] =
    paginationBuilder.make[Vehicle]((p, s) => vehicleDAO.findAllActive(p, s), () => vehicleDAO.countActive())

  def get(id: Long): Task[Option[Vehicle]] = vehicleDAO.find(id)

  def getList(ids: Set[Long]): Task[List[Vehicle]] = vehicleDAO.findList(ids.toList)

  def getAll(page: Option[Int], pageSize: Option[Int]): Task[Page[Vehicle]] =
    getAllPagination.getPage(page.fold(1)(identity), pageSize.fold(Int.MaxValue)(identity))

  def getAllActive(page: Option[Int], pageSize: Option[Int]): Task[Page[Vehicle]] =
    getActivePagination.getPage(page.fold(1)(identity), pageSize.fold(Int.MaxValue)(identity))

  def persist(req: NewVehicleRequest): Task[Vehicle] = {
    vehicleDAO.persist(LightVehicle(None, req.vehicle.name, clock.now()))
  }

  def delete(vehicleId: Long): Task[Vehicle] = {
    vehicleDAO.markAsDeleted(vehicleId)
  }

  def update(req: UpdateVehicleRequest): Task[Vehicle] = {
    vehicleDAO.update(req.data)
  }

  def setFleets(vehicle: Vehicle): Task[Vehicle] = {
    vehicleFleetDAO.setToVehicle(vehicle) >> vehicleDAO
      .find(vehicle.vehicle.ID)
      .map(_.getOrElse(throw new IllegalStateException("Vehicle with updated fleet not found")))
  }

  def setFleets(vehicle: Vehicle, fleetsId: List[Long]): Task[Vehicle] = {
    vehicleFleetDAO.setToVehicle(Vehicle(vehicle.vehicle, fleetsId.map(id => LightFleet(Some(id), "N/A")))) >>
      vehicleDAO.find(vehicle.vehicle.ID).map(_.getOrElse(throw new IllegalStateException("Vehicle with updated fleet not found")))
  }

}

object VehicleService {
  def apply(vehicleDAO: VehicleDAO, vehicleFleetDAO: VehicleFleetDAO, clock: Clock = DefaultClock): VehicleService =
    new VehicleService(vehicleDAO, vehicleFleetDAO, DefaultPaginationBuilder, clock)
}

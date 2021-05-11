package tracker.mocked

import tracker.{LightFleet, LightVehicle, Vehicle}
import tracker.dao.VehicleDAO
import zio.Task

class VehicleDAOMock(var store: List[Vehicle] = List.empty, vehicleFleetDaoMock: VehicleFleetDaoMock) extends VehicleDAO with DAOMock[Vehicle] {
  override def persist(vehicle: LightVehicle): Task[Vehicle] =
    Task.effect {
      maxId = maxId + 1
      val v = Vehicle(
        LightVehicle(
          Some(maxId),
          vehicle.name,
          vehicle.createdAt,
          vehicle.deletedAt
        ),
        List.empty
      )
      store = store.appended(v)
      v
    }

  override def update(vehicle: Vehicle): Task[Vehicle] =
    Task.effect {
      val size = store.size
      store = store.filter(p => p.vehicle.id != vehicle.vehicle.id)
      if (size == store.size) {
        throw new IllegalStateException()
      } else {
        store = store.appended(vehicle)
        vehicle
      }
    }

  override def delete(vehicle: Vehicle): Task[Int] = {
    if (store.nonEmpty) {
      store = store.filter(p => p.vehicle.id != vehicle.vehicle.id)
      Task.effect(1)
    } else Task.effect(0)
  }

  override def markAsDeleted(vehicleId: Long): Task[Vehicle] =
    throw new NotImplementedError("Vehicle mark as remove is not implemented for testing purposes.")

  override def find(id: Long): Task[Option[Vehicle]] =
    for {
      vehicle <- Task.effect(store.find(u => u.vehicle.ID == id))
      fleets = vehicleFleetDaoMock.store.filter(vf => vf.vehicleId == id).map(vf => LightFleet(Some(vf.fleetId), ""))
      vf <- Task.effect(vehicle.map(v => Vehicle(v.vehicle, fleets)))
    } yield vf

  override def findAll(offset: Int, limit: Int): Task[List[Vehicle]] = Task.effect(store)

  override def findAllActive(offset: Int, limit: Int): Task[List[Vehicle]] =
    Task.effect(store.filter(u => u.vehicle.deletedAt.isEmpty))

  override def findList(ids: List[Long]): Task[List[Vehicle]] =
    Task.effect(store.filter(u => ids.contains(u.vehicle.ID)))

  override def count(): Task[Int] = Task.effect(store.size)

  override def countActive(): Task[Int] =
    Task.effect(store.count(u => u.vehicle.deletedAt.isEmpty))

  override var maxId: Long = 0
}

object VehicleDAOMock {
  def apply(vehicles: List[Vehicle], vehicleFleetDaoMock: VehicleFleetDaoMock): VehicleDAOMock = {
    new VehicleDAOMock(vehicles, vehicleFleetDaoMock)
  }
}

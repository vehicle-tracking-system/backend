package tracker.mocked

import tracker.{Vehicle, VehicleFleet}
import tracker.dao.VehicleFleetDAO
import zio.Task

case class VehicleFleetDaoMock(var store: Store) extends VehicleFleetDAO with DAOMock[VehicleFleet] {
  override def persist(vehicleFleet: VehicleFleet): Task[Int] =
    Task.effect {
      maxId = maxId + 1
      val v = VehicleFleet(
        maxId,
        vehicleFleet.vehicleId,
        vehicleFleet.fleetId
      )
      store.vehicleFleets = store.vehicleFleets.appended(v)
      1
    }

  override def delete(vehicleFleet: VehicleFleet): Task[Int] =
    if (store.vehicleFleets.nonEmpty) {
      store.vehicleFleets = store.vehicleFleets.filter(p => p.id != vehicleFleet.id)
      Task.effect(1)
    } else Task.effect(0)

  override def find(id: Long): Task[Option[VehicleFleet]] =
    Task.effect(store.vehicleFleets.find(u => u.id == id))

  override def persistList(vehicleFleet: List[VehicleFleet]): Task[Int] =
    throw new NotImplementedError("Persist list is not implemented for testing purposes.")

  override def setToVehicle(vehicle: Vehicle): Task[Int] = {
    store.vehicleFleets = store.vehicleFleets.filter(p => p.vehicleId != vehicle.vehicle.ID)
    store.vehicleFleets = store.vehicleFleets.appendedAll(vehicle.fleets.map { f =>
      this.maxId = maxId + 1
      VehicleFleet(maxId, vehicle.vehicle.ID, f.ID)
    })
    Task.effect(1)
  }

  override var maxId: Long = 0
}

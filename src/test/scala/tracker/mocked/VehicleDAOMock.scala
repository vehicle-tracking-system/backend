package tracker.mocked

import tracker.{LightVehicle, Vehicle}
import tracker.dao.VehicleDAO
import zio.Task

case class VehicleDAOMock(var store: Store) extends VehicleDAO with DAOMock[Vehicle] {
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
      store.vehicles = store.vehicles.appended(v.toLight)
      v
    }

  override def update(vehicle: Vehicle): Task[Vehicle] =
    Task.effect {
      val size = store.vehicles.size
      store.vehicles = store.vehicles.filter(p => p.id != vehicle.vehicle.id)
      if (size == store.vehicles.size) {
        throw new IllegalStateException()
      } else {
        store.vehicles = store.vehicles.appended(vehicle.toLight)
        vehicle
      }
    }

  override def delete(vehicle: Vehicle): Task[Int] = {
    if (store.vehicles.nonEmpty) {
      store.vehicles = store.vehicles.filter(p => p.id != vehicle.vehicle.id)
      Task.effect(1)
    } else Task.effect(0)
  }

  override def markAsDeleted(vehicleId: Long): Task[Vehicle] =
    throw new NotImplementedError("Vehicle mark as remove is not implemented for testing purposes.")

  override def find(id: Long): Task[Option[Vehicle]] = {
    val fleetsId = store.vehicleFleets.filter(vf => vf.vehicleId == id).map(_.fleetId)
    val fleets = store.fleets.filter(v => fleetsId.contains(v.ID))
    Task.effect(store.vehicles.find(u => u.ID == id).map(lv => Vehicle(lv, fleets)))
  }

  override def findAll(offset: Int, limit: Int): Task[List[Vehicle]] =
    Task.effect {
      var allVehicles: List[Vehicle] = List.empty
      store.vehicles.foreach(vf => {
        val fleetId = store.vehicleFleets.filter(lf => lf.vehicleId == vf.ID)
        val fleets = store.fleets.filter(v => fleetId.contains(v.ID))
        allVehicles = allVehicles.appended(Vehicle(vf, fleets))
      })
      allVehicles
    }

  override def findAllActive(offset: Int, limit: Int): Task[List[Vehicle]] =
    Task.effect {
      var allFleets: List[Vehicle] = List.empty
      store.vehicles.foreach(vf => {
        if (vf.deletedAt.isEmpty) {
          val fleetId = store.vehicleFleets.filter(lf => lf.vehicleId == vf.ID)
          val fleets = store.fleets.filter(v => fleetId.contains(v.ID))
          allFleets = allFleets.appended(Vehicle(vf, fleets))
        }
      })
      allFleets
    }

  override def findList(ids: List[Long]): Task[List[Vehicle]] = {
    Task.effect {
      var allFleets: List[Vehicle] = List.empty
      store.vehicles
        .filter(u => ids.contains(u.ID))
        .foreach(vf => {
          val fleetId = store.vehicleFleets.filter(lf => lf.vehicleId == vf.ID)
          val fleets = store.fleets.filter(v => fleetId.contains(v.ID))
          allFleets = allFleets.appended(Vehicle(vf, fleets))
        })
      allFleets
    }
  }

  override def count(): Task[Int] = Task.effect(store.vehicles.size)

  override def countActive(): Task[Int] =
    Task.effect(store.vehicles.count(u => u.deletedAt.isEmpty))

  override var maxId: Long = 0
}

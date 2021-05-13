package tracker.mocked

import tracker.dao.FleetDAO
import tracker.{Fleet, LightFleet}
import zio.Task

case class FleetDaoMock(var store: Store) extends FleetDAO with DAOMock[Fleet] {
  override def persist(fleet: LightFleet): Task[Fleet] =
    Task.effect {
      maxId = maxId + 1
      val v = Fleet(
        LightFleet(
          Some(maxId),
          fleet.name
        ),
        List.empty
      )
      store.fleets = store.fleets.appended(v.toLight)
      v
    }

  override def update(fleet: Fleet): Task[Int] =
    Task.effect {
      val size = store.fleets.size
      store.fleets = store.fleets.filter(p => p.ID != fleet.fleet.ID)
      if (size == store.fleets.size) {
        throw new IllegalStateException()
      } else {
        store.fleets = store.fleets.appended(fleet.toLight)
        1
      }
    }

  override def delete(fleet: Fleet): Task[Int] = {
    store.fleets = store.fleets.filter(p => p.ID != fleet.fleet.ID)
    Task.effect(1)
  }

  override def find(id: Long): Task[Option[Fleet]] = {
    val vehiclesId = store.vehicleFleets.filter(vf => vf.fleetId == id).map(_.vehicleId)
    val vehicles = store.vehicles.filter(v => vehiclesId.contains(v.ID))
    Task.effect(store.fleets.find(u => u.ID == id).map(lf => Fleet(lf, vehicles)))
  }

  override def findAll(offset: Int, limit: Int): Task[List[Fleet]] = {
    Task.effect {
      var allFleets: List[Fleet] = List.empty
      store.fleets.foreach(lf => {
        val vehiclesId = store.vehicleFleets.filter(vf => vf.fleetId == lf.ID)
        val vehicles = store.vehicles.filter(v => vehiclesId.contains(v.ID))
        allFleets = allFleets.appended(Fleet(lf, vehicles))
      })
      allFleets
    }
  }

  override def count(): Task[Int] = Task.effect(store.fleets.size)

  override var maxId: Long = 0

}

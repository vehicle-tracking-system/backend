package tracker.mocked

import tracker.{LightTracker, LightVehicle, Tracker}
import tracker.dao.TrackerDAO
import zio.Task

case class TrackerDAOMock(var store: Store) extends TrackerDAO with DAOMock[Tracker] {
  override def persist(tracker: LightTracker): Task[Tracker] =
    Task.effect {
      maxId = maxId + 1
      val usr = Tracker(
        LightTracker(
          Some(maxId),
          tracker.name,
          tracker.vehicleId,
          tracker.token,
          tracker.createdAt,
          tracker.deletedAt
        ),
        LightVehicle(
          Some(tracker.vehicleId),
          ""
        )
      )
      store.trackers = store.trackers.appended(usr.toLight)
      usr
    }

  override def delete(tracker: LightTracker): Task[Int] = throw new NotImplementedError()

  override def markAsDeleted(id: Long): Task[Tracker] = throw new NotImplementedError()

  override def update(tracker: LightTracker): Task[Tracker] =
    Task.effect {
      val size = store.trackers.size
      val vehicle = store.vehicles.find(lv => lv.ID == tracker.vehicleId).get
      store.trackers = store.trackers.filter(lt => lt.ID != tracker.ID)
      if (size == store.trackers.size) {
        throw new IllegalStateException()
      } else {
        store.trackers = store.trackers.appended(tracker)
        Tracker(tracker, vehicle)
      }
    }

  override def updateAccessToken(id: Long, token: String): Task[Tracker] =
    Task.effect(
      store.trackers
        .find(lt => lt.ID == id)
        .map(lt => LightTracker(lt.id, lt.name, lt.vehicleId, token, lt.createdAt, lt.deletedAt))
        .flatMap(lt => store.vehicles.find(lv => lv.ID == lt.vehicleId).map(lv => Tracker(lt, lv)))
        .get
    )

  override def find(id: Long): Task[Option[Tracker]] =
    Task.effect(store.trackers.find(u => u.ID == id).map(t => store.vehicles.find(lv => lv.ID == t.vehicleId).map(lv => Tracker(t, lv)).get))

  override def findAll(offset: Int, limit: Int): Task[List[Tracker]] =
    Task.effect {
      var trackers: List[Tracker] = List.empty
      store.trackers.foreach(lt => {
        println(lt)
        val vehicle = store.vehicles.find(lv => lv.ID == lt.vehicleId).get
        trackers = trackers.appended(Tracker(lt, vehicle))
      })
      trackers
    }

  override def findAllActive(offset: Int, limit: Int): Task[List[Tracker]] = throw new NotImplementedError()

  override def findByVehicle(vehicleId: Long): Task[List[Tracker]] = throw new NotImplementedError()

  override def findByToken(token: String): Task[Option[Tracker]] = throw new NotImplementedError()

  override def count(): Task[Int] = Task.effect(store.trackers.size)

  override var maxId: Long = 0
}

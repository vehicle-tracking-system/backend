package tracker.mocked

import tracker.{LightTrack, LightVehicle, Track}
import tracker.dao.TrackDAO
import zio.Task

import java.time.ZonedDateTime

case class TrackDAOMock(var store: List[Track] = List.empty) extends TrackDAO with DAOMock[Track] {
  override def persist(track: LightTrack): Task[Track] =
    Task.effect {
      maxId = maxId + 1
      val usr = Track(
        LightTrack(
          Some(maxId),
          track.vehicleId
        ),
        LightVehicle(
          Some(track.vehicleId),
          ""
        )
      )
      store = store.appended(usr)
      usr
    }

  override def update(track: LightTrack): Task[Track] =
    Task.effect {
      val size = store.size
      store = store.filter(p => p.track.id != track.id)
      if (size == store.size) {
        throw new IllegalStateException()
      } else {
        store = store.appended(Track(track, LightVehicle(Some(track.vehicleId), "")))
        store.find(u => u.track.id == track.id).get
      }
    }

  override def find(id: Long): Task[Option[Track]] =
    Task.effect(store.find(u => u.track.ID == id))

  override def findAll(offset: Int, limit: Int): Task[List[Track]] = Task.effect(store)

  override def findByVehicle(vehicleId: Long, offset: Int, limit: Int): Task[List[Track]] =
    Task.effect(store.filter(u => u.vehicle.ID == vehicleId))

  override def findTracksInRage(vehicleId: Long, since: ZonedDateTime, until: ZonedDateTime): Task[List[Track]] =
    Task.effect(store.filter(u => u.track.timestamp.isAfter(since) && u.track.timestamp.isBefore(until)))

  override def countVehicleTracks(vehicleId: Long): Task[Int] =
    Task.effect(store.count(u => u.vehicle.ID == vehicleId))

  override def count(): Task[Int] = Task.effect(store.size)

  override var maxId: Long = 0
}

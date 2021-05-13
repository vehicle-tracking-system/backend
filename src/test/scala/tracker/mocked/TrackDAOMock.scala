package tracker.mocked

import tracker.{LightTrack, LightVehicle, Track}
import tracker.dao.TrackDAO
import zio.Task

import java.time.ZonedDateTime

case class TrackDAOMock(var store: Store) extends TrackDAO with DAOMock[Track] {
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
      store.tracks = store.tracks.appended(usr.toLight)
      usr
    }

  override def update(track: LightTrack): Task[Track] =
    Task.effect {
      val size = store.tracks.size
      val vehicle = store.vehicles.find(lv => lv.ID == track.vehicleId).get
      store.tracks = store.tracks.filter(lt => lt.ID != track.ID)
      if (size == store.tracks.size) {
        throw new IllegalStateException()
      } else {
        store.tracks = store.tracks.appended(track)
        Track(track, vehicle)
      }
    }

  override def find(id: Long): Task[Option[Track]] =
    Task.effect(store.tracks.find(u => u.ID == id).map(t => store.vehicles.find(lv => lv.ID == t.vehicleId).map(lv => Track(t, lv)).get))

  override def findAll(offset: Int, limit: Int): Task[List[Track]] = {
    Task.effect {
      var tracks: List[Track] = List.empty
      store.tracks.foreach(lt => {
        println(lt)
        val vehicle = store.vehicles.find(lv => lv.ID == lt.vehicleId).get
        tracks = tracks.appended(Track(lt, vehicle))
      })
      tracks
    }
  }

  override def findByVehicle(vehicleId: Long, offset: Int, limit: Int): Task[List[Track]] =
    Task.effect(
      store.tracks.filter(lt => lt.vehicleId == vehicleId).map(lt => store.vehicles.find(lv => lv.ID == lt.vehicleId).map(lv => Track(lt, lv)).get)
    )

  override def findTracksInRage(vehicleId: Long, since: ZonedDateTime, until: ZonedDateTime): Task[List[Track]] =
    Task.effect(
      store.tracks
        .filter(lt => (lt.vehicleId == vehicleId) && lt.timestamp.isBefore(until) && lt.timestamp.isAfter(since))
        .map(lt => store.vehicles.find(lv => lv.ID == lt.vehicleId).map(lv => Track(lt, lv)).get)
    )

  override def countVehicleTracks(vehicleId: Long): Task[Int] =
    Task.effect(store.tracks.count(lt => lt.vehicleId == vehicleId))

  override def count(): Task[Int] = Task.effect(store.tracks.size)

  override var maxId: Long = 0
}

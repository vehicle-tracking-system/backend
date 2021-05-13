package tracker.mocked

import cats.data.NonEmptyList
import tracker.Position
import tracker.dao.PositionDAO
import zio.Task

import java.time.{Instant, ZoneId, ZonedDateTime}

case class PositionDAOMock(var store: Store) extends PositionDAO with DAOMock[Position] {

  override var maxId: Long = 0

  override def persist(position: Position): Task[Position] =
    Task {
      maxId = maxId + 1
      val pos = Position(
        Some(maxId),
        position.vehicleId,
        1,
        position.speed,
        position.latitude,
        position.longitude,
        position.timestamp,
        position.sessionId
      )
      store.positions = store.positions.appended(pos)
      pos
    }

  override def update(position: Position): Task[Position] =
    Task {
      val size = store.positions.size
      store.positions = store.positions.filter(p => p.id != position.id)
      if (size == store.positions.size) {
        throw new IllegalStateException()
      } else {
        store.positions = store.positions.appended(position)
        position
      }
    }

  override def find(id: Long): Task[Option[Position]] =
    Task {
      store.positions.find(p => p.id.get == id)
    }

  def clear(): Unit = {
    store.positions = List.empty
    maxId = 0
  }

  override def findByVehicle(vehicleId: Long, offset: Int, limit: Int): Task[List[Position]] = {
    Task.effect(store.positions.filter(p => p.vehicleId == vehicleId))
  }

  override def findVehicleHistory(vehicleId: Long, since: ZonedDateTime, until: ZonedDateTime): Task[List[Position]] =
    Task.effect(store.positions.filter(p => (p.vehicleId == vehicleId) && p.timestamp.isAfter(since) && p.timestamp.isBefore(until)))

  override def findLastVehiclePosition(vehicleId: Long): Task[Option[Position]] = {
    var timestamp: ZonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochSecond(-1), ZoneId.of("Europe/Prague"))
    var latestPosition: Option[Position] = None
    store.positions
      .filter(p => p.vehicleId == vehicleId)
      .foreach(p =>
        if (p.timestamp.isAfter(timestamp)) {
          latestPosition = Some(p)
          timestamp = p.timestamp
        }
      )
    Task.effect(latestPosition)
  }

  override def persistList(positions: List[Position]): Task[Int] = throw new NotImplementedError()

  override def findByTrack(trackId: Long): Task[Option[NonEmptyList[Position]]] = {
    Task.effect(NonEmptyList.fromList(store.positions.filter(_.trackId == trackId)))
  }

  override def findActiveDays(vehicleId: Long, month: Int, year: Int): Task[List[Int]] = throw new NotImplementedError()
}

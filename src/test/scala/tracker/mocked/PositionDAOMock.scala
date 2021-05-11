package tracker.mocked

import cats.data.NonEmptyList
import tracker.Position
import tracker.dao.PositionDAO
import zio.Task

import java.time.ZonedDateTime

case class PositionDAOMock(var store: List[Position]) extends PositionDAO with DAOMock[Position] {

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
      store = store.appended(pos)
      pos
    }

  override def update(position: Position): Task[Position] =
    Task {
      val size = store.size
      store = store.filter(p => p.id != position.id)
      if (size == store.size) {
        throw new IllegalStateException()
      } else {
        store = store.appended(position)
        position
      }
    }

  override def find(id: Long): Task[Option[Position]] =
    Task {
      store.find(p => p.id.get == id)
    }

  def clear(): Unit = {
    store = List.empty
    maxId = 0
  }

  override def findByVehicle(vehicleId: Long, offset: Int, limit: Int): Task[List[Position]] = throw new NotImplementedError()

  override def findVehicleHistory(vehicleId: Long, since: ZonedDateTime, until: ZonedDateTime): Task[List[Position]] =
    throw new NotImplementedError()

  override def findLastVehiclePosition(vehicleId: Long): Task[Option[Position]] = throw new NotImplementedError()

  override def persistList(positions: List[Position]): Task[Int] = throw new NotImplementedError()

  override def findByTrack(trackId: Long): Task[Option[NonEmptyList[Position]]] = throw new NotImplementedError()

  override def findActiveDays(vehicleId: Long, month: Int, year: Int): Task[List[Int]] = throw new NotImplementedError()
}

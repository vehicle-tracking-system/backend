package tracker.dao

import cats.data.NonEmptyList
import zio.interop.catz._
import doobie.implicits._
import doobie.util.transactor.Transactor
import tracker.Position
import zio.Task
import doobie.implicits.javatime._
import doobie.util.fragment.Fragment
import doobie.Update

import java.time.ZonedDateTime

trait PositionDAO {
  def persist(position: Position): Task[Position]

  def persistList(positions: List[Position]): Task[Int]

  def update(position: Position): Task[Position]

  def find(id: Long): Task[Option[Position]]

  def findByVehicle(vehicleId: Long, offset: Int = 0, limit: Int = 20): Task[List[Position]]

  def findVehicleHistory(vehicleId: Long, since: ZonedDateTime, until: ZonedDateTime): Task[List[Position]]

  def findLastVehiclePosition(vehicleId: Long): Task[Option[Position]]

  def findByTrack(trackId: Long): Task[Option[NonEmptyList[Position]]]

  def findActiveDays(vehicleId: Long, month: Int, year: Int): Task[List[Int]]
}

class DefaultPositionDAO(transactor: Transactor[Task]) extends PositionDAO {
  override def persist(position: Position): Task[Position] = {
    for {
      id <-
        sql"""INSERT INTO POSITION (VEHICLE_ID, TRACK_ID, SPEED, LATITUDE, LONGITUDE, TIMESTAMP, SESSION_ID) VALUES
         (${position.vehicleId}, ${position.trackId}, ${position.speed}, ${position.latitude}, ${position.longitude}, ${position.timestamp}, ${position.sessionId})""".update
          .withUniqueGeneratedKeys[Long]("id")
          .transact(transactor)
      position <- find(id).map(_.getOrElse(throw new IllegalStateException("Could not find newly created entity!")))
    } yield position
  }

  override def persistList(positions: List[Position]): Task[Int] = {
    val positionsInfo: List[PositionInfo] = positions.map(p => (p.vehicleId, p.trackId, p.speed, p.latitude, p.longitude, p.timestamp, p.sessionId))
    val sql = """INSERT INTO POSITION (VEHICLE_ID, TRACK_ID, SPEED, LATITUDE, LONGITUDE, TIMESTAMP, SESSION_ID) VALUES (?, ?, ?, ?, ?, ?, ?)"""
    Update[PositionInfo](sql).updateMany(positionsInfo).transact(transactor)
  }

  override def update(position: Position): Task[Position] = {
    for {
      id <- sql"""UPDATE POSITION SET
         VEHICLE_ID = ${position.vehicleId},
         TRACK_ID = ${position.trackId},
         SPEED = ${position.speed},
         LATITUDE = ${position.latitude},
         LONGITUDE = ${position.longitude},
         TIMESTAMP = ${position.timestamp},
         SESSION_ID = ${position.sessionId} WHERE ID = ${position.id}""".update.withUniqueGeneratedKeys[Long]("id").transact(transactor)
      position <- find(id).map(_.getOrElse(throw new IllegalStateException("Could not find newly created entity!")))
    } yield position
  }

  override def find(id: Long): Task[Option[Position]] = {
    sql"""SELECT ID, VEHICLE_ID, TRACK_ID, SPEED, LATITUDE, LONGITUDE, TIMESTAMP, SESSION_ID FROM POSITION WHERE ID = $id"""
      .query[Position]
      .option
      .transact(transactor)
  }

  override def findByVehicle(vehicleId: Long, offset: Int = 0, limit: Int = 20): Task[List[Position]] = {
    findBy(fr"""WHERE VEHICLE_ID = ${vehicleId}""", offset, limit)
  }

  override def findVehicleHistory(vehicleId: Long, since: ZonedDateTime, until: ZonedDateTime): Task[List[Position]] = {
    findBy(fr"""WHERE VEHICLE_ID = ${vehicleId} AND TIMESTAMP >= $since AND TIMESTAMP <= $until""", 0, Int.MaxValue)
  }

  override def findLastVehiclePosition(vehicleId: Long): Task[Option[Position]] = {
    findBy(fr"""WHERE VEHICLE_ID = ${vehicleId}""", 0, 1).map {
      case List() => None
      case a      => Some(a.head)
    }
  }

  override def findByTrack(trackId: Long): Task[Option[NonEmptyList[Position]]] = {
    findBy(fr"""WHERE TRACK_ID = $trackId""", 0, Int.MaxValue).map(NonEmptyList.fromList)
  }

  override def findActiveDays(vehicleId: Long, month: Int, year: Int): Task[List[Int]] = {
    sql"""SELECT DAY_OF_MONTH(P.TIMESTAMP) as DAY
         |FROM (SELECT TIMESTAMP FROM POSITION WHERE VEHICLE_ID = $vehicleId GROUP BY TIMESTAMP) P
         |WHERE MONTH(P.TIMESTAMP) = $month AND YEAR(P.TIMESTAMP) = $year
         |GROUP BY DAY
         |""".stripMargin
      .query[Int]
      .to[List]
      .transact(transactor)
  }

  private def findBy(fra: Fragment, offset: Int, limit: Int): Task[List[Position]] = {
    (sql"""SELECT ID, VEHICLE_ID, TRACK_ID, SPEED, LATITUDE, LONGITUDE, TIMESTAMP, SESSION_ID FROM POSITION """
      ++ fra
      ++ sql"""ORDER BY TIMESTAMP DESC LIMIT $limit OFFSET $offset""")
      .query[Position]
      .to[List]
      .transact(transactor)
  }

  private type PositionInfo = (Long, Long, Double, Double, Double, ZonedDateTime, String)
}

object PositionDAO {
  def apply(transactor: Transactor[Task]): PositionDAO = new DefaultPositionDAO(transactor)
}

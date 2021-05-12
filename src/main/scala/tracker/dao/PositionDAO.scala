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

/**
  * Provides access and operations with Position records in database.
  */
trait PositionDAO {

  /**
    * Persist new position.
    *
    * If position is already exists, new one will be created with same data but with another ID. For update exiting position use `update` method.
    * @param position position to be saved (without unique identifier)
    * @return Newly inserted position with unique identifier
    */
  def persist(position: Position): Task[Position]

  /**
    * Persist list of new positions.
    *
    * This method has the same behaviour as `persist`. This method will not update positions, but only creates new ones. For update list of position
    *  call on each position method `update`.
    * @param positions positions to be saved (without unique identifiers)
    * @return Number of inserted positions
    */
  def persistList(positions: List[Position]): Task[Int]

  /**
    * Update position
    *
    * @param position modified position (position is matched with the position in database by ID)
    * @return Updated position from database
    */
  def update(position: Position): Task[Position]

  /**
    * @param id ID of position you are looking for
    * @return Some[Position] if position with specified identifier exists in database, otherwise None
    */
  def find(id: Long): Task[Option[Position]]

  /**
    * @param vehicleId identifier of vehicle
    * @param offset first `offset` positions in result will be ignore
    * @param limit positions after `offset` + `limit` in results will be ignored
    * @return "Page" of positions belongs to vehicle with `vehicleId`
    */
  def findByVehicle(vehicleId: Long, offset: Int = 0, limit: Int = 20): Task[List[Position]]

  /**
    * Same as `findByVehicle` method, but the parameter are given in human readable format (as time range)
    * @param vehicleId identifier od vehicle
    * @param since lower range limit
    * @param until upper range limit
    * @return all positions belongs to vehicle with identifier equals to `vehicleId` and the position must be newer then `since` and older then `until`
    */
  def findVehicleHistory(vehicleId: Long, since: ZonedDateTime, until: ZonedDateTime): Task[List[Position]]

  /**
    * @param vehicleId identifier of vehicle
    * @return latest position belong to vehicle with identifier equals to `vehicleId`
    */
  def findLastVehiclePosition(vehicleId: Long): Task[Option[Position]]

  /**
    * @param trackId identifier of track
    * @return positions of requested track. When there are no track with `trackId` in database, None is returned.
    */
  def findByTrack(trackId: Long): Task[Option[NonEmptyList[Position]]]

  /**
    * @param vehicleId identifier of vehicle
    * @param month requesting month
    * @param year requesting year
    * @return list of day number in specific `month` where at least one track was done
    */
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

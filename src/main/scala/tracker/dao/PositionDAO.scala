package tracker.dao

import zio.interop.catz._
import doobie.implicits._
import doobie.util.transactor.Transactor
import tracker.Position
import zio.Task
import doobie.implicits.javatime._
import doobie.util.fragment.Fragment

import java.time.ZonedDateTime

trait PositionDAO {
  def persist(position: Position): Task[Position]

  def update(position: Position): Task[Position]

  def find(id: Long): Task[Option[Position]]

  def findByVehicle(vehicleId: Long, offset: Int = 0, limit: Int = 20): Task[List[Position]]

  def findVehicleHistory(vehicleId: Long, since: ZonedDateTime, until: ZonedDateTime): Task[List[Position]]

}

class DefaultPositionDAO(transactor: Transactor[Task]) extends PositionDAO {
  override def persist(position: Position): Task[Position] = {
    for {
      id <-
        sql"""INSERT INTO POSITION (VEHICLE_ID, SPEED, LATITUDE, LONGITUDE, TIMESTAMP) VALUES
         (${position.vehicleId}, ${position.speed}, ${position.latitude}, ${position.longitude}, ${position.timestamp})""".update
          .withUniqueGeneratedKeys[Long]("id")
          .transact(transactor)
      position <- find(id).map(_.getOrElse(throw new IllegalStateException("Could not find newly created entity!")))
    } yield position
  }

  override def update(position: Position): Task[Position] = {
    for {
      id <- sql"""UPDATE POSITION SET
         VEHICLE_ID = ${position.id},
         SPEED = ${position.speed},
         LATITUDE = ${position.latitude},
         LONGITUDE = ${position.longitude},
         TIMESTAMP = ${position.timestamp} WHERE ID = ${position.id}""".update.withUniqueGeneratedKeys[Long]("id").transact(transactor)
      position <- find(id).map(_.getOrElse(throw new IllegalStateException("Could not find newly created entity!")))
    } yield position
  }

  override def find(id: Long): Task[Option[Position]] = {
    sql"""SELECT ID, VEHICLE_ID, SPEED, LATITUDE, LONGITUDE, TIMESTAMP FROM POSITION WHERE ID = $id"""
      .query[Position]
      .option
      .transact(transactor)
  }

  private def findBy(fra: Fragment, offset: Int, limit: Int): Task[List[Position]] = {
    (sql"""SELECT ID, VEHICLE_ID, SPEED, LATITUDE, LONGITUDE, TIMESTAMP FROM POSITION """
      ++ fra
      ++ sql"""ORDER BY TIMESTAMP DESC LIMIT $limit OFFSET $offset""")
      .query[Position]
      .to[List]
      .transact(transactor)
  }

  override def findByVehicle(vehicleId: Long, offset: Int = 0, limit: Int = 20): Task[List[Position]] = {
    findBy(fr"""WHERE VEHICLE_ID = ${vehicleId}""", offset, limit)
  }

  override def findVehicleHistory(vehicleId: Long, since: ZonedDateTime, until: ZonedDateTime): Task[List[Position]] = {
    findBy(fr"""WHERE VEHICLE_ID = ${vehicleId} AND TIMESTAMP >= $since AND TIMESTAMP <= $until""", 0, Int.MaxValue)
  }
}

object PositionDAO {
  def apply(transactor: Transactor[Task]): PositionDAO = new DefaultPositionDAO(transactor)
}

package tracker.dao

import zio.interop.catz._
import doobie.implicits._
import doobie.util.transactor.Transactor
import tracker.Position
import zio.Task
import doobie.implicits.javatime._

trait PositionDAO {
  def persist(position: Position): Task[Int]

  def update(position: Position): Task[Int]

  def find(id: Long): Task[Option[Position]]

}

class DefaultPositionDAO(transactor: Transactor[Task]) extends PositionDAO {
  override def persist(position: Position): Task[Int] = {
    sql"""INSERT INTO POSITION (VEHICLE_ID, SPEED, LATITUDE, LONGITUDE, TIMESTAMP) VALUES
         (${position.id}, ${position.speed}, ${position.latitude}, ${position.longitude}, ${position.timestamp})""".update.run
      .transact(transactor)
  }

  override def update(position: Position): Task[Int] = {
    sql"""UPDATE POSITION SET
         VEHICLE_ID = ${position.id},
         SPEED = ${position.speed},
         LATITUDE = ${position.latitude},
         LONGITUDE = ${position.longitude},
         TIMESTAMP = ${position.timestamp} WHERE ID = ${position.id}""".update.run.transact(transactor)
  }

  override def find(id: Long): Task[Option[Position]] = {
    sql"""SELECT ID, VEHICLE_ID, SPEED, LATITUDE, LONGITUDE, TIMESTAMP FROM POSITION WHERE ID = $id"""
      .query[Position]
      .option
      .transact(transactor)
  }
}

object PositionDAO {
  def apply(transactor: Transactor[Task]): PositionDAO = new DefaultPositionDAO(transactor)
}

package tracker.dao

import cats.free.Free
import zio.interop.catz._
import doobie.{Fragment, Transactor}
import doobie.free.connection
import doobie.implicits._
import doobie.implicits.toSqlInterpolator
import doobie.implicits.javatime._
import tracker.{LightTracker, LightVehicle, Tracker}
import zio.Task

trait TrackerDAO {
  def persist(tracker: LightTracker): Task[Tracker]

  def delete(tracker: LightTracker): Task[Int]

  def markAsDeleted(id: Long): Task[Tracker]

  def update(tracker: LightTracker): Task[Tracker]

  def updateAccessToken(id: Long, token: String): Task[Tracker]

  def find(id: Long): Task[Option[Tracker]]

  def findAll(offset: Int, limit: Int): Task[List[Tracker]]

  def findAllActive(offset: Int, limit: Int): Task[List[Tracker]]

  def findByVehicle(vehicleId: Long): Task[List[Tracker]]

  def findByToken(token: String): Task[Option[Tracker]]

  def count(): Task[Int]
}

class DefaultTrackerDAO(transactor: Transactor[Task]) extends TrackerDAO {
  override def persist(tracker: LightTracker): Task[Tracker] =
    for {
      id <-
        sql"""INSERT INTO TRACKER (NAME, VEHICLE_ID, TOKEN, CREATED_AT, DELETED_AT)
             VALUES (${tracker.name}, ${tracker.vehicleId}, ${tracker.token}, ${tracker.createdAt}, ${tracker.deletedAt})""".update
          .withUniqueGeneratedKeys[Long]("id")
          .transact(transactor)
      tracker <- find(id).map(_.getOrElse(throw new IllegalStateException("Could not find newly created entity!")))
    } yield tracker

  override def delete(tracker: LightTracker): Task[Int] =
    sql"""DELETE FROM TRACKER WHERE ID = ${tracker.id}""".update.run.transact(transactor)

  def markAsDeleted(id: Long): Task[Tracker] = {
    val transaction = for {
      _ <- sql"""UPDATE TRACKER SET DELETED_AT = NOW(), TOKEN = 'N/A' WHERE ID = $id""".update.run
      tracker <- findBy(fr""" WHERE T.ID = $id""", 0, Int.MaxValue)
    } yield tracker
    transaction.transact(transactor).map(_.head)
  }

  override def update(tracker: LightTracker): Task[Tracker] =
    for {
      id <- sql"""UPDATE TRACKER SET
         VEHICLE_ID = ${tracker.vehicleId},
         NAME = ${tracker.name},
         DELETED_AT = ${tracker.deletedAt} WHERE ID = ${tracker.id}""".update.withUniqueGeneratedKeys[Long]("id").transact(transactor)
      tracker <- find(id).map(_.getOrElse(throw new IllegalStateException("Could not find updated entity!")))
    } yield tracker

  override def updateAccessToken(id: Long, token: String): Task[Tracker] =
    for {
      id <- sql"""UPDATE TRACKER SET
         TOKEN = $token WHERE ID = $id""".update.withUniqueGeneratedKeys[Long]("id").transact(transactor)
      tracker <- find(id).map(_.getOrElse(throw new IllegalStateException("Could not find updated entity!")))
    } yield tracker

  override def find(id: Long): Task[Option[Tracker]] =
    sql"""SELECT T.ID, T.NAME, VEHICLE_ID, TOKEN, T.CREATED_AT, T.DELETED_AT, V.ID, V.NAME, V.CREATED_AT, V.DELETED_AT FROM TRACKER T JOIN VEHICLE V ON T.VEHICLE_ID = V.ID WHERE T.ID = $id"""
      .query[(LightTracker, LightVehicle)]
      .option
      .transact(transactor)
      .map(_.map(a => Tracker(a._1, a._2)))

  override def findAll(offset: Int, limit: Int): Task[List[Tracker]] =
    findBy(Fragment.empty, offset, limit).transact(transactor)

  override def findAllActive(offset: Int, limit: Int): Task[List[Tracker]] =
    findBy(fr"""WHERE T.DELETED_AT IS NULL""", offset, limit).transact(transactor)

  override def findByVehicle(vehicleId: Long): Task[List[Tracker]] =
    findBy(fr"""WHERE T.VEHICLE_ID = $vehicleId""").transact(transactor)

  override def findByToken(token: String): Task[Option[Tracker]] =
    findBy(fr"""WHERE T.TOKEN = $token""")
      .map {
        case List()  => None
        case tracker => Some(tracker.head)
      }
      .transact(transactor)

  def count(): Task[Int] = {
    sql"""SELECT COUNT(*) FROM TRACKER"""
      .query[Int]
      .unique
      .transact(transactor)
  }

  private def mapToList(in: List[(LightTracker, LightVehicle)]): List[Tracker] = in.groupBy(_._1).map(g => Tracker(g._1, g._2.map(_._2).head)).toList

  private def findBy(fra: Fragment, offset: Int = 0, limit: Int = Int.MaxValue): Free[connection.ConnectionOp, List[Tracker]] =
    (sql"""SELECT T.ID, T.NAME, VEHICLE_ID, TOKEN, T.CREATED_AT, T.DELETED_AT, V.ID, V.NAME, V.CREATED_AT, V.DELETED_AT FROM TRACKER T JOIN VEHICLE V on T.VEHICLE_ID = V.ID """
      ++ fra
      ++ sql"""LIMIT $limit OFFSET $offset""")
      .query[(LightTracker, LightVehicle)]
      .to[List]
      .map(mapToList)

}

object TrackerDAO {
  def apply(transactor: Transactor[Task]): TrackerDAO = new DefaultTrackerDAO(transactor)
}

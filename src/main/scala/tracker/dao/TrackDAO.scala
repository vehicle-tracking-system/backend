package tracker.dao

import zio.interop.catz._
import doobie.implicits._
import doobie.util.transactor.Transactor
import doobie.implicits.javatime._
import doobie.util.fragment.Fragment
import doobie.implicits.toSqlInterpolator
import tracker.Track
import zio.Task

trait TrackDAO {
  def persist(track: Track): Task[Track]

  def update(track: Track): Task[Track]

  def find(id: Long): Task[Option[Track]]

  def findAll(offset: Int, limit: Int): Task[List[Track]]

  def findByVehicle(vehicleId: Long, offset: Int = 0, limit: Int = 20): Task[List[Track]]

  def count(): Task[Int]
}

class DefaultTrackDAO(transactor: Transactor[Task]) extends TrackDAO {
  override def persist(track: Track): Task[Track] =
    for {
      id <-
        sql"""INSERT INTO TRACK (VEHICLE_ID, TIMESTAMP) VALUES
         (${track.vehicleId}, ${track.timestamp})""".update
          .withUniqueGeneratedKeys[Long]("id")
          .transact(transactor)
      track <- find(id).map(_.getOrElse(throw new IllegalStateException("Could not find newly created entity!")))
    } yield track

  override def update(track: Track): Task[Track] =
    for {
      id <- sql"""UPDATE TRACK SET
         VEHICLE_ID = ${track.vehicleId},
         TIMESTAMP = ${track.timestamp} WHERE ID = ${track.id}""".update.withUniqueGeneratedKeys[Long]("id").transact(transactor)
      position <- find(id).map(_.getOrElse(throw new IllegalStateException("Could not find newly created entity!")))
    } yield position

  override def find(id: Long): Task[Option[Track]] =
    findBy(Fragment.empty, 0, 1).map {
      case List() => None
      case a      => Some(a.head)
    }

  override def findAll(offset: Int, limit: Int): Task[List[Track]] = {
    findBy(Fragment.empty, offset, limit)
  }

  override def findByVehicle(vehicleId: Long, offset: Int, limit: Int): Task[List[Track]] = {
    findBy(fr"""WHERE VEHICLE_ID = ${vehicleId} """, offset, limit)
  }

  override def count(): Task[Int] = {
    sql"""SELECT COUNT(*) FROM TRACK"""
      .query[Int]
      .unique
      .transact(transactor)
  }

  private def findBy(fra: Fragment, offset: Int, limit: Int): Task[List[Track]] = {
    (sql"""SELECT ID, VEHICLE_ID, TIMESTAMP FROM TRACK """
      ++ fra
      ++ sql"""ORDER BY TIMESTAMP DESC LIMIT $limit OFFSET $offset""")
      .query[Track]
      .to[List]
      .transact(transactor)
  }
}

object TrackDAO {
  def apply(transactor: Transactor[Task]): TrackDAO = new DefaultTrackDAO(transactor)
}

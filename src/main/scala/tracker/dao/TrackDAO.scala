package tracker.dao

import zio.interop.catz._
import doobie.implicits._
import doobie.util.transactor.Transactor
import doobie.implicits.javatime._
import doobie.util.fragment.Fragment
import doobie.implicits.toSqlInterpolator
import tracker.{LightTrack, LightVehicle, Track}
import zio.Task

import java.time.ZonedDateTime

trait TrackDAO {
  def persist(track: LightTrack): Task[Track]

  def update(track: LightTrack): Task[Track]

  def find(id: Long): Task[Option[Track]]

  def findAll(offset: Int, limit: Int): Task[List[Track]]

  def findByVehicle(vehicleId: Long, offset: Int, limit: Int): Task[List[Track]]

  def findTracksInRage(vehicleId: Long, since: ZonedDateTime, until: ZonedDateTime): Task[List[Track]]

  def countVehicleTracks(vehicleId: Long): Task[Int]

  def count(): Task[Int]
}

class DefaultTrackDAO(transactor: Transactor[Task]) extends TrackDAO {
  override def persist(track: LightTrack): Task[Track] =
    for {
      id <-
        sql"""INSERT INTO TRACK (VEHICLE_ID, TIMESTAMP) VALUES
         (${track.vehicleId}, ${track.timestamp})""".update
          .withUniqueGeneratedKeys[Long]("id")
          .transact(transactor)
      track <- find(id).map(_.getOrElse(throw new IllegalStateException("Could not find newly created entity!")))
    } yield track

  override def update(track: LightTrack): Task[Track] =
    for {
      id <-
        sql"""UPDATE TRACK SET
         VEHICLE_ID = ${track.vehicleId}, TIMESTAMP = ${track.timestamp} WHERE ID = ${track.id}""".update
          .withUniqueGeneratedKeys[Long]("id")
          .transact(transactor)
      position <- find(id).map(_.getOrElse(throw new IllegalStateException("Could not find newly created entity!")))
    } yield position

  override def find(id: Long): Task[Option[Track]] =
    findBy(Fragment.empty, 0, Int.MaxValue).map {
      case List() => None
      case a      => Some(a.head)
    }

  override def findAll(offset: Int, limit: Int): Task[List[Track]] = {
    findBy(Fragment.empty, offset, limit)
  }

  override def findByVehicle(vehicleId: Long, offset: Int, limit: Int): Task[List[Track]] = {
    findBy(fr"""WHERE T.VEHICLE_ID = ${vehicleId} """, offset, limit)
  }

  override def findTracksInRage(vehicleId: Long, since: ZonedDateTime, until: ZonedDateTime): Task[List[Track]] = {
    findBy(fr"""WHERE T.VEHICLE_ID = $vehicleId AND TIMESTAMP >= $since AND TIMESTAMP <= $until""", 0, Int.MaxValue)
  }

  override def countVehicleTracks(vehicleId: Long): Task[Int] = {
    sql"""SELECT COUNT(*) FROM TRACK WHERE VEHICLE_ID = $vehicleId"""
      .query[Int]
      .unique
      .transact(transactor)
  }

  override def count(): Task[Int] = {
    sql"""SELECT COUNT(*) FROM TRACK"""
      .query[Int]
      .unique
      .transact(transactor)
  }

  private def mapToList(in: List[(LightTrack, LightVehicle)]): List[Track] = in.map(g => Track(g._1, g._2))

  private def findBy(fra: Fragment, offset: Int, limit: Int): Task[List[Track]] = {
    (sql"""SELECT T.ID, T.VEHICLE_ID, T.TIMESTAMP, V.ID, V.NAME FROM TRACK T INNER JOIN VEHICLE V on T.VEHICLE_ID = V.ID """
      ++ fra
      ++ sql"""ORDER BY TIMESTAMP DESC LIMIT $limit OFFSET $offset""")
      .query[(LightTrack, LightVehicle)]
      .to[List]
      .transact(transactor)
      .map(mapToList)
  }
}

object TrackDAO {
  def apply(transactor: Transactor[Task]): TrackDAO = new DefaultTrackDAO(transactor)
}

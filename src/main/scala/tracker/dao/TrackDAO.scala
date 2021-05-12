package tracker.dao

import doobie.free.connection
import zio.interop.catz._
import cats.free.Free
import doobie.implicits._
import doobie.util.transactor.Transactor
import doobie.implicits.javatime._
import doobie.util.fragment.Fragment
import doobie.implicits.toSqlInterpolator
import tracker.{LightTrack, LightVehicle, Track}
import zio.Task

import java.time.ZonedDateTime

/**
  * Provides access and operations with Track records in database.
  */
trait TrackDAO {

  /**
    * Persist new track.
    *
    * If track is already exists, new one will be created with same data but with another ID. For update exiting track use `update` method.
    * @param track track to be saved (without unique identifier)
    * @return Newly inserted track with unique identifier
    */
  def persist(track: LightTrack): Task[Track]

  /**
    * @param track modified track (track is matched with the track in database by ID)
    * @return Updated track from database
    */
  def update(track: LightTrack): Task[Track]

  /**
    * @param id ID of track you are looking for
    * @return Some[Track] if track with specified identifier exists in database, otherwise None
    */
  def find(id: Long): Task[Option[Track]]

  /**
    * @param offset first `offset` tracks in result will be ignore
    * @param limit tracks after `offset` + `limit` in results will be ignored
    * @return "Page" of tracks
    */
  def findAll(offset: Int, limit: Int): Task[List[Track]]

  /**
    * @param vehicleId identifier of vehicle
    * @param offset first `offset` tracks in result will be ignore
    * @param limit tracks after `offset` + `limit` in results will be ignored
    * @return "Page" of tracks belongs to vehicle with `vehicleId`
    */
  def findByVehicle(vehicleId: Long, offset: Int, limit: Int): Task[List[Track]]

  /**
    * Same as `findByVehicle` method, but the parameter are given in human readable format (as time range)
    * @param vehicleId identifier od vehicle
    * @param since lower range limit
    * @param until upper range limit
    * @return all tracks belongs to vehicle with identifier equals to `vehicleId` and the track must be newer then `since` and older then `until`
    */
  def findTracksInRage(vehicleId: Long, since: ZonedDateTime, until: ZonedDateTime): Task[List[Track]]

  /**
    * @param vehicleId identifier of vehicle
    * @return total count of tracks in database belongs to specified vehicle
    */
  def countVehicleTracks(vehicleId: Long): Task[Int]

  /**
    * @return count of tracks in database
    */
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
    countWhere(fr"""WHERE VEHICLE_ID = $vehicleId""").transact(transactor)
  }

  override def count(): Task[Int] = {
    countWhere(Fragment.empty).transact(transactor)
  }

  private def mapToList(in: List[(LightTrack, LightVehicle)]): List[Track] = in.map(g => Track(g._1, g._2))

  private def findBy(fra: Fragment, offset: Int, limit: Int): Task[List[Track]] = {
    (sql"""SELECT T.ID, T.VEHICLE_ID, T.TIMESTAMP, V.ID, V.NAME, V.CREATED_AT, V.DELETED_AT FROM TRACK T INNER JOIN VEHICLE V on T.VEHICLE_ID = V.ID """
      ++ fra
      ++ sql"""ORDER BY TIMESTAMP DESC LIMIT $limit OFFSET $offset""")
      .query[(LightTrack, LightVehicle)]
      .to[List]
      .transact(transactor)
      .map(mapToList)
  }

  private def countWhere(fra: Fragment): Free[connection.ConnectionOp, Int] = {
    (sql"""SELECT COUNT(*) FROM TRACK"""
      ++ fra)
      .query[Int]
      .unique
  }
}

object TrackDAO {
  def apply(transactor: Transactor[Task]): TrackDAO = new DefaultTrackDAO(transactor)
}

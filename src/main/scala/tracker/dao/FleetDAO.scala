package tracker.dao
import doobie.implicits._
import doobie.util.transactor.Transactor
import doobie.util.fragment.Fragment
import tracker.{Fleet, LightFleet, LightVehicle}
import zio.Task
import zio.interop.catz._
import doobie.implicits.javatime._

/**
  * Provides access and operations with Fleet records in database.
  */
trait FleetDAO {

  /**
    * Persist new fleet
    * @param fleet - fleet with ID set to None
    * @return Newly inserted fleet with unique identifier
    */
  def persist(fleet: LightFleet): Task[Fleet]

  /**
    * Update fleet in database. Fleets are match by ID.
    * @param fleet - updated fleet to be inserted
    * @return Number of updated fleets
    */
  def update(fleet: Fleet): Task[Int]

  /**
    * Delete fleet from database
    * @param fleet - fleet to be deleted
    * @return Number of deleted fleets
    */
  def delete(fleet: Fleet): Task[Int]

  /**
    * Find fleet in database by ID
    * @param id - identifier of fleet in database
    * @return Some if fleet was found, otherwise None
    */
  def find(id: Long): Task[Option[Fleet]]

  /**
    * Find all
    * @param offset - First `offset` fleets is ignored
    * @param limit - only `limit` number of fleets are returned
    * @return List of fleet in selected range. List may be empty.
    */
  def findAll(offset: Int, limit: Int): Task[List[Fleet]]

  /**
    * @return total count of fleets in database
    */
  def count(): Task[Int]
}

class DefaultFleetDAO(transactor: Transactor[Task]) extends FleetDAO {
  override def persist(fleet: LightFleet): Task[Fleet] = {
    for {
      id <-
        sql"""INSERT INTO FLEET (name) VALUES (${fleet.name})""".update
          .withUniqueGeneratedKeys[Long]("id")
          .transact(transactor)
      fleet <- find(id).map(_.getOrElse(throw new IllegalStateException("Could not find newly created entity!")))
    } yield fleet
  }

  override def update(fleet: Fleet): Task[Int] = {
    sql"""UPDATE FLEET SET (NAME) = (${fleet.fleet.name}) WHERE ID = ${fleet.fleet.id}""".update.run
      .transact(transactor)
  }

  override def delete(fleet: Fleet): Task[Int] = {
    sql"""DELETE FROM FLEET WHERE ID = ${fleet.fleet.id}""".update.run.transact(transactor)
  }

  def findAll(offset: Int, limit: Int): Task[List[Fleet]] =
    findBy(Fragment.empty, offset, limit)

  override def find(id: Long): Task[Option[Fleet]] = {
    sql"""SELECT ID, NAME FROM FLEET WHERE ID = $id"""
      .query[LightFleet]
      .option
      .transact(transactor)
      .flatMap { k =>
        sql"""SELECT v.ID, v.NAME, v.CREATED_AT, v.DELETED_AT FROM VEHICLEFLEET vf JOIN VEHICLE v ON vf.VEHICLE_ID = v.ID WHERE vf.FLEET_ID = $id"""
          .query[LightVehicle]
          .to[List]
          .transact(transactor)
          .map(v =>
            k match {
              case Some(a) => Some(Fleet(a, v))
              case None    => None
            }
          )
      }
  }

  private def mapToList(in: List[(LightFleet, Option[LightVehicle])]): List[Fleet] =
    in.groupBy(_._1).map(g => Fleet(g._1, g._2.filter(_._2.nonEmpty).map(_._2.get))).toList

  private def findBy(fra: Fragment, offset: Int, limit: Int): Task[List[Fleet]] =
    (sql"""SELECT F.ID, F.NAME, V.ID, V.NAME, V.CREATED_AT, V.DELETED_AT FROM (SELECT ID, NAME FROM FLEET ORDER BY NAME DESC LIMIT $limit OFFSET $offset) F LEFT JOIN VEHICLEFLEET VF on F.ID = VF.FLEET_ID LEFT JOIN VEHICLE V on VF.VEHICLE_ID = V.ID"""
      ++ fra)
      .query[(LightFleet, Option[LightVehicle])]
      .to[List]
      .transact(transactor)
      .map(mapToList)

  override def count(): Task[Int] = {
    sql"""SELECT COUNT(*) FROM FLEET"""
      .query[Int]
      .unique
      .transact(transactor)
  }
}

object FleetDAO {
  def apply(transactor: Transactor[Task]): FleetDAO = new DefaultFleetDAO(transactor)
}

package tracker.dao
import doobie.implicits._
import doobie.util.transactor.Transactor
import tracker.{Fleet, LightFleet, LightVehicle}
import zio.Task
import zio.interop.catz._

trait FleetDAO {
  def persist(fleet: Fleet): Task[Int]

  def update(fleet: Fleet): Task[Int]

  def delete(fleet: Fleet): Task[Int]

  def find(id: Long): Task[Option[Fleet]]
}

class DefaultFleetDAO(transactor: Transactor[Task]) extends FleetDAO {
  override def persist(fleet: Fleet): Task[Int] = {
    sql"""INSERT INTO FLEET (name) VALUES (${fleet.fleet.name})""".update.run.transact(transactor)
  }

  override def update(fleet: Fleet): Task[Int] = {
    sql"""UPDATE FLEET SET (NAME) = (${fleet.fleet.name}) WHERE ID = ${fleet.fleet.id}""".update.run
      .transact(transactor)
  }

  override def delete(fleet: Fleet): Task[Int] = {
    sql"""DELETE FROM FLEET WHERE ID = ${fleet.fleet.id}""".update.run.transact(transactor)
  }

  override def find(id: Long): Task[Option[Fleet]] = {
    sql"""SELECT ID, NAME FROM FLEET WHERE ID = $id"""
      .query[LightFleet]
      .option
      .transact(transactor)
      .flatMap { k =>
        sql"""SELECT v.ID, v.NAME FROM VEHICLEFLEET vf JOIN VEHICLE v ON vf.VEHICLE_ID = v.id WHERE vf.FLEET_ID = $id"""
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
}

object FleetDAO {
  def apply(transactor: Transactor[Task]): FleetDAO = new DefaultFleetDAO(transactor)
}

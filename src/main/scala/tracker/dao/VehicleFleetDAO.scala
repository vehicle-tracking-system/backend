package tracker.dao

import doobie.implicits._
import doobie.util.transactor.Transactor
import doobie.Update
import tracker.{Vehicle, VehicleFleet}
import zio.Task
import zio.interop.catz._

/**
  * Provides access and operations with VehicleFleet records in database.
  */
trait VehicleFleetDAO {

  /**
    * Persist new vehicleFleet.
    *
    * If vehicleFleet is already exists, new one will be created with same data but with another ID. For update exiting vehicleFleet use `update` method.
    * @param vehicleFleet vehicleFleet to be saved (without unique identifier)
    * @return Newly inserted vehicleFleet with unique identifier
    */
  def persist(vehicleFleet: VehicleFleet): Task[Int]

  /**
    * @param vehicleFleet vehicleFleets to be removed from database
    * @return number of vehicleFleets removed from database
    */
  def delete(vehicleFleet: VehicleFleet): Task[Int]

  /**
    * @param id identifier of vehicleFleet
    * @return Some[VehicleFleet] if vehicleFleet with specified identifier is persist in database, otherwise None
    */
  def find(id: Long): Task[Option[VehicleFleet]]

  /**
    * @param vehicleFleet
    * @return
    */
  def persistList(vehicleFleet: List[VehicleFleet]): Task[Int]

  /**
    * @param vehicle
    * @return
    */
  def setToVehicle(vehicle: Vehicle): Task[Int]
}

class DefaultVehicleFleetDAO(transactor: Transactor[Task]) extends VehicleFleetDAO {
  override def persist(vehicleFleet: VehicleFleet): Task[Int] = {
    sql"""INSERT INTO VEHICLEFLEET (VEHICLE_ID, FLEET_ID) VALUES (${vehicleFleet.vehicleId}, ${vehicleFleet.fleetId})""".update.run
      .transact(transactor)
  }

  override def delete(vehicleFleet: VehicleFleet): Task[Int] = {
    sql"""DELETE FROM VEHICLEFLEET WHERE ID = ${vehicleFleet.id}""".update.run.transact(transactor)
  }

  override def find(id: Long): Task[Option[VehicleFleet]] = {
    sql"""SELECT ID, VEHICLE_ID, FLEET_ID FROM VEHICLEFLEET WHERE ID = $id"""
      .query[VehicleFleet]
      .option
      .transact(transactor)
  }

  def setToVehicle(vehicle: Vehicle): Task[Int] = {
    val vehicleId = vehicle.vehicle.ID
    val vehicleFleet: List[VehicleFleetInfo] = vehicle.fleets.map { f =>
      (vehicleId, f.ID)
    }
    val transaction = for {
      _ <- sql"""DELETE FROM VEHICLEFLEET WHERE VEHICLE_ID = $vehicleId""".update.run
      insertQuery = """INSERT INTO VEHICLEFLEET (VEHICLE_ID, FLEET_ID) VALUES (?, ?)"""
      insert <- Update[VehicleFleetInfo](insertQuery).updateMany(vehicleFleet)
    } yield insert

    transaction.transact(transactor)
  }

  override def persistList(vehicleFleet: List[VehicleFleet]): Task[Int] = {
    val vehicleFleetInfo: List[VehicleFleetInfo] = vehicleFleet.map(vf => (vf.vehicleId, vf.fleetId))
    val sql = """INSERT INTO VEHICLEFLEET (VEHICLE_ID, FLEET_ID) VALUES (?, ?)"""
    Update[VehicleFleetInfo](sql).updateMany(vehicleFleetInfo).transact(transactor)
  }

  type VehicleFleetInfo = (Long, Long)
}

object VehicleFleetDAO {
  def apply(transactor: Transactor[Task]): VehicleFleetDAO = new DefaultVehicleFleetDAO(transactor)
}

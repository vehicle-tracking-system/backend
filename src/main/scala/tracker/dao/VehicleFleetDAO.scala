package tracker.dao

import doobie.implicits._
import doobie.util.transactor.Transactor
import doobie.Update
import tracker.{Vehicle, VehicleFleet}
import zio.Task
import zio.interop.catz._

trait VehicleFleetDAO {
  def persist(vehicleFleet: VehicleFleet): Task[Int]

  def delete(vehicleFleet: VehicleFleet): Task[Int]

  def find(id: Long): Task[Option[VehicleFleet]]

  def persistList(vehicleFleet: List[VehicleFleet]): Task[Int]

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

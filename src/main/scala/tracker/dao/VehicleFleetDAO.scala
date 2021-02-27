package tracker.dao

import doobie.implicits._
import doobie.util.transactor.Transactor
import tracker.VehicleFleet
import zio.Task
import zio.interop.catz._

trait VehicleFleetDAO {
  def persist(vehicle: VehicleFleet): Task[Int]

  def delete(vehicle: VehicleFleet): Task[Int]

  def find(id: Long): Task[Option[VehicleFleet]]
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

}

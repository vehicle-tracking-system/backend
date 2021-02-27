package tracker.dao

import doobie.implicits._
import doobie.util.transactor.Transactor
import tracker.{LightFleet, LightVehicle, Vehicle}
import zio.Task
import zio.interop.catz._

trait VehicleDAO {
  def persist(vehicle: Vehicle): Task[Int]

  def update(vehicle: Vehicle): Task[Int]

  def delete(vehicle: Vehicle): Task[Int]

  def find(id: Long): Task[Option[Vehicle]]
}

class DefaultVehicleDAO(transactor: Transactor[Task]) extends VehicleDAO {
  override def persist(vehicle: Vehicle): Task[Int] = {
    sql"""INSERT INTO VEHICLE
         (NAME) VALUES
         (${vehicle.vehicle.name})""".update.run
      .transact(transactor)
  }

  override def update(vehicle: Vehicle): Task[Int] = {
    sql"""UPDATE VEHICLE SET
          NAME = ${vehicle.vehicle.name}
          WHERE ID = ${vehicle.vehicle.id}""".update.run.transact(transactor)
  }

  override def delete(vehicle: Vehicle): Task[Int] = {
    sql"""DELETE FROM VEHICLE WHERE ID = ${vehicle.vehicle.id}""".update.run.transact(transactor)
  }

  override def find(id: Long): Task[Option[Vehicle]] = {
    sql"""SELECT ID, NAME FROM VEHICLE WHERE ID = $id"""
      .query[LightVehicle]
      .option
      .transact(transactor)
      .flatMap { k =>
        sql"""SELECT v.ID, v.NAME FROM VEHICLEFLEET vf JOIN FLEET v ON vf.FLEET_ID = v.ID WHERE vf.VEHICLE_ID = $id"""
          .query[LightFleet]
          .to[List]
          .transact(transactor)
          .map(v =>
            k match {
              case Some(a) => Some(Vehicle(a, v))
              case None    => None
            }
          )
      }

  }
}

object VehicleDAO {
  def apply(transactor: Transactor[Task]): VehicleDAO = new DefaultVehicleDAO(transactor)
}

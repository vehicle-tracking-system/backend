package tracker.dao

import cats.data.NonEmptyList
import doobie.implicits._
import doobie.util.fragment.Fragment
import doobie.util.transactor.Transactor
import doobie.Fragments
import tracker.{LightFleet, LightVehicle, Vehicle}
import zio.Task
import zio.interop.catz._

trait VehicleDAO {
  def persist(vehicle: Vehicle): Task[Int]

  def update(vehicle: Vehicle): Task[Int]

  def delete(vehicle: Vehicle): Task[Int]

  def find(id: Long): Task[Option[Vehicle]]

  def findAll(offset: Int, limit: Int): Task[List[Vehicle]]

  def findList(ids: List[Long]): Task[List[Vehicle]]
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

  override def findAll(offset: Int, limit: Int): Task[List[Vehicle]] = {
    findBy(Fragment.empty, offset, limit)
  }

  override def findList(ids: List[Long]): Task[List[Vehicle]] = {
    if (ids.isEmpty) Task(List.empty)
    else findBy(Fragments.in(fr" WHERE V.ID", NonEmptyList.fromListUnsafe(ids)), 0, Int.MaxValue)
  }

  private def mapToList(in: List[(LightVehicle, LightFleet)]): List[Vehicle] = in.groupBy(_._1).map(g => Vehicle(g._1, g._2.map(_._2))).toList

  private def findBy(fra: Fragment, offset: Int, limit: Int): Task[List[Vehicle]] = {
    (sql"""SELECT V.ID, V.NAME, F.ID, F.NAME FROM VEHICLE V INNER JOIN VEHICLEFLEET VF on V.ID = VF.VEHICLE_ID INNER JOIN FLEET F on VF.FLEET_ID = F.ID"""
      ++ fra
      ++ sql""" ORDER BY V.NAME DESC LIMIT $limit OFFSET $offset""")
      .query[(LightVehicle, LightFleet)]
      .to[List]
      .transact(transactor)
      .map(mapToList)
  }
}

object VehicleDAO {
  def apply(transactor: Transactor[Task]): VehicleDAO = new DefaultVehicleDAO(transactor)
}

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
  def persist(vehicle: LightVehicle): Task[Vehicle]

  def update(vehicle: Vehicle): Task[Vehicle]

  def delete(vehicle: Vehicle): Task[Int]

  def find(id: Long): Task[Option[Vehicle]]

  def findAll(offset: Int, limit: Int): Task[List[Vehicle]]

  def findList(ids: List[Long]): Task[List[Vehicle]]

  def count(): Task[Int]
}

class DefaultVehicleDAO(transactor: Transactor[Task]) extends VehicleDAO {
  override def persist(vehicle: LightVehicle): Task[Vehicle] = {
    for {
      id <-
        sql"""INSERT INTO VEHICLE
         (NAME) VALUES
         (${vehicle.name})""".update
          .withUniqueGeneratedKeys[Long]("id")
          .transact(transactor)
      vehicle <- find(id).map(_.getOrElse(throw new IllegalStateException("Could not find newly created entity!")))
    } yield vehicle
  }

  override def update(vehicle: Vehicle): Task[Vehicle] = {
    for {
      id <- sql"""UPDATE VEHICLE SET
          NAME = ${vehicle.vehicle.name}
          WHERE ID = ${vehicle.vehicle.id}""".update.withUniqueGeneratedKeys[Long]("id").transact(transactor)
      vehicle <- find(id).map(_.getOrElse(throw new IllegalStateException("Could not find newly created entity!")))
    } yield vehicle
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

  def count(): Task[Int] = {
    sql"""SELECT COUNT(*) FROM VEHICLE"""
      .query[Int]
      .unique
      .transact(transactor)
  }

  private def mapToList(in: List[(LightVehicle, Option[LightFleet])]): List[Vehicle] =
    in.groupBy(_._1).map(g => Vehicle(g._1, g._2.filter(_._2.nonEmpty).map(_._2.get))).toList

  private def findBy(fra: Fragment, offset: Int, limit: Int): Task[List[Vehicle]] = {
    (sql"""SELECT V.ID, V.NAME, F.ID, F.NAME FROM (SELECT ID, NAME FROM VEHICLE ORDER BY NAME DESC LIMIT $limit OFFSET $offset) V LEFT JOIN VEHICLEFLEET VF on V.ID = VF.VEHICLE_ID LEFT JOIN FLEET F on VF.FLEET_ID = F.ID"""
      ++ fra)
      .query[(LightVehicle, Option[LightFleet])]
      .to[List]
      .transact(transactor)
      .map(mapToList)
  }
}

object VehicleDAO {
  def apply(transactor: Transactor[Task]): VehicleDAO = new DefaultVehicleDAO(transactor)
}

package tracker.dao

import cats.data.NonEmptyList
import cats.free.Free
import doobie.implicits._
import doobie.util.fragment.Fragment
import doobie.util.transactor.Transactor
import doobie.Fragments
import doobie.free.connection
import tracker.{LightFleet, LightVehicle, Vehicle}
import zio.Task
import zio.interop.catz._
import doobie.implicits.javatime._

trait VehicleDAO {
  def persist(vehicle: LightVehicle): Task[Vehicle]

  def update(vehicle: Vehicle): Task[Vehicle]

  def delete(vehicle: Vehicle): Task[Int]

  def markAsDeleted(vehicleId: Long): Task[Vehicle]

  def find(id: Long): Task[Option[Vehicle]]

  def findAll(offset: Int, limit: Int): Task[List[Vehicle]]

  def findAllActive(offset: Int, limit: Int): Task[List[Vehicle]]

  def findList(ids: List[Long]): Task[List[Vehicle]]

  def count(): Task[Int]

  def countActive(): Task[Int]
}

class DefaultVehicleDAO(transactor: Transactor[Task]) extends VehicleDAO {
  override def persist(vehicle: LightVehicle): Task[Vehicle] = {
    for {
      id <-
        sql"""INSERT INTO VEHICLE
         (NAME, CREATED_AT, DELETED_AT) VALUES
         (${vehicle.name}, ${vehicle.createdAt}, ${vehicle.deletedAt})""".update
          .withUniqueGeneratedKeys[Long]("id")
          .transact(transactor)
      vehicle <- find(id).map(_.getOrElse(throw new IllegalStateException("Could not find newly created entity!")))
    } yield vehicle
  }

  override def update(vehicle: Vehicle): Task[Vehicle] = {
    for {
      id <- sql"""UPDATE VEHICLE SET
          NAME = ${vehicle.vehicle.name},
          CREATED_AT = ${vehicle.vehicle.createdAt},
          DELETED_AT = ${vehicle.vehicle.deletedAt}
          WHERE ID = ${vehicle.vehicle.id}""".update.withUniqueGeneratedKeys[Long]("id").transact(transactor)
      vehicle <- find(id).map(_.getOrElse(throw new IllegalStateException("Could not find newly created entity!")))
    } yield vehicle
  }

  override def delete(vehicle: Vehicle): Task[Int] = {
    sql"""DELETE FROM VEHICLE WHERE ID = ${vehicle.vehicle.id}""".update.run.transact(transactor)
  }

  override def markAsDeleted(vehicleId: Long): Task[Vehicle] = {
    val transaction = for {
      _ <- sql"""UPDATE VEHICLE SET DELETED_AT = NOW() WHERE ID = $vehicleId""".update.run
      vehicle <- findBy(fr""" WHERE V.ID = $vehicleId""", 0, Int.MaxValue)
    } yield vehicle
    transaction.transact(transactor).map(_.head)
  }

  override def find(id: Long): Task[Option[Vehicle]] = {
    sql"""SELECT ID, NAME, CREATED_AT, DELETED_AT FROM VEHICLE WHERE ID = $id"""
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
    findBy(Fragment.empty, offset, limit).transact(transactor)
  }

  override def findAllActive(offset: Int, limit: Int): Task[List[Vehicle]] = {
    findBy(fr""" WHERE DELETED_AT IS NULL""", offset, limit).transact(transactor)
  }

  override def findList(ids: List[Long]): Task[List[Vehicle]] = {
    if (ids.isEmpty) Task(List.empty)
    else findBy(Fragments.in(fr" WHERE V.ID", NonEmptyList.fromListUnsafe(ids)), 0, Int.MaxValue).transact(transactor)
  }

  override def count(): Task[Int] = {
    sql"""SELECT COUNT(*) FROM VEHICLE"""
      .query[Int]
      .unique
      .transact(transactor)
  }

  override def countActive(): Task[Int] = {
    sql"""SELECT COUNT(*) FROM VEHICLE WHERE DELETED_AT IS NULL"""
      .query[Int]
      .unique
      .transact(transactor)
  }

  private def mapToList(in: List[(LightVehicle, Option[LightFleet])]): List[Vehicle] =
    in.groupBy(_._1).map(g => Vehicle(g._1, g._2.filter(_._2.nonEmpty).map(_._2.get))).toList

  private def findBy(fra: Fragment, offset: Int, limit: Int): Free[connection.ConnectionOp, List[Vehicle]] = {
    (sql"""SELECT V.ID, V.NAME, V.CREATED_AT, V.DELETED_AT, F.ID, F.NAME FROM (SELECT ID, NAME, CREATED_AT, DELETED_AT FROM VEHICLE ORDER BY NAME DESC LIMIT $limit OFFSET $offset) V LEFT JOIN VEHICLEFLEET VF on V.ID = VF.VEHICLE_ID LEFT JOIN FLEET F on VF.FLEET_ID = F.ID"""
      ++ fra)
      .query[(LightVehicle, Option[LightFleet])]
      .to[List]
      .map(mapToList)
  }
}

object VehicleDAO {
  def apply(transactor: Transactor[Task]): VehicleDAO = new DefaultVehicleDAO(transactor)
}

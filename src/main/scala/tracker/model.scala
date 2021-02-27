package tracker

import java.time.ZonedDateTime
import doobie.h2.implicits._
import doobie.implicits.javatime._
import doobie.util.{Get, Put, Read, Write}
import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder}

final case class User(
    id: Long,
    name: String,
    createdAt: ZonedDateTime,
    deletedAt: Option[ZonedDateTime] = None,
    password: String,
    username: String,
    roles: Set[Role]
) {}

object User {
  implicit val userEncoder: Encoder[User] = deriveEncoder
  implicit val userDecoder: Decoder[User] = deriveDecoder

  implicit val userReade: Read[User] = Read[(Long, String, ZonedDateTime, Option[ZonedDateTime], String, String, List[String])].map {
    case (id, name, createdAt, deletedAt, password, username, roles) =>
      new User(
        id,
        name,
        createdAt,
        deletedAt,
        password,
        username,
        roles.map {
          Role.fromString(_).fold(e => throw new IllegalStateException(s"Illegal value for role: $e"), identity)
        }.toSet
      )
  }

  implicit val userWrite: Write[User] = Write[(Long, String, ZonedDateTime, Option[ZonedDateTime], String, String, List[String])].contramap { user =>
    (user.id, user.name, user.createdAt, user.deletedAt, user.password, user.username, user.roles.map(Role.toString).toList)
  }
}

sealed trait Role

object Roles {

  case object Read extends Role

  case object Admin extends Role

  case object User extends Role

}

object Role {
  implicit val roleDecoder: Decoder[Role] = Decoder.decodeString.emap(fromString)
  implicit val roleEncoder: Encoder[Role] = Encoder.encodeString.contramap(toString)
  implicit val roleRead: Get[Role] = Get[String].temap(fromString)
  implicit val roleWrite: Put[Role] = Put[String].contramap(toString)
  implicit val roleListWrite: Put[List[Role]] = Put[List[String]].contramap(_.map(toString))

  def fromString(name: String): Either[String, Role] = {
    name match {
      case "ADMIN" => Right(Roles.Admin)
      case "READ"  => Right(Roles.Read)
      case "USER"  => Right(Roles.User)
      case _       => Left(s"Non existing role: $name")
    }
  }

  def toString(role: Role): String = {
    role match {
      case Roles.Read  => "READ"
      case Roles.Admin => "ADMIN"
      case Roles.User  => "USER"
    }
  }
}

final case class LightVehicle(id: Long, name: String) {}
final case class Vehicle(vehicle: LightVehicle, fleets: List[LightFleet]) {
  def toLight: LightVehicle = this.vehicle
}

object LightVehicle {
  implicit val encoder: Encoder[LightVehicle] = deriveEncoder
  implicit val decoder: Decoder[LightVehicle] = deriveDecoder
}

object Vehicle {
  implicit val encoder: Encoder[Vehicle] = deriveEncoder
  implicit val decoder: Decoder[Vehicle] = deriveDecoder
}

final case class LightFleet(id: Long, name: String) {}
final case class Fleet(fleet: LightFleet, vehicles: List[LightVehicle]) {}

object LightFleet {
  implicit val encoder: Encoder[LightFleet] = deriveEncoder
  implicit val decoder: Decoder[LightFleet] = deriveDecoder
}

object Fleet {
  implicit val encoder: Encoder[Fleet] = deriveEncoder
  implicit val decoder: Decoder[Fleet] = deriveDecoder
}

final case class VehicleFleet(id: Long, vehicleId: Long, fleetId: Long)

object VehicleFleet {
  implicit val encoder: Encoder[VehicleFleet] = deriveEncoder
  implicit val decoder: Decoder[VehicleFleet] = deriveDecoder
}

final case class Position(id: Long, vehicleId: Long, speed: Double, latitude: Long, longitude: Long, timestamp: ZonedDateTime)

object Position {
  implicit val encoder: Encoder[User] = deriveEncoder
  implicit val decoder: Decoder[User] = deriveDecoder
}

package tracker

import java.time.ZonedDateTime
import doobie.h2.implicits._
import doobie.implicits.javatime._
import doobie.util.{Get, Put, Read, Write}
import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder}

final case class User(
    id: Option[Long],
    name: String,
    createdAt: ZonedDateTime = ZonedDateTime.now(),
    deletedAt: Option[ZonedDateTime] = None,
    password: String,
    username: String,
    roles: Set[Role]
)

object User {
  implicit val userEncoder: Encoder[User] =
    Encoder.forProduct6("id", "name", "created_at", "deleted_at", "username", "roles") { u =>
      (u.id, u.name, u.createdAt, u.deletedAt, u.username, u.roles)
    }
  implicit val userDecoder: Decoder[User] = deriveDecoder

  implicit val userReade: Read[User] =
    Read[(Option[Long], String, ZonedDateTime, Option[ZonedDateTime], String, String, List[String])].map {
      case (id, name, createdAt, deletedAt, password, username, roles) =>
        new User(
          id,
          name,
          createdAt,
          deletedAt,
          password,
          username,
          roles.map {
            Role
              .fromString(_)
              .fold(e => throw new IllegalStateException(s"Illegal value for role: $e"), identity)
          }.toSet
        )
    }

  implicit val userWrite: Write[User] =
    Write[(Option[Long], String, ZonedDateTime, Option[ZonedDateTime], String, String, List[String])].contramap { user =>
      (
        user.id,
        user.name,
        user.createdAt,
        user.deletedAt,
        user.password,
        user.username,
        user.roles.map(Role.toString).toList
      )
    }
}

sealed trait Role

object Roles {
  case object Admin extends Role

  case object User extends Role

  case object Reader extends Role

  case object Editor extends Role

  case object Tracker extends Role

}

object Role {
  implicit val roleDecoder: Decoder[Role] = Decoder.decodeString.emap(fromString)
  implicit val roleEncoder: Encoder[Role] = Encoder.encodeString.contramap(toString)
  implicit val roleRead: Get[Role] = Get[String].temap(fromString)
  implicit val roleWrite: Put[Role] = Put[String].contramap(toString)
  implicit val roleListWrite: Put[List[Role]] = Put[List[String]].contramap(_.map(toString))

  def fromString(name: String): Either[String, Role] = {
    name match {
      case "ADMIN"   => Right(Roles.Admin)
      case "USER"    => Right(Roles.User)
      case "READER"  => Right(Roles.Reader)
      case "EDITOR"  => Right(Roles.Editor)
      case "TRACKER" => Right(Roles.Tracker)
      case _         => Left(s"Non existing role: $name")
    }
  }

  def toString(role: Role): String = {
    role match {
      case Roles.Admin   => "ADMIN"
      case Roles.User    => "USER"
      case Roles.Editor  => "EDITOR"
      case Roles.Reader  => "READER"
      case Roles.Tracker => "TRACKER"
    }
  }
}

final case class LightVehicle(id: Option[Long], name: String) {
  lazy val ID: Long = id.getOrElse(throw new IllegalStateException("LightVehicle without identifier"))
}

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

case class LightFleet(id: Option[Long] = None, name: String) {
  lazy val ID: Long = id.getOrElse(throw new IllegalStateException("LightFleet without identifier"))
}
final case class Fleet(fleet: LightFleet, vehicles: List[LightVehicle])

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

final case class Position(
    id: Option[Long],
    vehicleId: Long,
    trackId: Long,
    speed: Double,
    latitude: Double,
    longitude: Double,
    timestamp: ZonedDateTime = ZonedDateTime.now(),
    sessionId: String
) {
  lazy val ID: Long = id.getOrElse(throw new IllegalStateException("Position without identifier"))
}

object Position {
  implicit val encoder: Encoder[Position] = deriveEncoder
  implicit val decoder: Decoder[Position] = deriveDecoder
}

final case class LightTrack(id: Option[Long], vehicleId: Long, timestamp: ZonedDateTime = ZonedDateTime.now()) {
  lazy val ID: Long = id.getOrElse(throw new IllegalStateException("LightTrack without identifier"))
}

final case class Track(track: LightTrack, vehicle: LightVehicle)

object LightTrack {
  implicit val encoder: Encoder[LightTrack] = deriveEncoder
  implicit val decoder: Decoder[LightTrack] = deriveDecoder
}

object Track {
  implicit val encoder: Encoder[Track] = deriveEncoder
  implicit val decoder: Decoder[Track] = deriveDecoder
}

final case class LightTracker(
    id: Option[Long],
    name: String,
    vehicleId: Long,
    token: String,
    createdAt: ZonedDateTime = ZonedDateTime.now(),
    deletedAt: Option[ZonedDateTime]
) {
  lazy val ID: Long = id.getOrElse(throw new IllegalStateException("LightTracker without identifier"))
}

final case class Tracker(tracker: LightTracker, vehicle: LightVehicle)

object LightTracker {
  implicit val encoder: Encoder[LightTracker] = deriveEncoder
  implicit val decoder: Decoder[LightTracker] = deriveDecoder
}

object Tracker {
  implicit val encoder: Encoder[Tracker] = deriveEncoder
  implicit val decoder: Decoder[Tracker] = deriveDecoder
}

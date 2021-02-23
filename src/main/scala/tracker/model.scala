package tracker

import doobie.util.{Get, Put, Read, Write}
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto._
import doobie.h2.implicits._
import doobie.implicits.javatime._
import java.time.ZonedDateTime

final case class User(id: Long,
                      name: String,
                      createdAt: ZonedDateTime,
                      deletedAt: Option[ZonedDateTime] = None,
                      password: String,
                      username: String, roles: Set[Role]) {

}

object User {
  implicit val encoder: Encoder[User] = deriveEncoder

  implicit val userReader: Read[User] = Read[(Long, String, ZonedDateTime, Option[ZonedDateTime], String, String, List[String])].map {
    case (id, name, createdAt, deletedAt, password, username, roles) =>
      new User(id, name, createdAt, deletedAt, password, username, roles.map(Role.fromString(_).fold(e => throw new IllegalStateException(s"Illegal value for role: $e"), identity)).toSet)
  }

  implicit val userWriter: Write[User] = Write[(Long, String, ZonedDateTime, Option[ZonedDateTime], String, String, List[String])].contramap {
    user => (user.id, user.name, user.createdAt, user.deletedAt, user.password, user.username, user.roles.map(Role.toString).toList)
  }
}

sealed trait Role

object Roles {

  case object Read extends Role

  case object Write extends Role

  case object User extends Role

}

object Role {
  implicit val decoder: Decoder[Role] = Decoder.decodeString.emap(fromString)
  implicit val encodeRole: Encoder[Role] = Encoder.encodeString.contramap(toString)
  implicit val roleRead: Get[Role] = Get[String].temap(fromString)
  implicit val roleWrite: Put[Role] = Put[String].contramap(toString)

  def fromString(name: String): Either[String, Role] = {
    name match {
      case "READ" => Right(Roles.Read)
      case "WRITE" => Right(Roles.Write)
      case "USER" => Right(Roles.User)
      case _ => Left(s"Non existing role: $name")
    }
  }

  def toString(role: Role): String = {
    role match {
      case Roles.Read => "READ"
      case Roles.Write => "WRITE"
      case Roles.User => "USER"
    }
  }
}

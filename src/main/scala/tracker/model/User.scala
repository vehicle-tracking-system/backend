package tracker.model

import io.circe.Encoder
import io.circe.generic.semiauto._

import java.time.ZonedDateTime

final case class User(id: Long,
                      name: String,
                      createdAt: ZonedDateTime,
                      deletedAt: Option[ZonedDateTime] = None,
                      password: String,
                      username: String) {

}

object User {
//  implicit val encode: Encoder[User] = (a: User) => Json.obj(
//    ("id", Json.fromLong(a.id)),
//    ("username", Json.fromString(a.username)),
//    ("name", Json.fromString(a.name)),
//    ("createdAt", Json.fromString(a.createdAt.format(DateTimeFormatter.ISO_ZONED_DATE_TIME))),
//    ("deletedAt", Json.fromString(a.deletedAt match {
//      case Some(date) => date.format(DateTimeFormatter.ISO_ZONED_DATE_TIME)
//      case None => "N/A"
//    })),
//    ("password", Json.fromString(a.password)),
//  )

  implicit val encoder: Encoder[User] = deriveEncoder
}

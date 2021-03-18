package tracker

import io.circe.{Decoder, HCursor}
import io.circe.generic.semiauto.deriveDecoder
import tracker.utils.PasswordUtility

import java.time.ZonedDateTime

abstract class UserRequest {
  def user: User
}

final case class LoginRequest(username: String, password: String)

object LoginRequest {
  implicit val decoder: Decoder[LoginRequest] = deriveDecoder
}

final case class NewUserRequest(override val user: User) extends UserRequest

object NewUserRequest {
  implicit val decoder: Decoder[NewUserRequest] = //deriveDecoder
    (c: HCursor) =>
      for {
        username <- c.downField("username").as[String]
        name <- c.downField("name").as[String]
        roles <- c.downField("roles").as[List[Role]]
        password <- c.downField("password").as[String]
      } yield {
        new NewUserRequest(
          User(None, name, ZonedDateTime.now(), None, PasswordUtility.hashPassword(password), username, roles.toSet)
        )
      }
}

final case class UpdateUserRequest(override val user: User) extends UserRequest

object UpdateUserRequest {
  implicit val decoder: Decoder[UpdateUserRequest] = deriveDecoder
}

final case class PositionRequest(position: Position)

object PositionRequest {
  implicit val decoder: Decoder[PositionRequest] = (c: HCursor) =>
    for {
      vehicleId <- c.downField("vehicleId").as[Long]
      trackId <- c.downField("trackId").as[Long]
      speed <- c.downField("speed").as[Double]
      latitude <- c.downField("latitude").as[Double]
      longitude <- c.downField("longitude").as[Double]
    } yield {
      new PositionRequest(
        Position(None, vehicleId, Some(trackId), speed, latitude, longitude)
      )
    }
}

final case class PageRequest(page: Int, pageSize: Int)

object PageRequest {
  implicit val decoder: Decoder[PageRequest] = (c: HCursor) =>
    for {
      page <- c.downField("page").as[Option[Int]]
      pageSize <- c.downField("pageSize").as[Option[Int]]
    } yield PageRequest(page.getOrElse(1), pageSize.getOrElse(Int.MaxValue))
}

final case class VehiclePositionsRequest(vehicleId: Long, page: Int = 1, pageSize: Int = 20)

object VehiclePositionsRequest {
  implicit val decoder: Decoder[VehiclePositionsRequest] = deriveDecoder
}

final case class VehiclePositionHistoryRequest(vehicleId: Long, since: ZonedDateTime, until: ZonedDateTime = ZonedDateTime.now())

object VehiclePositionHistoryRequest {
  implicit val decoder: Decoder[VehiclePositionHistoryRequest] = deriveDecoder
}

final case class NewTrackRequest(position: Position, track: Track)

object NewTrackRequest {
  implicit val decoder: Decoder[NewTrackRequest] = (c: HCursor) =>
    for {
      vehicleId <- c.downField("vehicleId").as[Long]
      speed <- c.downField("speed").as[Double]
      latitude <- c.downField("latitude").as[Double]
      longitude <- c.downField("longitude").as[Double]
    } yield {
      new NewTrackRequest(
        Position(None, vehicleId, None, speed, latitude, longitude),
        Track(None, vehicleId)
      )
    }
}

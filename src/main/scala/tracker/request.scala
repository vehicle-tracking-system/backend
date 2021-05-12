package tracker

import io.circe.{Decoder, HCursor}
import io.circe.generic.semiauto.deriveDecoder

import java.time.ZonedDateTime

sealed trait UserRequest

final case class LoginRequest(username: String, password: String)

object LoginRequest {
  implicit val decoder: Decoder[LoginRequest] = deriveDecoder
}

final case class NewUserRequest(username: String, name: String, roles: List[Role], password: String) extends UserRequest

object NewUserRequest {
  implicit val decoder: Decoder[NewUserRequest] = deriveDecoder
}

final case class NewPasswordRequest(password: String)

object NewPasswordRequest {
  implicit val decoder: Decoder[NewPasswordRequest] = deriveDecoder
}

final case class UpdateUserRequest(id: Long, username: Option[String], name: Option[String], roles: Option[List[Role]]) extends UserRequest

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
      sessionId <- c.downField("sessionId").as[String]
    } yield {
      new PositionRequest(
        Position(None, vehicleId, trackId, speed, latitude, longitude, sessionId = sessionId)
      )
    }
}

final case class PositionsRequest(vehicleId: Long, positions: List[Position])

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

sealed trait VehicleRequest

final case class NewVehicleRequest(vehicle: LightVehicle, fleetsId: List[Long]) extends VehicleRequest

object NewVehicleRequest {
  implicit val decoder: Decoder[NewVehicleRequest] = (c: HCursor) =>
    for {
      name <- c.downField("name").as[String]
      fleets <- c.downField("fleets").as[List[Long]]
    } yield {
      new NewVehicleRequest(
        LightVehicle(None, name),
        fleets
      )
    }
}

final case class NewFleetRequest(fleet: LightFleet)

object NewFleetRequest {
  implicit val decoder: Decoder[NewFleetRequest] = (c: HCursor) =>
    for {
      name <- c.downField("name").as[String]
    } yield {
      new NewFleetRequest(
        LightFleet(None, name)
      )
    }
}
final case class NewTrackRequest(track: Track)

object NewTrackRequest {
  implicit val decoder: Decoder[NewTrackRequest] = (c: HCursor) =>
    for {
      vehicleId <- c.downField("vehicleId").as[Long]
    } yield {
      new NewTrackRequest(
        Track(LightTrack(None, vehicleId), LightVehicle(Some(vehicleId), ""))
      )
    }
}

sealed trait TrackerRequest

final case class NewTrackerRequest(name: String, vehicleId: Long) extends TrackerRequest

object NewTrackerRequest {
  implicit val decoder: Decoder[NewTrackerRequest] = deriveDecoder
}

final case class UpdateVehicleRequest(data: Vehicle) extends VehicleRequest

object UpdateVehicleRequest {
  implicit val decoder: Decoder[UpdateVehicleRequest] = deriveDecoder
}

final case class UpdateTrackerRequest(tracker: LightTracker) extends TrackerRequest

object UpdateTrackerRequest {
  implicit val decoder: Decoder[UpdateTrackerRequest] = (c: HCursor) =>
    for {
      id <- c.downField("id").as[Long]
      name <- c.downField("name").as[String]
      vehicleId <- c.downField("vehicleId").as[Long]
    } yield {
      new UpdateTrackerRequest(LightTracker(Some(id), name, vehicleId, "N/A", ZonedDateTime.now(), None))
    }
}

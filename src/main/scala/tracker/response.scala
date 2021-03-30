package tracker

import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder

final case class NotFoundResponse(text: String)

object NotFoundResponse {
  implicit val encoder: Encoder[NotFoundResponse] = deriveEncoder
}

final case class AccessTokenResponse(token: String)

object AccessTokenResponse {
  implicit val encoder: Encoder[AccessTokenResponse] = deriveEncoder
}

final case class LoginResponse(token: String, user: User)

object LoginResponse {
  implicit val encoder: Encoder[LoginResponse] = deriveEncoder
}

final case class TrackerResponse(tracker: Tracker, vehicle: LightVehicle)

object TrackerResponse {
  implicit val encoder: Encoder[TrackerResponse] = deriveEncoder
}

package tracker.request

import io.circe.Decoder
import io.circe.generic.semiauto._

final case class LoginRequest(username: String, password: String)

object LoginRequest {
  implicit val decoder: Decoder[LoginRequest] = deriveDecoder
}

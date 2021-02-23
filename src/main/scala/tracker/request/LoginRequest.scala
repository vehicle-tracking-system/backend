package tracker.request

import io.circe.Decoder
import io.circe.generic.semiauto._

case class LoginRequest(username: String, password: String)

object LoginRequest {
  implicit val decoder: Decoder[LoginRequest] = deriveDecoder
}
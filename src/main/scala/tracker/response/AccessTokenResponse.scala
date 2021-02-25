package tracker.response

import io.circe.Encoder
import io.circe.generic.semiauto._

final case class AccessTokenResponse(token: String)

object AccessTokenResponse {
  implicit val encoder: Encoder[AccessTokenResponse] = deriveEncoder
}

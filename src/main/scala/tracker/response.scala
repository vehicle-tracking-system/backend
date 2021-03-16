package tracker

import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder

final case class AccessTokenResponse(token: String)

object AccessTokenResponse {
  implicit val encoder: Encoder[AccessTokenResponse] = deriveEncoder
}

final case class LoginResponse(token: String, user: User)

object LoginResponse {
  implicit val encoder: Encoder[LoginResponse] = deriveEncoder
}

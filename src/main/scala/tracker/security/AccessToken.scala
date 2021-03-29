package tracker.security

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import tracker.Role

import java.time.Instant

final case class RawAccessToken(value: String) extends AnyVal {
  override def toString: String = value
}

final case class AccessToken(
    clientId: Long,
    clientRoles: Seq[String],
    expiration: Option[Instant] = None,
    notBefore: Option[Instant] = None,
    issuedAt: Option[Instant] = None,
    raw: RawAccessToken
)

final case class AccessTokenPayload(
    clientId: Long,
    roles: Set[Role]
)

object AccessTokenPayload {
  implicit val encoder: Encoder[AccessTokenPayload] = deriveEncoder
  implicit val decoder: Decoder[AccessTokenPayload] = deriveDecoder
}

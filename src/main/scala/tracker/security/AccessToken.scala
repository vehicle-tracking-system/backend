package tracker.security

import java.time.Instant

final case class RawAccessToken(value: String) extends AnyVal {
  override def toString: String = value
}

final case class AccessToken(
    clientId: String,
    clientRoles: Map[String, Seq[String]],
    expiration: Option[Instant] = None,
    notBefore: Option[Instant] = None,
    issuedAt: Option[Instant] = None,
    raw: RawAccessToken
)

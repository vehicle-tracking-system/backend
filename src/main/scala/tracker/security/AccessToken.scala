package tracker.security

import java.time.Instant

case class RawAccessToken(value: String) extends AnyVal {
  override def toString: String = value
}

case class AccessToken(
                        clientId: String,
                        clientRoles: Map[String, Seq[String]],
                        expiration: Option[Instant] = None,
                        notBefore: Option[Instant] = None,
                        issuedAt: Option[Instant] = None,
                        raw: RawAccessToken
                      )

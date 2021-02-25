package tracker.security

import java.time.Clock

import io.circe.Json
import pdi.jwt.{JwtCirce, JwtClaim, JwtHeader}
import tracker.config.JwtConfig

object AccessTokenBuilder {
  implicit val clock: Clock = Clock.systemUTC

  def createToken(payload: Json, jwtConfig: JwtConfig): String = {
    val header = JwtHeader(jwtConfig.algorithm)
    val claim = JwtClaim(payload.noSpaces).issuedNow.expiresIn(jwtConfig.expiration)

    JwtCirce.encode(header, claim, jwtConfig.secret)
  }
}

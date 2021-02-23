package tracker.security

import io.circe.Json
import pdi.jwt.{JwtCirce, JwtClaim, JwtHeader}
import tracker.config.JwtConfig

import java.time.Clock

object AccessTokenBuilder {
  implicit val clock: Clock = Clock.systemUTC

  def createToken(payload: Json, jwtConfig: JwtConfig): String = {
    val header = JwtHeader(jwtConfig.algorithm)
    val claim = JwtClaim(payload.noSpaces).issuedNow.expiresIn(600)

    JwtCirce.encode(header, claim, jwtConfig.secret)
  }
}

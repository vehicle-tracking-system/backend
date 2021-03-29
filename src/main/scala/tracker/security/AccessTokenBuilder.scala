package tracker.security

import io.circe.syntax.EncoderOps
import pdi.jwt.{JwtCirce, JwtClaim, JwtHeader}
import tracker.config.JwtConfig

import java.time.Clock

object AccessTokenBuilder {
  implicit val clock: Clock = Clock.systemUTC

  def createToken(payload: AccessTokenPayload, jwtConfig: JwtConfig): String = {
    val header = JwtHeader(jwtConfig.algorithm)
    val claim = JwtClaim(payload.asJson.noSpacesSortKeys).issuedNow.expiresIn(jwtConfig.expiration)

    JwtCirce.encode(header, claim, jwtConfig.secret)
  }

  def createUnlimitedToken(payload: AccessTokenPayload, jwtConfig: JwtConfig): String = {
    val header = JwtHeader(jwtConfig.algorithm)
    val claim = JwtClaim(payload.asJson.noSpacesSortKeys).issuedNow

    JwtCirce.encode(header, claim, jwtConfig.secret)
  }
}

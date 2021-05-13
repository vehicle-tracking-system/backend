package tracker.security

import io.circe.syntax.EncoderOps
import pdi.jwt.{JwtCirce, JwtClaim, JwtHeader}
import tracker.config.JwtConfig

import java.time.Clock

object AccessTokenBuilder {

  /**
    * Create new JWT token with `payload`. Token lifetime is to `expiration` (seconds) in JWT config
    *
    * @return Serialized JWT token
    */
  def createToken(payload: AccessTokenPayload, jwtConfig: JwtConfig)(implicit clock: Clock = Clock.systemUTC()): String = {
    val header = JwtHeader(jwtConfig.algorithm)
    val claim = JwtClaim(payload.asJson.noSpacesSortKeys).issuedNow.expiresIn(jwtConfig.expiration)

    JwtCirce.encode(header, claim, jwtConfig.secret)
  }

  /**
    * Create new JWT token with `payload`. Token lifetime in not limited. Use this method carefully! The token must be revocable on server side.
    * Otherwise it can be a serious security issue.
    *
    * @return Serialized JWT token with unlimited lifetime
    */
  def createUnlimitedToken(payload: AccessTokenPayload, jwtConfig: JwtConfig)(implicit clock: Clock = Clock.systemUTC()): String = {
    val header = JwtHeader(jwtConfig.algorithm)
    val claim = JwtClaim(payload.asJson.noSpacesSortKeys).issuedNow

    JwtCirce.encode(header, claim, jwtConfig.secret)
  }
}

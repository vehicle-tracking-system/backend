package tracker.service

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers._
import pdi.jwt.JwtAlgorithm
import tracker.config.JwtConfig
import tracker.security.{AccessTokenBuilder, AccessTokenPayload, DefaultAccessTokenParser}
import tracker.Role

class AccessTokenParserTest extends AnyFlatSpec {
  "Token" should "be generated and parse with specific payload" in {
    import tracker.Roles._
    val secret = "thisissecreat"
    val payload = AccessTokenPayload(1223, Set(User, Admin))
    val token = AccessTokenBuilder.createToken(payload, JwtConfig(secret, 100, JwtAlgorithm.HMD5))
    val parsedToken = DefaultAccessTokenParser.parseAccessToken(token, secret).getOrElse(fail("Parsing token error"))
    token shouldEqual parsedToken.raw.value
    payload.roles.toList shouldEqual parsedToken.clientRoles.map(Role.fromString).map(_.getOrElse(fail("Parsing roles error")))
    payload.clientId shouldEqual parsedToken.clientId
  }
  it should "recognize expiration" in {
    import tracker.Roles._
    val secret = "thisissecreat"
    val payload = AccessTokenPayload(1223, Set(User, Admin))
    val token = AccessTokenBuilder.createToken(payload, JwtConfig(secret, -20, JwtAlgorithm.HMD5))
    val parsedToken = DefaultAccessTokenParser.parseAccessToken(token, secret)
    parsedToken shouldEqual Left("JWT token expired")
  }
}

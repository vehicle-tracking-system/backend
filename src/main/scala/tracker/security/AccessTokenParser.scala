package tracker.security

import java.time.Instant
import java.time.format.DateTimeParseException

import cats.syntax.either._
import io.circe._
import io.circe.parser.{parse => circeParse}
import pdi.jwt.exceptions.JwtExpirationException
import pdi.jwt.{Jwt, JwtAlgorithm}

import scala.util.control.NonFatal
import scala.util.{Failure, Success}

trait AccessTokenParser {

  /**
    * Parse access token and check its integrity.
    * @param accessToken
    * @param secret
    * @return Parsed access token as Right if the provided `accessToken` string is valid and parsable, otherwise Left with error message.
    */
  def parseAccessToken(accessToken: String, secret: String): Either[String, AccessToken]
}

object DefaultAccessTokenParser extends AccessTokenParser {
  private implicit final val decodeInstant: Decoder[Instant] =
    Decoder.instance { c =>
      c.as[Long] match {
        case Right(s) =>
          try Right(Instant.ofEpochSecond(s))
          catch {
            case _: DateTimeParseException => Left(DecodingFailure("Instant", c.history))
          }
        case l @ Left(_) => l.asInstanceOf[Decoder.Result[Instant]]
      }
    }

  def parseAccessToken(accessToken: String, secret: String): Either[String, AccessToken] = {
    Jwt.decodeRawAll(accessToken, secret, JwtAlgorithm.allHmac()) match {
      case Success((_, jwtoken, _))           => parseJwToken(jwtoken, accessToken)
      case Failure(_: JwtExpirationException) => Left("JWT token expired")
      case Failure(NonFatal(e))               => Left(s"${e.getClass.getSimpleName}:${e.getMessage}")
    }
  }

  private def parseJwToken(token: String, rawToken: String): Either[String, AccessToken] = {
    circeParse(token)
      .map { json =>
        val cursor = json.hcursor
        AccessToken(
          clientId = cursor.get[Long]("clientId").getOrElse(Long.MinValue),
          clientRoles = cursor.get[Seq[String]]("roles").getOrElse(Seq.empty),
          expiration = cursor.get[Instant]("exp").toOption,
          notBefore = cursor.get[Instant]("nbf").toOption,
          issuedAt = cursor.get[Instant]("iat").toOption,
          raw = RawAccessToken(rawToken)
        )
      }
      .leftMap(_ => "Token decoding failure")
  }
}

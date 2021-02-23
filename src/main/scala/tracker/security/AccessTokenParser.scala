package tracker.security

import cats.syntax.either._
import io.circe._
import io.circe.parser.{parse => circeParse}
import pdi.jwt.exceptions.JwtExpirationException
import pdi.jwt.{Jwt, JwtAlgorithm}

import java.time.Instant
import java.time.format.DateTimeParseException
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

trait AccessTokenParser {
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
        case l@Left(_) => l.asInstanceOf[Decoder.Result[Instant]]
      }
    }

  def parseAccessToken(accessToken: String, secret: String): Either[String, AccessToken] = {
    Jwt.decodeRawAll(accessToken, secret, JwtAlgorithm.allHmac()) match {
      case Success((_, jwtoken, _)) => parseJwToken(jwtoken, accessToken)
      case Failure(_: JwtExpirationException) => Left("JWT token expired")
      case Failure(NonFatal(e)) => Left(s"${e.getClass.getSimpleName}:${e.getMessage}")
    }
  }

  private def parseJwToken(token: String, rawToken: String): Either[String, AccessToken] = {
    circeParse(token).map { json =>
      val cursor = json.hcursor
      val clientRoles = extractRolesMap(cursor)
      AccessToken(
        clientId = cursor.get[String]("clientId").getOrElse("unavailable"),
        clientRoles = clientRoles,
        expiration = cursor.get[Instant]("exp").toOption,
        notBefore = cursor.get[Instant]("nbf").toOption,
        issuedAt = cursor.get[Instant]("iat").toOption,
        raw = RawAccessToken(rawToken)
      )
    }.leftMap(_ => "Token decoding failure")
  }

  private def extractRolesMap(cursor: ACursor): Map[String, Seq[String]] = {
    val c = cursor.downField("resource_access")
    val elems = c.keys.getOrElse(Vector.empty)
    elems.map { elemName =>
      elemName -> extractRoles(c, elemName)
    }.toMap
  }

  private def extractRoles(c: ACursor, elemName: String): Seq[String] = {
    c.downField(elemName).get[Seq[String]]("roles").getOrElse(Seq.empty)
  }
}
package tracker

import io.circe.{Decoder, HCursor}
import io.circe.generic.semiauto.deriveDecoder
import tracker.utils.PasswordUtility

import java.time.ZonedDateTime

abstract class UserRequest {
  def user: User
}

final case class LoginRequest(username: String, password: String)

object LoginRequest {
  implicit val decoder: Decoder[LoginRequest] = deriveDecoder
}

final case class NewUserRequest(override val user: User) extends UserRequest

object NewUserRequest {
  implicit val decoder: Decoder[NewUserRequest] = //deriveDecoder
    (c: HCursor) =>
      for {
        username <- c.downField("username").as[String]
        name <- c.downField("name").as[String]
        roles <- c.downField("roles").as[List[Role]]
        password <- c.downField("password").as[String]
      } yield {
        new NewUserRequest(
          User(None, name, ZonedDateTime.now(), None, PasswordUtility.hashPassword(password), username, roles.toSet)
        )
      }
}

final case class UpdateUserRequest(override val user: User) extends UserRequest

object UpdateUserRequest {
  implicit val decoder: Decoder[UpdateUserRequest] = deriveDecoder
}

package tracker

import io.circe.generic.semiauto.deriveDecoder
import io.circe.{Decoder, HCursor}
import tracker.utils.PasswordUtility

import java.time.ZonedDateTime

final case class LoginRequest(username: String, password: String)

object LoginRequest {
  implicit val decoder: Decoder[LoginRequest] = deriveDecoder
}

abstract class UserRequest {
  def user: User
}

final case class NewUserRequest(override val user: User) extends UserRequest

object NewUserRequest {
  implicit val decoder: Decoder[NewUserRequest] = (c: HCursor) =>
    for {
      username <- c.downField("username").as[String]
      name <- c.downField("name").as[String]
      password <- c.downField("password").as[String]
      roles <- c.downField("roles").as[List[Role]]
    } yield {
      new NewUserRequest(User(-1, name, ZonedDateTime.now(), None, PasswordUtility.hashPassword(password), username, roles.toSet))
    }
}

final case class UpdateUserRequest(override val user: User) extends UserRequest

object UpdateUserRequest {
  implicit val decoder: Decoder[UpdateUserRequest] = deriveDecoder
}

package tracker

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.EncoderOps

sealed trait MessageType

object MessageType {
  case object Empty extends MessageType
  case object Heartbeat extends MessageType
  case object Text extends MessageType
  case object Position extends MessageType
  case object Subscribe extends MessageType

  implicit val msgTypeDecoder: Decoder[MessageType] = Decoder.decodeString.emap(fromString)
  implicit val msgTypeEncoder: Encoder[MessageType] = Encoder.encodeString.contramap(toString)

  def fromString(name: String): Either[String, MessageType] = {
    name match {
      case "EMPTY"     => Right(Empty)
      case "HEARTBEAT" => Right(Heartbeat)
      case "TEXT"      => Right(Text)
      case "POSITION"  => Right(Position)
      case "SUBSCRIBE" => Right(Subscribe)
      case _           => Left(s"Non existing message type: $name")
    }
  }

  def toString(msgType: MessageType): String = {
    msgType match {
      case Empty     => "EMPTY"
      case Heartbeat => "HEARTBEAT"
      case Text      => "TEXT"
      case Position  => "POSITION"
      case Subscribe => "SUBSCRIBE"
    }
  }
}

sealed trait WebSocketMessage {
  def msgType: MessageType
  def payload: String
}

object WebSocketMessage {
  import tracker.MessageType._

  implicit val websocketMessageDecoder: Decoder[WebSocketMessage] = DefaultWebSocketMessage.websocketMessageDecoder.map(identity)
  implicit val websocketMessageEncoder: Encoder[WebSocketMessage] =
    DefaultWebSocketMessage.websocketMessageEncoder.contramap(_.asInstanceOf[DefaultWebSocketMessage])
  val heartbeat: WebSocketMessage = new DefaultWebSocketMessage(Heartbeat, "")
  def text(text: String): WebSocketMessage = new DefaultWebSocketMessage(Text, text)
  def position(position: Position): WebSocketMessage = new DefaultWebSocketMessage(Position, position.asJson.noSpacesSortKeys)
}

private case class DefaultWebSocketMessage(msgType: MessageType, payload: String) extends WebSocketMessage

private object DefaultWebSocketMessage {
  implicit val websocketMessageDecoder: Decoder[DefaultWebSocketMessage] = deriveDecoder
  implicit val websocketMessageEncoder: Encoder[DefaultWebSocketMessage] = deriveEncoder
}

sealed trait AuthenticatedWebSocketMessage extends WebSocketMessage {
  def token: Option[String]
}

object AuthenticatedWebSocketMessage {
  implicit val websocketAuthenticatedMessageDecoder: Decoder[AuthenticatedWebSocketMessage] =
    DefaultAuthenticatedSocketMessage.websocketAuthenticatedMessageDecoder.map(identity)
  implicit val websocketAuthenticatedMessageEncoder: Encoder[AuthenticatedWebSocketMessage] =
    DefaultAuthenticatedSocketMessage.websocketAuthenticatedMessageEncoder.contramap(_.asInstanceOf[DefaultAuthenticatedSocketMessage])
}

private case class DefaultAuthenticatedSocketMessage(msgType: MessageType, token: Option[String], payload: String)
    extends AuthenticatedWebSocketMessage

private object DefaultAuthenticatedSocketMessage {
  import tracker.MessageType._

  implicit val websocketAuthenticatedMessageDecoder: Decoder[DefaultAuthenticatedSocketMessage] = deriveDecoder
  implicit val websocketAuthenticatedMessageEncoder: Encoder[DefaultAuthenticatedSocketMessage] = deriveEncoder

  val empty = new DefaultAuthenticatedSocketMessage(Empty, None, "")
  def text(text: String, token: Option[String] = None): AuthenticatedWebSocketMessage = new DefaultAuthenticatedSocketMessage(Text, token, text)
  def position(position: Position, token: Option[String] = None): AuthenticatedWebSocketMessage =
    new DefaultAuthenticatedSocketMessage(Position, token, position.asJson.noSpacesSortKeys)
}

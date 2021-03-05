package tracker

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.EncoderOps

sealed trait MessageType

object MessageType {
  case object Heartbeat extends MessageType
  case object Text extends MessageType
  case object Position extends MessageType

  implicit val msgTypeDecoder: Decoder[MessageType] = Decoder.decodeString.emap(fromString)
  implicit val msgTypeEncoder: Encoder[MessageType] = Encoder.encodeString.contramap(toString)

  def fromString(name: String): Either[String, MessageType] = {
    name match {
      case "HEARTBEAT" => Right(Heartbeat)
      case "TEXT"      => Right(Text)
      case "POSITION"  => Right(Position)
      case _           => Left(s"Non existing message type: $name")
    }
  }

  def toString(msgType: MessageType): String = {
    msgType match {
      case Heartbeat => "HEARTBEAT"
      case Text      => "TEXT"
      case Position  => "POSITION"
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
  def position(position: Position): WebSocketMessage = new DefaultWebSocketMessage(MessageType.Position, position.asJson.noSpaces)
}

private class DefaultWebSocketMessage(val msgType: MessageType, val payload: String) extends WebSocketMessage

private object DefaultWebSocketMessage {
  implicit val websocketMessageDecoder: Decoder[DefaultWebSocketMessage] = deriveDecoder
  implicit val websocketMessageEncoder: Encoder[DefaultWebSocketMessage] = deriveEncoder
}

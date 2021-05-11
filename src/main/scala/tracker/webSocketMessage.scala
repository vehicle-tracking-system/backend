package tracker

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.EncoderOps

sealed trait MessageType

object MessageType {
  case object Empty extends MessageType
  case object Heartbeat extends MessageType
  case object Text extends MessageType
  case object Position extends MessageType
  case object SubscribePositions extends MessageType
  case object UnsubscribePositions extends MessageType
  case object Error extends MessageType
  case object Vehicle extends MessageType

  implicit val decoder: Decoder[MessageType] = Decoder.decodeString.emap(fromString)
  implicit val encoder: Encoder[MessageType] = Encoder.encodeString.contramap(toString)

  def fromString(name: String): Either[String, MessageType] = {
    name match {
      case "EMPTY"                 => Right(Empty)
      case "HEARTBEAT"             => Right(Heartbeat)
      case "TEXT"                  => Right(Text)
      case "POSITION"              => Right(Position)
      case "SUBSCRIBE_POSITIONS"   => Right(SubscribePositions)
      case "UNSUBSCRIBE_POSITIONS" => Right(UnsubscribePositions)
      case "ERROR"                 => Right(Error)
      case "VEHICLE"               => Right(Vehicle)
      case _                       => Left(s"Non existing message type: $name")
    }
  }

  def toString(msgType: MessageType): String = {
    msgType match {
      case Empty                => "EMPTY"
      case Heartbeat            => "HEARTBEAT"
      case Text                 => "TEXT"
      case Position             => "POSITION"
      case SubscribePositions   => "SUBSCRIBE_POSITIONS"
      case UnsubscribePositions => "UNSUBSCRIBE_POSITIONS"
      case Error                => "ERROR"
      case Vehicle              => "VEHICLE"
    }
  }
}

case class SubscribeMessage(id: Long)

object SubscribeMessage {
  implicit val decoder: Decoder[SubscribeMessage] = deriveDecoder
  implicit val encoder: Encoder[SubscribeMessage] = deriveEncoder
}

sealed trait WebSocketMessage {
  def msgType: MessageType
  def token: Option[String]
  def payload: Json
}

object WebSocketMessage {
  import tracker.MessageType._

  implicit val decoder: Decoder[WebSocketMessage] = DefaultWebSocketMessage.decoder.map(identity)
  implicit val encoder: Encoder[WebSocketMessage] =
    DefaultWebSocketMessage.encoder.contramap(_.asInstanceOf[DefaultWebSocketMessage])

  val empty: WebSocketMessage = new DefaultWebSocketMessage(Empty, None, "{}".asJson)
  val heartbeat: WebSocketMessage = new DefaultWebSocketMessage(Heartbeat, None, "{}".asJson)
  val internalError: WebSocketMessage = error("Internal server error")
  def text(text: Json): WebSocketMessage = new DefaultWebSocketMessage(Text, None, text)
  def error(text: String): WebSocketMessage = new DefaultWebSocketMessage(Error, None, Json.obj(("msg", Json.fromString(text))))
  def position(position: Position): WebSocketMessage =
    new DefaultWebSocketMessage(
      Position,
      None,
      //position.asJson.deepMerge(Json.fromFields(List(("isMoving", Json.fromBoolean(isMoving))))).noSpacesSortKeys
      position.asJson
    )
  def vehicle(vehicles: List[Vehicle], token: Option[String] = None): WebSocketMessage =
    new DefaultWebSocketMessage(Vehicle, token, vehicles.asJson)
}

private case class DefaultWebSocketMessage(msgType: MessageType, token: Option[String], payload: Json) extends WebSocketMessage

private object DefaultWebSocketMessage {
  implicit val decoder: Decoder[DefaultWebSocketMessage] = deriveDecoder
  implicit val encoder: Encoder[DefaultWebSocketMessage] = deriveEncoder
}

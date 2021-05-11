package tracker.service

import io.circe.parser.decode
import io.circe.syntax.EncoderOps
import io.circe.Json
import org.scalatest.flatspec.AnyFlatSpec
import tracker.{MessageType, Position, WebSocketMessage}

import java.time.{ZoneId, ZonedDateTime}

class WebSocketTest extends AnyFlatSpec {

  "Heartbeat message" should "be serializable to JSON" in {
    val message: WebSocketMessage = WebSocketMessage.heartbeat
    assert(message.asJson.noSpacesSortKeys == """{"msgType":"HEARTBEAT","payload":"{}","token":null}""")
  }

  "Text message" should "be serializable to JSON" in {
    val message: WebSocketMessage = WebSocketMessage.text(Json.obj(("msg", Json.fromString("This is t e  x t for testING!"))))
    assert(message.asJson.noSpacesSortKeys == """{"msgType":"TEXT","payload":{"msg":"This is t e  x t for testING!"},"token":null}""")
  }
  it should "be parsable from String" in {
    val messageString: String = """{"msgType":"TEXT","payload":{"msg":"This is t e  x t for testING!"},"token":null}"""
    decode[WebSocketMessage](messageString) match {
      case Right(msg) =>
        assert(msg.msgType equals MessageType.Text)
        assert(msg.payload equals Json.obj(("msg", Json.fromString("This is t e  x t for testING!"))))
      case _ => false
    }
  }
  it should "have consistent convertors" in {
    val message: WebSocketMessage = WebSocketMessage.text(Json.obj(("msg", Json.fromString("This is t e  x t for testING!"))))
    decode[WebSocketMessage](message.asJson.noSpacesSortKeys) match {
      case Right(msg) => assert(msg.asJson.noSpacesSortKeys equals message.asJson.noSpacesSortKeys)
      case _          => false
    }
  }

  "Position message" should "be serializable to JSON" in {
    val message: WebSocketMessage =
      WebSocketMessage.position(
        Position(
          Some(1),
          2,
          1,
          52.51,
          50.087823,
          14.430026,
          ZonedDateTime.of(2021, 2, 25, 0, 0, 0, 0, ZoneId.of("Europe/Prague")),
          "sessionId234"
        )
      )

    assert(
      message.asJson.noSpacesSortKeys equals Json
        .obj(
          ("msgType", Json.fromString("POSITION")),
          (
            "payload",
            Json.obj(
              ("id", Json.fromInt(1)),
              ("latitude", Json.fromDouble(50.087823).get),
              ("longitude", Json.fromDouble(14.430026).get),
              ("sessionId", Json.fromString("sessionId234")),
              ("speed", Json.fromDouble(52.51).get),
              ("timestamp", Json.fromString("2021-02-25T00:00:00+01:00[Europe/Prague]")),
              ("trackId", Json.fromLong(1)),
              ("vehicleId", Json.fromLong(2))
            )
          ),
          ("token", Json.Null)
        )
        .noSpacesSortKeys
    )
  }

}

package tracker.service

import io.circe._
import io.circe.syntax._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers._
import tracker._

import java.time.{ZoneId, ZonedDateTime}

class ResponseTest extends AnyFlatSpec {
  "Response" should "NotFound should be serialized to JSON" in {
    val response = NotFoundResponse("page not found")
    val expectedJSON = Json.obj(("text", Json.fromString("page not found")))
    response.asJson shouldEqual expectedJSON
  }
  it should "AccessTokenResponse should be serialized to JSON" in {
    val response = AccessTokenResponse("this is access token")
    val expectedJSON = Json.obj(("token", Json.fromString("this is access token")))
    response.asJson shouldEqual expectedJSON
  }
  it should "LoginResponse should be serialized to JSON" in {
    val user = User(
      Some(345),
      "Karel",
      ZonedDateTime.of(2021, 2, 25, 0, 0, 0, 0, ZoneId.of("Europe/Prague")),
      None,
      "10000:WS4enQfVmeoAtnCXUtRqCdHu+A1Tv7i1QReXH/bj5pU=:aumeogsLFIqGr9HrfvxdMw==",
      "karel",
      Set(Roles.Reader)
    )
    val response = LoginResponse("this is access token", user)
    val expectedJSON = Json.obj(("token", Json.fromString("this is access token")), ("user", user.asJson))
    response.asJson shouldEqual expectedJSON
  }
  it should "TrackerResponse should be serialized to JSON" in {
    val tracker = LightTracker(Some(233), "tracker", 232, "token", ZonedDateTime.of(2021, 2, 25, 0, 0, 0, 0, ZoneId.of("Europe/Prague")), None)
    val vehicle = LightVehicle(Some(667), "vehicle")
    val response = TrackerResponse(Tracker(tracker, vehicle), vehicle)
    val expectedJson = Json.obj(("tracker", Tracker(tracker, vehicle).asJson), ("vehicle", vehicle.asJson))
    response.asJson shouldEqual expectedJson
  }
}

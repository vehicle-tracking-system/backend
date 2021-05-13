package tracker.api

import io.circe.Json
import io.circe.syntax.EncoderOps
import org.http4s.{AuthedRequest, Method, Request, Status}
import org.http4s.circe.jsonDecoder
import org.http4s.implicits.{http4sKleisliResponseSyntaxOptionT, http4sLiteralsSyntax}
import tracker._
import tracker.mocked.{Store, TestClock}
import tracker.module.routes.TrackRoutes
import tracker.utils.TestEnv
import zio.interop.catz._
import zio.test._
import zio.test.Assertion.equalTo

import java.time.{Duration, Instant, ZoneId, ZonedDateTime}

object TrackAPITest extends DefaultRunnableSpec {
  override def spec: Spec[Any, TestFailure[Throwable], TestSuccess] = {
    suite("Track routes suite")(
      testM("Get list of all tracks") {
        val startTime: ZonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(0), ZoneId.of("Europe/Prague"))
        val clock = new TestClock(startTime, Duration.ofSeconds(1))

        val testEnv = TestEnv(
          Store(
            vehicles = List(
              LightVehicle(Some(11234), "Vehicle 22", ZonedDateTime.parse("2012-12-12T12:22:12.121+01:00[Europe/Prague]"), None),
              LightVehicle(Some(3), "Vehicle 2", ZonedDateTime.parse("2012-12-12T12:22:12.121+01:00[Europe/Prague]"), None),
              LightVehicle(Some(2), "CAR", ZonedDateTime.parse("2012-12-12T12:22:12.121+01:00[Europe/Prague]"), None)
            ),
            positions = List(
              Position(Some(1), 11234, 1, 23.4, 66.74316, 161.47482, ZonedDateTime.parse("2021-04-01T16:24:11.252+01:00[Europe/Prague]"), "session1"),
              Position(
                Some(2),
                11234,
                1,
                3.4,
                -31.61897,
                -124.25575,
                ZonedDateTime.parse("2021-04-01T16:20:11.252+01:00[Europe/Prague]"),
                "session1"
              ),
              Position(Some(3), 3, 3, 23.4, 6.84985, -8.17319, ZonedDateTime.parse("2021-01-05T00:24:11.252+01:00[Europe/Prague]"), "session13"),
              Position(Some(4), 3, 3, 23.4, 36.79386, 120.91501, ZonedDateTime.parse("2021-01-05T00:24:00.252+01:00[Europe/Prague]"), "session13"),
              Position(Some(5), 2, 56, 23.4, -23.28416, 64.18032, ZonedDateTime.parse("2021-01-05T22:01:57.034+01:00[Europe/Prague]"), "session345"),
              Position(Some(6), 2, 56, 23.4, 5.70111, 24.03095, ZonedDateTime.parse("2021-01-05T22:02:00.034+01:00[Europe/Prague]"), "session345"),
              Position(Some(7), 11234, 1, 23.4, 5.43438, -179.01164, ZonedDateTime.parse("2021-04-01T16:26:11.252+01:00[Europe/Prague]"), "session1"),
              Position(Some(8), 3, 4, 23.4, 30.63340, 30.63340, ZonedDateTime.parse("2021-01-01T00:00:00.00+01:00[Europe/Prague]"), "session14"),
              Position(Some(9), 2, 56, 23.4, -1.67141, 81.39267, ZonedDateTime.parse("2021-01-05T22:02:01.034+01:00[Europe/Prague]"), "session342"),
              Position(
                Some(10),
                11234,
                1,
                23.4,
                15.83878,
                34.66970,
                ZonedDateTime.parse("2021-04-01T16:30:11.252+01:00[Europe/Prague]"),
                "session11"
              ),
              Position(
                Some(11),
                1,
                2,
                23.4,
                -36.57176,
                -71.78524,
                ZonedDateTime.parse("2021-04-06T06:24:11.252+01:00[Europe/Prague]"),
                "session123"
              ),
              Position(Some(12), 1, 2, 23.4, 19.73304, 59.79881, ZonedDateTime.parse("2021-04-06T06:27:11.252+01:00[Europe/Prague]"), "session123")
            ),
            tracks = List(
              LightTrack(Some(1), 11234, ZonedDateTime.parse("2021-04-01T16:24:11.252+01:00[Europe/Prague]")),
              LightTrack(Some(2), 3, ZonedDateTime.parse("2021-01-05T00:24:12.252+01:00[Europe/Prague]")),
              LightTrack(Some(3), 3, ZonedDateTime.parse("2021-01-01T00:00:00.00+01:00[Europe/Prague]")),
              LightTrack(Some(4), 2, ZonedDateTime.parse("2021-01-05T22:01:57.034+01:00[Europe/Prague]"))
            )
          ),
          clock = clock
        )

        val expectedResponse =
          Page[Track](
            1,
            1,
            Int.MaxValue,
            List(
              Track(
                LightTrack(Some(1), 11234, ZonedDateTime.parse("2021-04-01T16:24:11.252+01:00[Europe/Prague]")),
                LightVehicle(Some(11234), "Vehicle 22", ZonedDateTime.parse("2012-12-12T12:22:12.121+01:00[Europe/Prague]"), None)
              ),
              Track(
                LightTrack(Some(2), 3, ZonedDateTime.parse("2021-01-05T00:24:12.252+01:00[Europe/Prague]")),
                LightVehicle(Some(3), "Vehicle 2", ZonedDateTime.parse("2012-12-12T12:22:12.121+01:00[Europe/Prague]"), None)
              ),
              Track(
                LightTrack(Some(3), 3, ZonedDateTime.parse("2021-01-01T00:00:00.00+01:00[Europe/Prague]")),
                LightVehicle(Some(3), "Vehicle 2", ZonedDateTime.parse("2012-12-12T12:22:12.121+01:00[Europe/Prague]"), None)
              ),
              Track(
                LightTrack(Some(4), 2, ZonedDateTime.parse("2021-01-05T22:01:57.034+01:00[Europe/Prague]")),
                LightVehicle(Some(2), "CAR", ZonedDateTime.parse("2012-12-12T12:22:12.121+01:00[Europe/Prague]"), None)
              )
            )
          )

        val service = new TrackRoutes(testEnv.trackService)
        for {
          response <- service.routes.orNotFound.run(
            AuthedRequest(
              testEnv.adminUser,
              Request(method = Method.GET, uri = uri"/list")
            )
          )
          body <- response.as[Json]
        } yield assert(response.status)(equalTo(Status.Ok)) && assert(body)(equalTo(expectedResponse.asJson))

      },
      testM("Get list of routes for specific vehicle") {
        val startTime: ZonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(0), ZoneId.of("Europe/Prague"))
        val clock = new TestClock(startTime, Duration.ofSeconds(1))

        val testEnv = TestEnv(
          Store(
            vehicles = List(
              LightVehicle(Some(11234), "Vehicle 22", ZonedDateTime.parse("2012-12-12T12:22:12.121+01:00[Europe/Prague]"), None),
              LightVehicle(Some(3), "Vehicle 2", ZonedDateTime.parse("2012-12-12T12:22:12.121+01:00[Europe/Prague]"), None),
              LightVehicle(Some(2), "CAR", ZonedDateTime.parse("2012-12-12T12:22:12.121+01:00[Europe/Prague]"), None)
            ),
            positions = List(
              Position(Some(1), 11234, 1, 23.4, 66.74316, 161.47482, ZonedDateTime.parse("2021-04-01T16:24:11.252+01:00[Europe/Prague]"), "session1"),
              Position(
                Some(2),
                11234,
                1,
                3.4,
                -31.61897,
                -124.25575,
                ZonedDateTime.parse("2021-04-01T16:20:11.252+01:00[Europe/Prague]"),
                "session1"
              ),
              Position(Some(3), 3, 3, 23.4, 6.84985, -8.17319, ZonedDateTime.parse("2021-01-05T00:24:11.252+01:00[Europe/Prague]"), "session13"),
              Position(Some(4), 3, 3, 23.4, 36.79386, 120.91501, ZonedDateTime.parse("2021-01-05T00:24:00.252+01:00[Europe/Prague]"), "session13"),
              Position(Some(5), 2, 56, 23.4, -23.28416, 64.18032, ZonedDateTime.parse("2021-01-05T22:01:57.034+01:00[Europe/Prague]"), "session345"),
              Position(Some(6), 2, 56, 23.4, 5.70111, 24.03095, ZonedDateTime.parse("2021-01-05T22:02:00.034+01:00[Europe/Prague]"), "session345"),
              Position(Some(7), 11234, 1, 23.4, 5.43438, -179.01164, ZonedDateTime.parse("2021-04-01T16:26:11.252+01:00[Europe/Prague]"), "session1"),
              Position(Some(8), 3, 4, 23.4, 30.63340, 30.63340, ZonedDateTime.parse("2021-01-01T00:00:00.00+01:00[Europe/Prague]"), "session14"),
              Position(Some(9), 2, 56, 23.4, -1.67141, 81.39267, ZonedDateTime.parse("2021-01-05T22:02:01.034+01:00[Europe/Prague]"), "session342"),
              Position(
                Some(10),
                11234,
                1,
                23.4,
                15.83878,
                34.66970,
                ZonedDateTime.parse("2021-04-01T16:30:11.252+01:00[Europe/Prague]"),
                "session11"
              ),
              Position(
                Some(11),
                1,
                2,
                23.4,
                -36.57176,
                -71.78524,
                ZonedDateTime.parse("2021-04-06T06:24:11.252+01:00[Europe/Prague]"),
                "session123"
              ),
              Position(Some(12), 1, 2, 23.4, 19.73304, 59.79881, ZonedDateTime.parse("2021-04-06T06:27:11.252+01:00[Europe/Prague]"), "session123")
            ),
            tracks = List(
              LightTrack(Some(1), 11234, ZonedDateTime.parse("2021-04-01T16:24:11.252+01:00[Europe/Prague]")),
              LightTrack(Some(2), 3, ZonedDateTime.parse("2021-01-05T00:24:12.252+01:00[Europe/Prague]")),
              LightTrack(Some(3), 3, ZonedDateTime.parse("2021-01-01T00:00:00.00+01:00[Europe/Prague]")),
              LightTrack(Some(4), 2, ZonedDateTime.parse("2021-01-05T22:01:57.034+01:00[Europe/Prague]"))
            )
          ),
          clock = clock
        )

        val expectedResponse =
          Page[Track](
            1,
            1,
            Int.MaxValue,
            List(
              Track(
                LightTrack(Some(2), 3, ZonedDateTime.parse("2021-01-05T00:24:12.252+01:00[Europe/Prague]")),
                LightVehicle(Some(3), "Vehicle 2", ZonedDateTime.parse("2012-12-12T12:22:12.121+01:00[Europe/Prague]"), None)
              ),
              Track(
                LightTrack(Some(3), 3, ZonedDateTime.parse("2021-01-01T00:00:00.00+01:00[Europe/Prague]")),
                LightVehicle(Some(3), "Vehicle 2", ZonedDateTime.parse("2012-12-12T12:22:12.121+01:00[Europe/Prague]"), None)
              )
            )
          )
        val service = new TrackRoutes(testEnv.trackService)
        for {
          response <- service.routes.orNotFound.run(
            AuthedRequest(
              testEnv.adminUser,
              Request(method = Method.GET, uri = uri"/list?vehicleId=3")
            )
          )
          body <- response.as[Json]
        } yield assert(response.status)(equalTo(Status.Ok)) && assert(body)(equalTo(expectedResponse.asJson))

      },
      testM("Get list of tracks without permissions - User") {
        val startTime: ZonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(0), ZoneId.of("Europe/Prague"))
        val clock = new TestClock(startTime, Duration.ofSeconds(1))

        val testEnv = TestEnv(
          Store(
            vehicles = List(
              LightVehicle(Some(11234), "Vehicle 22", ZonedDateTime.parse("2012-12-12T12:22:12.121+01:00[Europe/Prague]"), None),
              LightVehicle(Some(3), "Vehicle 2", ZonedDateTime.parse("2012-12-12T12:22:12.121+01:00[Europe/Prague]"), None),
              LightVehicle(Some(2), "CAR", ZonedDateTime.parse("2012-12-12T12:22:12.121+01:00[Europe/Prague]"), None)
            ),
            positions = List(
              Position(Some(1), 11234, 1, 23.4, 66.74316, 161.47482, ZonedDateTime.parse("2021-04-01T16:24:11.252+01:00[Europe/Prague]"), "session1"),
              Position(
                Some(2),
                11234,
                1,
                3.4,
                -31.61897,
                -124.25575,
                ZonedDateTime.parse("2021-04-01T16:20:11.252+01:00[Europe/Prague]"),
                "session1"
              ),
              Position(Some(3), 3, 3, 23.4, 6.84985, -8.17319, ZonedDateTime.parse("2021-01-05T00:24:11.252+01:00[Europe/Prague]"), "session13"),
              Position(Some(4), 3, 3, 23.4, 36.79386, 120.91501, ZonedDateTime.parse("2021-01-05T00:24:00.252+01:00[Europe/Prague]"), "session13"),
              Position(Some(5), 2, 56, 23.4, -23.28416, 64.18032, ZonedDateTime.parse("2021-01-05T22:01:57.034+01:00[Europe/Prague]"), "session345"),
              Position(Some(6), 2, 56, 23.4, 5.70111, 24.03095, ZonedDateTime.parse("2021-01-05T22:02:00.034+01:00[Europe/Prague]"), "session345"),
              Position(Some(7), 11234, 1, 23.4, 5.43438, -179.01164, ZonedDateTime.parse("2021-04-01T16:26:11.252+01:00[Europe/Prague]"), "session1"),
              Position(Some(8), 3, 4, 23.4, 30.63340, 30.63340, ZonedDateTime.parse("2021-01-01T00:00:00.00+01:00[Europe/Prague]"), "session14"),
              Position(Some(9), 2, 56, 23.4, -1.67141, 81.39267, ZonedDateTime.parse("2021-01-05T22:02:01.034+01:00[Europe/Prague]"), "session342"),
              Position(
                Some(10),
                11234,
                1,
                23.4,
                15.83878,
                34.66970,
                ZonedDateTime.parse("2021-04-01T16:30:11.252+01:00[Europe/Prague]"),
                "session11"
              ),
              Position(
                Some(11),
                1,
                2,
                23.4,
                -36.57176,
                -71.78524,
                ZonedDateTime.parse("2021-04-06T06:24:11.252+01:00[Europe/Prague]"),
                "session123"
              ),
              Position(Some(12), 1, 2, 23.4, 19.73304, 59.79881, ZonedDateTime.parse("2021-04-06T06:27:11.252+01:00[Europe/Prague]"), "session123")
            ),
            tracks = List(
              LightTrack(Some(1), 11234, ZonedDateTime.parse("2021-04-01T16:24:11.252+01:00[Europe/Prague]")),
              LightTrack(Some(2), 3, ZonedDateTime.parse("2021-01-05T00:24:12.252+01:00[Europe/Prague]")),
              LightTrack(Some(3), 3, ZonedDateTime.parse("2021-01-01T00:00:00.00+01:00[Europe/Prague]")),
              LightTrack(Some(4), 2, ZonedDateTime.parse("2021-01-05T22:01:57.034+01:00[Europe/Prague]"))
            )
          ),
          clock = clock
        )

        val service = new TrackRoutes(testEnv.trackService)
        for {
          response <- service.routes.orNotFound.run(
            AuthedRequest(
              testEnv.normalUser,
              Request(method = Method.GET, uri = uri"/list?vehicleId=3")
            )
          )
        } yield assert(response.status)(equalTo(Status.Forbidden))

      },
      testM("Get specific track") {
        val startTime: ZonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(0), ZoneId.of("Europe/Prague"))
        val clock = new TestClock(startTime, Duration.ofSeconds(1))

        val testEnv = TestEnv(
          Store(
            vehicles = List(
              LightVehicle(Some(11234), "Vehicle 22", ZonedDateTime.parse("2012-12-12T12:22:12.121+01:00[Europe/Prague]"), None),
              LightVehicle(Some(3), "Vehicle 2", ZonedDateTime.parse("2012-12-12T12:22:12.121+01:00[Europe/Prague]"), None),
              LightVehicle(Some(2), "CAR", ZonedDateTime.parse("2012-12-12T12:22:12.121+01:00[Europe/Prague]"), None)
            ),
            positions = List(
              Position(Some(1), 11234, 1, 23.4, 66.74316, 161.47482, ZonedDateTime.parse("2021-04-01T16:24:11.252+01:00[Europe/Prague]"), "session1"),
              Position(
                Some(2),
                11234,
                1,
                3.4,
                -31.61897,
                -124.25575,
                ZonedDateTime.parse("2021-04-01T16:20:11.252+01:00[Europe/Prague]"),
                "session1"
              ),
              Position(Some(3), 3, 3, 23.4, 6.84985, -8.17319, ZonedDateTime.parse("2021-01-05T00:24:11.252+01:00[Europe/Prague]"), "session13"),
              Position(Some(4), 3, 3, 23.4, 36.79386, 120.91501, ZonedDateTime.parse("2021-01-05T00:24:00.252+01:00[Europe/Prague]"), "session13"),
              Position(Some(5), 2, 56, 23.4, -23.28416, 64.18032, ZonedDateTime.parse("2021-01-05T22:01:57.034+01:00[Europe/Prague]"), "session345"),
              Position(Some(6), 2, 56, 23.4, 5.70111, 24.03095, ZonedDateTime.parse("2021-01-05T22:02:00.034+01:00[Europe/Prague]"), "session345"),
              Position(Some(7), 11234, 1, 23.4, 5.43438, -179.01164, ZonedDateTime.parse("2021-04-01T16:26:11.252+01:00[Europe/Prague]"), "session1"),
              Position(Some(8), 3, 4, 23.4, 30.63340, 30.63340, ZonedDateTime.parse("2021-01-01T00:00:00.00+01:00[Europe/Prague]"), "session14"),
              Position(Some(9), 2, 56, 23.4, -1.67141, 81.39267, ZonedDateTime.parse("2021-01-05T22:02:01.034+01:00[Europe/Prague]"), "session342"),
              Position(
                Some(10),
                11234,
                1,
                23.4,
                15.83878,
                34.66970,
                ZonedDateTime.parse("2021-04-01T16:30:11.252+01:00[Europe/Prague]"),
                "session11"
              ),
              Position(
                Some(11),
                1,
                2,
                23.4,
                -36.57176,
                -71.78524,
                ZonedDateTime.parse("2021-04-06T06:24:11.252+01:00[Europe/Prague]"),
                "session123"
              ),
              Position(Some(12), 1, 2, 23.4, 19.73304, 59.79881, ZonedDateTime.parse("2021-04-06T06:27:11.252+01:00[Europe/Prague]"), "session123")
            ),
            tracks = List(
              LightTrack(Some(1), 11234, ZonedDateTime.parse("2021-04-01T16:24:11.252+01:00[Europe/Prague]")),
              LightTrack(Some(2), 3, ZonedDateTime.parse("2021-01-05T00:24:12.252+01:00[Europe/Prague]")),
              LightTrack(Some(3), 3, ZonedDateTime.parse("2021-01-01T00:00:00.00+01:00[Europe/Prague]")),
              LightTrack(Some(4), 2, ZonedDateTime.parse("2021-01-05T22:01:57.034+01:00[Europe/Prague]"))
            )
          ),
          clock = clock
        )

        val expectedResponse =
          Track(
            LightTrack(Some(4), 2, ZonedDateTime.parse("2021-01-05T22:01:57.034+01:00[Europe/Prague]")),
            LightVehicle(Some(2), "CAR", ZonedDateTime.parse("2012-12-12T12:22:12.121+01:00[Europe/Prague]"), None)
          )

        val service = new TrackRoutes(testEnv.trackService)
        for {
          response <- service.routes.orNotFound.run(
            AuthedRequest(
              testEnv.adminUser,
              Request(method = Method.GET, uri = uri"?id=4")
            )
          )
          body <- response.as[Json]
        } yield assert(response.status)(equalTo(Status.Ok)) && assert(body)(equalTo(expectedResponse.asJson))

      },
      testM("Get not existed track") {
        val startTime: ZonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(0), ZoneId.of("Europe/Prague"))
        val clock = new TestClock(startTime, Duration.ofSeconds(1))

        val testEnv = TestEnv(
          Store(
            vehicles = List(
              LightVehicle(Some(11234), "Vehicle 22", ZonedDateTime.parse("2012-12-12T12:22:12.121+01:00[Europe/Prague]"), None),
              LightVehicle(Some(3), "Vehicle 2", ZonedDateTime.parse("2012-12-12T12:22:12.121+01:00[Europe/Prague]"), None),
              LightVehicle(Some(2), "CAR", ZonedDateTime.parse("2012-12-12T12:22:12.121+01:00[Europe/Prague]"), None)
            ),
            positions = List(
              Position(Some(1), 11234, 1, 23.4, 66.74316, 161.47482, ZonedDateTime.parse("2021-04-01T16:24:11.252+01:00[Europe/Prague]"), "session1"),
              Position(
                Some(2),
                11234,
                1,
                3.4,
                -31.61897,
                -124.25575,
                ZonedDateTime.parse("2021-04-01T16:20:11.252+01:00[Europe/Prague]"),
                "session1"
              ),
              Position(Some(3), 3, 3, 23.4, 6.84985, -8.17319, ZonedDateTime.parse("2021-01-05T00:24:11.252+01:00[Europe/Prague]"), "session13"),
              Position(Some(4), 3, 3, 23.4, 36.79386, 120.91501, ZonedDateTime.parse("2021-01-05T00:24:00.252+01:00[Europe/Prague]"), "session13"),
              Position(Some(5), 2, 56, 23.4, -23.28416, 64.18032, ZonedDateTime.parse("2021-01-05T22:01:57.034+01:00[Europe/Prague]"), "session345"),
              Position(Some(6), 2, 56, 23.4, 5.70111, 24.03095, ZonedDateTime.parse("2021-01-05T22:02:00.034+01:00[Europe/Prague]"), "session345"),
              Position(Some(7), 11234, 1, 23.4, 5.43438, -179.01164, ZonedDateTime.parse("2021-04-01T16:26:11.252+01:00[Europe/Prague]"), "session1"),
              Position(Some(8), 3, 4, 23.4, 30.63340, 30.63340, ZonedDateTime.parse("2021-01-01T00:00:00.00+01:00[Europe/Prague]"), "session14"),
              Position(Some(9), 2, 56, 23.4, -1.67141, 81.39267, ZonedDateTime.parse("2021-01-05T22:02:01.034+01:00[Europe/Prague]"), "session342"),
              Position(
                Some(10),
                11234,
                1,
                23.4,
                15.83878,
                34.66970,
                ZonedDateTime.parse("2021-04-01T16:30:11.252+01:00[Europe/Prague]"),
                "session11"
              ),
              Position(
                Some(11),
                1,
                2,
                23.4,
                -36.57176,
                -71.78524,
                ZonedDateTime.parse("2021-04-06T06:24:11.252+01:00[Europe/Prague]"),
                "session123"
              ),
              Position(Some(12), 1, 2, 23.4, 19.73304, 59.79881, ZonedDateTime.parse("2021-04-06T06:27:11.252+01:00[Europe/Prague]"), "session123")
            ),
            tracks = List(
              LightTrack(Some(1), 11234, ZonedDateTime.parse("2021-04-01T16:24:11.252+01:00[Europe/Prague]")),
              LightTrack(Some(2), 3, ZonedDateTime.parse("2021-01-05T00:24:12.252+01:00[Europe/Prague]")),
              LightTrack(Some(3), 3, ZonedDateTime.parse("2021-01-01T00:00:00.00+01:00[Europe/Prague]")),
              LightTrack(Some(4), 2, ZonedDateTime.parse("2021-01-05T22:01:57.034+01:00[Europe/Prague]"))
            )
          ),
          clock = clock
        )

        val service = new TrackRoutes(testEnv.trackService)
        for {
          response <- service.routes.orNotFound.run(
            AuthedRequest(
              testEnv.adminUser,
              Request(method = Method.GET, uri = uri"?id=444")
            )
          )
        } yield assert(response.status)(equalTo(Status.NotFound))

      },
      testM("Get list of routes for specific vehicle") {
        val startTime: ZonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(0), ZoneId.of("Europe/Prague"))
        val clock = new TestClock(startTime, Duration.ofSeconds(1))

        val testEnv = TestEnv(
          Store(
            vehicles = List(
              LightVehicle(Some(11234), "Vehicle 22", ZonedDateTime.parse("2012-12-12T12:22:12.121+01:00[Europe/Prague]"), None),
              LightVehicle(Some(3), "Vehicle 2", ZonedDateTime.parse("2012-12-12T12:22:12.121+01:00[Europe/Prague]"), None),
              LightVehicle(Some(2), "CAR", ZonedDateTime.parse("2012-12-12T12:22:12.121+01:00[Europe/Prague]"), None)
            ),
            positions = List(
              Position(Some(1), 11234, 1, 23.4, 66.74316, 161.47482, ZonedDateTime.parse("2021-04-01T16:24:11.252+01:00[Europe/Prague]"), "session1"),
              Position(
                Some(2),
                11234,
                1,
                3.4,
                -31.61897,
                -124.25575,
                ZonedDateTime.parse("2021-04-01T16:20:11.252+01:00[Europe/Prague]"),
                "session1"
              ),
              Position(Some(3), 3, 3, 23.4, 6.84985, -8.17319, ZonedDateTime.parse("2021-01-05T00:24:11.252+01:00[Europe/Prague]"), "session13"),
              Position(Some(4), 3, 3, 23.4, 36.79386, 120.91501, ZonedDateTime.parse("2021-01-05T00:24:00.252+01:00[Europe/Prague]"), "session13"),
              Position(Some(5), 2, 56, 23.4, -23.28416, 64.18032, ZonedDateTime.parse("2021-01-05T22:01:57.034+01:00[Europe/Prague]"), "session345"),
              Position(Some(6), 2, 56, 23.4, 5.70111, 24.03095, ZonedDateTime.parse("2021-01-05T22:02:00.034+01:00[Europe/Prague]"), "session345"),
              Position(Some(7), 11234, 1, 23.4, 5.43438, -179.01164, ZonedDateTime.parse("2021-04-01T16:26:11.252+01:00[Europe/Prague]"), "session1"),
              Position(Some(8), 3, 4, 23.4, 30.63340, 30.63340, ZonedDateTime.parse("2021-01-01T00:00:00.00+01:00[Europe/Prague]"), "session14"),
              Position(Some(9), 2, 56, 23.4, -1.67141, 81.39267, ZonedDateTime.parse("2021-01-05T22:02:01.034+01:00[Europe/Prague]"), "session342"),
              Position(
                Some(10),
                11234,
                1,
                23.4,
                15.83878,
                34.66970,
                ZonedDateTime.parse("2021-04-01T16:30:11.252+01:00[Europe/Prague]"),
                "session11"
              ),
              Position(
                Some(11),
                1,
                2,
                23.4,
                -36.57176,
                -71.78524,
                ZonedDateTime.parse("2021-04-06T06:24:11.252+01:00[Europe/Prague]"),
                "session123"
              ),
              Position(Some(12), 1, 2, 23.4, 19.73304, 59.79881, ZonedDateTime.parse("2021-04-06T06:27:11.252+01:00[Europe/Prague]"), "session123")
            ),
            tracks = List(
              LightTrack(Some(1), 11234, ZonedDateTime.parse("2021-04-01T16:24:11.252+01:00[Europe/Prague]")),
              LightTrack(Some(2), 3, ZonedDateTime.parse("2021-01-05T00:24:12.252+01:00[Europe/Prague]")),
              LightTrack(Some(3), 3, ZonedDateTime.parse("2021-01-01T00:00:00.00+01:00[Europe/Prague]")),
              LightTrack(Some(4), 2, ZonedDateTime.parse("2021-01-05T22:01:57.034+01:00[Europe/Prague]"))
            )
          ),
          clock = clock
        )

        val expectedResponse = List(
          Position(Some(5), 2, 56, 23.4, -23.28416, 64.18032, ZonedDateTime.parse("2021-01-05T22:01:57.034+01:00[Europe/Prague]"), "session345"),
          Position(Some(6), 2, 56, 23.4, 5.70111, 24.03095, ZonedDateTime.parse("2021-01-05T22:02:00.034+01:00[Europe/Prague]"), "session345"),
          Position(Some(9), 2, 56, 23.4, -1.67141, 81.39267, ZonedDateTime.parse("2021-01-05T22:02:01.034+01:00[Europe/Prague]"), "session342")
        )
        val service = new TrackRoutes(testEnv.trackService)
        for {
          response <- service.routes.orNotFound.run(
            AuthedRequest(
              testEnv.adminUser,
              Request(method = Method.GET, uri = uri"/positions?id=56")
            )
          )
          body <- response.as[Json]
        } yield assert(response.status)(equalTo(Status.Ok)) && assert(body)(equalTo(expectedResponse.asJson))

      },
      testM("Get list of routes for specific not existing vehicle") {
        val startTime: ZonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(0), ZoneId.of("Europe/Prague"))
        val clock = new TestClock(startTime, Duration.ofSeconds(1))

        val testEnv = TestEnv(
          Store(
            vehicles = List(
              LightVehicle(Some(11234), "Vehicle 22", ZonedDateTime.parse("2012-12-12T12:22:12.121+01:00[Europe/Prague]"), None),
              LightVehicle(Some(3), "Vehicle 2", ZonedDateTime.parse("2012-12-12T12:22:12.121+01:00[Europe/Prague]"), None),
              LightVehicle(Some(2), "CAR", ZonedDateTime.parse("2012-12-12T12:22:12.121+01:00[Europe/Prague]"), None)
            ),
            positions = List(
              Position(Some(1), 11234, 1, 23.4, 66.74316, 161.47482, ZonedDateTime.parse("2021-04-01T16:24:11.252+01:00[Europe/Prague]"), "session1"),
              Position(
                Some(2),
                11234,
                1,
                3.4,
                -31.61897,
                -124.25575,
                ZonedDateTime.parse("2021-04-01T16:20:11.252+01:00[Europe/Prague]"),
                "session1"
              ),
              Position(Some(3), 3, 3, 23.4, 6.84985, -8.17319, ZonedDateTime.parse("2021-01-05T00:24:11.252+01:00[Europe/Prague]"), "session13"),
              Position(Some(4), 3, 3, 23.4, 36.79386, 120.91501, ZonedDateTime.parse("2021-01-05T00:24:00.252+01:00[Europe/Prague]"), "session13"),
              Position(Some(5), 2, 56, 23.4, -23.28416, 64.18032, ZonedDateTime.parse("2021-01-05T22:01:57.034+01:00[Europe/Prague]"), "session345"),
              Position(Some(6), 2, 56, 23.4, 5.70111, 24.03095, ZonedDateTime.parse("2021-01-05T22:02:00.034+01:00[Europe/Prague]"), "session345"),
              Position(Some(7), 11234, 1, 23.4, 5.43438, -179.01164, ZonedDateTime.parse("2021-04-01T16:26:11.252+01:00[Europe/Prague]"), "session1"),
              Position(Some(8), 3, 4, 23.4, 30.63340, 30.63340, ZonedDateTime.parse("2021-01-01T00:00:00.00+01:00[Europe/Prague]"), "session14"),
              Position(Some(9), 2, 56, 23.4, -1.67141, 81.39267, ZonedDateTime.parse("2021-01-05T22:02:01.034+01:00[Europe/Prague]"), "session342"),
              Position(
                Some(10),
                11234,
                1,
                23.4,
                15.83878,
                34.66970,
                ZonedDateTime.parse("2021-04-01T16:30:11.252+01:00[Europe/Prague]"),
                "session11"
              ),
              Position(
                Some(11),
                1,
                2,
                23.4,
                -36.57176,
                -71.78524,
                ZonedDateTime.parse("2021-04-06T06:24:11.252+01:00[Europe/Prague]"),
                "session123"
              ),
              Position(Some(12), 1, 2, 23.4, 19.73304, 59.79881, ZonedDateTime.parse("2021-04-06T06:27:11.252+01:00[Europe/Prague]"), "session123")
            ),
            tracks = List(
              LightTrack(Some(1), 11234, ZonedDateTime.parse("2021-04-01T16:24:11.252+01:00[Europe/Prague]")),
              LightTrack(Some(2), 3, ZonedDateTime.parse("2021-01-05T00:24:12.252+01:00[Europe/Prague]")),
              LightTrack(Some(3), 3, ZonedDateTime.parse("2021-01-01T00:00:00.00+01:00[Europe/Prague]")),
              LightTrack(Some(4), 2, ZonedDateTime.parse("2021-01-05T22:01:57.034+01:00[Europe/Prague]"))
            )
          ),
          clock = clock
        )

        val service = new TrackRoutes(testEnv.trackService)
        for {
          response <- service.routes.orNotFound.run(
            AuthedRequest(
              testEnv.adminUser,
              Request(method = Method.GET, uri = uri"/positions?id=5611")
            )
          )
        } yield assert(response.status)(equalTo(Status.NotFound))

      }
    )
  }
}

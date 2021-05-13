package tracker.api

import io.circe.Json
import io.circe.syntax.EncoderOps
import org.http4s.{AuthedRequest, Method, Request, Status}
import org.http4s.circe.CirceEntityCodec.{circeEntityDecoder, circeEntityEncoder}
import org.http4s.implicits.{http4sKleisliResponseSyntaxOptionT, http4sLiteralsSyntax}
import tracker.{Fleet, LightFleet, Page}
import tracker.mocked.{Store, TestClock}
import tracker.module.routes.FleetRoutes
import tracker.utils.TestEnv
import zio.interop.catz._
import zio.test._
import zio.test.Assertion.equalTo

import java.time.{Duration, Instant, ZoneId, ZonedDateTime}

object FleetAPITest extends DefaultRunnableSpec {
  override def spec: Spec[Any, TestFailure[Throwable], TestSuccess] = {
    suite("Fleet routes suite")(
      testM("Insert new fleet") {
        val startTime: ZonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(0), ZoneId.of("Europe/Prague"))
        val clock = new TestClock(startTime, Duration.ofSeconds(1))

        val testEnv = TestEnv(Store(), clock = clock)

        val expectedVehicle = Fleet(LightFleet(Some(1), "Fleet 1"), List.empty)

        val service = new FleetRoutes(testEnv.fleetService)

        for {
          response <- service.routes.orNotFound.run(
            AuthedRequest(
              testEnv.adminUser,
              Request(method = Method.POST, uri = uri"/new").withEntity {
                Json.obj(
                  ("name", Json.fromString("Fleet 1"))
                )
              }
            )
          )
          body <- response.as[Json]
        } yield assert(body)(equalTo(expectedVehicle.asJson))
      },
      testM("Insert new fleet without permissions - User") {
        val startTime: ZonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(0), ZoneId.of("Europe/Prague"))
        val clock = new TestClock(startTime, Duration.ofSeconds(1))

        val testEnv = TestEnv(Store(), clock = clock)

        val service = new FleetRoutes(testEnv.fleetService)

        for {
          response <- service.routes.orNotFound.run(
            AuthedRequest(
              testEnv.normalUser,
              Request(method = Method.POST, uri = uri"/new").withEntity {
                Json.obj(
                  ("name", Json.fromString("Fleet 1"))
                )
              }
            )
          )
        } yield assert(response.status)(equalTo(Status.Forbidden))
      },
      testM("Get list of all fleets") {
        val startTime: ZonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(0), ZoneId.of("Europe/Prague"))
        val clock = new TestClock(startTime, Duration.ofSeconds(1))

        val testEnv = TestEnv(Store(fleets = List(LightFleet(Some(1), "Fleet 1"), LightFleet(Some(2), "Fleet 2"))), clock = clock)

        val expectedVehicle =
          Page[Fleet](1, 1, Int.MaxValue, List(Fleet(LightFleet(Some(1), "Fleet 1"), List.empty), Fleet(LightFleet(Some(2), "Fleet 2"), List.empty)))

        val service = new FleetRoutes(testEnv.fleetService)

        for {
          response <- service.routes.orNotFound.run(
            AuthedRequest(
              testEnv.adminUser,
              Request(method = Method.GET, uri = uri"/list")
            )
          )
          body <- response.as[Json]
        } yield assert(response.status)(equalTo(Status.Ok)) && assert(body)(equalTo(expectedVehicle.asJson))
      },
      testM("Get list of all fleets without permissions - User") {
        val startTime: ZonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(0), ZoneId.of("Europe/Prague"))
        val clock = new TestClock(startTime, Duration.ofSeconds(1))

        val testEnv = TestEnv(Store(fleets = List(LightFleet(Some(1), "Fleet 1"), LightFleet(Some(2), "Fleet 2"))), clock = clock)

        val service = new FleetRoutes(testEnv.fleetService)

        for {
          response <- service.routes.orNotFound.run(
            AuthedRequest(
              testEnv.normalUser,
              Request(method = Method.GET, uri = uri"/list")
            )
          )
        } yield assert(response.status)(equalTo(Status.Forbidden))
      },
      testM("Get fleet") {
        val startTime: ZonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(0), ZoneId.of("Europe/Prague"))
        val clock = new TestClock(startTime, Duration.ofSeconds(1))

        val testEnv = TestEnv(Store(fleets = List(LightFleet(Some(1), "Fleet 1"), LightFleet(Some(2), "Fleet 2"))), clock = clock)

        val expectedVehicle = Fleet(LightFleet(Some(2), "Fleet 2"), List.empty)
        val service = new FleetRoutes(testEnv.fleetService)

        for {
          response <- service.routes.orNotFound.run(
            AuthedRequest(
              testEnv.adminUser,
              Request(method = Method.GET, uri = uri"/?id=2")
            )
          )
          body <- response.as[Json]
        } yield assert(response.status)(equalTo(Status.Ok)) && assert(body)(equalTo(expectedVehicle.asJson))
      },
      testM("Get fleet without permissions - User") {
        val startTime: ZonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(0), ZoneId.of("Europe/Prague"))
        val clock = new TestClock(startTime, Duration.ofSeconds(1))

        val testEnv = TestEnv(Store(fleets = List(LightFleet(Some(1), "Fleet 1"), LightFleet(Some(2), "Fleet 2"))), clock = clock)

        val service = new FleetRoutes(testEnv.fleetService)

        for {
          response <- service.routes.orNotFound.run(
            AuthedRequest(
              testEnv.normalUser,
              Request(method = Method.GET, uri = uri"/?id=1")
            )
          )
        } yield assert(response.status)(equalTo(Status.Forbidden))
      }
    )
  }
}

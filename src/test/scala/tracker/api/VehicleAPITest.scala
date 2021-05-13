package tracker.api

import cats.effect.Blocker
import io.circe.Json
import io.circe.syntax.EncoderOps
import org.http4s.{AuthedRequest, Method, Request, Status}
import org.http4s.circe.{jsonDecoder, jsonEncoder}
import org.http4s.implicits.{http4sKleisliResponseSyntaxOptionT, http4sLiteralsSyntax}
import tracker._
import tracker.config.VolumesConfig
import tracker.mocked.{Store, TestClock}
import tracker.module.routes.VehicleRoutes
import tracker.service.PositionService
import tracker.utils.{CaffeineAtomicCache, GPXFileGeneratorBuilder, TestEnv}
import zio.test._
import zio.test.Assertion.equalTo
import zio.Task
import zio.interop.catz._

import java.time.{Duration, Instant, ZoneId, ZonedDateTime}

object VehicleAPITest extends DefaultRunnableSpec {

  override def spec: Spec[Any, TestFailure[Throwable], TestSuccess] = {
    suite("Vehicle routes suite")(
      testM("Add new vehicle") {
        val startTime: ZonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(0), ZoneId.of("Europe/Prague"))
        val clock = new TestClock(startTime, Duration.ofSeconds(1))
        val expectedVehicle =
          Vehicle(LightVehicle(Some(1), "Vehicle 1", clock.now(), None), List(LightFleet(Some(1), "Fleet 1"), LightFleet(Some(4), "Fleet 2")))
        val testEnv = TestEnv(Store(fleets = List(LightFleet(Some(1), "Fleet 1"), LightFleet(Some(4), "Fleet 2"))), clock = clock)
        Blocker[Task].use {
          blocker =>
            for {
              caffeine <- CaffeineAtomicCache.make[Long, Position](testEnv.loggerFactory)
              gpx = new GPXFileGeneratorBuilder(VolumesConfig("", ""), blocker)
              positionService = PositionService(testEnv.positionDao, testEnv.loggerFactory, caffeine, gpx)
              service = new VehicleRoutes(testEnv.vehicleService, positionService, testEnv.trackService)
              response <- service.routes.orNotFound.run(
                AuthedRequest(
                  testEnv.adminUser,
                  Request(method = Method.POST, uri = uri"/new").withEntity {
                    Json.obj(
                      ("name", Json.fromString("Vehicle 1")),
                      ("fleets", List(1, 4).asJson)
                    )
                  }
                )
              )
              body <- response.as[Json]
            } yield assert(body)(equalTo(expectedVehicle.asJson))
        }
      },
      testM("Add new vehicle without permissions - Reader") {
        val testEnv = TestEnv()
        Blocker[Task].use {
          blocker =>
            for {
              caffeine <- CaffeineAtomicCache.make[Long, Position](testEnv.loggerFactory)
              gpx = new GPXFileGeneratorBuilder(VolumesConfig("", ""), blocker)
              positionService = PositionService(testEnv.positionDao, testEnv.loggerFactory, caffeine, gpx)
              service = new VehicleRoutes(testEnv.vehicleService, positionService, testEnv.trackService)
              response <- service.routes.orNotFound.run(
                AuthedRequest(
                  testEnv.readerUser,
                  Request(method = Method.POST, uri = uri"/new").withEntity {
                    Json.obj(
                      ("name", Json.fromString("Vehicle 1")),
                      ("fleets", List(1, 4).asJson)
                    )
                  }
                )
              )
            } yield assert(response.status)(equalTo(Status.Forbidden))
        }
      },
      testM("Get list of vehicles") {
        val startTime: ZonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(0), ZoneId.of("Europe/Prague"))
        val clock = new TestClock(startTime, Duration.ofSeconds(1))
        val vehicles = List(
          LightVehicle(Some(1), "Vehicle 1", clock.now(), None),
          LightVehicle(Some(2), "Vehicle 2", clock.now(), None),
          LightVehicle(Some(3), "Vehicle 3", clock.now(), None)
        )
        val expectedVehicles = List(
          Vehicle(LightVehicle(Some(1), "Vehicle 1", clock.now(), None), List()),
          Vehicle(LightVehicle(Some(2), "Vehicle 2", clock.now(), None), List()),
          Vehicle(LightVehicle(Some(3), "Vehicle 3", clock.now(), None), List())
        )
        val testEnv = TestEnv(Store(vehicles = vehicles))
        Blocker[Task].use {
          blocker =>
            for {
              caffeine <- CaffeineAtomicCache.make[Long, Position](testEnv.loggerFactory)
              gpx = new GPXFileGeneratorBuilder(VolumesConfig("", ""), blocker)
              positionService = PositionService(testEnv.positionDao, testEnv.loggerFactory, caffeine, gpx)
              service = new VehicleRoutes(testEnv.vehicleService, positionService, testEnv.trackService)
              response <- service.routes.orNotFound.run(
                AuthedRequest(
                  testEnv.readerUser,
                  Request(method = Method.GET, uri = uri"/list")
                )
              )
              body <- response.as[Json]
            } yield assert(body)(equalTo(Page[Vehicle](1, 1, Int.MaxValue, expectedVehicles).asJson))
        }
      },
      testM("Get list of vehicles without permissions - User") {
        val startTime: ZonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(0), ZoneId.of("Europe/Prague"))
        val clock = new TestClock(startTime, Duration.ofSeconds(1))
        val testEnv = TestEnv(
          Store(vehicles =
            List(
              LightVehicle(Some(1), "Vehicle 1", clock.now(), None),
              LightVehicle(Some(2), "Vehicle 2", clock.now(), None),
              LightVehicle(Some(3), "Vehicle 3", clock.now(), None)
            )
          )
        )
        Blocker[Task].use {
          blocker =>
            for {
              caffeine <- CaffeineAtomicCache.make[Long, Position](testEnv.loggerFactory)
              gpx = new GPXFileGeneratorBuilder(VolumesConfig("", ""), blocker)
              positionService = PositionService(testEnv.positionDao, testEnv.loggerFactory, caffeine, gpx)
              service = new VehicleRoutes(testEnv.vehicleService, positionService, testEnv.trackService)
              response <- service.routes.orNotFound.run(
                AuthedRequest(
                  testEnv.normalUser,
                  Request(method = Method.GET, uri = uri"/list")
                )
              )
            } yield assert(response.status)(equalTo(Status.Forbidden))
        }
      },
      testM("Update vehicle") {
        val startTime: ZonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(0), ZoneId.of("Europe/Prague"))
        val clock = new TestClock(startTime, Duration.ofSeconds(1))
        val testEnv =
          TestEnv(
            Store(
              vehicles = List(LightVehicle(Some(1), "Vehicle 2", clock.now(), None)),
              fleets = List(LightFleet(Some(1), "Fleet 1"), LightFleet(Some(2), "")),
              vehicleFleets = List(VehicleFleet(1, 1, 1))
            )
          )
        val modifiedVehicle =
          Vehicle(LightVehicle(Some(1), "Vehicle 22", clock.now(), None), List(LightFleet(Some(2), "")))
        Blocker[Task].use {
          blocker =>
            for {
              caffeine <- CaffeineAtomicCache.make[Long, Position](testEnv.loggerFactory)
              gpx = new GPXFileGeneratorBuilder(VolumesConfig("", ""), blocker)
              positionService = PositionService(testEnv.positionDao, testEnv.loggerFactory, caffeine, gpx)
              service = new VehicleRoutes(testEnv.vehicleService, positionService, testEnv.trackService)
              response <- service.routes.orNotFound.run(
                AuthedRequest(
                  testEnv.editorUser,
                  Request(method = Method.PUT, uri = uri"").withEntity {
                    Json.obj(("data", modifiedVehicle.asJson))
                  }
                )
              )
              body <- response.as[Json]
            } yield assert(response.status)(equalTo(Status.Ok)) && assert(body)(equalTo(modifiedVehicle.asJson))
        }
      },
      testM("Update vehicle without permissions - Reader") {
        val startTime: ZonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(0), ZoneId.of("Europe/Prague"))
        val clock = new TestClock(startTime, Duration.ofSeconds(1))
        val testEnv =
          TestEnv(
            Store(
              vehicles = List(LightVehicle(Some(1), "Vehicle 2", clock.now(), None)),
              fleets = List(LightFleet(Some(1), "Fleet 1"), LightFleet(Some(2), "")),
              vehicleFleets = List(VehicleFleet(1, 1, 1))
            )
          )
        val modifiedVehicle =
          Vehicle(LightVehicle(Some(1), "Vehicle 22", clock.now(), None), List(LightFleet(Some(2), "")))
        Blocker[Task].use {
          blocker =>
            for {
              caffeine <- CaffeineAtomicCache.make[Long, Position](testEnv.loggerFactory)
              gpx = new GPXFileGeneratorBuilder(VolumesConfig("", ""), blocker)
              positionService = PositionService(testEnv.positionDao, testEnv.loggerFactory, caffeine, gpx)
              service = new VehicleRoutes(testEnv.vehicleService, positionService, testEnv.trackService)
              response <- service.routes.orNotFound.run(
                AuthedRequest(
                  testEnv.readerUser,
                  Request(method = Method.PUT, uri = uri"").withEntity {
                    Json.obj(("data", modifiedVehicle.asJson))
                  }
                )
              )
            } yield assert(response.status)(equalTo(Status.Forbidden))
        }
      },
      testM("Find last position for not existing vehicle") {
        val startTime: ZonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(0), ZoneId.of("Europe/Prague"))
        val clock = new TestClock(startTime, Duration.ofSeconds(1))
        val testEnv =
          TestEnv(
            Store(
              vehicles = List(LightVehicle(Some(1), "Vehicle 22", clock.now(), None)),
              fleets = List(LightFleet(Some(1), "Fleet 1"), LightFleet(Some(2), "")),
              vehicleFleets = List(VehicleFleet(1, 1, 1)),
              positions = List(
                Position(
                  Some(1),
                  2,
                  1,
                  52.51,
                  50.087823,
                  14.430026,
                  ZonedDateTime.of(2021, 2, 25, 3, 0, 0, 0, ZoneId.of("Europe/Prague")),
                  "sessionId234"
                ),
                Position(
                  Some(2),
                  2,
                  1,
                  52.51,
                  50.087823,
                  14.430026,
                  ZonedDateTime.of(2021, 2, 25, 1, 0, 0, 0, ZoneId.of("Europe/Prague")),
                  "sessionId234"
                ),
                Position(
                  Some(3),
                  2,
                  1,
                  52.51,
                  50.087823,
                  14.430026,
                  ZonedDateTime.of(2021, 2, 25, 2, 0, 0, 0, ZoneId.of("Europe/Prague")),
                  "sessionId234"
                )
              )
            )
          )
        Blocker[Task].use {
          blocker =>
            for {
              caffeine <- CaffeineAtomicCache.make[Long, Position](testEnv.loggerFactory)
              gpx = new GPXFileGeneratorBuilder(VolumesConfig("", ""), blocker)
              positionService = PositionService(testEnv.positionDao, testEnv.loggerFactory, caffeine, gpx)
              service = new VehicleRoutes(testEnv.vehicleService, positionService, testEnv.trackService)
              response <- service.routes.orNotFound.run(
                AuthedRequest(
                  testEnv.editorUser,
                  Request(method = Method.GET, uri = uri"/position?id=324")
                )
              )
            } yield assert(response.status)(equalTo(Status.NotFound))
        }
      },
      testM("Find last position") {
        val startTime: ZonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(0), ZoneId.of("Europe/Prague"))
        val clock = new TestClock(startTime, Duration.ofSeconds(1))
        val testEnv =
          TestEnv(
            Store(
              vehicles = List(LightVehicle(Some(1), "Vehicle 22", clock.now(), None)),
              fleets = List(LightFleet(Some(1), "Fleet 1"), LightFleet(Some(2), "")),
              vehicleFleets = List(VehicleFleet(1, 1, 1)),
              positions = List(
                Position(
                  Some(1),
                  2,
                  1,
                  52.51,
                  50.087823,
                  14.430026,
                  ZonedDateTime.of(2021, 2, 25, 3, 0, 0, 0, ZoneId.of("Europe/Prague")),
                  "sessionId234"
                ),
                Position(
                  Some(2),
                  1,
                  1,
                  52.51,
                  50.087823,
                  14.430026,
                  ZonedDateTime.of(2021, 2, 25, 2, 0, 0, 0, ZoneId.of("Europe/Prague")),
                  "sessionId234"
                ),
                Position(
                  Some(3),
                  1,
                  1,
                  52.51,
                  50.087823,
                  14.430026,
                  ZonedDateTime.of(2021, 2, 25, 1, 0, 0, 0, ZoneId.of("Europe/Prague")),
                  "sessionId234"
                )
              )
            )
          )
        val expectedLastPosition = Position(
          Some(2),
          1,
          1,
          52.51,
          50.087823,
          14.430026,
          ZonedDateTime.of(2021, 2, 25, 2, 0, 0, 0, ZoneId.of("Europe/Prague")),
          "sessionId234"
        )
        Blocker[Task].use {
          blocker =>
            for {
              caffeine <- CaffeineAtomicCache.make[Long, Position](testEnv.loggerFactory)
              gpx = new GPXFileGeneratorBuilder(VolumesConfig("", ""), blocker)
              positionService = PositionService(testEnv.positionDao, testEnv.loggerFactory, caffeine, gpx)
              service = new VehicleRoutes(testEnv.vehicleService, positionService, testEnv.trackService)
              response <- service.routes.orNotFound.run(
                AuthedRequest(
                  testEnv.editorUser,
                  Request(method = Method.GET, uri = uri"/position?id=1")
                )
              )
              body <- response.as[Json]
            } yield assert(response.status)(equalTo(Status.Ok)) && assert(body)(
              equalTo(expectedLastPosition.asJson)
            )
        }
      },
      testM("Find last position without permissions - User") {
        val testEnv = TestEnv(Store())
        Blocker[Task].use {
          blocker =>
            for {
              caffeine <- CaffeineAtomicCache.make[Long, Position](testEnv.loggerFactory)
              gpx = new GPXFileGeneratorBuilder(VolumesConfig("", ""), blocker)
              positionService = PositionService(testEnv.positionDao, testEnv.loggerFactory, caffeine, gpx)
              service = new VehicleRoutes(testEnv.vehicleService, positionService, testEnv.trackService)
              response <- service.routes.orNotFound.run(
                AuthedRequest(
                  testEnv.normalUser,
                  Request(method = Method.GET, uri = uri"/position?id=1")
                )
              )
            } yield assert(response.status)(equalTo(Status.Forbidden))
        }
      },
      testM("Find all positions of vehicle") {
        val startTime: ZonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(0), ZoneId.of("Europe/Prague"))
        val clock = new TestClock(startTime, Duration.ofSeconds(1))
        val testEnv =
          TestEnv(
            Store(
              vehicles = List(LightVehicle(Some(1), "Vehicle 22", clock.now(), None)),
              fleets = List(LightFleet(Some(1), "Fleet 1"), LightFleet(Some(2), "")),
              vehicleFleets = List(VehicleFleet(1, 1, 1)),
              positions = List(
                Position(
                  Some(1),
                  2,
                  1,
                  52.51,
                  50.087823,
                  14.430026,
                  ZonedDateTime.of(2021, 2, 25, 3, 0, 0, 0, ZoneId.of("Europe/Prague")),
                  "sessionId234"
                ),
                Position(
                  Some(2),
                  1,
                  1,
                  52.51,
                  50.087823,
                  14.430026,
                  ZonedDateTime.of(2021, 2, 25, 2, 0, 0, 0, ZoneId.of("Europe/Prague")),
                  "sessionId234"
                ),
                Position(
                  Some(3),
                  1,
                  1,
                  52.51,
                  50.087823,
                  14.430026,
                  ZonedDateTime.of(2021, 2, 25, 1, 0, 0, 0, ZoneId.of("Europe/Prague")),
                  "sessionId234"
                )
              )
            )
          )
        val expectedLastPosition = List(
          Position(
            Some(2),
            1,
            1,
            52.51,
            50.087823,
            14.430026,
            ZonedDateTime.of(2021, 2, 25, 2, 0, 0, 0, ZoneId.of("Europe/Prague")),
            "sessionId234"
          ),
          Position(
            Some(3),
            1,
            1,
            52.51,
            50.087823,
            14.430026,
            ZonedDateTime.of(2021, 2, 25, 1, 0, 0, 0, ZoneId.of("Europe/Prague")),
            "sessionId234"
          )
        )
        Blocker[Task].use {
          blocker =>
            for {
              caffeine <- CaffeineAtomicCache.make[Long, Position](testEnv.loggerFactory)
              gpx = new GPXFileGeneratorBuilder(VolumesConfig("", ""), blocker)
              positionService = PositionService(testEnv.positionDao, testEnv.loggerFactory, caffeine, gpx)
              service = new VehicleRoutes(testEnv.vehicleService, positionService, testEnv.trackService)
              response <- service.routes.orNotFound.run(
                AuthedRequest(
                  testEnv.editorUser,
                  Request(method = Method.POST, uri = uri"/positions").withEntity {
                    Json.obj(("vehicleId", Json.fromLong(1)), ("page", Json.fromInt(1)), ("pageSize", Json.fromInt(Int.MaxValue)))
                  }
                )
              )
              body <- response.as[Json]
            } yield assert(response.status)(equalTo(Status.Ok)) && assert(body)(
              equalTo(expectedLastPosition.asJson)
            )
        }
      },
      testM("Find all positions of not existing vehicle") {
        val testEnv = TestEnv(Store())
        Blocker[Task].use {
          blocker =>
            for {
              caffeine <- CaffeineAtomicCache.make[Long, Position](testEnv.loggerFactory)
              gpx = new GPXFileGeneratorBuilder(VolumesConfig("", ""), blocker)
              positionService = PositionService(testEnv.positionDao, testEnv.loggerFactory, caffeine, gpx)
              service = new VehicleRoutes(testEnv.vehicleService, positionService, testEnv.trackService)
              response <- service.routes.orNotFound.run(
                AuthedRequest(
                  testEnv.editorUser,
                  Request(method = Method.POST, uri = uri"/positions").withEntity {
                    Json.obj(("vehicleId", Json.fromLong(213)), ("page", Json.fromInt(1)), ("pageSize", Json.fromInt(Int.MaxValue)))
                  }
                )
              )
            } yield assert(response.status)(equalTo(Status.NotFound))
        }
      },
      testM("Get vehicle history without tracks") {
        val startTime: ZonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(0), ZoneId.of("Europe/Prague"))
        val clock = new TestClock(startTime, Duration.ofSeconds(1))
        val testEnv =
          TestEnv(
            Store(
              vehicles = List(LightVehicle(Some(1), "Vehicle 22", clock.now(), None)),
              fleets = List(LightFleet(Some(1), "Fleet 1"), LightFleet(Some(2), "")),
              vehicleFleets = List(VehicleFleet(1, 1, 1)),
              positions = List(
                Position(
                  Some(1),
                  2,
                  1,
                  52.51,
                  50.087823,
                  14.430026,
                  ZonedDateTime.of(2021, 2, 25, 3, 0, 0, 0, ZoneId.of("Europe/Prague")),
                  "sessionId234"
                ),
                Position(
                  Some(2),
                  1,
                  1,
                  52.51,
                  50.087823,
                  14.430026,
                  ZonedDateTime.of(2021, 2, 25, 2, 0, 0, 0, ZoneId.of("Europe/Prague")),
                  "sessionId234"
                ),
                Position(
                  Some(3),
                  1,
                  1,
                  52.51,
                  50.087823,
                  14.430026,
                  ZonedDateTime.of(2021, 2, 25, 1, 0, 0, 0, ZoneId.of("Europe/Prague")),
                  "sessionId234"
                )
              )
            )
          )
        val expectedLastPosition = Json.obj(
          (
            "positions",
            Json.arr(
              Position(
                Some(2),
                1,
                1,
                52.51,
                50.087823,
                14.430026,
                ZonedDateTime.of(2021, 2, 25, 2, 0, 0, 0, ZoneId.of("Europe/Prague")),
                "sessionId234"
              ).asJson
            )
          ),
          ("tracks", Json.arr())
        )
        Blocker[Task].use {
          blocker =>
            for {
              caffeine <- CaffeineAtomicCache.make[Long, Position](testEnv.loggerFactory)
              gpx = new GPXFileGeneratorBuilder(VolumesConfig("", ""), blocker)
              positionService = PositionService(testEnv.positionDao, testEnv.loggerFactory, caffeine, gpx)
              service = new VehicleRoutes(testEnv.vehicleService, positionService, testEnv.trackService)
              response <- service.routes.orNotFound.run(
                AuthedRequest(
                  testEnv.editorUser,
                  Request(method = Method.POST, uri = uri"/history").withEntity {
                    Json.obj(
                      ("vehicleId", Json.fromLong(1)),
                      ("since", Json.fromString("2021-02-25T01:15:30+01:00")),
                      ("until", Json.fromString("2021-02-25T03:15:30+01:00"))
                    )
                  }
                )
              )
              body <- response.as[Json]
            } yield assert(response.status)(equalTo(Status.Ok)) && assert(body)(
              equalTo(expectedLastPosition)
            )
        }
      },
      testM("Get vehicle history with tracks") {
        val startTime: ZonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(0), ZoneId.of("Europe/Prague"))
        val clock = new TestClock(startTime, Duration.ofSeconds(1))
        val testEnv =
          TestEnv(
            Store(
              tracks = List(LightTrack(Some(1), 1, ZonedDateTime.of(2021, 2, 25, 1, 0, 0, 0, ZoneId.of("Europe/Prague")))),
              vehicles = List(LightVehicle(Some(1), "Vehicle 22", clock.now(), None)),
              fleets = List(LightFleet(Some(1), "Fleet 1"), LightFleet(Some(2), "")),
              vehicleFleets = List(VehicleFleet(1, 1, 1)),
              positions = List(
                Position(
                  Some(1),
                  2,
                  1,
                  52.51,
                  50.087823,
                  14.430026,
                  ZonedDateTime.of(2021, 2, 25, 3, 0, 0, 0, ZoneId.of("Europe/Prague")),
                  "sessionId234"
                ),
                Position(
                  Some(2),
                  1,
                  1,
                  52.51,
                  50.087823,
                  14.430026,
                  ZonedDateTime.of(2021, 2, 25, 2, 0, 0, 0, ZoneId.of("Europe/Prague")),
                  "sessionId234"
                ),
                Position(
                  Some(3),
                  1,
                  1,
                  52.51,
                  50.087823,
                  14.430026,
                  ZonedDateTime.of(2021, 2, 25, 1, 0, 0, 0, ZoneId.of("Europe/Prague")),
                  "sessionId234"
                )
              )
            )
          )
        val expectedLastPosition = Json.obj(
          (
            "positions",
            Json.arr(
              Position(
                Some(2),
                1,
                1,
                52.51,
                50.087823,
                14.430026,
                ZonedDateTime.of(2021, 2, 25, 2, 0, 0, 0, ZoneId.of("Europe/Prague")),
                "sessionId234"
              ).asJson,
              Position(
                Some(3),
                1,
                1,
                52.51,
                50.087823,
                14.430026,
                ZonedDateTime.of(2021, 2, 25, 1, 0, 0, 0, ZoneId.of("Europe/Prague")),
                "sessionId234"
              ).asJson
            )
          ),
          (
            "tracks",
            Json.arr(
              Track(
                LightTrack(Some(1), 1, ZonedDateTime.of(2021, 2, 25, 1, 0, 0, 0, ZoneId.of("Europe/Prague"))),
                LightVehicle(Some(1), "Vehicle 22", clock.now(), None)
              ).asJson
            )
          )
        )
        Blocker[Task].use {
          blocker =>
            for {
              caffeine <- CaffeineAtomicCache.make[Long, Position](testEnv.loggerFactory)
              gpx = new GPXFileGeneratorBuilder(VolumesConfig("", ""), blocker)
              positionService = PositionService(testEnv.positionDao, testEnv.loggerFactory, caffeine, gpx)
              service = new VehicleRoutes(testEnv.vehicleService, positionService, testEnv.trackService)
              response <- service.routes.orNotFound.run(
                AuthedRequest(
                  testEnv.editorUser,
                  Request(method = Method.POST, uri = uri"/history").withEntity {
                    Json.obj(
                      ("vehicleId", Json.fromLong(1)),
                      ("since", Json.fromString("2021-02-25T00:15:30+01:00")),
                      ("until", Json.fromString("2021-02-25T03:15:30+01:00"))
                    )
                  }
                )
              )
              body <- response.as[Json]
            } yield assert(response.status)(equalTo(Status.Ok)) && assert(body)(
              equalTo(expectedLastPosition)
            )
        }
      }
    )
  }
}

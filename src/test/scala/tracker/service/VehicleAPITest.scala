package tracker.service

import cats.effect.Blocker

import io.circe.Json
import io.circe.syntax.EncoderOps
import org.http4s.circe.{jsonDecoder, jsonEncoder}
import org.http4s.{AuthedRequest, Method, Request, Status}
import org.http4s.implicits.{http4sKleisliResponseSyntaxOptionT, http4sLiteralsSyntax}
import slog4s.slf4j.Slf4jFactory
import slog4s.LoggerFactory
import tracker._
import tracker.config.VolumesConfig
import tracker.dao.{PositionDAO, TrackDAO, VehicleDAO, VehicleFleetDAO}
import tracker.mocked.{PositionDAOMock, TestClock, TrackDAOMock, VehicleDAOMock, VehicleFleetDaoMock}
import tracker.module.routes.VehicleRoutes
import tracker.utils.{CaffeineAtomicCache, GPXFileGeneratorBuilder}
import zio.test._
import zio.test.Assertion.equalTo
import zio.Task
import zio.interop.catz._

import java.time.{Duration, Instant, ZoneId, ZonedDateTime}

object VehicleAPITest extends DefaultRunnableSpec {
  val startTime: ZonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(0), ZoneId.of("Europe/Prague"))
  val clock: TestClock = new TestClock(startTime, Duration.ofSeconds(1))
  val vehicleFleetDao: VehicleFleetDAO = VehicleFleetDaoMock(List.empty)
  val vehicleDao: VehicleDAO = VehicleDAOMock(List.empty, vehicleFleetDao.asInstanceOf[VehicleFleetDaoMock])
  val positionDao: PositionDAO = PositionDAOMock(
    List(Position(Some(1), 2, 2, 45.13, 23.3212, 123.231, ZonedDateTime.of(2021, 2, 25, 0, 0, 0, 0, ZoneId.of("Europe/Prague")), "sessionID33"))
  )
  val vehicleService: VehicleService = VehicleService(vehicleDao, vehicleFleetDao, clock)
  val loggerFactory: LoggerFactory[Task] = Slf4jFactory[Task].withoutContext.loggerFactory
  val trackDao: TrackDAO = TrackDAOMock()
  val trackService: TrackService = TrackService(trackDao, positionDao, loggerFactory)

  val adminUser: User = User(
    Some(1),
    "Karel",
    ZonedDateTime.of(2021, 2, 25, 0, 0, 0, 0, ZoneId.of("Europe/Prague")),
    None,
    "10000:WS4enQfVmeoAtnCXUtRqCdHu+A1Tv7i1QReXH/bj5pU=:aumeogsLFIqGr9HrfvxdMw==",
    "karel",
    Set(Roles.Admin)
  )
  //      val editorUser: User = User(
  //        Some(1),
  //        "Karel",
  //        ZonedDateTime.of(2021, 2, 25, 0, 0, 0, 0, ZoneId.of("Europe/Prague")),
  //        None,
  //        "10000:WS4enQfVmeoAtnCXUtRqCdHu+A1Tv7i1QReXH/bj5pU=:aumeogsLFIqGr9HrfvxdMw==",
  //        "karel",
  //        Set(Roles.Editor)
  //      )
  val readerUser: User = User(
    Some(1),
    "Karel",
    ZonedDateTime.of(2021, 2, 25, 0, 0, 0, 0, ZoneId.of("Europe/Prague")),
    None,
    "10000:WS4enQfVmeoAtnCXUtRqCdHu+A1Tv7i1QReXH/bj5pU=:aumeogsLFIqGr9HrfvxdMw==",
    "karel",
    Set(Roles.Reader)
  )
  //      val normalUser: User = User(
  //        Some(2),
  //        "John Doe",
  //        ZonedDateTime.of(2221, 2, 5, 0, 0, 0, 0, ZoneId.of("Europe/Prague")),
  //        None,
  //        "password",
  //        "john",
  //        Set(Roles.Reader, Roles.User)
  //      )

  override def spec: Spec[Any, TestFailure[Throwable], TestSuccess] = {
    suite("Vehicle routes suite")(
      testM("Add new vehicle") {
        val expectedVehicle = Vehicle(LightVehicle(Some(1), "Vehicle 1", clock.now(), None), List(LightFleet(Some(1), ""), LightFleet(Some(4), "")))
        Blocker[Task].use {
          blocker =>
            for {
              caffeine <- CaffeineAtomicCache.make[Long, Position](loggerFactory)
              gpx = new GPXFileGeneratorBuilder(VolumesConfig("", ""), blocker)
              positionService = PositionService(positionDao, loggerFactory, caffeine, gpx)
              service = new VehicleRoutes(vehicleService, positionService, trackService)
              response <- service.routes.orNotFound.run(
                AuthedRequest(
                  adminUser,
                  Request(method = Method.POST, uri = uri"/new").withEntity {
                    Json.obj(
                      ("name", Json.fromString("Vehicle 1")),
                      ("fleets", List(1, 4).asJson)
                    )
                  }
                )
              )
//              _ <- response.body.through(utf8Decode).compile.string.map(b => println(b))
              body <- response.as[Json]
            } yield assert(body)(equalTo(expectedVehicle.asJson))
        }
      },
      testM("Add new vehicle without permisions - Reader") {
        Blocker[Task].use {
          blocker =>
            for {
              caffeine <- CaffeineAtomicCache.make[Long, Position](loggerFactory)
              gpx = new GPXFileGeneratorBuilder(VolumesConfig("", ""), blocker)
              positionService = PositionService(positionDao, loggerFactory, caffeine, gpx)
              service = new VehicleRoutes(vehicleService, positionService, trackService)
              response <- service.routes.orNotFound.run(
                AuthedRequest(
                  readerUser,
                  Request(method = Method.POST, uri = uri"/new").withEntity {
                    Json.obj(
                      ("name", Json.fromString("Vehicle 1")),
                      ("fleets", List(1, 4).asJson)
                    )
                  }
                )
              )
//              _ <- response.body.through(utf8Decode).compile.string.map(b => println(b))
            } yield assert(response.status)(equalTo(Status.Forbidden))
        }
      }
    )
  }
}

package tracker.utils

import pdi.jwt.JwtAlgorithm
import slog4s.slf4j.Slf4jFactory
import slog4s.LoggerFactory
import tracker.{Roles, User}
import tracker.config.{JwtConfig, MqttConfig}
import tracker.dao._
import tracker.mocked._
import tracker.service.{FleetService, TrackService, TrackerService, VehicleService}
import zio.Task
import zio.interop.catz._

import java.time.{Duration, Instant, ZoneId, ZonedDateTime}
import scala.concurrent.duration.DurationInt

class TestEnv(val store: Store, val clock: TestClock) {
  val startTime: ZonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(0), ZoneId.of("Europe/Prague"))
  val fleetDao: FleetDAO = FleetDaoMock(store)
  val vehicleFleetDao: VehicleFleetDAO = VehicleFleetDaoMock(store)
  val vehicleDao: VehicleDAO = VehicleDAOMock(store)
  val userDao: UserDAO = UserDAOTest(store)
  val positionDao: PositionDAO = PositionDAOMock(store)
  val trackerDao: TrackerDAO = TrackerDAOMock(store)
  val vehicleService: VehicleService = VehicleService(vehicleDao, vehicleFleetDao, clock)
  val loggerFactory: LoggerFactory[Task] = Slf4jFactory[Task].withoutContext.loggerFactory
  val trackDao: TrackDAO = TrackDAOMock(store)
  val trackService: TrackService = TrackService(trackDao, positionDao, loggerFactory)
  val fleetService: FleetService = FleetService(fleetDao)
  val jwtConfig: JwtConfig = JwtConfig("secretforsignjwttokens", 0, JwtAlgorithm.HS256)
  val mqttConfig: MqttConfig = MqttConfig("", 0, ssl = true, None, None, "", "", 1.second, 5, 15, 15.minutes)
  val trackerService: TrackerService = TrackerService(trackerDao, userDao, mqttConfig, jwtConfig, loggerFactory, clock)

  val adminUser: User = User(
    Some(1),
    "Karel",
    ZonedDateTime.of(2021, 2, 25, 0, 0, 0, 0, ZoneId.of("Europe/Prague")),
    None,
    "10000:WS4enQfVmeoAtnCXUtRqCdHu+A1Tv7i1QReXH/bj5pU=:aumeogsLFIqGr9HrfvxdMw==",
    "karel",
    Set(Roles.Admin)
  )
  val editorUser: User = User(
    Some(1),
    "Karel",
    ZonedDateTime.of(2021, 2, 25, 0, 0, 0, 0, ZoneId.of("Europe/Prague")),
    None,
    "10000:WS4enQfVmeoAtnCXUtRqCdHu+A1Tv7i1QReXH/bj5pU=:aumeogsLFIqGr9HrfvxdMw==",
    "karel",
    Set(Roles.Editor)
  )
  val readerUser: User = User(
    Some(1),
    "Karel",
    ZonedDateTime.of(2021, 2, 25, 0, 0, 0, 0, ZoneId.of("Europe/Prague")),
    None,
    "10000:WS4enQfVmeoAtnCXUtRqCdHu+A1Tv7i1QReXH/bj5pU=:aumeogsLFIqGr9HrfvxdMw==",
    "karel",
    Set(Roles.Reader)
  )

  val normalUser: User = User(
    Some(2),
    "John Doe",
    ZonedDateTime.of(2221, 2, 5, 0, 0, 0, 0, ZoneId.of("Europe/Prague")),
    None,
    "password",
    "john",
    Set(Roles.User)
  )
}

object TestEnv {
  def apply(
      store: Store = Store(),
      clock: TestClock = new TestClock(ZonedDateTime.ofInstant(Instant.ofEpochMilli(0), ZoneId.of("Europe/Prague")), Duration.ofSeconds(1))
  ): TestEnv =
    new TestEnv(store, clock)
}

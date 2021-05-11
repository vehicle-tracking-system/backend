package tracker.service

import cats.implicits.catsSyntaxFlatMapOps
import io.circe.Json
import slog4s.{Logger, LoggerFactory}
import tracker._
import tracker.config.Configuration
import tracker.dao.{TrackerDAO, UserDAO}
import tracker.security.{AccessTokenBuilder, AccessTokenPayload}
import tracker.utils.{Clock, DefaultClock}
import zio.interop.catz._
import zio.Task

class TrackerService(trackerDAO: TrackerDAO, config: Configuration, logger: Logger[Task], clock: Clock) {
  val pagination: Pagination[Tracker] = DefaultPagination(trackerDAO.findAllActive, () => trackerDAO.count())

  def persist(request: NewTrackerRequest): Task[Tracker] = {
    for {
      tmpTracker <- trackerDAO.persist(LightTracker(None, request.name, request.vehicleId, "N/A", clock.now(), None))
      trackerWithToken <- updateAccessToken(tmpTracker.tracker)
    } yield trackerWithToken
  }

  def update(request: UpdateTrackerRequest): Task[Tracker] = {
    trackerDAO.update(request.tracker)
  }

  def delete(trackerId: Long): Task[Tracker] = {
    logger.info(s"Mark tracker $trackerId as deleted") >>
      trackerDAO.markAsDeleted(trackerId)
  }

  def get(trackerId: Long): Task[Option[Tracker]] =
    logger.info(s"Get tracker $trackerId") >>
      trackerDAO.find(trackerId)

  def getByToken(token: String): Task[Option[Tracker]] =
    logger.info(s"Get tracker by token: $token") >>
      trackerDAO.findByToken(token)

  def getAll(request: PageRequest): Task[Page[Tracker]] =
    logger.info(s"Get page ${request.page} with size ${request.pageSize} from all trackers") >>
      pagination.getPage(request.page, request.pageSize)

  def getAll(page: Option[Int], pageSize: Option[Int]): Task[Page[Tracker]] =
    logger.info(s"Get page $page with size $pageSize from all trackers") >>
      pagination.getPage(page.fold(1)(identity), pageSize.fold(Int.MaxValue)(identity))

  def updateAccessToken(request: UpdateTrackerRequest): Task[Tracker] = {
    updateAccessToken(request.tracker)
  }

  def updateAccessToken(tracker: LightTracker): Task[Tracker] = {
    for {
      _ <- logger.info(s"Generate new token for tracker ${tracker.id}")
      token <- Task(generateAccessToken(tracker.id.getOrElse(throw new IllegalArgumentException("Tracker must have unique identifier"))))
      updatedTracker <- trackerDAO.updateAccessToken(tracker.ID, token)
    } yield updatedTracker

  }

  def configFile(trackerId: Long): Task[Option[fs2.Stream[Task, Byte]]] = {
    trackerDAO.find(trackerId).map { tracker =>
      tracker.fold(Option.empty[fs2.Stream[Task, Byte]]) { t =>
        Some {
          fs2.Stream
            .eval(Task.effect {
              Json
                .obj(
                  ("token", Json.fromString(t.tracker.token)),
                  ("id", Json.fromLong(t.tracker.ID)),
                  ("vehicleId", Json.fromLong(t.vehicle.ID)),
                  ("mqttHost", Json.fromString(config.mqtt.host)),
                  ("mqttPort", Json.fromInt(config.mqtt.port)),
                  ("mqttUsername", Json.fromString(config.mqtt.user.getOrElse(""))),
                  ("mqttPassword", Json.fromString(config.mqtt.password.getOrElse(""))),
                  ("mqttTopic", Json.fromString(config.mqtt.topic))
                )
                .noSpacesSortKeys
                .toCharArray
                .toSeq
            })
            .flatMap(fs2.Stream.emits)
            .through(t => t.map(j => j.toByte))
        }
      }
    }
  }

  def verifyAccessToken(token: String): Task[Boolean] = {
    getByToken(token).map {
      case Some(_) => true
      case None    => false
    }
  }

  private def generateAccessToken(trackerId: Long): String = {
    AccessTokenBuilder.createUnlimitedToken(
      AccessTokenPayload(trackerId, Set(Roles.Tracker)),
      config.jwt
    )
  }
}

object TrackerService {
  def apply(
      trackerDAO: TrackerDAO,
      userDAO: UserDAO,
      config: Configuration,
      loggerFactory: LoggerFactory[Task],
      clock: Clock = DefaultClock
  ): TrackerService = {
    userDAO.count()
    new TrackerService(trackerDAO, config, loggerFactory.make("tracker-service"), clock)
  }
}

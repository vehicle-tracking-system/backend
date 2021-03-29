package tracker.service

import cats.implicits.catsSyntaxFlatMapOps
import slog4s.{Logger, LoggerFactory}
import tracker._
import tracker.config.JwtConfig
import tracker.dao.{TrackerDAO, UserDAO}
import tracker.security.{AccessTokenBuilder, AccessTokenPayload}
import zio.interop.catz._
import zio.Task

import java.time.ZonedDateTime

class TrackerService(trackerDAO: TrackerDAO, jwtConfig: JwtConfig, logger: Logger[Task]) {
  val pagination: Pagination[Tracker] = DefaultPagination(trackerDAO.findAllActive, () => trackerDAO.count())

  def persist(request: NewTrackerRequest): Task[Tracker] = {
    for {
      tmpTracker <- trackerDAO.persist(request.tracker)
      trackerWithToken <- updateAccessToken(tmpTracker.tracker)
    } yield trackerWithToken
  }

  def update(request: UpdateTrackerRequest): Task[Tracker] = {
    trackerDAO.update(request.tracker)
  }

  def delete(request: UpdateTrackerRequest): Task[Tracker] = {
    logger.info(s"Mark tracker ${request.tracker.id} as deleted") >>
      trackerDAO
        .update(
          LightTracker(
            request.tracker.id,
            request.tracker.name,
            request.tracker.vehicleId,
            request.tracker.token,
            request.tracker.createdAt,
            Some(ZonedDateTime.now())
          )
        );
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
      updatedTracker <- trackerDAO.update(LightTracker(tracker.id, tracker.name, tracker.vehicleId, token, tracker.createdAt, tracker.deletedAt))
    } yield updatedTracker

  }

  private def generateAccessToken(trackerId: Long): String = {
    AccessTokenBuilder.createUnlimitedToken(
      AccessTokenPayload(trackerId, Set(Roles.Tracker)),
      jwtConfig
    )
  }
}

object TrackerService {
  def apply(trackerDAO: TrackerDAO, userDAO: UserDAO, jwtConfig: JwtConfig, loggerFactory: LoggerFactory[Task]): TrackerService = {
    userDAO.count()
    new TrackerService(trackerDAO, jwtConfig, loggerFactory.make("tracker-service"))
  }
}

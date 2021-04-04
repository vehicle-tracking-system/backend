package tracker.service

import slog4s.{Logger, LoggerFactory}
import tracker._
import tracker.dao.{PositionDAO, TrackDAO}
import zio.Task

import java.time.ZonedDateTime

class TrackService(
    trackDAO: TrackDAO,
    positionDAO: PositionDAO,
    pagination: Pagination[Track],
    vehicleTracksPagination: Long => Pagination[Track],
    logger: Logger[Task]
) {

  def persist(positionId: Long, timestamp: ZonedDateTime): Task[Track] =
    trackDAO.persist(LightTrack(None, positionId, timestamp))

  def getAll(page: Option[Int], pageSize: Option[Int]): Task[Page[Track]] =
    pagination.getPage(page.fold(1)(identity), pageSize.fold(Int.MaxValue)(identity))

  def getAll(request: PageRequest): Task[Page[Track]] = pagination.getPage(request.page, request.pageSize)

  def getByVehicle(vehicleId: Long, page: Option[Int], pageSize: Option[Int]): Task[Page[Track]] =
    vehicleTracksPagination(vehicleId).getPage(page.fold(1)(identity), pageSize.fold(Int.MaxValue)(identity))

  def getPositions(trackId: Long): Task[List[Position]] = {
    for {
      positions <- positionDAO.findByTrack(trackId)
      _ <- logger.debug(positions.toString())
    } yield positions
  }

  def getInRange(vehicleId: Long, since: ZonedDateTime, until: ZonedDateTime): Task[List[Track]] =
    trackDAO.findTracksInRage(vehicleId, since, until)

  def get(id: Long): Task[Option[Track]] = trackDAO.find(id)
}

object TrackService {
  def apply(trackDAO: TrackDAO, positionDAO: PositionDAO, loggerFactory: LoggerFactory[Task]): TrackService =
    new TrackService(
      trackDAO,
      positionDAO,
      DefaultPagination(trackDAO.findAll, () => trackDAO.count()),
      (id: Long) => DefaultPagination(trackDAO.findByVehicle(id, _, _), () => trackDAO.countVehicleTracks(id)),
      loggerFactory.make("track-service")
    )
}

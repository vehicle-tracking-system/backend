package tracker.service

import cats.implicits.catsSyntaxFlatMapOps
import slog4s.{Logger, LoggerFactory}
import tracker._
import tracker.dao.PositionDAO
import tracker.utils.{CaffeineAtomicCache, GPXFileGeneratorBuilder}
import zio.Task
import zio.interop.catz._

import java.util.NoSuchElementException

class PositionService(
    positionDAO: PositionDAO,
    logger: Logger[Task],
    cache: CaffeineAtomicCache[Long, Position],
    gpxFileGenerator: GPXFileGeneratorBuilder
) {
  def get(id: Long): Task[Option[Position]] = positionDAO.find(id)

  def getByVehicle(request: VehiclePositionsRequest): Task[List[Position]] = {
    logger.debug(s"Getting position by vehicle id: ${request.vehicleId} - page ${request.page} with size ${request.pageSize}") >>
      positionDAO.findByVehicle(request.vehicleId, (request.page - 1) * request.pageSize, request.pageSize)
  }

  def persist(request: PositionRequest): Task[Position] = {
    cache.update(request.position.vehicleId) {
      request.position.id match {
        case Some(_) => positionDAO.update(request.position)
        case None    => positionDAO.persist(request.position)
      }
    }
  }

  def persist(request: PositionsRequest): Task[Position] = {
    val sortedPositions: List[Position] = request.positions.filter(p => p.vehicleId == request.vehicleId).sortBy(_.timestamp)
    cache.update(request.vehicleId) {
      positionDAO.persistList(request.positions) >> Task(sortedPositions.last)
    }
  }

  def getVehiclePositionHistory(request: VehiclePositionHistoryRequest): Task[List[Position]] = {
    logger.debug(s"Searching history of vehicle ${request.vehicleId}, since ${request.since} until ${request.until}") >>
      positionDAO.findVehicleHistory(request.vehicleId, request.since, request.until)
  }

  def getLastVehiclePosition(vehicleId: Long): Task[Option[Position]] = {
    val position = for {
      cachedPosition <- cache.get(vehicleId)
      res <-
        cachedPosition
          .fold(
            cache
              .update(vehicleId)(positionDAO.findLastVehiclePosition(vehicleId).someOrFail(new NoSuchElementException))
          )(p => Task(p))
    } yield res
    position.fold(
      {
        case _: NoSuchElementException => None
        case e                         => throw new Error(e.toString)
      },
      Some(_)
    )
  }

  def getActiveDays(vehicleId: Long, month: Int, year: Int): Task[List[Int]] = positionDAO.findActiveDays(vehicleId, month, year)

  def generateGPX(trackId: Long): Task[Option[fs2.Stream[Task, Byte]]] =
    positionDAO
      .findByTrack(trackId)
      .map(_.map(gpxFileGenerator.make(trackId.toString, _).generate.stream()))
}

object PositionService {
  def apply(
      positionDAO: PositionDAO,
      loggerFactory: LoggerFactory[Task],
      cache: CaffeineAtomicCache[Long, Position],
      gpxModule: GPXFileGeneratorBuilder
  ): PositionService =
    new PositionService(positionDAO, loggerFactory.make("position-service"), cache, gpxModule)
}

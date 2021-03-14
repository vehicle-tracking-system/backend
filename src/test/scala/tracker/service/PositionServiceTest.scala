package tracker.service

import io.circe.syntax.EncoderOps
import org.scalatest.flatspec.AnyFlatSpec
import tracker.{Position, PositionRequest}
import tracker.dao.PositionDAO
import zio.{Task, _}

import java.time.{ZoneId, ZonedDateTime}

class PositionServiceTest extends AnyFlatSpec {
  class PositionDAOTest(var positions: List[Position]) extends PositionDAO {

    var maxId: Long = 0

    override def persist(position: Position): Task[Position] =
      Task.effect {
        maxId = maxId + 1
        val pos = Position(Some(maxId), position.vehicleId, position.speed, position.latitude, position.longitude, position.timestamp)
        positions = positions.appended(pos)
        pos
      }

    override def update(position: Position): Task[Position] =
      Task.effect {
        val size = positions.size
        positions = positions.filter(p => p.id != position.id)
        if (size == positions.size) {
          throw new IllegalStateException()
        } else {
          positions = positions.appended(position)
          position
        }
      }

    override def find(id: Long): Task[Option[Position]] =
      Task.succeed {
        positions.find(p => p.id.get == id)
      }

    def clear(): Unit = positions = List.empty

    override def findByVehicle(vehicleId: Long, offset: Int, limit: Int): Task[List[Position]] = throw new NotImplementedError()

    override def findVehicleHistory(vehicleId: Long, since: ZonedDateTime, until: ZonedDateTime): Task[List[Position]] =
      throw new NotImplementedError()
  }

  val positionDAO: PositionDAOTest = new PositionDAOTest(List.empty)
  val mockedPositionService: PositionService = PositionService(positionDAO)

  val runtime: Runtime[zio.ZEnv] = Runtime.default

  "Position" should "be serializable to JSON without ID" in {
    positionDAO.clear()
    val newPosition = Position(None, 1, 100.152, 52.524, 64.25445, ZonedDateTime.of(2021, 2, 25, 0, 0, 0, 0, ZoneId.of("Europe/Prague")))
    val insertedPos = runtime.unsafeRun(mockedPositionService.persist(PositionRequest(newPosition)))
    assert(
      insertedPos.asJson.noSpacesSortKeys equals """{"id":1,"latitude":52.524,"longitude":64.25445,"speed":100.152,"timestamp":"2021-02-25T00:00:00+01:00[Europe/Prague]","vehicleId":1}"""
    )
  }
  it should "be serializable to JSON with ID" in {
    positionDAO.clear()
    val newPosition = Position(None, 1, 100.152, 52.524, 64.25445, ZonedDateTime.of(2021, 2, 25, 0, 0, 0, 0, ZoneId.of("Europe/Prague")))
    assert(
      newPosition.asJson.noSpacesSortKeys equals """{"id":null,"latitude":52.524,"longitude":64.25445,"speed":100.152,"timestamp":"2021-02-25T00:00:00+01:00[Europe/Prague]","vehicleId":1}"""
    )
  }

  "PositionService" should "handle new position" in {
    positionDAO.clear()
    val newPosition = Position(None, 1, 100.152, 52.524, 64.25445, ZonedDateTime.of(2021, 2, 25, 0, 0, 0, 0, ZoneId.of("Europe/Prague")))
    runtime.unsafeRun(mockedPositionService.persist(PositionRequest(newPosition)))
    assert(positionDAO.positions.size equals 1)
    runtime.unsafeRun(mockedPositionService.persist(PositionRequest(newPosition)))
    assert(positionDAO.positions.size equals 2)
  }
  it should "find specified position" in {
    positionDAO.clear()
    val newPosition1 = Position(None, 1, 100.152, 52.524, 64.25445, ZonedDateTime.of(2021, 2, 25, 0, 0, 0, 0, ZoneId.of("Europe/Prague")))
    val newPosition2 = Position(None, 1, 45.1, 52.524, 64.25445, ZonedDateTime.of(2020, 2, 25, 0, 0, 0, 0, ZoneId.of("Europe/Prague")))
    val insertedPos = runtime.unsafeRun(mockedPositionService.persist(PositionRequest(newPosition1)))
    runtime.unsafeRun(mockedPositionService.persist(PositionRequest(newPosition2)))
    assert(
      runtime.unsafeRun(mockedPositionService.get(insertedPos.id.get)).getOrElse(throw new Error) equals insertedPos
    )
  }

}

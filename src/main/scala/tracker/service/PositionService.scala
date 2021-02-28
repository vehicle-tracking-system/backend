package tracker.service

import tracker.dao.PositionDAO
import tracker.Position
import zio.Task

class PositionService(positionDAO: PositionDAO) {
  def find(id: Long): Task[Option[Position]] = positionDAO.find(id)
}

object PositionService {
  def apply(positionDAO: PositionDAO): PositionService = new PositionService(positionDAO)
}

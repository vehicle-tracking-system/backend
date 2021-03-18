package tracker.service

import tracker.dao.TrackDAO
import tracker.{DefaultPagination, NewTrackRequest, Page, PageRequest, Pagination, Track}
import zio.Task

class TrackService(trackDAO: TrackDAO, val pagination: Pagination[Track]) {

  def newTrack(request: NewTrackRequest): Task[Track] = { trackDAO.persist(request.track) }

  def getAll(request: PageRequest): Task[Page[Track]] = pagination.getPage(request.page, request.pageSize)
}

object TrackService {
  def apply(trackDAO: TrackDAO): TrackService =
    new TrackService(trackDAO, DefaultPagination(trackDAO.findAll, () => trackDAO.count()))
}

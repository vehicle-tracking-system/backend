package tracker.utils

import java.time.ZonedDateTime

trait Clock {
  def now(): ZonedDateTime
}

object DefaultClock extends Clock {
  override def now(): ZonedDateTime = ZonedDateTime.now()
}

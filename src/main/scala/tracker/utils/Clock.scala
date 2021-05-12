package tracker.utils

import java.time.ZonedDateTime

trait Clock {

  /**
    * @return Current time
    */
  def now(): ZonedDateTime
}

object DefaultClock extends Clock {

  /**
    *  @return Clock return real current system time
    */
  override def now(): ZonedDateTime = ZonedDateTime.now()
}

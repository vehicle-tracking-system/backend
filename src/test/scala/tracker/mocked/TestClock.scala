package tracker.mocked

import tracker.utils.Clock

import java.time.{Duration, ZonedDateTime}

class TestClock(startTime: ZonedDateTime, iteration: Duration) extends Clock {
  var time: ZonedDateTime = startTime

  override def now(): ZonedDateTime = time
  def tick(): Unit = time = time.plusSeconds(iteration.getSeconds)
}

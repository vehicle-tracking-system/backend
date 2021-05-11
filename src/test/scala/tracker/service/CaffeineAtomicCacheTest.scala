package tracker.service

import slog4s.slf4j.Slf4jFactory
import slog4s.LoggerFactory
import tracker.utils.CaffeineAtomicCache
import zio.{Task}
import zio.interop.catz._
import zio.test.{assert, DefaultRunnableSpec, _}
import zio.test.Assertion._

object CaffeineAtomicCacheTest extends DefaultRunnableSpec {
  override def spec: Spec[_root_.zio.test.environment.TestEnvironment, TestFailure[Throwable], TestSuccess] = {
    val loggerFactory: LoggerFactory[Task] = Slf4jFactory[Task].withoutContext.loggerFactory
    suite("Inserting into cache") {
      testM("Put item into cache") {
        for {
          cache <- CaffeineAtomicCache.make[Long, Int](loggerFactory)
          _ <- cache.update(1)(Task(42))
          res <- cache.get(1)
        } yield assert(res.getOrElse(0))(equalTo(42))
      }
//      testM("Put item from more fibers") {
//        for {
//          cache <- CaffeineAtomicCache.make[Long, Int](loggerFactory)
//          n1 <- ZIO.forkAll(ZIO.replicate(2)(cache.update(1)(Task(42))))
//          n2 <- ZIO.forkAll(ZIO.replicate(100)(cache.update(1)(Task(4))))
//          n3 <- ZIO.forkAll(ZIO.replicate(2)(cache.update(2)(Task(42))))
//          _ <- cache.update(1)(Task(1))
//          n4 <- ZIO.forkAll(ZIO.replicate(100)(cache.update(1)(Task(41))))
//          _ <- (n1 <*> n2 <*> n3 <*> n4).interrupt
//
//          res <- cache.get(1)
//        } yield assert(res.getOrElse(0))(equalTo(42))
//      }
    }
  }
}

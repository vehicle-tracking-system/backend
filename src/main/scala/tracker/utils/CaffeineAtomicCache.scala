package tracker.utils

import scalacache.caffeine._
import scalacache.{Mode, _}
import slog4s.{Logger, LoggerFactory}
import tracker.Position
import zio.stm.{TMap, TReentrantLock, ZSTM}
import zio.Task
import zio.interop.catz._

trait Comparable[A] {
  def chooseNewer(first: A, second: A): A
}
object Comparable {
  implicit val positionComparable: Comparable[Position] = (first: Position, second: Position) => {
    if (first.timestamp.isBefore(second.timestamp)) second else first
  }
}

class CaffeineAtomicCache[K, V: Comparable] private (private val data: Cache[V], private val locks: TMap[K, TReentrantLock], logger: Logger[Task]) {
  implicit val mode: Mode[Task] = scalacache.CatsEffect.modes.async

  /**
    * Get item with `key` from cache
    *
    * @param key the key of item
    */
  def get(key: K): Task[Option[V]] = data.get(key)

  /**
    * Atomic update of item in cache. If item with `key` does not exists, `update` creates it.
    *
    * @param key          the key to update
    * @param getNewValue  new value of key wrapped by Task effect. This value is used when item does not exists in cache and as parameter of `f` function.
    * @return             value (result of effect)
    */
  def update(key: K)(getNewValue: Task[V]): Task[V] = {
    withLock(key) {
      for {
        cachedValue <- data.get(key)
        _ <- cachedValue match {
          case Some(_) => logger.debug(s"Key $key found")
          case None    => logger.debug(s"Key $key not found")
        }
        newValue <- getNewValue
        _ <- logger.debug(s"Updating value $newValue")
        _ <- cachedValue match {
          case Some(v) => data.put(key)(implicitly[Comparable[V]].chooseNewer(v, newValue))
          case None    => data.put(key)(newValue)
        }
      } yield newValue
    }
  }

  def locksNo: Task[Int] = locks.size.commit

  // await for write lock. After that locked them for yourself.
  private def withLock(key: K)(f: Task[V]): Task[V] = {
    for {
      lock <- locks.get(key).flatMap(_.map(ZSTM.partial(_)).getOrElse(TReentrantLock.make.tap(nl => locks.put(key, nl)))).commit
      result <- lock.writeLock.use_(f)
    } yield result
  }

}

object CaffeineAtomicCache {
  def make[K, V: Comparable](loggerFactory: LoggerFactory[Task]): Task[CaffeineAtomicCache[K, V]] =
    (for {
      locksRef <- TMap.empty[K, TReentrantLock]
    } yield new CaffeineAtomicCache[K, V](CaffeineCache[V], locksRef, loggerFactory.make("atomic-cache"))).commit
}

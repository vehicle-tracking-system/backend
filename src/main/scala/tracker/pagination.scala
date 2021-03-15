package tracker

import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder
import zio.Task

import scala.math.max

trait Pagination[A] {
  def getPage(number: Int, size: Int): Task[Page[A]]
}

case class Page[A](page: Int, totalPage: Int, pageSize: Int, data: List[A])

object Page {
  implicit val encoder: Encoder[Page[Vehicle]] = deriveEncoder
}

class DefaultPagination[A](find: (Int, Int) => Task[List[A]], count: () => Task[Int]) extends Pagination[A] {
  override def getPage(number: Int, size: Int): Task[Page[A]] =
    for {
      list <- find((number - 1) * size, size)
      count <- count()
    } yield new Page[A](number, max(count / size, 1), size, list)
}

object DefaultPagination {
  def apply(f: (Offset, Size) => Task[List[Vehicle]], count: () => Task[Int]): Pagination[Vehicle] =
    new DefaultPagination[Vehicle](f, count)

  type Offset = Int
  type Size = Int
}

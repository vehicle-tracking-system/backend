package tracker

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import zio.Task

import scala.math.max

trait PaginationBuilder {

  /**
    * Create class allows paginating data from database.
    * @param find - function takes two parameter, offset and limit, and returns List of `A` in effect Task
    * @param count - function returns total amount of `A` in database
    * @tparam A - paginating object
    * @return Pagination[A] - allows get page with data
    */
  def make[A](find: (Int, Int) => Task[List[A]], count: () => Task[Int]): Pagination[A]
}

trait Pagination[A] {

  /**
    * @param number - page number
    * @param size - size of page
    * @return requested page
    */
  def getPage(number: Int, size: Int): Task[Page[A]]
}

case class Page[A](page: Int, totalPages: Int, pageSize: Int, data: List[A])

object Page {
  implicit val vehicleEncoder: Encoder[Page[Vehicle]] = deriveEncoder
  implicit val trackEncoder: Encoder[Page[Track]] = deriveEncoder
  implicit val trackerEncoder: Encoder[Page[Tracker]] = deriveEncoder
  implicit val userEncoder: Encoder[Page[User]] = deriveEncoder
  implicit val fleetEncoder: Encoder[Page[Fleet]] = deriveEncoder

  implicit val userDecoder: Decoder[Page[User]] = deriveDecoder
}

class DefaultPagination[A](find: (Int, Int) => Task[List[A]], count: () => Task[Int]) extends Pagination[A] {
  override def getPage(number: Int, size: Int): Task[Page[A]] =
    for {
      list <- find((number - 1) * size, size)
      count <- count()
    } yield new Page[A](number, max(count / size, 1), size, list)
}

object DefaultPaginationBuilder extends PaginationBuilder {
  override def make[A](find: (Int, Int) => Task[List[A]], count: () => Task[Int]): DefaultPagination[A] =
    new DefaultPagination[A](find, count)
}

object DefaultPagination {
  def apply[A](f: (Offset, Size) => Task[List[A]], count: () => Task[Int]): Pagination[A] =
    new DefaultPagination[A](f, count)

  type Offset = Int
  type Size = Int
}

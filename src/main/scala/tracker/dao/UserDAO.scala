package tracker.dao

import cats.implicits.catsSyntaxFlatMapOps
import doobie.implicits._
import doobie.implicits.javatime._
import doobie.util.transactor.Transactor
import org.log4s.{getLogger, Logger}
import tracker.User
import zio.Task
import zio.interop.catz._

trait UserDAO {
  def find(id: Long): Task[Option[User]]

  def findByUsername(username: String): Task[Option[User]]
}

class DefaultUserDAO(transactor: Transactor[Task], logger: Logger = getLogger) extends UserDAO {
  def find(id: Long): Task[Option[User]] = {
    Task {
      logger.debug(s"Selecting user with id: ${id}")
    } >> (sql"""SELECT id, name, created_at, deleted_at, password, username, roles FROM USER WHERE ID = $id""")
      .query[User]
      .option
      .transact(transactor)
  }

  def findByUsername(username: String): Task[Option[User]] = {
    Task {
      logger.debug(s"Selecting user username id: ${username}")
    } >> (sql"""SELECT id, name, created_at, deleted_at, password, username, roles FROM USER WHERE username = $username""")
      .query[User]
      .option
      .transact(transactor)
  }

}

object UserDAO {
  def apply(transactor: Transactor[Task]): UserDAO = new DefaultUserDAO(transactor)
}

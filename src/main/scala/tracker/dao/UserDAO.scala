package tracker.dao

import cats.implicits.catsSyntaxFlatMapOps
import doobie.implicits._
import doobie.implicits.javatime._
import doobie.util.transactor.Transactor
import doobie.Fragment
import slog4s.{Logger, LoggerFactory}
import tracker.User
import zio.Task
import zio.interop.catz._

trait UserDAO {
  def persist(user: User): Task[User]

  def update(user: User): Task[User]

  def find(id: Long): Task[Option[User]]

  def findAll(offset: Int, limit: Int): Task[List[User]]

  def findByUsername(username: String): Task[Option[User]]

  def count(): Task[Int]
}

class DefaultUserDAO(transactor: Transactor[Task], logger: Logger[Task]) extends UserDAO {
  def find(id: Long): Task[Option[User]] = {
    logger.info(
      s"Selecting user with id: ${id}"
    ) >> (sql"""SELECT id, name, created_at, deleted_at, password, username, roles FROM USER WHERE ID = $id""")
      .query[User]
      .option
      .transact(transactor)
  }

  override def findAll(offset: Int, limit: Int): Task[List[User]] = {
    logger.info("Selecting all users") >>
      findBy(Fragment.empty, offset, limit)
  }

  def findByUsername(username: String): Task[Option[User]] = {
    logger.debug(s"Selecting user username id: ${username}") >>
      findBy(fr"""WHERE USERNAME = $username""").map {
        case List() => None
        case user   => Some(user.head)
      }
  }

  def persist(user: User): Task[User] = {
    for {
      id <-
        sql"""INSERT INTO USER
         (name, created_at, deleted_at, password, username, roles) VALUES
         (${user.name}, ${user.createdAt}, ${user.deletedAt}, ${user.password}, ${user.username}, ${user.roles.toList})""".update
          .withUniqueGeneratedKeys[Long]("id")
          .transact(transactor)
      user <- find(id).map(_.getOrElse(throw new IllegalStateException("Could not find newly created entity!")))
    } yield user
  }

  def update(user: User): Task[User] = {
    for {
      id <-
        sql"""UPDATE USER SET
          name = ${user.name},
          created_at = ${user.createdAt},
          deleted_at = ${user.deletedAt},
          password = ${user.password},
          username = ${user.username},
          roles = ${user.roles.toList} WHERE id = ${user.id}""".update
          .withUniqueGeneratedKeys[Long]("id")
          .transact(transactor)
      user <- find(id).map(_.getOrElse(throw new IllegalStateException("Could not find newly created entity!")))
    } yield user
  }

  override def count(): Task[Int] = {
    sql"""SELECT COUNT(*) FROM USER"""
      .query[Int]
      .unique
      .transact(transactor)
  }

  private def findBy(fra: Fragment, offset: Int = 0, limit: Int = Int.MaxValue): Task[List[User]] =
    (sql"""SELECT ID, NAME, CREATED_AT, DELETED_AT, PASSWORD, USERNAME, ROLES FROM USER """
      ++ fra
      ++ sql"""ORDER BY NAME LIMIT $limit OFFSET $offset""")
      .query[User]
      .to[List]
      .transact(transactor)
}

object UserDAO {
  def apply(transactor: Transactor[Task], loggerFactory: LoggerFactory[Task]): UserDAO =
    new DefaultUserDAO(transactor, loggerFactory.make("user-dao"))
}

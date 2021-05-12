package tracker.dao

import cats.implicits.catsSyntaxFlatMapOps
import doobie.implicits._
import doobie.implicits.javatime._
import doobie.util.transactor.Transactor
import doobie.{ConnectionIO, Fragment}
import slog4s.{Logger, LoggerFactory}
import tracker.User
import zio.Task
import zio.interop.catz._

/**
  * Provides access and operations with User records in database.
  */
trait UserDAO {

  /**
    * Persist new user .
    *
    * If user is already exists, new one will be created with same data but with another ID. For update exiting user use `update` method.
    * @param user user to be saved (without unique identifier)
    * @return Newly inserted user with unique identifier
    */
  def persist(user: User): Task[User]

  /**
    * This method update all columns except password. If you want to update password, you need to use `updatePassword` method.
    *
    * @param user modified user (user is matched with the user in database by ID)
    * @return updated user from database
    */
  def update(user: User): Task[User]

  /**
    * @param id identifier of user
    * @param password new password to be persist to specified user
    * @return updated user with new password
    */
  def updatePassword(id: Long, password: String): Task[User]

  /**
    * Mark user as deleted, but the entity will be still save in database.
    * @param id identifier of user to be marked
    * @return user with updated deletedAt field
    */
  def markAsDeleted(id: Long): Task[User]

  /**
    * @param id identifier of user
    * @return Some[User] if user with specified identifier is persist in database, otherwise None
    */
  def find(id: Long): Task[Option[User]]

  /**
    * @param offset first `offset` users in result will be ignore
    * @param limit users after `offset` + `limit` in results will be ignored
    * @return "Page" of users
    */
  def findAll(offset: Int, limit: Int): Task[List[User]]

  /**
    * @param offset first `offset` users in result will be ignore
    * @param limit users after `offset` + `limit` in results will be ignored
    * @return "Page" of users not marked as deleted
    */
  def findAllActive(offset: Int, limit: Int): Task[List[User]]

  /**
    * @param username user's username
    * @return Some[User] if user with specified username is persist in database, otherwise None
    */
  def findByUsername(username: String): Task[Option[User]]

  /**
    * @param username user's username
    * @return Some[User] if user with specified username is persist in database and is not mark as deleted, otherwise None
    */
  def findActiveByUsername(username: String): Task[Option[User]]

  /**
    * @return count of users in database
    */
  def count(): Task[Int]

  /**
    * @return count of users in database that is not marked as deleted
    */
  def countActive(): Task[Int]
}

class DefaultUserDAO(transactor: Transactor[Task], logger: Logger[Task]) extends UserDAO {
  def update(user: User): Task[User] = {
    for {
      id <-
        sql"""UPDATE USER SET
          name = ${user.name},
          username = ${user.username},
          roles = ${user.roles.toList} WHERE id = ${user.id}""".update
          .withUniqueGeneratedKeys[Long]("id")
          .transact(transactor)
      user <- find(id).map(_.getOrElse(throw new IllegalStateException("Could not find newly created entity!")))
    } yield user
  }

  def updatePassword(id: Long, password: String): Task[User] = {
    for {
      id <-
        sql"""UPDATE USER SET PASSWORD = $password WHERE ID = $id""".update
          .withUniqueGeneratedKeys[Long]("id")
          .transact(transactor)
      user <- find(id).map(_.getOrElse(throw new IllegalStateException("Could not find newly created entity!")))
    } yield user
  }

  override def markAsDeleted(id: Long): Task[User] = {
    val transaction = for {
      _ <- sql"""UPDATE USER SET DELETED_AT = NOW(), PASSWORD = '' WHERE ID = $id""".update.run
      user <- findBy(fr"""WHERE ID = $id""", 0, Int.MaxValue)
    } yield user
    transaction.transact(transactor).map(_.head)
  }

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
      findBy(Fragment.empty, offset, limit).transact(transactor)
  }

  override def findAllActive(offset: Int, limit: Int): Task[List[User]] = {
    logger.info("Selecting all users") >>
      findBy(fr"""WHERE DELETED_AT IS NULL""", offset, limit).transact(transactor)
  }

  def findByUsername(username: String): Task[Option[User]] = {
    logger.debug(s"Selecting user: ${username}") >>
      findBy(fr"""WHERE USERNAME = $username""")
        .map {
          case List() => None
          case user   => Some(user.head)
        }
        .transact(transactor)
  }

  def findActiveByUsername(username: String): Task[Option[User]] = {
    logger.debug(s"Selecting active user: ${username}") >>
      findBy(fr"""WHERE USERNAME = $username AND DELETED_AT IS NULL""")
        .map {
          case List() => None
          case user   => Some(user.head)
        }
        .transact(transactor)
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

  override def count(): Task[Int] = {
    countWhere(Fragment.empty).transact(transactor)
  }

  override def countActive(): Task[Int] = {
    countWhere(fr"""WHERE DELETED_AT IS NULL""").transact(transactor)
  }

  private def findBy(fra: Fragment, offset: Int = 0, limit: Int = Int.MaxValue): ConnectionIO[List[User]] =
    (sql"""SELECT ID, NAME, CREATED_AT, DELETED_AT, PASSWORD, USERNAME, ROLES FROM USER """
      ++ fra
      ++ sql"""ORDER BY NAME LIMIT $limit OFFSET $offset""")
      .query[User]
      .to[List]

  private def countWhere(fra: Fragment): ConnectionIO[Int] =
    (sql"""SELECT COUNT(*) FROM USER """
      ++ fra)
      .query[Int]
      .unique
}

object UserDAO {
  def apply(transactor: Transactor[Task], loggerFactory: LoggerFactory[Task]): UserDAO =
    new DefaultUserDAO(transactor, loggerFactory.make("user-dao"))
}

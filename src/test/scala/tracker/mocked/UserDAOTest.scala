package tracker.mocked

import tracker.User
import tracker.dao.UserDAO
import zio.Task

class UserDAOTest(var users: List[User]) extends UserDAO {

  private var maxId: Long = 0

  override def find(id: Long): Task[Option[User]] =
    Task.effect(users.find(u => u.id.get == id))

  override def findByUsername(username: String): Task[Option[User]] =
    Task.succeed {
      users.find(u => u.username == username)
    }

  override def persist(user: User): Task[User] =
    Task.effect {
      maxId = maxId + 1
      val usr = User(
        Some(maxId),
        user.name,
        user.createdAt,
        user.deletedAt,
        user.password,
        user.username,
        user.roles
      )
      users = users.appended(usr)
      usr
    }

  override def update(user: User): Task[User] =
    Task.effect {
      val size = users.size
      users = users.filter(p => p.id != user.id)
      if (size == users.size) {
        throw new IllegalStateException()
      } else {
        users = users.appended(user)
        user
      }
    }

  override def findAll(offset: Int, limit: Int): Task[List[User]] =
    Task.effect(users)

  override def count(): Task[Int] =
    Task.effect(users.length)

  override def findAllActive(offset: Int, limit: Int): Task[List[User]] =
    Task.effect(users.filter(u => u.deletedAt.isEmpty))

  override def updatePassword(id: Long, password: String): Task[User] =
    throw new NotImplementedError("User updating is not implemented for testing purposes.")

  override def markAsDeleted(id: Long): Task[User] =
    throw new NotImplementedError("User updating is not implemented for testing purposes.")

  override def findActiveByUsername(username: String): Task[Option[User]] =
    Task.effect(users.find(user => user.username == username && user.deletedAt.isDefined))

  override def countActive(): Task[Int] =
    Task {
      val activeUsers = users.filter(u => u.deletedAt.isEmpty)
      activeUsers.length
    }

}

object UserDAOTest {
  def apply(users: List[User]): UserDAOTest = {
    new UserDAOTest(users)
  }
}

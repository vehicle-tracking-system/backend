package tracker.mocked

import tracker.User
import tracker.dao.UserDAO
import zio.Task

case class UserDAOTest(var store: Store) extends UserDAO with DAOMock[User] {

  override def find(id: Long): Task[Option[User]] =
    Task.effect(store.users.find(u => u.id.get == id))

  override def findByUsername(username: String): Task[Option[User]] =
    Task.succeed {
      store.users.find(u => u.username == username)
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
      store.users = store.users.appended(usr)
      usr
    }

  override def update(user: User): Task[User] =
    Task.effect {
      val size = store.users.size
      store.users = store.users.filter(p => p.id != user.id)
      if (size == store.users.size) {
        throw new IllegalStateException()
      } else {
        store.users = store.users.appended(user)
        user
      }
    }

  override def findAll(offset: Int, limit: Int): Task[List[User]] =
    Task.effect(store.users)

  override def count(): Task[Int] =
    Task.effect(store.users.length)

  override def findAllActive(offset: Int, limit: Int): Task[List[User]] =
    Task.effect(store.users.filter(u => u.deletedAt.isEmpty))

  override def updatePassword(id: Long, password: String): Task[User] =
    throw new NotImplementedError("User updating is not implemented for testing purposes.")

  override def markAsDeleted(id: Long): Task[User] =
    throw new NotImplementedError("User updating is not implemented for testing purposes.")

  override def findActiveByUsername(username: String): Task[Option[User]] =
    Task.effect(store.users.find(user => user.username == username && user.deletedAt.isDefined))

  override def countActive(): Task[Int] =
    Task {
      val activeUsers = store.users.filter(u => u.deletedAt.isEmpty)
      activeUsers.length
    }

  override var maxId: Long = 0
}

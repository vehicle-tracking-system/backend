package tracker.service

import io.circe.syntax.EncoderOps
import org.scalatest.funsuite.AsyncFunSuite
import tracker.dao.UserDAO
import tracker.{Roles, User}
import zio.Task

import java.time.{ZoneId, ZonedDateTime}

class UserServiceTest extends AsyncFunSuite {

  class UserDAOTest(users: List[User]) extends UserDAO {

    override def find(id: Long): Task[Option[User]] =
      Task.effect {
        users.find(u => u.id.get == id)
      }

    override def findByUsername(username: String): Task[Option[User]] =
      Task.succeed {
        users.find(u => u.username == username)
      }

    override def persist(user: User): Task[User] =
      throw new NotImplementedError("User persisting is not implemented for testing purposes.")

    override def update(user: User): Task[User] =
      throw new NotImplementedError("User updating is not implemented for testing purposes.")

    override def findAll(offset: Int, limit: Int): Task[List[User]] =
      throw new NotImplementedError("User updating is not implemented for testing purposes.")

    override def count(): Task[Int] = throw new NotImplementedError("User updating is not implemented for testing purposes.")

    override def findAllActive(offset: Int, limit: Int): Task[List[User]] =
      throw new NotImplementedError("User updating is not implemented for testing purposes.")
  }

  object UserDAOTest {
    def apply(users: List[User]): UserDAOTest = {
      new UserDAOTest(users)
    }
  }

  test("Parse User to JSON") {
    val user = User(
      Some(1),
      "Karel",
      ZonedDateTime.of(2021, 2, 25, 0, 0, 0, 0, ZoneId.of("Europe/Prague")),
      None,
      "10000:WS4enQfVmeoAtnCXUtRqCdHu+A1Tv7i1QReXH/bj5pU=:aumeogsLFIqGr9HrfvxdMw==",
      "karel",
      Set(Roles.Reader)
    )

    assert(
      user.asJson.noSpaces == """{"id":1,"name":"Karel","createdAt":"2021-02-25T00:00:00+01:00[Europe/Prague]","deletedAt":null,"password":"10000:WS4enQfVmeoAtnCXUtRqCdHu+A1Tv7i1QReXH/bj5pU=:aumeogsLFIqGr9HrfvxdMw==","username":"karel","roles":["READER"]}"""
    )
  }

  test("Parse deleted User to JSON") {
    val user = User(
      Some(1),
      "Karel",
      ZonedDateTime.of(2021, 2, 25, 0, 0, 0, 0, ZoneId.of("Europe/Prague")),
      Some(ZonedDateTime.of(2222, 2, 25, 5, 0, 25, 0, ZoneId.of("Europe/Prague"))),
      "10000:WS4enQfVmeoAtnCXUtRqCdHu+A1Tv7i1QReXH/bj5pU=:aumeogsLFIqGr9HrfvxdMw==",
      "karel",
      Set(Roles.Editor)
    )

    assert(
      user.asJson.noSpaces == """{"id":1,"name":"Karel","createdAt":"2021-02-25T00:00:00+01:00[Europe/Prague]","deletedAt":"2222-02-25T05:00:25+01:00[Europe/Prague]","password":"10000:WS4enQfVmeoAtnCXUtRqCdHu+A1Tv7i1QReXH/bj5pU=:aumeogsLFIqGr9HrfvxdMw==","username":"karel","roles":["EDITOR"]}"""
    )
  }

}

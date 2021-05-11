package tracker.service

import fs2.text.utf8Decode
import io.circe.Json
import io.circe.syntax.EncoderOps
import org.http4s.{AuthedRequest, Method, Request, Status}
import org.http4s.circe.{jsonDecoder, jsonEncoder}
import org.http4s.implicits.{http4sKleisliResponseSyntaxOptionT, http4sLiteralsSyntax}
import pdi.jwt.JwtAlgorithm
import slog4s.slf4j.Slf4jFactory
import tracker.{Page, Roles, User}
import tracker.config.JwtConfig
import tracker.dao.UserDAO
import tracker.mocked.{TestClock, UserDAOTest}
import tracker.module.routes.UserRoutes
import tracker.utils.PasswordUtility
import zio.Task
import zio.interop.catz.taskConcurrentInstance
import zio.test._
import zio.test.Assertion._

import java.time.{Duration, Instant, ZoneId, ZonedDateTime}

object UserAPITest extends DefaultRunnableSpec {
  val startTime: ZonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(0), ZoneId.of("Europe/Prague"))
  val expectedJson: Json = Json.obj(
    ("name", Json.fromString("johndoe")),
    ("age", Json.fromBigInt(42))
  )

  override def spec: Spec[Any, TestFailure[Throwable], TestSuccess] = {
    val adminUserJson: Json = Json.obj(
      ("id", Json.fromBigInt(1)),
      ("name", Json.fromString("Karel")),
      ("createdAt", Json.fromString("2021-02-25T00:00:00+01:00[Europe/Prague]")),
      ("deletedAt", Json.Null),
      ("username", Json.fromString("karel")),
      ("roles", Json.arr(Json.fromString("READER"), Json.fromString("ADMIN")))
    )

    val adminUser: User = User(
      Some(1),
      "Karel",
      ZonedDateTime.of(2021, 2, 25, 0, 0, 0, 0, ZoneId.of("Europe/Prague")),
      None,
      "10000:WS4enQfVmeoAtnCXUtRqCdHu+A1Tv7i1QReXH/bj5pU=:aumeogsLFIqGr9HrfvxdMw==",
      "karel",
      Set(Roles.Reader, Roles.Admin)
    )

    val normalUser: User = User(
      Some(2),
      "John Doe",
      ZonedDateTime.of(2221, 2, 5, 0, 0, 0, 0, ZoneId.of("Europe/Prague")),
      None,
      "password",
      "john",
      Set(Roles.Reader, Roles.User)
    )

    suite("User routes suite")(
      testM("Insert user") {
        val dao: UserDAO = UserDAOTest(List.empty)
        for {
          _ <- dao.persist(adminUser)
          cnt <- dao.count()
        } yield assert(cnt)(equalTo(1))
      },
      testM("Read users without permissions") {
        val dao: UserDAO = UserDAOTest(List.empty)
        val service: UserRoutes = new UserRoutes(
          UserService(
            dao,
            JwtConfig("secret", 100, JwtAlgorithm.HMD5),
            Slf4jFactory[Task].withoutContext.loggerFactory
          )
        )
        for {
          response <- service.routes.orNotFound.run(
            AuthedRequest(normalUser, Request(method = Method.GET, uri = uri"?id=1"))
          )
        } yield assert(response.status)(equalTo(Status.Forbidden))
      },
      testM("Query existing users") {
        val dao: UserDAO = UserDAOTest(List.empty)
        val service: UserRoutes = new UserRoutes(
          UserService(dao, JwtConfig("secret", 100, JwtAlgorithm.HMD5), Slf4jFactory[Task].withoutContext.loggerFactory)
        )
        for {
          _ <- dao.persist(adminUser)
          response <- service.routes.orNotFound.run(
            AuthedRequest(adminUser, Request(method = Method.GET, uri = uri"?id=1"))
          )
          body <- response.as[Json]
        } yield assert(body)(equalTo(adminUserJson)) && assert(response.status)(equalTo(Status.Ok))
      },
      testM("Query non existing user") {
        val dao: UserDAO = UserDAOTest(List.empty)
        val service: UserRoutes = new UserRoutes(
          UserService(dao, JwtConfig("secret", 100, JwtAlgorithm.HMD5), Slf4jFactory[Task].withoutContext.loggerFactory)
        )
        for {
          _ <- dao.persist(adminUser)
          response <- service.routes.orNotFound.run(
            AuthedRequest(adminUser, Request(method = Method.GET, uri = uri"?id=999"))
          )
          body <- response.body.through(utf8Decode).compile.string
        } yield assert(body)(equalTo(""""User not found"""")) && assert(response.status)(equalTo(Status.NotFound))
      },
      testM("Query list of users") {
        val dao: UserDAO = UserDAOTest(List.empty)
        val service: UserRoutes = new UserRoutes(
          UserService(dao, JwtConfig("secret", 100, JwtAlgorithm.HMD5), Slf4jFactory[Task].withoutContext.loggerFactory)
        )
        val expectedList = Page[User](1, 1, Int.MaxValue, List(adminUser, normalUser)).asJson
        for {
          _ <- dao.persist(adminUser)
          _ <- dao.persist(normalUser)
          response <- service.routes.orNotFound.run(
            AuthedRequest(adminUser, Request(method = Method.GET, uri = uri"/list"))
          )
          body <- response.as[Json]
        } yield assert(body)(equalTo(expectedList))
      },
      testM("Add user") {
        val dao: UserDAO = UserDAOTest(List.empty)
        val clock: TestClock = new TestClock(startTime, Duration.ofSeconds(1))
        val service: UserRoutes = new UserRoutes(
          UserService(
            dao,
            JwtConfig("secret", 100, JwtAlgorithm.HMD5),
            Slf4jFactory[Task].withoutContext.loggerFactory,
            clock
          )
        )

        val newUserJson = Json.obj(
          ("name", Json.fromString("John Doe")),
          ("deletedAt", Json.Null),
          ("username", Json.fromString("jdoe")),
          ("roles", Json.arr(Json.fromString("USER"))),
          ("password", Json.fromString("password"))
        )

        val expectedUser = User(Some(2), "John Doe", clock.now(), None, PasswordUtility.hashPassword("password"), "jdoe", Set(Roles.User))

        for {
          _ <- dao.persist(adminUser)
          response <- service.routes.orNotFound.run(
            AuthedRequest(adminUser, Request(method = Method.POST, uri = uri"/new").withEntity(newUserJson.asJson))
          )
          body <- response.as[Json]
        } yield assert(body)(equalTo(expectedUser.asJson))
      }
    )
  }
}

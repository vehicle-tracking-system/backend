package tracker.service

import cats.effect.Resource
import com.avast.sst.doobie._
import tracker.config.Configuration
import com.avast.sst.jvm.execution.ConfigurableThreadFactory.Config
import com.avast.sst.jvm.execution.{ConfigurableThreadFactory, ExecutorModule}
import com.avast.sst.pureconfig.PureConfigModule
import scala.concurrent.ExecutionContext
import zio._
import zio.interop.catz._

import org.scalatest.funsuite.AsyncFunSuite
import com.dimafeng.testcontainers._

class RandomServiceTest extends AsyncFunSuite with ForAllTestContainer {

  // Docker must be installed to be able to run this
  override val container = PostgreSQLContainer()

  test("RandomService should return") {
    val runtime = Runtime.default

    val test: Resource[Task, RandomService] = for {
      configuration <- Resource.liftF(PureConfigModule.makeOrRaise[Task, Configuration])
      executorModule <- ExecutorModule.makeFromExecutionContext[Task](runtime.platform.executor.asEC)
      boundedConnectExecutionContext <-
        executorModule
          .makeThreadPoolExecutor(configuration.boundedConnectExecutor, new ConfigurableThreadFactory(Config(Some("hikari-connect-%02d"))))
          .map(ExecutionContext.fromExecutorService)
      doobieTransactor <- DoobieHikariModule.make[Task](
        overrideDbConfiguration(configuration.database),
        boundedConnectExecutionContext,
        executorModule.blocker
      )
      executorModule <- ExecutorModule.makeFromExecutionContext[Task](runtime.platform.executor.asEC)
      randomService = RandomService(doobieTransactor)
    } yield randomService

    val task = test
      .use { randomService =>
        randomService.randomNumber.map(n => assert(n >= 0.0 && n <= 1.0))
      }

    runtime.unsafeRunToFuture(task)
  }

  private def overrideDbConfiguration(config: DoobieHikariConfig): DoobieHikariConfig =
    config.copy(url = container.jdbcUrl, username = container.username, password = container.password)

}

package tracker

import java.util.concurrent.TimeUnit

import cats.effect.{Clock, Resource}
import com.avast.sst.bundle.ZioServerApp
import com.avast.sst.doobie.DoobieHikariModule
import com.avast.sst.http4s.client.Http4sBlazeClientModule
import com.avast.sst.http4s.client.monix.catnap.Http4sClientCircuitBreakerModule
import com.avast.sst.http4s.server.Http4sBlazeServerModule
import com.avast.sst.http4s.server.micrometer.MicrometerHttp4sServerMetricsModule
import com.avast.sst.jvm.execution.ConfigurableThreadFactory.Config
import com.avast.sst.jvm.execution.{ConfigurableThreadFactory, ExecutorModule}
import com.avast.sst.jvm.micrometer.MicrometerJvmModule
import com.avast.sst.jvm.system.console.{Console, ConsoleModule}
import com.avast.sst.micrometer.jmx.MicrometerJmxModule
import com.avast.sst.monix.catnap.CircuitBreakerModule
import com.avast.sst.monix.catnap.CircuitBreakerModule.{withLogging, withMetrics}
import com.avast.sst.monix.catnap.micrometer.MicrometerCircuitBreakerMetricsModule
import com.avast.sst.pureconfig.PureConfigModule
import com.zaxxer.hikari.metrics.micrometer.MicrometerMetricsTrackerFactory
import org.http4s.server.Server
import tracker.config.Configuration
import tracker.dao.UserDAO
import tracker.module.Http4sRoutingModule
import tracker.service.UserService
import zio.Task
import zio.interop.catz._
import zio.interop.catz.implicits._

import scala.concurrent.ExecutionContext

object Main extends ZioServerApp {

  def program: Resource[Task, Server[Task]] = {
    for {
      configuration <- Resource.liftF(PureConfigModule.makeOrRaise[Task, Configuration])
      executorModule <- ExecutorModule.makeFromExecutionContext[Task](runtime.platform.executor.asEC)
      clock = Clock.create[Task]
      currentTime <- Resource.liftF(clock.realTime(TimeUnit.MILLISECONDS))
      console <- Resource.pure[Task, Console[Task]](ConsoleModule.make[Task])
      _ <- Resource.liftF(
        console.printLine(s"The current Unix epoch time is $currentTime. This system has ${executorModule.numOfCpus} CPUs.")
      )
      meterRegistry <- MicrometerJmxModule.make[Task](configuration.jmx)
      _ <- Resource.liftF(MicrometerJvmModule.make[Task](meterRegistry))
      serverMetricsModule <- Resource.liftF(MicrometerHttp4sServerMetricsModule.make[Task](meterRegistry, clock))
      boundedConnectExecutionContext <-
        executorModule
          .makeThreadPoolExecutor(
            configuration.boundedConnectExecutor,
            new ConfigurableThreadFactory(Config(Some("hikari-connect-%02d")))
          )
          .map(ExecutionContext.fromExecutorService)
      hikariMetricsFactory = new MicrometerMetricsTrackerFactory(meterRegistry)
      doobieTransactor <-
        DoobieHikariModule
          .make[Task](configuration.database, boundedConnectExecutionContext, executorModule.blocker, Some(hikariMetricsFactory))

      userDAO = UserDAO(doobieTransactor)

      userService = UserService(userDAO, configuration.jwt)

      httpClient <- Http4sBlazeClientModule.make[Task](configuration.client, executorModule.executionContext)
      circuitBreakerMetrics <- Resource.liftF(MicrometerCircuitBreakerMetricsModule.make[Task]("test-http-client", meterRegistry))
      circuitBreaker <- Resource.liftF(CircuitBreakerModule[Task].make(configuration.circuitBreaker, clock))
      enrichedCircuitBreaker = withLogging("test-http-client", withMetrics(circuitBreakerMetrics, circuitBreaker))
      client = Http4sClientCircuitBreakerModule.make[Task](httpClient, enrichedCircuitBreaker)
      routingModule = new Http4sRoutingModule(userDAO, userService, client, serverMetricsModule, configuration)
      server <- Http4sBlazeServerModule.make[Task](configuration.server, routingModule.router, executorModule.executionContext)
    } yield server
  }

}

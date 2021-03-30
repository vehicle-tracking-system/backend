package tracker

import cats.effect.{Clock, Resource}
import com.avast.sst.doobie.DoobieHikariModule
import com.avast.sst.http4s.client.Http4sBlazeClientModule
import com.avast.sst.http4s.client.monix.catnap.Http4sClientCircuitBreakerModule
import com.avast.sst.http4s.server.Http4sBlazeServerModule
import com.avast.sst.http4s.server.micrometer.MicrometerHttp4sServerMetricsModule
import com.avast.sst.jvm.execution.{ConfigurableThreadFactory, ExecutorModule}
import com.avast.sst.jvm.execution.ConfigurableThreadFactory.Config
import com.avast.sst.jvm.micrometer.MicrometerJvmModule
import com.avast.sst.jvm.system.console.{Console, ConsoleModule}
import com.avast.sst.micrometer.jmx.MicrometerJmxModule
import com.avast.sst.monix.catnap.CircuitBreakerModule
import com.avast.sst.monix.catnap.CircuitBreakerModule.{withLogging, withMetrics}
import com.avast.sst.monix.catnap.micrometer.MicrometerCircuitBreakerMetricsModule
import com.avast.sst.pureconfig.PureConfigModule
import com.zaxxer.hikari.metrics.micrometer.MicrometerMetricsTrackerFactory
import fs2.concurrent.Topic
import org.http4s.server.Server
import org.slf4j.LoggerFactory
import slog4s.slf4j.Slf4jFactory
import tracker.config.Configuration
import tracker.dao._
import tracker.module.{Http4sRoutingModule, MqttModule}
import tracker.security.DefaultAccessTokenParser
import tracker.service._
import zio._
import zio.interop.catz._
import zio.interop.catz.implicits._

import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext

object Main extends CatsApp {

  case class MainResources(server: Resource[Task, Server[Task]], mqtt: Resource[Task, MqttModule.type])

  private val logger = LoggerFactory.getLogger(this.getClass)

  override def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] = {
    program
      .use { resources =>
        for {
          _ <- UIO.effectTotal(logger.info(s"Server started @ ${resources._1.address.getHostString}:${resources._1.address.getPort}"))
          _ <- resources._2.make().use(_ => Task.unit)
        } yield resources
      }
      .fold(
        ex => {
          logger.error("Server initialization failed!", ex)
          ExitCode.failure
        },
        _ => ExitCode.success
      )
  }

  def program: Resource[Task, (Server[Task], MqttModule)] = {
    for {
      configuration <- Resource.liftF(PureConfigModule.makeOrRaise[Task, Configuration])
      executorModule <- ExecutorModule.makeFromExecutionContext[Task](runtime.platform.executor.asEC)
      clock = Clock.create[Task]
      currentTime <- Resource.liftF(clock.realTime(TimeUnit.MILLISECONDS))
      console <- Resource.pure[Task, Console[Task]](ConsoleModule.make[Task])
      _ <- Resource.liftF(
        console.printLine(
          s"The current Unix epoch time is $currentTime. This system has ${executorModule.numOfCpus} CPUs."
        )
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
          .make[Task](
            configuration.database,
            boundedConnectExecutionContext,
            executorModule.blocker,
            Some(hikariMetricsFactory)
          )

      loggerFactory = Slf4jFactory[Task].withoutContext.loggerFactory
      topic <- Resource.liftF(Topic[Task, WebSocketMessage](WebSocketMessage.heartbeat))

      userDAO = UserDAO(doobieTransactor, loggerFactory)
      fleetDAO = FleetDAO(doobieTransactor)
      vehicleDAO = VehicleDAO(doobieTransactor)
      vehicleFleetDAO = VehicleFleetDAO(doobieTransactor)
      positionDAO = PositionDAO(doobieTransactor)
      trackDAO = TrackDAO(doobieTransactor)
      trackerDAO = TrackerDAO(doobieTransactor)

      userService = UserService(userDAO, configuration.jwt, loggerFactory)
      fleetService = FleetService(fleetDAO)
      vehicleService = VehicleService(vehicleDAO, vehicleFleetDAO)
      positionService = PositionService(positionDAO, loggerFactory)
      trackService = TrackService(trackDAO)
      trackerService = TrackerService(trackerDAO, userDAO, configuration.jwt, loggerFactory)
      mqttService = MqttService(loggerFactory, trackerService, positionService, topic, DefaultAccessTokenParser, configuration)

      httpClient <- Http4sBlazeClientModule.make[Task](configuration.client, executorModule.executionContext)
      circuitBreakerMetrics <- Resource.liftF(MicrometerCircuitBreakerMetricsModule.make[Task]("test-http-client", meterRegistry))
      circuitBreaker <- Resource.liftF(CircuitBreakerModule[Task].make(configuration.circuitBreaker, clock))
      enrichedCircuitBreaker = withLogging("test-http-client", withMetrics(circuitBreakerMetrics, circuitBreaker))
      client = Http4sClientCircuitBreakerModule.make[Task](httpClient, enrichedCircuitBreaker)
      routingModule = new Http4sRoutingModule(
        topic,
        userService,
        vehicleService,
        fleetService,
        positionService,
        trackService,
        trackerService,
        loggerFactory,
        client,
        serverMetricsModule,
        configuration
      )
      mqtt = new MqttModule(mqttService.processMessage, configuration, loggerFactory.make("mqtt-module"))
      server <- Http4sBlazeServerModule.make[Task](configuration.server, routingModule.router, executorModule.executionContext)
    } yield (server, mqtt)
  }

}

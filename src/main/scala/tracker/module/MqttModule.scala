package tracker.module

import cats.effect.Resource
import cats.implicits._
import fs2.concurrent.SignallingRef
import net.sigusr.mqtt.api._
import net.sigusr.mqtt.api.ConnectionState.{Connected, Connecting, Disconnected, Error, SessionStarted}
import net.sigusr.mqtt.api.Errors.{ConnectionFailure, ProtocolError}
import net.sigusr.mqtt.api.QualityOfService.AtLeastOnce
import net.sigusr.mqtt.api.RetryConfig.Custom
import retry.RetryPolicies
import slog4s.Logger
import tracker.config.Configuration
import zio.Task
import zio.interop.catz._
import zio.interop.catz.implicits._

import scala.concurrent.duration._

/**
  * Inspired by: https://github.com/alarm-garage/alarm-garage-server/blob/4b05e4d51b9204ef3762a1afba86c37dfc6755a8/src/main/scala/cz/jenda/alarm/garage/MqttModule.scala
  */
object MqttModule {
  def make(processMessage: Message => Task[Unit], config: Configuration, logger: Logger[Task]): Resource[Task, MqttModule.type] = {

    val retryConfig: Custom[Task] = Custom[Task] {
      RetryPolicies
        .limitRetries[Task](config.mqtt.connectionRetries)
        .join(RetryPolicies.fullJitter[Task](2.seconds))
    }

    val transportConfig: TransportConfig[Task] = TransportConfig[Task](
      config.mqtt.host,
      config.mqtt.port,
      tlsConfig = if (config.mqtt.ssl) Some(TLSConfig(TLSContextKind.System)) else None,
      retryConfig = retryConfig,
      traceMessages = false
    )

    val sessionConfig: SessionConfig = SessionConfig(
      clientId = config.mqtt.subscriberName,
      cleanSession = false,
      user = config.mqtt.user,
      password = config.mqtt.password,
      keepAlive = config.mqtt.keepAliveSecs
    )

    val topics = Vector(config.mqtt.topic -> AtLeastOnce)

    Session[Task](transportConfig, sessionConfig).flatMap { session =>
      Resource(for {
        _ <- logger.debug(s"Initializing MQTT connection to ${config.mqtt.host}:${config.mqtt.port}")
        stopSignal <- SignallingRef[Task, Boolean](false)
        startedResult <- SignallingRef[Task, Option[Option[Throwable]]](None) // isStarted[wasError]

        sessionStatusStream =
          session.state.discrete
            .evalTap(logSessionStatus(logger, startedResult))
            .evalTap(onSessionError)

        subscriptionStream = fs2.Stream.eval(session.subscribe(topics)) *> session.messages().evalMap(processMessage)

        waitForStart =
          startedResult.continuous.unNone
            .take(1)
            .flatMap[Task, Unit] {
              case None    => fs2.Stream.empty
              case Some(e) => fs2.Stream.raiseError[Task](e)
            }
            .compile
            .drain

        _ <- sessionStatusStream.concurrently(subscriptionStream).interruptWhen(stopSignal).compile.drain.fork
        _ <- waitForStart
      } yield {

        // here we are in Resource; `stopSignal.set(true)` will interrupt all streams above and end the processing
        (MqttModule, logger.debug("Shutting down MQTT connection") *> stopSignal.set(true))
      })
    }
  }

  private def logSessionStatus(
      logger: Logger[Task],
      started: SignallingRef[Task, Option[Option[Throwable]]]
  ): ConnectionState => Task[Unit] = {
    case Error(e @ ConnectionFailure(reason)) => started.set(Some(Some(e))) *> logger.error(reason.show)
    case Error(ProtocolError)                 => logger.error("Protocol error")
    case Disconnected                         => started.get.flatMap(s => logger.warn("Transport disconnected").when(s.nonEmpty))
    case Connecting(nextDelay, retriesSoFar)  => logger.warn(s"Transport connecting. $retriesSoFar attempt(s) so far, next in $nextDelay")
    case Connected                            => logger.info("Transport connected")
    case SessionStarted                       => started.set(Some(None)) *> logger.info("Session started")
  }

  private def onSessionError: ConnectionState => Task[Unit] = {
    case Error(e) => Task.fail(e)
    case _        => Task.unit
  }
}

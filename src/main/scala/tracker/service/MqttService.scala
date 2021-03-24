package tracker.service

import net.sigusr.mqtt.api.Message
import protocol.tracker._
import slog4s.{Logger, LoggerFactory}
import tracker.config.Configuration
import tracker.security.AccessTokenParser
import tracker.Position
import zio.{IO, Task}

import java.time.{Instant, ZoneId, ZonedDateTime}

class MqttService(logger: Logger[Task], positionService: PositionService, tokenParser: AccessTokenParser, config: Configuration) {
  def processMessage(message: Message): Task[Unit] = {
    val processed = for {
      _ <- logger.debug("New message income throw MQTT")
      report <- Task(Report.parseFrom(message.payload.toArray))
      token <- IO.fromEither(tokenParser.parseAccessToken(report.token, config.jwt.secret)).mapError(e => s"Token issue: $e")
      _ <- logger.debug(s"token: ${token.raw.value} - ${report.positions.head.latitude} ${report.positions.head.longitude}")
      newPosition <- handleNewPosition(report)
    } yield newPosition

    processed.either.flatMap {
      _.fold(
        e => logger.error(s"MQTT message error: ${e}"),
        cnt => logger.debug(s"MQTT message successfully proceeded. $cnt new position inserted.")
      )
    }
  }

  private def handleNewPosition(report: Report): Task[Int] = {
    positionService.persist(
      report.positions
        .map(tp =>
          Position(
            None,
            tp.vehicleId,
            Some(tp.track),
            tp.speed,
            tp.latitude,
            tp.longitude,
            ZonedDateTime.ofInstant(Instant.ofEpochSecond(tp.timestamp), ZoneId.of("Europe/Prague"))
          )
        )
        .toList
    )
  }
}

object MqttService {
  def apply(
      loggerFactory: LoggerFactory[Task],
      positionService: PositionService,
      tokenParser: AccessTokenParser,
      configuration: Configuration
  ): MqttService =
    new MqttService(loggerFactory.make("mqtt-service"), positionService, tokenParser, configuration)
}

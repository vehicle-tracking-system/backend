package tracker.service

import fs2.concurrent.Topic
import net.sigusr.mqtt.api.Message
import protocol.tracker._
import slog4s.{Logger, LoggerFactory}
import tracker.{Position, Role, Roles, WebSocketMessage}
import tracker.config.Configuration
import tracker.security.{AccessToken, AccessTokenParser}
import zio.{IO, Task}

import java.time.{Instant, ZoneId, ZonedDateTime}

class MqttService(
    logger: Logger[Task],
    trackerService: TrackerService,
    positionService: PositionService,
    topic: Topic[Task, WebSocketMessage],
    tokenParser: AccessTokenParser,
    config: Configuration
) {
  def processMessage(message: Message): Task[Unit] = {
    val processed = for {
      _ <- logger.debug("New message income throw MQTT")
      report <- Task(Report.parseFrom(message.payload.toArray))
      token <- IO.fromEither(tokenParser.parseAccessToken(report.token, config.jwt.secret)).mapError(e => s"Token issue: $e")
      tracker <-
        trackerService.getByToken(report.token).map(_.getOrElse(throw new IllegalStateException("Could not find tracker which sent new positions!")))
      _ <- logger.debug(
        s"token: ${token.raw.value
          .slice(0, 10)}... vehicleId: ${report.positions.head.vehicleId} - ${report.positions.head.latitude} ${report.positions.head.longitude}"
      )
      positionRequest <- IO.fromEither(handleNewPosition(token, report)).mapError(e => s"Handling new position error: $e")
      positionSaved <- positionRequest
      optLastPosition <- positionService.getLastVehiclePosition(tracker.tracker.vehicleId)
      lastPosition <- IO.fromEither(optLastPosition.toRight("Last position not found"))
      _ <- topic.publish1(WebSocketMessage.position(lastPosition, report.isMoving))
    } yield positionSaved

    processed.either.flatMap {
      _.fold(
        e => logger.error(s"MQTT message error: ${e}"),
        cnt => logger.debug(s"MQTT message successfully proceeded. $cnt new position inserted.")
      )
    }
  }

  private def handleNewPosition(token: AccessToken, report: Report): Either[String, Task[Int]] = {
    if (token.clientRoles.contains(Role.toString(Roles.Tracker))) {
      val positions = report.positions
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
      Right(positionService.persist(positions))
    } else {
      Left("Tracker does not have required role")
    }
  }
}

object MqttService {
  def apply(
      loggerFactory: LoggerFactory[Task],
      trackerService: TrackerService,
      positionService: PositionService,
      topic: Topic[Task, WebSocketMessage],
      tokenParser: AccessTokenParser,
      configuration: Configuration
  ): MqttService =
    new MqttService(loggerFactory.make("mqtt-service"), trackerService, positionService, topic, tokenParser, configuration)
}

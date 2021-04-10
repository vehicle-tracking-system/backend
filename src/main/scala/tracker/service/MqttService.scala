package tracker.service

import com.google.protobuf.ByteString
import fs2.concurrent.Topic
import net.sigusr.mqtt.api.Message
import protocol.tracker._
import slog4s.{Logger, LoggerFactory}
import tracker.{Position, PositionsRequest, Role, Roles, WebSocketMessage}
import tracker.config.Configuration
import tracker.security.{AccessToken, AccessTokenParser}
import zio.{IO, Task}

import java.util.Base64
import java.time.{Instant, ZoneId, ZonedDateTime}
import scala.concurrent.duration.{Duration, DurationLong}

class MqttService(
    logger: Logger[Task],
    trackerService: TrackerService,
    positionService: PositionService,
    trackService: TrackService,
    topic: Topic[Task, WebSocketMessage],
    tokenParser: AccessTokenParser,
    config: Configuration
) {
  def processMessage(message: Message): Task[Unit] = {
    val processed = for {
      _ <- logger.debug("New message income throw MQTT")
      report <- Task(Report.parseFrom(message.payload.toArray))
      sessionId = Base64.getEncoder.encodeToString(report.sessionId.toByteArray)
      token <- IO.fromEither(tokenParser.parseAccessToken(report.token, config.jwt.secret)).mapError(e => s"Token issue: $e")
      reportLastPosition = report.positions.last
      _ <-
        trackerService
          .verifyAccessToken(report.token)
          .map(valid => if (!valid) throw new IllegalStateException("Could not find tracker which sent new positions!"))
      _ <- logger.debug(
        s"token: ${token.raw.value
          .slice(0, 10)}... vehicleId: ${report.vehicleId} - ${reportLastPosition.latitude} ${reportLastPosition.longitude}"
      )
      optLastPosition <- positionService.getLastVehiclePosition(report.vehicleId)
      _ <- logger.debug(s"Last position of vehicle ${report.vehicleId} is $optLastPosition")
      timeFromLastPosition = reportLastPosition.timestamp.seconds - optLastPosition.fold(Duration.Zero)(lp => lp.timestamp.toEpochSecond.seconds)
      _ <- logger.debug(s"Time from last position is $timeFromLastPosition seconds")
      isInSameSession = optLastPosition.exists(_.sessionId == sessionId)
      trackId <-
        if (timeFromLastPosition > config.mqtt.newTrackThreshold && !isInSameSession) {
          trackService
            .persist(report.vehicleId, ZonedDateTime.ofInstant(Instant.ofEpochSecond(reportLastPosition.timestamp), ZoneId.of("Europe/Prague")))
            .map(_.track.ID)
        } else
          optLastPosition match {
            case Some(position) => Task(position.trackId)
            case None           => Task.fail(throw new IllegalStateException("Undefined position could not have track"))
          }
      _ <- logger.debug(s"Using track id $trackId in same session? $isInSameSession")
      positionRequest <- IO.fromEither(handleNewPosition(token, sessionId, trackId, report)).mapError(e => s"Handling new position error: $e")
      positionSaved <- positionRequest
      _ <-
        if (reportLastPosition.timestamp.seconds > optLastPosition.fold(Duration.Zero)(p => p.timestamp.toEpochSecond.seconds)) { // new 'lastPosition' position is really newer
          topic.publish1( // send new position to clients
            WebSocketMessage.position(
              toPosition(report.vehicleId, sessionId, trackId, reportLastPosition),
              isMoving = true
            )
          )
        } else Task.unit
    } yield positionSaved

    processed.either.flatMap {
      _.fold(
        e => logger.error(s"MQTT message error: ${e}"),
        cnt => logger.debug(s"MQTT message successfully proceeded. $cnt new position inserted.")
      )
    }
  }

  private def handleNewPosition(token: AccessToken, sessionId: String, trackId: Long, report: Report): Either[String, Task[Position]] = {
    if (token.clientRoles.contains(Role.toString(Roles.Tracker))) {
      val positions = report.positions
        .map(tp => toPosition(report.vehicleId, sessionId, trackId, tp))
        .toList
      Right(positionService.persist(PositionsRequest(report.vehicleId, positions)))
    } else {
      Left("Tracker does not have required role")
    }
  }

  private def toPosition(vehicleId: Long, sessionId: String, trackId: Long, trackerPosition: TrackerPosition): Position = {
    Position(
      None,
      vehicleId,
      trackId,
      trackerPosition.speed,
      trackerPosition.latitude,
      trackerPosition.longitude,
      ZonedDateTime.ofInstant(Instant.ofEpochSecond(trackerPosition.timestamp), ZoneId.of("Europe/Prague")),
      sessionId
    )

  }
}

case class LatLng(lat: Double, lng: Double, timestamp: Long, trackId: Long, sessionId: ByteString)
object LatLng {
  val empty: LatLng = LatLng(-1, -1, -1, -1, ByteString.EMPTY)
}

object MqttService {
  def apply(
      loggerFactory: LoggerFactory[Task],
      trackerService: TrackerService,
      positionService: PositionService,
      trackService: TrackService,
      topic: Topic[Task, WebSocketMessage],
      tokenParser: AccessTokenParser,
      configuration: Configuration
  ): MqttService =
    new MqttService(
      loggerFactory.make("mqtt-service"),
      trackerService,
      positionService,
      trackService,
      topic,
      tokenParser,
      configuration
    )
}

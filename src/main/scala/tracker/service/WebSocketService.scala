package tracker.service

import cats.effect.concurrent.Ref
import cats.implicits.catsSyntaxFlatMapOps
import fs2.{Pipe, Stream}
import fs2.concurrent.Topic
import io.circe.parser._
import io.circe.syntax.EncoderOps
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.websocket.WebSocketFrame
import org.http4s.websocket.WebSocketFrame.Text
import org.http4s.Response
import slog4s._
import tracker._
import tracker.DefaultAuthenticatedSocketMessage._
import tracker.config.Configuration
import tracker.security.DefaultAccessTokenParser
import tracker.WebSocketMessage.heartbeat
import zio.{IO, Task, ZIO}
import zio.interop.catz.implicits._
import zio.interop.catz.taskConcurrentInstance

import scala.concurrent.duration._

class WebSocketService(
    logger: Logger[Task],
    subscribedVehicles: Ref[Task, Set[Long]],
    topic: Topic[Task, WebSocketMessage],
    sessionTopic: Topic[Task, AuthenticatedWebSocketMessage],
    vehicleService: VehicleService,
    config: Configuration
) {

  private val sessionTopicStream =
    sessionTopic
      .subscribe(100)
      .map(msg => Text(msg.asJson.noSpaces))

  private val toClient: Stream[Task, WebSocketFrame] = {
    topic
      .subscribe(100)
      .evalMap {
        case DefaultWebSocketMessage(MessageType.Position, pos) =>
          decode[Position](pos) match {
            case Right(position) => newPositionResponse(position).map(_.getOrElse(Text("")))
            case _               => logger.warn("Position in topic cannot be decoded") >> Task(Text(internalError.asJson.noSpacesSortKeys))
          }
        case msg => Task(Text(msg.asJson.noSpaces))
      }
      .merge(sessionTopicStream)
      .merge(Stream.awakeEvery[Task](5.seconds).map(_ => Text(heartbeat.asJson.noSpaces)))
  }

  private val fromClient: Pipe[Task, WebSocketFrame, Unit] =
    _.evalMap {
      case Text(txt, _) =>
        decode[DefaultAuthenticatedSocketMessage](txt) match {
          case Right(message) =>
            message match {
              case DefaultAuthenticatedSocketMessage(MessageType.Subscribe, jwt, payload) =>
                val vehicles = for {
                  _ <- logger.debug(s"New subscription for vehicle: $payload")
                  token <- IO.fromEither(jwt.toRight("Access token must be provided"))
                  _ <- IO.fromEither(DefaultAccessTokenParser.parseAccessToken(token, config.jwt.secret)).mapError(e => s"Invalid token $e")
                  id <- IO.fromEither(payload.toLongOption.toRight("Could not convert vehicle ID to number"))
                  vehicleOpt <- vehicleService.get(id).mapError(e => s"${e.getClass.getName}: ${e.getMessage}")
                  _ <- IO.fromEither(vehicleOpt.toRight("Vehicle not found"))
                  vehicles <- subscribedVehicles.modify(sv => (sv + id, sv + id))
                } yield vehicles

                vehicles.either.flatMap {
                  _.fold(
                    e => sessionTopic.publish1(error(e.toString)),
                    v => vehicleService.getList(v).flatMap(a => sessionTopic.publish1(text(a.asJson.noSpacesSortKeys)))
                  )
                }
              case _ => ZIO.unit
            }
          case Left(e) => sessionTopic.publish1(error(e.toString))
        }
      case _ => ZIO.unit
    }

  private def newPositionResponse(position: Position): Task[Option[Text]] = {
    for {
      isSubscribed <- subscribedVehicles.get.map(_.contains(position.vehicleId))
      res <- Task(
        if (isSubscribed) Some(Text(WebSocketMessage.position(position).asJson.noSpacesSortKeys))
        else None
      )
    } yield res
  }

  def build: Task[Response[Task]] = WebSocketBuilder[Task].build(toClient, fromClient)
}

object WebSocketService {
  def apply(
      loggerFactory: LoggerFactory[Task],
      topic: Topic[Task, WebSocketMessage],
      vehicleService: VehicleService,
      config: Configuration
  ): Task[WebSocketService] =
    for {
      sessionTopic <- Topic[Task, AuthenticatedWebSocketMessage](DefaultAuthenticatedSocketMessage.empty)
      subscribedVehicles <- Ref.of[Task, Set[Long]](Set.empty)
    } yield new WebSocketService(loggerFactory.make("websocket-service"), subscribedVehicles, topic, sessionTopic, vehicleService, config)
}

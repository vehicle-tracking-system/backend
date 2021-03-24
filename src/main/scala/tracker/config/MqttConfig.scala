package tracker.config

import pureconfig.ConfigReader
import pureconfig.generic.semiauto.deriveReader

import scala.concurrent.duration.FiniteDuration

final case class MqttConfig(
    host: String,
    port: Int,
    ssl: Boolean,
    user: Option[String],
    password: Option[String],
    subscriberName: String,
    topic: String,
    readTimeout: FiniteDuration,
    connectionRetries: Int,
    keepAliveSecs: Int
)

object MqttConfig {
  implicit val jwtConfigReader: ConfigReader[MqttConfig] = deriveReader[MqttConfig]
}

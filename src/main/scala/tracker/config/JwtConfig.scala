package tracker.config

import pdi.jwt.JwtAlgorithm
import pureconfig.ConfigReader
import pureconfig.generic.semiauto.deriveReader

final case class JwtConfig(
                            secret: String,
                            algorithm: JwtAlgorithm
                          )

object JwtConfig {
  implicit val jwtAlgorithmReader: ConfigReader[JwtAlgorithm] = ConfigReader[String].map(JwtAlgorithm.fromString)
  implicit val jwtConfigReader: ConfigReader[JwtConfig] = deriveReader[JwtConfig]
}
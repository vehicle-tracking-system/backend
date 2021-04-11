package tracker.config

import pureconfig.ConfigReader
import pureconfig.generic.semiauto.deriveReader

final case class VolumesConfig(
    frontend: String,
    tracksExport: String
)

object VolumesConfig {
  implicit val volumesConfigReader: ConfigReader[VolumesConfig] = deriveReader[VolumesConfig]
}

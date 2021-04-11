package tracker.utils

import cats.data.NonEmptyList
import io.jenetics.jpx._
import tracker.Position
import tracker.config.VolumesConfig

import java.nio.file.{FileSystems, Path}
import scala.jdk.CollectionConverters._

case class GPXFileGeneratorBuilder(config: VolumesConfig) {
  def make(name: String, positions: NonEmptyList[Position]) = new GPXFileGenerator(name, config, positions)
}

class GPXFileGenerator(name: String, config: VolumesConfig, positions: NonEmptyList[Position]) {
  private val gpx = GPX.builder(GPX.Version.V11, "vehicle-tracking-system")
  val path: Path = FileSystems.getDefault.getPath(config.tracksExport, name + ".gpx")
  private lazy val generate: GPX.Builder = {
    val trackSegmentBuilder = io.jenetics.jpx.TrackSegment.builder()
    val waypoints = positions.map { p =>
      WayPoint.of(Latitude.ofDegrees(p.latitude), Longitude.ofDegrees(p.longitude), p.timestamp)
    }
    val trackSegment = trackSegmentBuilder.points(waypoints.toList.asJava).build()
    val track = io.jenetics.jpx.Track.builder().addSegment(trackSegment).build()
    gpx.addTrack(track)
  }
  lazy val build: GPX = generate.build()
  def save(): Unit = {
    if (path.getParent.toFile.exists()) { GPX.write(build, path) }
    else {
      path.toFile.mkdirs()
      GPX.write(build, path)
    }
  }
}

package tracker.utils

import cats.data.NonEmptyList
import cats.effect.Blocker
import io.jenetics.jpx._
import tracker.Position
import tracker.config.VolumesConfig
import zio.Task
import zio.interop.catz._

import java.nio.file.{FileSystems, Path}
import scala.jdk.CollectionConverters._

class GPXFileGeneratorBuilder(config: VolumesConfig, blocker: Blocker) {
  def make(name: String, positions: NonEmptyList[Position]): GPXFileGenerator = new GPXFileGenerator(name, config, positions, blocker)
}

class GPXFileGenerator(name: String, config: VolumesConfig, positions: NonEmptyList[Position], blocker: Blocker) {
  private val gpx = GPX.builder(GPX.Version.V11, "vehicle-tracking-system")

  private val path: Path = FileSystems.getDefault.getPath(config.tracksExport, name + ".gpx")

  private lazy val builder: GPX.Builder = {
    val trackSegmentBuilder = io.jenetics.jpx.TrackSegment.builder()
    val waypoints = positions.map { p =>
      WayPoint.of(Latitude.ofDegrees(p.latitude), Longitude.ofDegrees(p.longitude), p.timestamp)
    }
    val trackSegment = trackSegmentBuilder.points(waypoints.toList.asJava).build()
    val track = io.jenetics.jpx.Track.builder().addSegment(trackSegment).build()
    gpx.addTrack(track)
  }

  lazy val generate: GPXFile = new GPXFile(builder.build(), path, blocker)
}

class GPXFile(gpx: GPX, path: Path, blocker: Blocker) {

  def stream(): fs2.Stream[Task, Byte] = {
    fs2.io.readOutputStream(blocker, 4096) { out =>
      Task.effect(GPX.write(gpx, out))
    }
  }

  def save(): Unit = {
    if (path.getParent.toFile.exists()) {
      GPX.write(gpx, path)
    } else {
      path.getParent.toFile.mkdirs()
      GPX.write(gpx, path)
    }
  }
}

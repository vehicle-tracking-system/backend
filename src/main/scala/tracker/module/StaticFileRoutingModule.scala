package tracker.module

import cats.effect._
import cats.data.OptionT
import org.http4s._
import org.http4s.dsl.Http4sDsl
import tracker.config.Configuration
import zio.Task
import zio.interop.catz._

import java.io.File
import java.util.concurrent.Executors

/**
  * Serving static file (UI) to client. The path to this files are specified in config `volumes.frontend`.
  */
object StaticFileRoutingModule extends Http4sDsl[Task] {
  private val blockingPool = Executors.newFixedThreadPool(4)
  private val blocker = Blocker.liftExecutorService(blockingPool)
  private val supportedStaticExtensions =
    List(".html", ".js", ".map", ".css", ".png", ".ico", ".jpg", ".jpeg", ".otf", ".ttf", ".woff", ".woff2", ".svg")

  def make(config: Configuration): HttpRoutes[Task] =
    HttpRoutes.of[Task] {
      case req if supportedStaticExtensions.exists(req.pathInfo.endsWith) =>
        StaticFile
          .fromFile(new File(config.volumes.frontend + req.pathInfo), blocker, Some(req))
          .map(_.putHeaders())
          .orElse(
            OptionT
              .fromOption[Task](Option(this.getClass.getResource(config.volumes.frontend + req.pathInfo)))
              .flatMap(StaticFile.fromURL(_, blocker, Some(req)))
          )
          .getOrElseF(NotFound())

      case request =>
        StaticFile
          .fromFile(new File(config.volumes.frontend + "index.html"), blocker, Some(request))
          .getOrElseF(NotFound())
    }
}

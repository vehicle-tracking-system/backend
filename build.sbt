Global / onChangedBuildSource := ReloadOnSourceChanges
ThisBuild / turbo := true
ThisBuild / organization := "cz.cvut.fit"

name := "tracker-server"
version := "1.0"
enablePlugins(JavaAppPackaging)

enablePlugins(DockerPlugin)
packageName in Docker := "jehlima2/" + packageName.value
dockerExposedVolumes := Seq("/opt/docker/logs")
dockerExposedPorts ++= Seq(8080)

resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"

lazy val app = (project in file("app"))
  .settings(commonSettings: _*)
  .settings(
    mainClass in assembly := Some("tracker.Main")
  )

lazy val utils = (project in file("utils"))
  .settings(commonSettings: _*)
  .settings(
    assemblyJarName in assembly := "tracker-server.jar"
  )

assemblyMergeStrategy in assembly := {
  case "module-info.class"                  => MergeStrategy.rename
  case "scala-collection-compat.properties" => MergeStrategy.rename
  case PathList("META-INF", xs @ _*) =>
    (xs map { _.toLowerCase }) match {
      case ("manifest.mf" :: Nil) | ("index.list" :: Nil) | ("dependencies" :: Nil) =>
        MergeStrategy.discard
      case ps @ (x :: xs) if ps.last.endsWith(".sf") || ps.last.endsWith(".dsa") =>
        MergeStrategy.discard
      case "plexus" :: xs =>
        MergeStrategy.discard
      case "services" :: xs =>
        MergeStrategy.filterDistinctLines
      case ("spring.schemas" :: Nil) | ("spring.handlers" :: Nil) =>
        MergeStrategy.filterDistinctLines
      case _ => MergeStrategy.deduplicate
    }
  case _ => MergeStrategy.deduplicate
}

lazy val commonSettings = BuildSettings.common ++ Seq(
  version := "0.1-SNAPSHOT",
  organization := "cz.cvut.fit",
  scalaVersion := "2.13.3",
  test in assembly := {},
  libraryDependencies ++= Seq(
    Dependencies.scalaCache,
    Dependencies.mqttClient,
    Dependencies.fs2,
    Dependencies.slog4sAPI,
    Dependencies.slog4s,
    Dependencies.pac4j,
    Dependencies.pac4jJwt,
    Dependencies.circeGeneric,
    Dependencies.circeGenericExtras,
    Dependencies.circeCore,
    Dependencies.circeParser,
    Dependencies.deadbolt,
    Dependencies.h2Database,
    Dependencies.jwt,
    Dependencies.jwtCirce,
    Dependencies.logbackClassic,
    Dependencies.scalaTest % Test,
    Dependencies.testContainers % Test,
    Dependencies.testContainersPostgres % Test
  ),
  Test / publishArtifact := false
)

lazy val root = project
  .in(file("."))
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      Dependencies.sstBundleZioHttp4sBlaze,
      Dependencies.sstHttp4sClientBlazePureConfig,
      Dependencies.sstHttp4sClientMonixCatcap,
      Dependencies.sstMonixCatnapPureConfig,
      Dependencies.sstDoobieHikariPureConfig,
      Dependencies.sstDoobieHikari,
      Dependencies.sstFlywayPureConfig,
      Dependencies.sstJvm,
      Dependencies.sstMicrometerJmxPureConfig,
//      Dependencies.doobie
      Dependencies.doobieh2
    ),
    Compile / PB.targets := Seq(
      scalapb.gen() -> (Compile / sourceManaged).value / "scalapb"
    ),
    name := "tracker-server"
  )

addCommandAlias("checkAll", "; scalafmtSbtCheck; scalafmtCheckAll; compile:scalafix --check; test:scalafix --check; test")
addCommandAlias("fixAll", "; compile:scalafix; test:scalafix; scalafmtSbt; scalafmtAll")

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

lazy val commonSettings = BuildSettings.common ++ Seq(
  libraryDependencies ++= Seq(
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

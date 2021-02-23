Global / onChangedBuildSource := ReloadOnSourceChanges
ThisBuild / turbo := true
ThisBuild / organization := "cz.cvut.fit"

resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"

lazy val commonSettings = BuildSettings.common ++ Seq(
  libraryDependencies ++= Seq(
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
    name := "tracker-server"
  )

addCommandAlias("checkAll", "; scalafmtSbtCheck; scalafmtCheckAll; compile:scalafix --check; test:scalafix --check; test")
addCommandAlias("fixAll", "; compile:scalafix; test:scalafix; scalafmtSbt; scalafmtAll")

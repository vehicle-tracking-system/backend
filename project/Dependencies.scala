import sbt._

object Dependencies {
  val pac4j = "org.pac4j" % "http4s-pac4j_2.13" % Versions.pac4j_http4s
  val pac4jJwt = "org.pac4j" % "pac4j-jwt" % Versions.pac4j
  val circeCore = "io.circe" %% "circe-core" % Versions.circe
  val circeParser = "io.circe" %% "circe-parser" % Versions.circe
  val circeGeneric = "io.circe" %% "circe-generic" % Versions.circe
  val circeGenericExtras = "io.circe" %% "circe-generic-extras" % Versions.circe
  val jwt = "com.pauldijou" %% "jwt-core" % Versions.jwt
  val jwtCirce = "com.pauldijou" %% "jwt-circe" % Versions.jwt
  val deadbolt = "be.objectify" %% "deadbolt-scala" % Versions.deadbolt
  val h2Database = "com.h2database" % "h2" % Versions.h2
  val doobie = "org.tpolecat" %% "doobie-postgres" % Versions.doobie
  val doobieh2 = "org.tpolecat" %% "doobie-h2" % Versions.doobie
  val kindProjector = "org.typelevel" % "kind-projector" % "0.11.0" cross CrossVersion.full
  val logbackClassic = "ch.qos.logback" % "logback-classic" % "1.2.3"
  val scalaTest = "org.scalatest" %% "scalatest" % "3.2.3"
  val scalafixScaluzzi = "com.github.vovapolu" %% "scaluzzi" % "0.1.15"
  val scalafixSortImports = "com.nequissimus" %% "sort-imports" % "0.5.5"
  val silencer = "com.github.ghik" % "silencer-plugin" % Versions.silencer cross CrossVersion.full
  val silencerLib = "com.github.ghik" % "silencer-lib" % Versions.silencer cross CrossVersion.full
  val sstBundleZioHttp4sBlaze = "com.avast" %% "sst-bundle-zio-http4s-blaze" % Versions.sst
  val sstDoobieHikari = "com.avast" %% "sst-doobie-hikari" % Versions.sst
  val sstDoobieHikariPureConfig = "com.avast" %% "sst-doobie-hikari-pureconfig" % Versions.sst
  val sstFlywayPureConfig = "com.avast" %% "sst-flyway-pureconfig" % Versions.sst
  val sstHttp4sClientBlazePureConfig = "com.avast" %% "sst-http4s-client-blaze-pureconfig" % Versions.sst
  val sstHttp4sClientMonixCatcap = "com.avast" %% "sst-http4s-client-monix-catnap" % Versions.sst
  val sstJvm = "com.avast" %% "sst-jvm" % Versions.sst
  val sstMicrometerJmxPureConfig = "com.avast" %% "sst-micrometer-jmx-pureconfig" % Versions.sst
  val sstMonixCatnapPureConfig = "com.avast" %% "sst-monix-catnap-pureconfig" % Versions.sst
  val testContainers = "com.dimafeng" %% "testcontainers-scala-scalatest" % Versions.testContainers
  val testContainersPostgres = "com.dimafeng" %% "testcontainers-scala-postgresql" % Versions.testContainers
  val zio = "dev.zio" %% "zio" % "1.0.3"
  val zioInteropCats = "dev.zio" %% "zio-interop-cats" % "2.2.0.1"

  object Versions {
    val sst = "0.3.3"
    val silencer = "1.7.1"
    val doobie = "0.9.2"
    val testContainers = "0.38.6"
    val h2 = "1.4.200"
    val deadbolt = "2.8.2"
    val jwt = "5.0.0"
    val circe = "0.13.0"
    val pac4j_http4s = "2.0.1"
    val pac4j = "4.3.1"
  }

}

import sbt.Keys._
import sbt._
import scalafix.sbt.ScalafixPlugin.autoImport._

object BuildSettings {

  lazy val common: Seq[Def.Setting[_]] = Seq(
    scalaVersion := "2.13.3",
    libraryDependencies ++= Seq(
      compilerPlugin(Dependencies.kindProjector),
      compilerPlugin(Dependencies.silencer),
      compilerPlugin(scalafixSemanticdb), // necessary for Scalafix
      Dependencies.silencerLib
    ),
    ThisBuild / scalafixDependencies ++= Seq(
      Dependencies.scalafixScaluzzi,
      Dependencies.scalafixSortImports
    ),
    scalacOptions ++= Seq(
      "-Yrangepos", // necessary for Scalafix (required by SemanticDB compiler plugin)
      "-Ywarn-unused", // necessary for Scalafix RemoveUnused rule (not present in sbt-tpolecat for 2.13)
      "-P:silencer:checkUnused"
    ),
    Test / publishArtifact := false
  )

}

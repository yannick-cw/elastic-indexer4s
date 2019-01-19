import sbt._

object Dependencies {
  val elastic4sVersion = "6.4.0"
  val circeVersion     = "0.10.0"
  val catsVersion      = "1.4.0"
  val akkaVersion      = "2.5.18"

  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.0.5" % "test"

  lazy val scalaCheck = "org.scalacheck" % "scalacheck_2.12" % "1.14.0" % "test"

  lazy val elastic4s = Seq(
    "com.sksamuel.elastic4s" %% "elastic4s-core",
    "com.sksamuel.elastic4s" %% "elastic4s-http-streams",
    "com.sksamuel.elastic4s" %% "elastic4s-embedded",
    "com.sksamuel.elastic4s" %% "elastic4s-circe"
  ).map(_ % elastic4sVersion)

  lazy val akkaStream = "com.typesafe.akka" %% "akka-stream" % akkaVersion

  lazy val cats = Seq(
    "org.typelevel" %% "cats-core"
  ).map(_ % catsVersion)

  lazy val circe = Seq(
    "io.circe" %% "circe-core",
    "io.circe" %% "circe-generic",
    "io.circe" %% "circe-parser",
    "io.circe" %% "circe-optics"
  ).map(_ % circeVersion)

  lazy val itUtilDependencies = Seq(
    "com.whisk" %% "docker-testkit-scalatest"    % "0.9.5" % "it",
    "com.whisk" %% "docker-testkit-impl-spotify" % "0.9.8" % "it"
  )

  lazy val log = Seq(
    "ch.qos.logback"             % "logback-classic" % "1.2.3",
    "com.typesafe.scala-logging" %% "scala-logging"  % "3.9.0"
  )
}

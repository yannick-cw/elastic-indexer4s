import sbt._

object Dependencies {
  val elastic4sVersion = "6.3.3"
  val circeVersion     = "0.9.3"
  val catsVersion      = "1.1.0"
  val akkaVersion      = "2.5.14"

  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.0.5" % "test"

  lazy val scalaCheck = "org.scalacheck" % "scalacheck_2.12" % "1.14.0" % "test"

  lazy val elastic4s = Seq(
    "com.sksamuel.elastic4s" %% "elastic4s-core",
    "com.sksamuel.elastic4s" %% "elastic4s-http-streams",
    "com.sksamuel.elastic4s" %% "elastic4s-embedded",
    "com.sksamuel.elastic4s" %% "elastic4s-circe"
  ).map(_ % elastic4sVersion)

  lazy val akkaStream = "com.typesafe.akka" %% "akka-stream" % akkaVersion

  lazy val cats = Seq("org.typelevel" %% "cats-core", "org.typelevel" %% "cats-free").map(_ % catsVersion)

  lazy val circe = Seq(
    "io.circe" %% "circe-core",
    "io.circe" %% "circe-generic",
    "io.circe" %% "circe-parser",
    "io.circe" %% "circe-optics"
  ).map(_ % circeVersion)

  lazy val log = Seq(
    "ch.qos.logback"             % "logback-classic" % "1.1.7",
    "com.typesafe.scala-logging" %% "scala-logging"  % "3.5.0"
  )
}

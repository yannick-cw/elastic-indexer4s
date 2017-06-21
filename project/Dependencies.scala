import sbt._

object Dependencies {
  val elastic4sVersion = "5.4.5"
  val circeVersion = "0.8.0"
  private val akkaVersion = "2.5.3"

  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.0.1" % "test"

  lazy val scalaCheck =  "org.scalacheck" % "scalacheck_2.12" % "1.13.4" % "test"

  lazy val elastic4s = Seq(
    "com.sksamuel.elastic4s" %% "elastic4s-core",
    "com.sksamuel.elastic4s" %% "elastic4s-streams",
    "com.sksamuel.elastic4s" %% "elastic4s-circe"
  ).map(_ % elastic4sVersion)

  lazy val elastic4sTestkit = "com.sksamuel.elastic4s" %% "elastic4s-testkit" % elastic4sVersion

  lazy val akkaStream = "com.typesafe.akka" %% "akka-stream" % akkaVersion

  lazy val cats =  "org.typelevel" %% "cats" % "0.9.0"

  lazy val circe = Seq(
    "io.circe" %% "circe-core",
    "io.circe" %% "circe-generic",
    "io.circe" %% "circe-parser",
    "io.circe" %% "circe-optics"
  ).map(_ % circeVersion)

  lazy val log = Seq(
    "ch.qos.logback" % "logback-classic" % "1.1.7",
    "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0"
  )
}

import sbt._

object Dependencies {
  lazy val akkaVersion      = "2.5.17"
  lazy val catsVersion      = "1.4.0"
  lazy val circeVersion     = "0.10.0"
  lazy val elastic4sVersion = "5.6.7"
  lazy val log4jVersion     = "2.11.1"

  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.0.5" % "test"

  lazy val scalaCheck =  "org.scalacheck" % "scalacheck_2.12" % "1.14.0" % "test"

  lazy val elastic4s = Seq(
    "com.sksamuel.elastic4s" %% "elastic4s-core",
    "com.sksamuel.elastic4s" %% "elastic4s-streams",
    "com.sksamuel.elastic4s" %% "elastic4s-circe"
  ).map(_ % elastic4sVersion)

  lazy val elastic4sTestkit = "com.sksamuel.elastic4s" %% "elastic4s-testkit" % elastic4sVersion

  lazy val akkaStream = "com.typesafe.akka" %% "akka-stream" % akkaVersion

  lazy val cats =  "org.typelevel" %% "cats-core" % catsVersion

  lazy val circe = Seq(
    "io.circe" %% "circe-core",
    "io.circe" %% "circe-generic",
    "io.circe" %% "circe-parser",
    "io.circe" %% "circe-optics"
  ).map(_ % circeVersion)

  lazy val log = Seq(
    "ch.qos.logback"             % "logback-classic" % "1.2.3",
    "com.typesafe.scala-logging" %% "scala-logging"  % "3.9.0"
  )

  lazy val itLog = Seq(
    "org.apache.logging.log4j" % "log4j-api"  % log4jVersion % "it",
    "org.apache.logging.log4j" % "log4j-core" % log4jVersion % "it"
  )
}

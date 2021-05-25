import sbt._

object Dependencies {
  val elastic4sVersion     = "6.7.2"
  val circeVersion         = "0.11.0"
  val catsVersion          = "1.6.1"
  val akkaVersion          = "2.5.24"
  val elasticsearchVersion = "6.8.16"

  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.0.8" % "test,it"

  lazy val scalaCheck = "org.scalacheck" % "scalacheck_2.12" % "1.14.0" % "test,it"

  lazy val elastic4s = Seq(
    "com.sksamuel.elastic4s" %% "elastic4s-core",
    "com.sksamuel.elastic4s" %% "elastic4s-http-streams",
    "com.sksamuel.elastic4s" %% "elastic4s-embedded",
    "com.sksamuel.elastic4s" %% "elastic4s-circe"
  ).map(_ % elastic4sVersion)

  lazy val elasticsearch = Seq(
    "org.elasticsearch.client" % "elasticsearch-rest-client-sniffer" % elasticsearchVersion
  )

  lazy val elastic4sTestkit = "com.sksamuel.elastic4s" %% "elastic4s-testkit" % elastic4sVersion % "it"

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
    "com.whisk" %% "docker-testkit-scalatest"    % "0.9.9" % "it",
    "com.whisk" %% "docker-testkit-impl-spotify" % "0.9.9" % "it"
  )

  lazy val log = Seq(
    "ch.qos.logback"             % "logback-classic" % "1.2.3",
    "com.typesafe.scala-logging" %% "scala-logging"  % "3.9.2"
  )
}

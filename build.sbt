import Dependencies._

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      scalaVersion := "2.12.7",
      version      := "0.4.1",
      organization := "io.github.yannick-cw",
      fork in run := true
    )),
    name := "elastic_indexer4s",
    libraryDependencies ++= Seq(
      scalaTest,
      scalaCheck,
      akkaStream,
      cats,
      elastic4sTestkit
    ) ++ circe ++ elastic4s ++ log ++ itLog,
  )
  .configs(IntegrationTest)
  .settings(Defaults.itSettings: _*)

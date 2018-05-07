import Dependencies._

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      scalaVersion := "2.12.4",
      version      := "0.5.0",
      organization := "io.github.yannick-cw",
      fork in run := true
    )),
    name := "elastic_indexer4s",
    libraryDependencies ++= Seq(
      scalaTest,
      scalaCheck,
      akkaStream,
      elastic4sTestkit
    ) ++ circe ++ elastic4s ++ cats ++ log
  )
  .configs(IntegrationTest)
  .settings(Defaults.itSettings: _*)

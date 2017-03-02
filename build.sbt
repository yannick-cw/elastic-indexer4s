import Dependencies._

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      scalaVersion := "2.12.1",
      version      := "0.1",
      fork in run := true
    )),
    name := "elastic-indexer4s",
    libraryDependencies ++= Seq(
      scalaTest,
      scalaCheck,
      akkaStream,
      cats,
      elastic4sTestkit
    ) ++ circe ++ elastic4s ++ log
  )
  .configs(IntegrationTest)
  .settings(Defaults.itSettings: _*)
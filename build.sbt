import Dependencies._

lazy val root = (project in file("."))
  .settings(
    parallelExecution in IntegrationTest := false,
    inThisBuild(
      List(
        scalaVersion := "2.12.15",
        version := "0.6.6-SNAPSHOT",
        organization := "io.github.yannick-cw",
        fork in run := true,
        scalafmtVersion := "1.2.0",
        scalafmtOnCompile := true,
        autoCompilerPlugins := true,
        addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.10")
      )),
    name := "elastic_indexer4s",
    Defaults.itSettings,
    publishTo := sonatypePublishTo.value,
    libraryDependencies ++= Seq(
      scalaTest,
      scalaCheck,
      akkaStream,
      elastic4sTestkit
    ) ++ circe ++ elastic4s ++ cats ++ log ++ itUtilDependencies ++ elasticsearch
  )
  .configs(IntegrationTest)

import Dependencies._

lazy val root = (project in file("."))
  .settings(
    inThisBuild(
      List(
        scalaVersion := "2.12.8",
        version := "0.6.5-SNAPSHOT",
        organization := "io.github.yannick-cw",
        fork in run := true,
        scalafmtVersion := "1.2.0",
        scalafmtOnCompile := true,
        autoCompilerPlugins := true,
        addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.8")
      )),
    name := "elastic_indexer4s",
    Defaults.itSettings,
    publishTo := sonatypePublishTo.value,
    libraryDependencies ++= Seq(
      scalaTest,
      scalaCheck,
      akkaStream,
    ) ++ circe ++ elastic4s ++ cats ++ log ++ itUtilDependencies
  )
  .configs(IntegrationTest)

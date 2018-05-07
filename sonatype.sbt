sonatypeProfileName := "io.github.yannick-cw"

publishMavenStyle := true

publishArtifact in Test := false

pomIncludeRepository := { _ =>
  false
}

pomExtra in Global := {
  <url>https://github.com/yannick-cw/elastic-indexer4s</url>
    <licenses>
      <license>
        <name>MIT</name>
        <url>https://opensource.org/licenses/MIT</url>
      </license>
    </licenses>
    <scm>
      <connection>scm:git:github.com/yannick-cw/elastic-indexer4s</connection>
      <developerConnection>scm:git:git@github.com:yannick-cw/elastic-indexer4s</developerConnection>
      <url>github.com/yannick-cw/elastic-indexer4s</url>
    </scm>
    <developers>
      <developer>
        <id>7374</id>
        <name>Yannick Gladow</name>
        <url>https://github.com/yannick-cw</url>
      </developer>
    </developers>
}

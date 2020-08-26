// Comment to get more information during initialization
//logLevel := Level.Warn

// Provided https repo because repo1.maven.org doesn't allow non-https connection now...
resolvers += "Maven central HTTPs" at "https://repo1.maven.org/maven2"

resolvers ++= Seq(
  Resolver.sonatypeRepo("releases"),
  Resolver.sonatypeRepo("snapshots"),
  Resolver.typesafeRepo("releases")
)

dependencyOverrides += "org.scala-sbt" % "sbt" % "0.13.13"

// Use the Play sbt plugin for Play projects
// Play 2.3.x the last smart version before the D.I nightmare
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.3.10")
addSbtPlugin("com.updateimpact" % "updateimpact-sbt-plugin" % "2.1.1")
addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.0-M14")

// web plugins
addSbtPlugin("com.typesafe.sbt" % "sbt-less" % "1.0.6")
addSbtPlugin("com.typesafe.sbt" % "sbt-web" % "1.0.0")
addSbtPlugin("com.typesafe.sbt" % "sbt-webdriver" % "1.0.0")
addSbtPlugin("com.typesafe.sbt" % "sbt-js-engine" % "1.0.0")

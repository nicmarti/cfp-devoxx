name := "cfp-devoxxfr"

version := "2.3-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala, SbtWeb)

includeFilter in(Assets, LessKeys.less) := "*.less"

scalaVersion := "2.11.6"

javaOptions += "-Duser.timezone=UTC"

resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

libraryDependencies ++= Seq(
  cache,
  ws,
  filters
)

val jacksonV = "2.4.3"

val elastic4sVersion = "7.1.0"

// Coursier
libraryDependencies ++= Seq(
  "redis.clients" % "jedis" % "2.1.0"
  , "com.typesafe.play" %% "play-mailer" % "2.4.1"
  , "org.apache.commons" % "commons-lang3" % "3.5"
  , "commons-io" % "commons-io" % "2.4"
  , "commons-codec" % "commons-codec" % "1.9" // for new Base64 that has support for String
  , "org.ocpsoft.prettytime" % "prettytime" % "3.2.4.Final"
  , "com.github.rjeschke" % "txtmark" % "0.13" // Used for Markdown in Proposal
  , "com.pauldijou" %% "jwt-core" % "0.9.2" // JWT for MyDevoxx
  , "org.scalaz" %% "scalaz-core" % "7.2.10"
  , "com.google.api-client" % "google-api-client" % "1.30.7"
  , "com.google.apis" % "google-api-services-oauth2" % "v2-rev20190313-1.30.1"
  , "com.sksamuel.elastic4s" %% "elastic4s-client-esjava" % elastic4sVersion
  , "com.fasterxml.jackson.datatype" % "jackson-datatype-joda" % jacksonV

)

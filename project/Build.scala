import sbt._
import Keys._
import play.Project._

/**
  * Build conf file.
  * Import Scala Sedis driver for Redis.
  *
  * View the dependency tree via updateimpact using link below
  * @see https://app.updateimpact.com/builds/766661207762538496/c9bfdb89-39e7-4f00-b373-87a68d5902f4
  *
  * @author nmartignole
  * @author stephan007
  */
object ApplicationBuild extends Build {

  val appName = "cfp-devoxx"
  val appVersion = "1.3-SNAPSHOT"

  val appDependencies = Seq(
    filters, // protection against CSRF
    "redis.clients" % "jedis" % "2.1.0"
    , "com.typesafe" %% "play-plugins-mailer" % "2.1.0"
    , "org.apache.commons" % "commons-lang3" % "3.1"
    , "commons-io" % "commons-io" % "2.4"
    , "commons-logging" % "commons-logging" % "1.2"
    , "commons-codec" % "commons-codec" % "1.9" // for new Base64 that has support for String
    , "com.typesafe.play" %% "play-cache" % "2.2.0"
    ,"org.ocpsoft.prettytime" % "prettytime" % "3.2.4.Final"

    , "com.amazonaws" % "aws-java-sdk-sns" % "1.11.29"
    exclude ("com.fasterxml.jackson.core", "jackson-annotations")
    exclude ("com.fasterxml.jackson.core", "jackson-core")
    exclude ("com.fasterxml.jackson.core", "jackson-databind")
    exclude ("joda-time", "joda-time")

    , "org.scalamock" %% "scalamock-specs2-support" % "3.0.1" % "test"
  )

  libraryDependencies ++= Seq(
  )

  val main = play.Project(appName, appVersion, appDependencies).settings(
    resolvers += "Sonatype snapshots" at "http://oss.sonatype.org/content/repositories/snapshots/",
    scalacOptions ++= Seq("-Xmax-classfile-name", "100") // add "-feature" to the Seq to have more details
  )
}
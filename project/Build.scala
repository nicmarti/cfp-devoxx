import sbt._
import Keys._
import play.Project._

/**
 * Build conf file.
 * Import Scala Sedis driver for Redis.
 * @nmartignole
 */
object ApplicationBuild extends Build {

  val appName = "cfp-devoxxfr"
  val appVersion = "1.3-SNAPSHOT"

  val appDependencies = Seq(
    "redis.clients" % "jedis" % "2.1.0"
    , "com.typesafe" %% "play-plugins-mailer" % "2.1.0"
    , "org.apache.commons" % "commons-lang3" % "3.1"
    , "commons-io" % "commons-io" % "2.4"
    , "commons-codec" % "commons-codec" % "1.7" // for new Base64 that has support for String
    , "com.typesafe.play" %% "play-cache" % "2.2.0"
    ,"org.ocpsoft.prettytime" % "prettytime" % "3.2.4.Final"
    , "org.scalamock" %% "scalamock-specs2-support" % "3.0.1" % "test"

  )

  libraryDependencies ++= Seq(

  )

  val main = play.Project(appName, appVersion, appDependencies).settings(
    resolvers += "Sonatype snapshots" at "http://oss.sonatype.org/content/repositories/snapshots/",
    scalacOptions ++= Seq("-Xmax-classfile-name", "100") // add "-feature" to the Seq to have more details
  )
}



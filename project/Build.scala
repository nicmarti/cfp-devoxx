import sbt._
import Keys._
import play.Project._

object ApplicationBuild extends Build {

  val appName = "cfp-devoxxfr"
  val appVersion = "1.0-SNAPSHOT"


  val appDependencies = Seq(
    "org.reactivemongo" %% "play2-reactivemongo" % "0.9"
    , "com.typesafe" %% "play-plugins-mailer" % "2.1.0"
    , "commons-lang" % "commons-lang" % "2.6"
    , "commons-io" % "commons-io" % "2.4"
    , "commons-codec" % "commons-codec" % "1.7" // for new Base64 that has support for String
    //"com.google.api.client" % "google-api-client-auth-oauth" % "1.2.3-alpha"
    //"com.google.api-client" % "google-api-client" % "1.5.0-beta"
  )

  libraryDependencies ++= Seq(

  )

  val main = play.Project(appName, appVersion, appDependencies).settings(
    resolvers += "Sonatype snapshots" at "http://oss.sonatype.org/content/repositories/snapshots/",
    resolvers += "Sonatype Google" at "https://oss.sonatype.org/content/repositories/google-releases/"
  )

}

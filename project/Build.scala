import sbt._
import Keys._
import play.Project._

object ApplicationBuild extends Build {

  val appName         = "cfp-devoxxfr"
  val appVersion      = "1.0-SNAPSHOT"


  val appDependencies = Seq(
  "org.reactivemongo" %% "play2-reactivemongo" % "0.9"
  )

  libraryDependencies ++= Seq(
    
  )

  val main = play.Project(appName, appVersion, appDependencies).settings(
      resolvers += "Sonatype snapshots" at "http://oss.sonatype.org/content/repositories/snapshots/"
  )

}

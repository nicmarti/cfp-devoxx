import akka.actor.Props
import library._
import play.api._
import play.api.mvc.{Action, WithFilters, RequestHeader}
import mvc.Results._
import Play.current
import scala.Some
import play.api.libs.concurrent._
import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.duration._

object Global extends GlobalSettings {

  override def onStart(app: Application) {
    if (Play.configuration.getBoolean("actor.cronUpdater.active").isDefined) {
      CronTask.computeStats()
    } else {
      play.Logger.info("actor.cronUpdater.active is set to false, application won't compute stats")
    }
  }

  override def onStop(app: Application) {
//    ElasticSearchActor.masterActor ! StopIndex()
//    ElasticSearchActor.masterActor ! akka.actor.PoisonPill
  }


}

object CronTask {

  val zapActor = Akka.system.actorOf(Props[ZapActor])

  def computeStats() = {
//    val computeStats = Play.configuration.getInt("actor.computeStats.minuts")
//    computeStats match {
//      case None => play.Logger.debug("CronTask: add [actor.computeStats.minuts=10] to compute stats every 10 minuts")
//      case Some(everyX) => {
//        play.Logger.debug("CronTask : compute stats every " + everyX + " minuts")
//        Akka.system.scheduler.schedule(1 minutes, everyX minutes, zaptravelActor, ComputeStats())
//      }
//    }
  }

}
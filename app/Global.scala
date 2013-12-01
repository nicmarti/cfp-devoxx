import akka.actor.Props
import library._
import play.api._
import play.api.mvc.{Action, WithFilters, RequestHeader}
import mvc.Results._
import Play.current
import scala.concurrent.Future
import scala.Some
import play.api.libs.concurrent._
import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.duration._
import scala.util.control.NonFatal

object Global extends GlobalSettings {

  override def onStart(app: Application) {
    if (Play.configuration.getBoolean("actor.cronUpdater.active").isDefined) {
      CronTask.computeStats()
    } else {
      play.Logger.info("actor.cronUpdater.active is set to false, application won't compute stats")
    }
  }

  override def onError(request: RequestHeader, ex: Throwable) = {
    try {
      Future.successful(InternalServerError(Play.maybeApplication.map {
        case app if app.mode != Mode.Prod => views.html.defaultpages.devError.f
        case app => views.html.errorPage.f
      }.getOrElse(views.html.defaultpages.devError.f) {
        ex match {
          case e: UsefulException => e
          case NonFatal(e) => UnexpectedException(unexpected = Some(e))
        }
      }))
    } catch {
      case e: Throwable => {
        Logger.error("Error while rendering default error page", e)
        Future.successful(InternalServerError)
      }
    }
  }

  /**
   * 404 custom page, for Prod mode only
   */
  override def onHandlerNotFound(request: RequestHeader) = {
    Future.successful(NotFound(Play.maybeApplication.map {
      case app if app.mode != Mode.Prod =>  views.html.defaultpages.devNotFound.f
      case app => views.html.notFound.f
    }.getOrElse(views.html.defaultpages.devNotFound.f)(request, Play.maybeApplication.flatMap(_.routes))))
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
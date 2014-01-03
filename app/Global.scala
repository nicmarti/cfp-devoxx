import akka.actor.Props
import java.util.concurrent.TimeUnit
import library._
import library.DraftReminder
import library.search._
import library.search.StopIndex
import org.joda.time.DateMidnight
import play.api._
import play.api.mvc.RequestHeader
import mvc.Results._
import play.api.UnexpectedException
import Play.current
import scala.concurrent.Future
import scala.Some
import play.api.libs.concurrent._
import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.duration._
import scala.Some
import scala.util.control.NonFatal

object Global extends GlobalSettings {
  override def onStart(app: Application) {
    if (Play.configuration.getBoolean("actor.cronUpdater.active").isDefined) {
      CronTask.draftReminder()
      CronTask.elasticSearch()
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
      case app if app.mode != Mode.Prod => views.html.defaultpages.devNotFound.f
      case app => views.html.notFound.f
    }.getOrElse(views.html.defaultpages.devNotFound.f)(request, Play.maybeApplication.flatMap(_.routes))))
  }

  override def onStop(app: Application) = {
    ZapActor.actor ! akka.actor.PoisonPill
    ElasticSearchActor.masterActor ! StopIndex()

    super.onStop(app)
  }
}

object CronTask {
  // postfix operator days should be enabled by making the implicit value scala.language.postfixOps visible.
  // This can be achieved by adding the import clause 'import scala.language.postfixOps'
  import scala.language.postfixOps

  val zapActor = Akka.system.actorOf(Props[ZapActor])

  // Send an email for each Proposal with status draft
  def draftReminder() = {
    val draftTime = Play.configuration.getInt("actor.draftReminder.days")
    draftTime match {
      case Some(everyX) => {
        // Compute delay between now and 8:00 in the morning
        // This is a trick to avoid to send a message when we restart the server
        val tomorrow = DateMidnight.now().plusDays(1)
        val interval = tomorrow.toInterval
        val initialDelay = Duration.create(interval.getEndMillis - interval.getStartMillis, TimeUnit.MILLISECONDS)
        play.Logger.debug("CronTask : check for Draft proposals every " + everyX + " days and send an email in " + initialDelay.toHours + " hours")
        Akka.system.scheduler.schedule(initialDelay, everyX days, zapActor, DraftReminder())
      }
      case _ => {
        play.Logger.debug("CronTask : do not send reminder for draft")
      }
    }
  }

  def elasticSearch() = {
    // Create a cron task
    Akka.system.scheduler.schedule(15 seconds, 2 hours, ElasticSearchActor.masterActor, DoIndexSpeaker())
    Akka.system.scheduler.schedule(55 seconds, 2 hours, ElasticSearchActor.masterActor, DoIndexProposal())
    Akka.system.scheduler.schedule(2 minutes, 2 hours, ElasticSearchActor.masterActor, DoIndexEvent())
  }


}

import akka.actor.Props
import java.util.concurrent.TimeUnit
import library._
import org.joda.time.DateMidnight
import play.api._
import play.api.mvc.RequestHeader
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
      CronTask.draftReminder()
      CronTask.cleanupInvalidReviews()
      CronTask.updateProposalByAuthor()
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
}

object CronTask {

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

  // Delete votes and reviews for each deleted speaker (or cfp admin reviewer)
  def cleanupInvalidReviews() = {
    Akka.system.scheduler.schedule(600 seconds, 1 days, zapActor, CleanupInvalidReviews())
  }

  def updateProposalByAuthor() = {
    Akka.system.scheduler.schedule(1 seconds, 7 days, zapActor, CreateMissingIndexOnRedis())
  }

}
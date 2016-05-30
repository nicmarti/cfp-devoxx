import java.io.File
import java.util.concurrent.TimeUnit

import library.search.{StopIndex, _}
import library.{DraftReminder, _}
import org.joda.time.DateMidnight
import play.api.Play.current
import play.api.libs.concurrent._
import play.api.mvc.RequestHeader
import play.api.mvc.Results._
import play.api.templates.HtmlFormat
import play.api.{UnexpectedException, _}
import play.core.Router.Routes

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NonFatal

object Global extends GlobalSettings {
  override def onStart(app: Application) {
    Play.current.configuration.getBoolean("actor.cronUpdater.active") match {
      case Some(true) if Play.isProd =>
        CronTask.draftReminder()
        CronTask.doIndexElasticSearch()
        CronTask.doComputeStats()
      //CronTask.doSetupOpsGenie()
      case Some(true) if Play.isDev => {
        CronTask.doIndexElasticSearch()
        CronTask.doComputeStats()
      }
      case _ =>
        play.Logger.of("Global").warn("actor.cronUpdated.active is not active => no ElasticSearch or Stats updates")
    }

  }

  override def onError(request: RequestHeader, ex: Throwable) = {
    val viewO: Option[(UsefulException) => HtmlFormat.Appendable] = Play.maybeApplication.map {
      case app if app.mode != Mode.Prod => views.html.defaultpages.devError.f
      case app => views.html.errorPage.f(_: UsefulException)(request)
    }
    try {
      Future.successful(InternalServerError(viewO.getOrElse(views.html.defaultpages.devError.f) {
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
    val viewO: Option[(RequestHeader, Option[Routes]) => HtmlFormat.Appendable] = Play.maybeApplication.map {
      case app if app.mode != Mode.Prod => views.html.defaultpages.devNotFound.f
      case app => views.html.notFound.f(_, _)(request)
    }
    Future.successful(NotFound(viewO.getOrElse(views.html.defaultpages.devNotFound.f)(request, Play.maybeApplication.flatMap(_.routes))))
  }

  override def onStop(app: Application) = {
    ZapActor.actor ! akka.actor.PoisonPill
    ElasticSearchActor.masterActor ! StopIndex
    super.onStop(app)
  }
}

object CronTask {
  // postfix operator days should be enabled by making the implicit value scala.language.postfixOps visible.
  // This can be achieved by adding the import clause 'import scala.language.postfixOps'

  import scala.language.postfixOps

  // Send an email for each Proposal with status draft
  def draftReminder() = {
    import play.api.libs.concurrent.Execution.Implicits._

    val draftTime = Play.configuration.getInt("actor.draftReminder.days")
    draftTime match {
      case Some(everyX) => {
        // Compute delay between now and 8:00 in the morning
        // This is a trick to avoid to send a message when we restart the server
        val tomorrow = DateMidnight.now().plusDays(1)
        val interval = tomorrow.toInterval
        val initialDelay = Duration.create(interval.getEndMillis - interval.getStartMillis, TimeUnit.MILLISECONDS)
        play.Logger.debug("CronTask : check for Draft proposals every " + everyX + " days and send an email in " + initialDelay.toHours + " hours")
        Akka.system.scheduler.schedule(initialDelay, everyX days, ZapActor.actor, DraftReminder())
      }
      case _ => {
        play.Logger.debug("CronTask : do not send reminder for draft")
      }
    }
  }

  def doIndexElasticSearch() = {
    import library.Contexts.elasticSearchContext

    Akka.system.scheduler.scheduleOnce(12 minutes, ElasticSearchActor.masterActor, DoIndexAllProposals)
    Akka.system.scheduler.scheduleOnce(12 minutes, ElasticSearchActor.masterActor, DoIndexAllSpeakers)
    Akka.system.scheduler.scheduleOnce(4 minutes, ElasticSearchActor.masterActor, DoIndexAllAccepted)

    Akka.system.scheduler.schedule(25 minutes, 10 minutes, ElasticSearchActor.masterActor, DoIndexAllProposals)
    Akka.system.scheduler.schedule(25 minutes, 10 minutes, ElasticSearchActor.masterActor, DoIndexAllAccepted)
    Akka.system.scheduler.schedule(1 hour, 10 minutes, ElasticSearchActor.masterActor, DoIndexAllSpeakers)
    Akka.system.scheduler.schedule(2 minutes, 10 minutes, ElasticSearchActor.masterActor, DoIndexAllHitViews)
  }

  def doComputeStats() = {
    import library.Contexts.statsContext
    Akka.system.scheduler.schedule(10 minutes, 5 minutes, ZapActor.actor, ComputeLeaderboard())
    Akka.system.scheduler.schedule(4 minutes, 5 minutes, ZapActor.actor, ComputeVotesAndScore())
    Akka.system.scheduler.schedule(2 minutes, 30 minutes, ZapActor.actor, RemoveVotesForDeletedProposal())

  }

  def doSetupOpsGenie() = {
    import library.Contexts.statsContext
    for (apiKey <- Play.configuration.getString("opsgenie.apiKey");
         name <- Play.configuration.getString("opsgenie.name")) {
      Akka.system.scheduler.schedule(1 minute, 10 minutes, ZapActor.actor, SendHeartbeat(apiKey, name))
    }

  }
}

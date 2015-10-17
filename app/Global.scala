import java.util.concurrent.TimeUnit

import library.{DraftReminder, _}
import library.search.{StopIndex, _}
import models.{Proposal, Speaker}
import org.joda.time.DateMidnight
import play.api.Play.current
import play.api.{UnexpectedException, _}
import play.api.libs.concurrent._
import play.api.mvc.RequestHeader
import play.api.mvc.Results._
import play.api.templates.HtmlFormat
import play.core.Router.Routes

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NonFatal

object Global extends GlobalSettings {
  override def onStart(app: Application) {
    if (Play.configuration.getBoolean("actor.cronUpdater.active").isDefined && Play.isTest == false) {
     // CronTask.draftReminder()
      CronTask.elasticSearch()
      CronTask.doComputeStats()
      CronTask.doSetupOpsGenie()
    } else {
      play.Logger.debug("actor.cronUpdater.active is set to false, application won't compute stats")
    }

    // To fix bug #165 we need to iterate all speakers, then check all proposals and do the appropriate clean-up
    if (Play.isProd) {
     play.Logger.info("Production mode")
    }else{
      play.Logger.info("Development mode")
      val redis=Play.configuration.getString("redis.host")
      val redis_port=Play.configuration.getInt("redis.port")
      play.Logger.info(s"Redis server: $redis $redis_port")
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

  def elasticSearch() = {
    import library.Contexts.elasticSearchContext

    if (Play.isProd) {
      Akka.system.scheduler.scheduleOnce(12 minutes, ElasticSearchActor.masterActor, DoIndexAllProposals)
      Akka.system.scheduler.scheduleOnce(12 minutes, ElasticSearchActor.masterActor, DoIndexAllSpeakers)
      Akka.system.scheduler.scheduleOnce(4 minutes, ElasticSearchActor.masterActor, DoIndexAllAccepted)

      Akka.system.scheduler.schedule(25 minutes, 10 minutes, ElasticSearchActor.masterActor, DoIndexAllProposals)
      Akka.system.scheduler.schedule(25 minutes, 10 minutes, ElasticSearchActor.masterActor, DoIndexAllAccepted)
      Akka.system.scheduler.schedule(1 hour, 10 minutes, ElasticSearchActor.masterActor, DoIndexAllSpeakers)
      Akka.system.scheduler.schedule(2 minutes, 10 minutes, ElasticSearchActor.masterActor, DoIndexAllHitViews)
    }
  }

  def doComputeStats() = {
    import library.Contexts.statsContext

    // Create a cron task
    if (Play.isDev) {
      Akka.system.scheduler.schedule(30 minute, 10 minutes, ZapActor.actor, ComputeLeaderboard())
      Akka.system.scheduler.schedule(45 minute, 30 minutes, ZapActor.actor, ComputeVotesAndScore())
      Akka.system.scheduler.schedule(10 seconds, 10 minutes, ZapActor.actor, RemoveVotesForDeletedProposal())
    }
    if (Play.isProd) {
      Akka.system.scheduler.schedule(10 minutes, 5 minutes, ZapActor.actor, ComputeLeaderboard())
      Akka.system.scheduler.schedule(4 minutes, 5 minutes, ZapActor.actor, ComputeVotesAndScore())
      Akka.system.scheduler.schedule(2 minutes, 10 minutes, ZapActor.actor, RemoveVotesForDeletedProposal())
    }

  }

  def doSetupOpsGenie() = {
    import library.Contexts.statsContext
    for (apiKey <- Play.configuration.getString("opsgenie.apiKey");
         name <- Play.configuration.getString("opsgenie.name")) {
      // Create a cron task
      if (Play.isDev) {
        Akka.system.scheduler.schedule(10 seconds, 10 minutes, ZapActor.actor, SendHeartbeat(apiKey, name))
      }
      if (Play.isProd) {
        Akka.system.scheduler.schedule(1 minute, 10 minutes, ZapActor.actor, SendHeartbeat(apiKey, name))
      }
    }

  }
}

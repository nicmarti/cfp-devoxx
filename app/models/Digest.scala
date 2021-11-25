package models

import library.Redis
import org.joda.time.format.{DateTimeFormat, ISODateTimeFormat}
import org.joda.time.{DateTime, Instant}
import play.Play
import play.api.i18n.Messages
import play.twirl.api.Html

import java.util.concurrent.Callable
import scala.collection.mutable

/**
  * The email digest.
  *
  * Redis keys used :
  *
  * 1) Identify which digest setting a CFP web user has:
  * "Key = Digest:User:[WebUserId] / Value = Realtime, Daily, Weekly, Never"
  *
  * 2) The next redis key holds the Daily and Weekly new proposals IDs
  * "Key = Digest:Daily /  Keys = IDs of new proposals for one day  / Values = creation data
  * "Key = Digest:Weekly / Keys = IDs of new proposals for one week / Values = creation data
  *
  * @author Stephan Janssen
  */
case class Digest(value: Digest.Frequency, labelI18nMessage: Function0[String], storeProposals: Boolean) {}

object Digest {
  type Frequency = String

  val REAL_TIME = Digest("Realtime", () => Messages("email.digest.realtime.description"), storeProposals = true)    // Real time email updates
  val DAILY = Digest("Daily", () => Messages("email.digest.daily.description", getDayMoment), storeProposals = true)           // Daily email digest
  val WEEKLY = Digest("Weekly", () => Messages("email.digest.weekly.description", getDayMoment, getWeekMoment), storeProposals = true)         // Weekly
  val NEVER = Digest("Never", () => Messages("email.digest.never.description"), storeProposals = false)          // Never, means the CFP user will never receive proposal updates!

  // All the digest interval values
  val allDigests = List(REAL_TIME, DAILY, WEEKLY, NEVER)
  val selectableDigests = List(REAL_TIME, DAILY, WEEKLY)

  private val digestRedisKey = "Digest:"
  private val digestUserRedisKey = digestRedisKey + "User:"
  private val digestFilterRedisKey = digestUserRedisKey + "Filter:"

  private val app = Play.application()

  def getDayMoment: String = {
    val daily = app.configuration().getString("digest.daily")
    if (daily == null) {
      "00:00" // default value
    } else {
      daily
    }
  }

  def getWeekMoment: String = {
    val day = app.configuration().getInt("digest.weekly")
    if (day == null) {
      "Monday"  // default value
    } else {
      Messages("email.digest.day." + day)
    }
  }

  /**
    * Purge (delete) the given digest.
    *
    * @param digest the digest to clean up
    * @return
    */
  def purge(digest: Digest): Long = Redis.pool.withClient {
    implicit client =>
      client.del(digestRedisKey + digest.value)
  }

  /**
    * Add the submitted proposal to the digest queue.
    *
    * @param proposalId the actual new proposal
    */
  def addProposal(proposalId : String): Unit = Redis.pool.withClient {
    implicit client =>
      val now = DateTime.now().toString("d MMM yyyy HH:mm")
      allDigests.filter(digest => digest.storeProposals)
                .foreach(digest => {
        client.hset(digestRedisKey + digest.value, proposalId, now)
      })
  }

  /**
    * Delete a proposal from the email digest queue.
    *
    * @param proposalId the actual new proposal
    */
  def deleteProposal(proposalId : String): Unit = Redis.pool.withClient {
    implicit client =>
      allDigests.foreach(digest => {
        if (client.hexists(digestRedisKey + digest.value, proposalId)) {
          client.hdel(digestRedisKey + digest.value, proposalId)
        }
      })
  }

  /**
    * The pending proposals for the given digest.
    *
    * @param digest the digest to query
    * @return list of proposals
    */
  def pendingProposals(digest : Digest) : Map[String, String] = Redis.pool.withClient {
    implicit client =>
      client.hgetAll(digestRedisKey + digest.value).map {
        case (key: String, value: String) =>
          (key, value)
      }
  }

  def saveLatestExecutionOf(digest: Digest, instant: Instant) = Redis.pool.withClient {
    implicit client =>
      client.set(s"""Digest:${digest.value}:LatestExecution""", ISODateTimeFormat.dateTime().print(instant))
  }

  def latestExecutionOf(digest: Digest): Option[Instant] = Redis.pool.withClient {
    implicit client =>
      client.get(s"""Digest:${digest.value}:LatestExecution""").map{ isoDateStr =>
        ISODateTimeFormat.dateTime().parseDateTime(isoDateStr).toInstant
      }
  }
  def processDigest[T](digest: Digest, webuserId: String)(call: Function4[Webuser, NotificationUserPreference, Html, String, T]): Option[T] = {
    processDigest(digest, Some(List(webuserId)), false)(call).headOption
  }

  def processDigest[T](digest: Digest, maybeOnlyForUsers: Option[List[String]] = None, storeDigestExecuted: Boolean = true)(call: Function4[Webuser, NotificationUserPreference, Html, String, T]): List[T] = {
    val startingRange = Digest.latestExecutionOf(digest).getOrElse(Instant.EPOCH)
    val endingRange = Instant.now()

    val digestedEvents = Event.loadDigestEventsBetween(startingRange, endingRange)
    val proposalIds = digestedEvents.map(_.objRef().get).toSet

    play.Logger.of("models.Digest").debug(s"""Digest [${digest.value}] => Found ${proposalIds.size} concerned proposal(s)""")

    val proposalsByWatcherId = proposalIds
      .map{ proposalId => ProposalUserWatchPreference.proposalWatchers(proposalId).map((proposalId, _)) }
      .flatten
      .groupBy(_._2.webuserId)

    val concernedWatcherNotificationPreferences = proposalsByWatcherId.keys
      // if maybeOnlyForUsers is set, filtering watchers based on this list
      // otherwise keeping only watchers who subscribed to target digest frequency
      .filter{ watcherId => maybeOnlyForUsers.map(_.contains(watcherId)).getOrElse(true) }
      .map{ watcherId => Tuple2(watcherId, NotificationUserPreference.load(watcherId)) }
      .filter{ notifUserPref => maybeOnlyForUsers.map{ _ => true }.getOrElse(notifUserPref._2.digestFrequency == digest.value)}
      .toSet

    val proposalsByConcernedWatcherId = proposalsByWatcherId.filterKeys{ watcherId => concernedWatcherNotificationPreferences.map(_._1).contains(watcherId) }

    play.Logger.of("models.Digest").debug(s"""${proposalsByWatcherId.keys.size} watchers filtered to ${concernedWatcherNotificationPreferences.size} watchers""")

    val proposalsById = Proposal.loadAndParseProposals(proposalsByConcernedWatcherId.values.flatten.map(_._1).toSet)

    val result = concernedWatcherNotificationPreferences.toList.flatMap { case (watcherId, notifUserPrefs) =>
      Webuser.findByUUID(watcherId).flatMap { watcher =>
        val userNotificationEvents = notifUserPrefs.eventIds.map(NotificationEvent.valueOf(_).get)
        val watcherInfosByProposalId = proposalsByConcernedWatcherId
          .get(watcherId).get
          .groupBy(_._1)
          .mapValues { proposalsAndWatcher => proposalsAndWatcher.map{ case (proposal, watcher) => watcher } }

        val events = digestedEvents
          .filter{ event => watcherInfosByProposalId.contains(event.proposalId) }
          // Removing events where watcher is the initiator
          .filterNot(_.creator == watcher.uuid)
          // Removing events where user is not a watcher
          .filter { event =>
            val watcherEntryForProposal = watcherInfosByProposalId.get(event.proposalId).get.find(_.webuserId == watcher.uuid).get
            watcherInfosByProposalId.contains(event.proposalId) &&
              (event.date.isAfter(watcherEntryForProposal.startedWatchingAt) || event.date.isEqual(watcherEntryForProposal.startedWatchingAt))
          }
          // Removing events types not matching with user's prefs
          .filter{ event => userNotificationEvents.map(_.applicableEventTypes).flatten.contains(event.getClass) }

        if(events.nonEmpty) {
          val htmlContent = views.html.Mails.digest.sendDigest(UserDigest(watcher, digest, notifUserPrefs, events, proposalsById))
          val textContent = views.txt.Mails.digest.sendDigest(UserDigest(watcher, digest, notifUserPrefs, events, proposalsById)).toString()
          Some(call(watcher, notifUserPrefs, htmlContent, textContent))
        } else {
          None
        }
      }
    }

    if(storeDigestExecuted) {
      Digest.saveLatestExecutionOf(digest, endingRange)
    }

    result
  }
}

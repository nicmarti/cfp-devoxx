package models

import library.Redis
import org.joda.time.Instant
import play.api.libs.json.{Format, Json}
import play.twirl.api.TxtFormat.raw
import play.twirl.api.{Html, TxtFormat}

import scala.collection.JavaConversions.iterableAsScalaIterable

case class AutoWatch(id: AutoWatch.AutoWatchId, labelI18nKey: Function1[Webuser, String], applicableTo: Function[Webuser, Boolean] = (_) => true)

object AutoWatch {
  type AutoWatchId = String

  val ONCE_PROPOSAL_SUBMITTED: AutoWatch = AutoWatch("ONCE_PROPOSAL_SUBMITTED", (_) => "autowatch.options.once.proposal.submitted")
  val AFTER_INTERACTION: AutoWatch = AutoWatch("AFTER_INTERACTION", (webuser) => if (Webuser.isMember(webuser.uuid, "cfp")) {
    "autowatch.options.after.interaction"
  } else {
    "autowatch.options.after.gt-interaction"
  })
  val AFTER_COMMENT: AutoWatch = AutoWatch("AFTER_COMMENT", (_) => "autowatch.options.after.comment", (user) => Webuser.isMember(user.uuid, "cfp"))
  val MANUAL_WATCH_ONLY: AutoWatch = AutoWatch("MANUAL_WATCH_ONLY", (_) => "autowatch.options.manual.watch.only")

  val allAutowatches: Seq[AutoWatch] = List(ONCE_PROPOSAL_SUBMITTED, AFTER_INTERACTION, AFTER_COMMENT, MANUAL_WATCH_ONLY)

}

case class NotificationEvent(
                              id: NotificationEvent.NotificationEventId,
                              labelI18nKey: String,
                              applicableTo: Function[Webuser, Boolean],
                              onlyForAutoWatches: Option[List[AutoWatch]] = None,
                              // It is important to have ProposalEvent here (and not Event) as we need proposalId
                              // on event to resolve its watchers list during digesting
                              applicableEventTypes: List[Class[_ <: ProposalEvent]]
                            ) {}

object NotificationEvent {
  type NotificationEventId = String

  val ONCE_PROPOSAL_SUBMITTED: NotificationEvent = NotificationEvent(
    id = "ONCE_PROPOSAL_SUBMITTED", labelI18nKey = "email.notifications.events.once.proposal.submitted",
    applicableTo = _ => true,
    onlyForAutoWatches = Option(List(AutoWatch.ONCE_PROPOSAL_SUBMITTED)),
    applicableEventTypes = List(
      classOf[ProposalSubmissionEvent]
    )
  )
  val PROPOSAL_CONTENT_UPDATED: NotificationEvent = NotificationEvent(
    id = "PROPOSAL_CONTENT_UPDATED", labelI18nKey = "email.notifications.events.once.proposal.content.updated",
    applicableTo = _ => true,
    applicableEventTypes = List(
      classOf[ChangedTypeOfProposalEvent],
      classOf[UpdatedSubmittedProposalEvent]
    )
  )
  val PROPOSAL_RESUBMITTED: NotificationEvent = NotificationEvent(
    id = "PROPOSAL_RESUBMITTED", labelI18nKey = "email.notifications.events.once.proposal.is.submitted.again",
    applicableTo = _ => true,
    applicableEventTypes = List(
      classOf[ProposalResubmitedEvent]
    )
  )

  // Deprecated as GT don't have access to public comments
  // I assume that we should bw able to remove this entry as no GT has created any notification pref as of Nov 25th 2021
  val DEPRECATED_PROPOSAL_PUBLIC_COMMENT_SUBMITTED: NotificationEvent = NotificationEvent(
    id = "PROPOSAL_GT_COMMENT_SUBMITTED", labelI18nKey = "email.notifications.events.once.public.comment.submitted",
    applicableTo = _ => false,
    applicableEventTypes = List(
      classOf[ProposalPublicCommentSentBySpeakerEvent],
      classOf[ProposalPublicCommentSentByReviewersEvent]
    )
  )
  val PROPOSAL_PUBLIC_COMMENT_SUBMITTED: NotificationEvent = NotificationEvent(
    id = "PROPOSAL_PUBLIC_COMMENT_SUBMITTED", labelI18nKey = "email.notifications.events.once.public.comment.submitted",
    applicableTo = user => Webuser.isMember(user.uuid, "cfp"),
    applicableEventTypes = List(
      classOf[ProposalPublicCommentSentBySpeakerEvent],
      classOf[ProposalPublicCommentSentByReviewersEvent]
    )
  )
  val PROPOSAL_INTERNAL_COMMENT_SUBMITTED: NotificationEvent = NotificationEvent(
    id = "PROPOSAL_INTERNAL_COMMENT_SUBMITTED", labelI18nKey = "email.notifications.events.once.internal.comment.submitted",
    applicableTo = user => Webuser.isMember(user.uuid, "cfp"),
    applicableEventTypes = List(
      classOf[ProposalPrivateCommentSentByComiteeEvent]
    )
  )
  val PROPOSAL_SPEAKERS_LIST_ALTERED: NotificationEvent = NotificationEvent(
    id = "PROPOSAL_SPEAKERS_LIST_ALTERED", labelI18nKey = "email.notifications.events.once.proposal.speakers.altered",
    applicableTo = user => Webuser.isMember(user.uuid, "cfp"),
    applicableEventTypes = List(
      classOf[UpdatedProposalSpeakersListEvent],
      classOf[AddedSecondarySpeakerToProposalEvent],
      classOf[RemovedSecondarySpeakerFromProposalEvent],
      classOf[ReplacedProposalSecondarySpeakerEvent]
    )
  )
  val PROPOSAL_COMITEE_VOTES_RESETTED: NotificationEvent = NotificationEvent(
    id = "PROPOSAL_COMITEE_VOTES_RESETTED", labelI18nKey = "email.notifications.events.once.comitee.votes.resetted",
    applicableTo = user => Webuser.isMember(user.uuid, "cfp"),
    applicableEventTypes = List(
      classOf[AllProposalVotesResettedEvent]
    )
  )
  val PROPOSAL_FINAL_APPROVAL_SUBMITTED: NotificationEvent = NotificationEvent(
    id = "PROPOSAL_FINAL_APPROVAL_SUBMITTED", labelI18nKey = "email.notifications.events.once.proposal.final.approval.provided",
    applicableTo = user => Webuser.isMember(user.uuid, "cfp"),
    applicableEventTypes = List(
      classOf[SpeakerAcceptedPropositionApprovalEvent]
    )
  )

  val allNotificationEvents: List[NotificationEvent] = List(
    ONCE_PROPOSAL_SUBMITTED, PROPOSAL_CONTENT_UPDATED, PROPOSAL_RESUBMITTED,
    DEPRECATED_PROPOSAL_PUBLIC_COMMENT_SUBMITTED,
    PROPOSAL_PUBLIC_COMMENT_SUBMITTED, PROPOSAL_INTERNAL_COMMENT_SUBMITTED,
    PROPOSAL_SPEAKERS_LIST_ALTERED, PROPOSAL_COMITEE_VOTES_RESETTED,
    PROPOSAL_FINAL_APPROVAL_SUBMITTED
  )

  def valueOf(id: String): Option[NotificationEvent] = {
    allNotificationEvents.find(_.id == id)
  }

  def countEventsOfTypes(events: Iterable[ProposalEvent], types: Iterable[Class[_]]): Int = {
    types.foldLeft(0) { (total, eventType) => total + events.count(_.isOfSameTypeThan(eventType)) }
  }

  def countEventsOfType[T <: ProposalEvent](events: Iterable[ProposalEvent]): Int = {
    events.count { e => e.isInstanceOf[T] }
  }

  def hasEventOfTypes(events: Iterable[ProposalEvent], types: Class[_]*): Boolean = {
    countEventsOfTypes(events, types) > 0
  }
}

case class NotificationUserPreference(autowatchId: AutoWatch.AutoWatchId, autowatchFilterForTrackIds: Option[List[String]], digestFrequency: Digest.Frequency, eventIds: List[NotificationEvent.NotificationEventId]) {
  val autoWatch: Option[AutoWatch] = AutoWatch.allAutowatches.find(_.id == autowatchId)
}

object NotificationUserPreference {
  implicit val notifUserPrefFormat: Format[NotificationUserPreference] = Json.format[NotificationUserPreference]

  val DEFAULT_FALLBACK_PREFERENCES: NotificationUserPreference = NotificationUserPreference(AutoWatch.MANUAL_WATCH_ONLY.id, None, Digest.NEVER.value, List())

  def applyForm(autowatchId: AutoWatch.AutoWatchId, autowatchPerTrack: String, autowatchFilterForTrackIds: Option[List[String]], digestFrequency: Digest.Frequency, eventIds: List[NotificationEvent.NotificationEventId]): NotificationUserPreference = {
    NotificationUserPreference(autowatchId, autowatchFilterForTrackIds, digestFrequency, eventIds)
  }

  def unapplyForm(notifUserPref: NotificationUserPreference): Option[(AutoWatch.AutoWatchId, String, Option[List[String]], Digest.Frequency, List[NotificationEvent.NotificationEventId])] = {
    Some(notifUserPref.autowatchId, notifUserPref.autowatchFilterForTrackIds.map { _ => "autoWatchFilteredTracks" }.getOrElse("autoWatchAllTracks"), notifUserPref.autowatchFilterForTrackIds, notifUserPref.digestFrequency, notifUserPref.eventIds)
  }

  def save(webUserId: String, notifUserPref: NotificationUserPreference): String = Redis.pool.withClient {
    implicit client =>
      val json = Json.toJson(notifUserPref).toString()
      client.set(s"""NotificationUserPreference:${ConferenceDescriptor.current().eventCode}:${webUserId}""", json)
  }

  def loadAll(webUserIds: List[String]): Map[String, NotificationUserPreference] = Redis.pool.withClient { implicit client =>
    if (webUserIds.isEmpty) {
      Map()
    } else {
      val notifUserPrefsKeys = webUserIds.map(webUserId => s"NotificationUserPreference:${ConferenceDescriptor.current().eventCode}:${webUserId}")
      val notifPrefEntries = for ((webUserId, notificationPrefJSON) <- (webUserIds zip client.mget(notifUserPrefsKeys: _*).map(Option(_))))
        yield webUserId -> notificationPrefJSON.map(Json.parse(_).as[NotificationUserPreference]).getOrElse(DEFAULT_FALLBACK_PREFERENCES)

      notifPrefEntries.toMap
    }
  }

  def load(webUserId: String): NotificationUserPreference = {
    loadKey(s"NotificationUserPreference:${ConferenceDescriptor.current().eventCode}:${webUserId}")
  }

  private def loadKey(key: String): NotificationUserPreference = Redis.pool.withClient {
    implicit client =>
      val json = client.get(key)
      json.map {
        Json.parse(_).as[NotificationUserPreference]
      }.getOrElse(DEFAULT_FALLBACK_PREFERENCES)
  }

  def loadAllForCurrentYear(): Set[Tuple2[String, NotificationUserPreference]] = Redis.pool.withClient {
    implicit client =>
      client.keys(s"NotificationUserPreference:${ConferenceDescriptor.current().eventCode}:*").map { key =>
        Tuple2(key.substring(s"NotificationUserPreference:${ConferenceDescriptor.current().eventCode}:".length), loadKey(key))
      }
  }
}

case class Watcher(webuserId: String, proposalId: String, startedWatchingAt: Instant)

object Watcher {
  def userWatchedProposals(webUserId: String): List[Watcher] = Redis.pool.withClient { client =>
    client.zrangeWithScores(s"WatchedProposals:ByWatcher:${webUserId}", 0, -1)
      .toList
      .map { proposalWithScore => Watcher(webUserId, proposalWithScore.getElement, new Instant(proposalWithScore.getScore.toLong)) }
  }

  def proposalWatchers(proposalId: String): List[Watcher] = Redis.pool.withClient {
    implicit client =>
      client.zrangeWithScores(s"""Watchers:${proposalId}""", 0, -1)
        .toList
        .map { watcherWithScore => Watcher(watcherWithScore.getElement, proposalId, new Instant(watcherWithScore.getScore.toLong)) }
  }

  def addProposalWatcher(proposalId: String, webUserId: String, automatic: Boolean): Unit = Redis.pool.withClient {
    implicit client =>
      val now = Instant.now().getMillis
      if (client.zadd(s"Watchers:${proposalId}", now, webUserId) == 1) { // If you use a tx.multi then the zadd returns a Response[Long]
        val watchEvent = if (automatic) {
          ProposalAutoWatchedEvent(webUserId, proposalId)
        } else {
          ProposalManuallyWatchedEvent(webUserId, proposalId)
        }
        Event.storeEvent(watchEvent)
      }
      client.zadd(s"WatchedProposals:ByWatcher:${webUserId}", now, proposalId)
  }

  def removeProposalWatcher(proposalId: String, webUserId: String): Unit = Redis.pool.withClient {
    implicit client =>
      Event.storeEvent(ProposalUnwatchedEvent(webUserId, proposalId))
      val tx = client.multi()
      tx.zrem(s"""Watchers:${proposalId}""", webUserId)
      tx.zrem(s"WatchedProposals:ByWatcher:${webUserId}", proposalId)
      tx.exec()
  }

  def watchersNotificationUserPreferencesFor(proposalId: String): Map[String, (NotificationUserPreference, Watcher)] = {
    val watchers = Watcher.proposalWatchers(proposalId)
    NotificationUserPreference.loadAll(watchers.map(_.webuserId))
      .map { entry =>
        entry._1 -> (entry._2, watchers.find(_.webuserId == entry._1).get)
      }
  }

  def eligibleWatchersForProposalEvent(proposalEvent: ProposalEvent, notifUserPreferenceByWatcherId: Map[String, NotificationUserPreference]): Seq[String] = {
    notifUserPreferenceByWatcherId.toSeq.flatMap { case (watcherId, notificationPreferences) =>
      val userNotificationEvents = notificationPreferences.eventIds.map(NotificationEvent.valueOf(_).get)
      // Removing events types not matching with user's prefs
      if (!userNotificationEvents.flatMap(_.applicableEventTypes).contains(proposalEvent.getClass)) {
        None
      } else {
        Some(watcherId)
      }
    }
  }

  def watchersOnProposalEvent(proposalEvent: ProposalEvent, excludeEventInitiatorFromWatchers: Boolean = true): Seq[String] = {
    val notifUserPreferenceByWatcherId = Watcher.watchersNotificationUserPreferencesFor(proposalEvent.proposalId)
      .filterKeys(watcherId => !excludeEventInitiatorFromWatchers || proposalEvent.creator != watcherId)
      .mapValues(_._1)

    Watcher.eligibleWatchersForProposalEvent(proposalEvent, notifUserPreferenceByWatcherId)
  }

  /**
    * @deprecated for single use only
    */
  def initializeWatchingProposals(): Set[Watcher] = Redis.pool.withClient { client =>
    val watchers = client.keys("Watchers:*").flatMap { watcherKey =>
      client.zrangeWithScores(watcherKey, 0, -1).map { watcherWithScore =>
        val userId = watcherWithScore.getElement
        val proposalId = watcherKey.substring("Watchers:".length)
        Watcher(userId, proposalId, new Instant(watcherWithScore.getScore.toLong))
      }
    }

    val tx = client.multi()
    watchers.foreach { watcher =>
      tx.zadd(s"WatchedProposals:ByWatcher:${watcher.webuserId}", watcher.startedWatchingAt.getMillis, watcher.proposalId)
    }
    tx.exec()
    watchers
  }

  def doAttic() = Redis.pool.withClient { client =>
    client.keys("Watchers:*").foreach { watcherKey =>
      client.del(watcherKey)
    }
    client.keys("WatchedProposals:*").foreach { watcherKey =>
      client.del(watcherKey)
    }
  }
}

case class ProposalUserWatchPreference(proposalId: String, webUserId: String, isWatcher: Boolean, watchingEvents: List[NotificationEvent])

object ProposalUserWatchPreference {

  def proposalUserWatchPreference(proposalId: String, webUserId: String): ProposalUserWatchPreference = {
    val userPrefs = NotificationUserPreference.load(webUserId)
    val watchers = Watcher.proposalWatchers(proposalId)
    ProposalUserWatchPreference(proposalId, webUserId, isWatcher = watchers.map(_.webuserId).contains(webUserId), userPrefs.eventIds.map {
      eventId => NotificationEvent.allNotificationEvents.find(_.id == eventId).get
    })
  }

  private def applyProposalUserPrefsAutowatch(userPreferences: Set[(String, NotificationUserPreference)], proposalId: String, havingAutowatch: AutoWatch): Unit = Redis.pool.withClient {
    implicit client =>
      Proposal.findProposalTrack(proposalId).map { track =>
        val allMatchingPreferences = userPreferences.filter { prefAndKey =>
          prefAndKey._2.autowatchId == havingAutowatch.id && (prefAndKey._2.autowatchFilterForTrackIds.isEmpty || prefAndKey._2.autowatchFilterForTrackIds.get.contains(track.id))
        }

        allMatchingPreferences.foreach { prefAndKey =>
          Watcher.addProposalWatcher(proposalId, prefAndKey._1, automatic = true)
        }
      }
  }

  def applyAllUserProposalAutowatch(proposalId: String, havingAutowatch: AutoWatch): Unit = {
    applyProposalUserPrefsAutowatch(NotificationUserPreference.loadAllForCurrentYear(), proposalId, havingAutowatch)
  }

  def applyUserProposalAutowatch(webUserId: String, proposalId: String, havingAutowatch: AutoWatch): Unit = {
    applyProposalUserPrefsAutowatch(Set((webUserId, NotificationUserPreference.load(webUserId))), proposalId, havingAutowatch)
  }
}

case class UserDigest(webuser: Webuser, digest: Digest, notificationUserPreference: NotificationUserPreference, events: List[ProposalEvent], proposalsById: Map[String, Proposal]) {
  def concernedByEvents_html(notifEvents: NotificationEvent*)(eventsPartial: Function1[List[ProposalEvent], Html]): Html =
    concernedByEvents(Html(""), notifEvents)(eventsPartial)

  def concernedByEvents_txt(notifEvents: NotificationEvent*)(eventsPartial: Function1[List[ProposalEvent], TxtFormat.Appendable]): TxtFormat.Appendable =
    concernedByEvents(raw(""), notifEvents)(eventsPartial)

  private def concernedByEvents[CONTENT_TYPE](emptyContent: CONTENT_TYPE, notifEvents: Seq[NotificationEvent])(eventsPartial: Function1[List[ProposalEvent], CONTENT_TYPE]): CONTENT_TYPE = {
    if (notificationUserPreference.eventIds.intersect(notifEvents.map(_.id)).nonEmpty) {
      val applicableEventTypes = notifEvents.flatMap(_.applicableEventTypes)
      val concernedEvents = events.filter { event => applicableEventTypes.exists(event.isOfSameTypeThan(_)) }
      if (concernedEvents.nonEmpty) {
        eventsPartial.apply(concernedEvents)
      } else {
        emptyContent
      }
    } else {
      emptyContent
    }
  }

  def distinctProposalsOf[CONTENT_TYPE](events: List[ProposalEvent])(partial: Function3[Proposal, EventLink, List[ProposalEvent], CONTENT_TYPE]): List[CONTENT_TYPE] = {
    // distinctBy is no available on collections before Scala 2.13 :'(
    scala.reflect.internal.util.Collections.distinctBy(events.map { e => (proposalsById.get(e.proposalId).get, e.linksFor(webuser).head) })(_._1)
      .map { case (proposal, link) => partial.apply(proposal, link, events.filter(_.proposalId == proposal.id)) }
  }
}

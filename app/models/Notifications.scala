package models

import library.Redis
import org.joda.time.Instant
import play.api.libs.json.Json
import play.twirl.api.TxtFormat.raw
import play.twirl.api.{Html, TxtFormat}

import scala.collection.JavaConversions.iterableAsScalaIterable

case class AutoWatch(id: AutoWatch.AutoWatchId, labelI18nKey: Function1[Webuser, String], applicableTo: Function[Webuser, Boolean] = (_) => true)

object AutoWatch {
  type AutoWatchId = String

  val ONCE_PROPOSAL_SUBMITTED = AutoWatch("ONCE_PROPOSAL_SUBMITTED", (_) => "autowatch.options.once.proposal.submitted")
  val AFTER_INTERACTION = AutoWatch("AFTER_INTERACTION", (webuser) => if(Webuser.isMember(webuser.uuid, "cfp")) { "autowatch.options.after.interaction" } else { "autowatch.options.after.gt-interaction" })
  val AFTER_COMMENT = AutoWatch("AFTER_COMMENT", (_) => "autowatch.options.after.comment", (user) => Webuser.isMember(user.uuid, "cfp"))
  val MANUAL_WATCH_ONLY = AutoWatch("MANUAL_WATCH_ONLY", (_) => "autowatch.options.manual.watch.only")

  val allAutowatches = List(ONCE_PROPOSAL_SUBMITTED, AFTER_INTERACTION, AFTER_COMMENT, MANUAL_WATCH_ONLY)

}

case class NotificationEvent(
    id: NotificationEvent.NotificationEventId,
    labelI18nKey: String,
    applicableTo: Function[Webuser, Boolean],
    onlyForAutoWatches: Option[List[AutoWatch]]=None,
    // It is important to have ProposalEvent here (and not Event) as we need proposalId
    // on event to resolve its watchers list during digesting
    applicableEventTypes: List[Class[_ <: ProposalEvent]]
){}

object NotificationEvent {
  type NotificationEventId = String

  val ONCE_PROPOSAL_SUBMITTED = NotificationEvent(
    id="ONCE_PROPOSAL_SUBMITTED", labelI18nKey="email.notifications.events.once.proposal.submitted",
    applicableTo = _ => true,
    onlyForAutoWatches=Option(List(AutoWatch.ONCE_PROPOSAL_SUBMITTED)),
    applicableEventTypes=List(
      classOf[ProposalSubmissionEvent]
    )
  )
  val PROPOSAL_CONTENT_UPDATED = NotificationEvent(
    id="PROPOSAL_CONTENT_UPDATED", labelI18nKey="email.notifications.events.once.proposal.content.updated",
    applicableTo = _ => true,
    applicableEventTypes = List(
      classOf[ChangedTypeOfProposalEvent],
      classOf[UpdatedSubmittedProposalEvent]
    )
  )
  val PROPOSAL_RESUBMITTED = NotificationEvent(
    id="PROPOSAL_RESUBMITTED", labelI18nKey="email.notifications.events.once.proposal.is.submitted.again",
    applicableTo = _ => true,
    applicableEventTypes = List(
      classOf[ProposalResubmitedEvent]
    )
  )

  // Deprecated as GT don't have access to public comments
  // I assume that we should bw able to remove this entry as no GT has created any notification pref as of Nov 25th 2021
  val DEPRECATED_PROPOSAL_PUBLIC_COMMENT_SUBMITTED = NotificationEvent(
    id="PROPOSAL_GT_COMMENT_SUBMITTED", labelI18nKey="email.notifications.events.once.public.comment.submitted",
    applicableTo = _ => false,
    applicableEventTypes = List(
      classOf[ProposalPublicCommentSentBySpeakerEvent],
      classOf[ProposalPublicCommentSentByReviewersEvent]
    )
  )
  val PROPOSAL_PUBLIC_COMMENT_SUBMITTED = NotificationEvent(
    id="PROPOSAL_PUBLIC_COMMENT_SUBMITTED", labelI18nKey="email.notifications.events.once.public.comment.submitted",
    applicableTo = user => Webuser.isMember(user.uuid, "cfp"),
    applicableEventTypes = List(
      classOf[ProposalPublicCommentSentBySpeakerEvent],
      classOf[ProposalPublicCommentSentByReviewersEvent]
    )
  )
  val PROPOSAL_INTERNAL_COMMENT_SUBMITTED = NotificationEvent(
    id="PROPOSAL_INTERNAL_COMMENT_SUBMITTED", labelI18nKey="email.notifications.events.once.internal.comment.submitted",
    applicableTo = user => Webuser.isMember(user.uuid, "cfp"),
    applicableEventTypes = List(
      classOf[ProposalPrivateCommentSentByComiteeEvent]
    )
  )
  val PROPOSAL_SPEAKERS_LIST_ALTERED = NotificationEvent(
    id="PROPOSAL_SPEAKERS_LIST_ALTERED", labelI18nKey="email.notifications.events.once.proposal.speakers.altered",
    applicableTo = user => Webuser.isMember(user.uuid, "cfp"),
    applicableEventTypes = List(
      classOf[UpdatedProposalSpeakersListEvent],
      classOf[AddedSecondarySpeakerToProposalEvent],
      classOf[RemovedSecondarySpeakerFromProposalEvent],
      classOf[ReplacedProposalSecondarySpeakerEvent]
    )
  )
  val PROPOSAL_COMITEE_VOTES_RESETTED = NotificationEvent(
    id="PROPOSAL_COMITEE_VOTES_RESETTED", labelI18nKey="email.notifications.events.once.comitee.votes.resetted",
    applicableTo = user => Webuser.isMember(user.uuid, "cfp"),
    applicableEventTypes = List(
      classOf[AllProposalVotesResettedEvent]
    )
  )
  val PROPOSAL_FINAL_APPROVAL_SUBMITTED = NotificationEvent(
    id="PROPOSAL_FINAL_APPROVAL_SUBMITTED", labelI18nKey="email.notifications.events.once.proposal.final.approval.provided",
    applicableTo = user => Webuser.isMember(user.uuid, "cfp"),
    applicableEventTypes = List(
      classOf[SpeakerAcceptedPropositionApprovalEvent]
    )
  )

  val allNotificationEvents = List(
    ONCE_PROPOSAL_SUBMITTED, PROPOSAL_CONTENT_UPDATED, PROPOSAL_RESUBMITTED,
    DEPRECATED_PROPOSAL_PUBLIC_COMMENT_SUBMITTED,
    PROPOSAL_PUBLIC_COMMENT_SUBMITTED, PROPOSAL_INTERNAL_COMMENT_SUBMITTED,
    PROPOSAL_SPEAKERS_LIST_ALTERED, PROPOSAL_COMITEE_VOTES_RESETTED,
    PROPOSAL_FINAL_APPROVAL_SUBMITTED
  )

  def valueOf(id: String) = {
    allNotificationEvents.find(_.id == id)
  }

  def countEventsOfTypes(events: List[ProposalEvent], types: Iterable[Class[_]]) = {
    types.foldLeft(0) { (total, eventType) => total + events.count(_.isOfSameTypeThan(eventType)) }
  }

  def countEventsOfType[T <: ProposalEvent](events: List[ProposalEvent]) = {
    events.count { e => e.isInstanceOf[T] }
  }

  def hasEventOfTypes(events: List[ProposalEvent], types: Class[_]*) = {
    countEventsOfTypes(events, types) > 0
  }
}

case class NotificationUserPreference(autowatchId: AutoWatch.AutoWatchId, autowatchFilterForTrackIds: Option[List[String]], digestFrequency: Digest.Frequency, eventIds: List[NotificationEvent.NotificationEventId]){
  val autoWatch = AutoWatch.allAutowatches.find(_.id == autowatchId)
}
object NotificationUserPreference {
  implicit val notifUserPrefFormat = Json.format[NotificationUserPreference]

  val DEFAULT_FALLBACK_PREFERENCES = NotificationUserPreference(AutoWatch.MANUAL_WATCH_ONLY.id, None, Digest.NEVER.value, List())

  def applyForm(autowatchId: AutoWatch.AutoWatchId, autowatchPerTrack: String, autowatchFilterForTrackIds: Option[List[String]], digestFrequency: Digest.Frequency, eventIds: List[NotificationEvent.NotificationEventId]): NotificationUserPreference = {
    NotificationUserPreference(autowatchId, autowatchFilterForTrackIds, digestFrequency, eventIds)
  }

  def unapplyForm(notifUserPref: NotificationUserPreference): Option[(AutoWatch.AutoWatchId, String, Option[List[String]], Digest.Frequency, List[NotificationEvent.NotificationEventId])] = {
    Some(notifUserPref.autowatchId, notifUserPref.autowatchFilterForTrackIds.map{_ => "autoWatchFilteredTracks"}.getOrElse("autoWatchAllTracks"), notifUserPref.autowatchFilterForTrackIds, notifUserPref.digestFrequency, notifUserPref.eventIds)
  }

  def save(webUserId: String, notifUserPref: NotificationUserPreference): String = Redis.pool.withClient {
    implicit client =>
      val json = Json.toJson(notifUserPref).toString()
      client.set(s"""NotificationUserPreference:${ConferenceDescriptor.current().eventCode}:${webUserId}""", json)
  }

  def loadAll(webUserIds: List[String]): Map[String, NotificationUserPreference] = Redis.pool.withClient { implicit client =>
    val notifUserPrefsKeys = webUserIds.map(webUserId => s"""NotificationUserPreference:${ConferenceDescriptor.current().eventCode}:${webUserId}""")
    val notifPrefEntries = for ((webUserId, notificationPrefJSON) <- (webUserIds zip client.mget(notifUserPrefsKeys:_*)))
      yield webUserId -> Json.parse(notificationPrefJSON).as[NotificationUserPreference]

    notifPrefEntries.toMap
  }

  def load(webUserId: String): NotificationUserPreference =  {
    loadKey(s"""NotificationUserPreference:${ConferenceDescriptor.current().eventCode}:${webUserId}""")
  }

  private def loadKey(key: String): NotificationUserPreference = Redis.pool.withClient {
    implicit client =>
      val json = client.get(key)
      json.map { Json.parse(_).as[NotificationUserPreference] }.getOrElse(DEFAULT_FALLBACK_PREFERENCES)
  }

  def loadAllForCurrentYear(): Set[Tuple2[String, NotificationUserPreference]] = Redis.pool.withClient {
    implicit client =>
      client.keys(s"""NotificationUserPreference:${ConferenceDescriptor.current().eventCode}:*""").map { key =>
        Tuple2(key.substring(s"""NotificationUserPreference:${ConferenceDescriptor.current().eventCode}:""".length), loadKey(key))
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
        .map{ watcherWithScore => Watcher(watcherWithScore.getElement, proposalId, new Instant(watcherWithScore.getScore.toLong)) }
  }

  def addProposalWatcher(proposalId: String, webUserId: String, automatic: Boolean): Unit = Redis.pool.withClient {
    implicit client =>
      val tx = client.multi()
      val now = Instant.now().getMillis
      if(tx.zadd(s"""Watchers:${proposalId}""", now, webUserId) == 1) {
        val watchEvent = if(automatic) {
          ProposalAutoWatchedEvent(webUserId, proposalId)
        } else {
          ProposalManuallyWatchedEvent(webUserId, proposalId)
        }
        Event.storeEvent(watchEvent)
      }
      tx.zadd(s"WatchedProposals:ByWatcher:${webUserId}", now, proposalId)
      tx.exec()
  }

  def removeProposalWatcher(proposalId: String, webUserId: String): Unit = Redis.pool.withClient {
    implicit client =>
      Event.storeEvent(ProposalUnwatchedEvent(webUserId, proposalId))
      val tx = client.multi()
      tx.zrem(s"""Watchers:${proposalId}""", webUserId)
      tx.zrem(s"WatchedProposals:ByWatcher:${webUserId}", proposalId)
      tx.exec()
  }

  def watchersOnProposalEvent(proposalEvent: ProposalEvent, excludeEventInitiatorFromWatchers: Boolean = true): Seq[String] = {
    val watchers = Watcher.proposalWatchers(proposalEvent.proposalId)
      .filterNot(excludeEventInitiatorFromWatchers && proposalEvent.creator == _.webuserId)

    val watchersById = NotificationUserPreference.loadAll(watchers.map(_.webuserId))

    watchersById.toSeq.flatMap { case (watcherId, notificationPreferences) =>
      val userNotificationEvents = notificationPreferences.eventIds.map(NotificationEvent.valueOf(_).get)
      // Removing events types not matching with user's prefs
      if(!userNotificationEvents.map(_.applicableEventTypes).flatten.contains(proposalEvent.getClass)) {
        None
      } else {
        Some(watcherId)
      }
    }
  }

  /**
    * @deprecated for single use only
    */
  def initializeWatchingProposals(): Set[Watcher] = Redis.pool.withClient { client =>
    val watchers = client.keys("Watchers:*").map { watcherKey =>
      client.zrangeWithScores(watcherKey, 0, -1).map { watcherWithScore =>
        val userId = watcherWithScore.getElement
        val proposalId = watcherKey.substring("Watchers:".length)
        Watcher(userId, proposalId, new Instant(watcherWithScore.getScore.toLong))
      }
    }.flatten

    val tx = client.multi()
    watchers.foreach { watcher =>
      tx.zadd(s"WatchedProposals:ByWatcher:${watcher.webuserId}", watcher.startedWatchingAt.getMillis, watcher.proposalId)
    }
    tx.exec()
    watchers
  }
}

case class ProposalUserWatchPreference(proposalId: String, webUserId: String, isWatcher: Boolean, watchingEvents: List[NotificationEvent])
object ProposalUserWatchPreference {

  def proposalUserWatchPreference(proposalId: String, webUserId: String): ProposalUserWatchPreference = {
      val userPrefs = NotificationUserPreference.load(webUserId)
      val watchers = Watcher.proposalWatchers(proposalId)
      ProposalUserWatchPreference(proposalId, webUserId, isWatcher = watchers.map(_.webuserId).contains(webUserId), userPrefs.eventIds.map{
        eventId => NotificationEvent.allNotificationEvents.find(_.id == eventId).get
      })
  }

  private def applyProposalUserPrefsAutowatch(userPreferences: Set[(String, NotificationUserPreference)], proposalId: String, havingAutowatch: AutoWatch): Unit = Redis.pool.withClient {
    implicit client =>
      Proposal.findProposalTrack(proposalId).map { track =>
        val allMatchingPreferences = userPreferences.filter{ prefAndKey =>
          prefAndKey._2.autowatchId == havingAutowatch.id && (prefAndKey._2.autowatchFilterForTrackIds.isEmpty || prefAndKey._2.autowatchFilterForTrackIds.get.contains(track.id))
        }

        allMatchingPreferences.foreach{ prefAndKey =>
          Watcher.addProposalWatcher(proposalId, prefAndKey._1, true)
        }
      }
  }

  def applyAllUserProposalAutowatch(proposalId: String, havingAutowatch: AutoWatch) {
    applyProposalUserPrefsAutowatch(NotificationUserPreference.loadAllForCurrentYear(), proposalId, havingAutowatch)
  }

  def applyUserProposalAutowatch(webUserId: String, proposalId: String, havingAutowatch: AutoWatch) {
    applyProposalUserPrefsAutowatch(Set((webUserId, NotificationUserPreference.load(webUserId))), proposalId, havingAutowatch)
  }
}

case class UserDigest(webuser: Webuser, digest: Digest, notificationUserPreference: NotificationUserPreference, events: List[ProposalEvent], proposalsById: Map[String, Proposal]) {
  def concernedByEvents_html(notifEvents: NotificationEvent*)(eventsPartial: Function1[List[ProposalEvent], Html]): Html =
    concernedByEvents(Html(""), notifEvents)(eventsPartial)
  def concernedByEvents_txt(notifEvents: NotificationEvent*)(eventsPartial: Function1[List[ProposalEvent], TxtFormat.Appendable]): TxtFormat.Appendable =
    concernedByEvents(raw(""), notifEvents)(eventsPartial)

  private def concernedByEvents[CONTENT_TYPE](emptyContent: CONTENT_TYPE, notifEvents: Seq[NotificationEvent])(eventsPartial: Function1[List[ProposalEvent], CONTENT_TYPE]): CONTENT_TYPE = {
    if(notificationUserPreference.eventIds.intersect(notifEvents.map(_.id)).nonEmpty) {
      val applicableEventTypes = notifEvents.map(_.applicableEventTypes).flatten
      val concernedEvents = events.filter{ event => applicableEventTypes.exists(event.isOfSameTypeThan(_)) }
      if(concernedEvents.nonEmpty) {
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
    scala.reflect.internal.util.Collections.distinctBy(events.map{ e => (proposalsById.get(e.proposalId).get, e.linksFor(webuser).head) })(_._1)
       .map{ case (proposal, link) => partial.apply(proposal, link, events.filter(_.proposalId == proposal.id)) }
  }
}

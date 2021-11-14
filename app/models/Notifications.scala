package models

import library.Redis
import play.api.libs.json.Json

case class AutoWatch(id: AutoWatch.AutoWatchId, labelI18nKey: String){}

object AutoWatch {
  type AutoWatchId = String

  val ONCE_PROPOSAL_SUBMITTED = AutoWatch("ONCE_PROPOSAL_SUBMITTED", "autowatch.options.once.proposal.submitted")
  val AFTER_INTERACTION = AutoWatch("AFTER_INTERACTION", "autowatch.options.after.interaction")
  val MANUAL_WATCH_ONLY = AutoWatch("MANUAL_WATCH_ONLY", "autowatch.options.manual.watch.only")

  val allAutowatches = List(ONCE_PROPOSAL_SUBMITTED, AFTER_INTERACTION, MANUAL_WATCH_ONLY)

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
  val PROPOSAL_PUBLIC_COMMENT_SUBMITTED = NotificationEvent(
    id="PROPOSAL_GT_COMMENT_SUBMITTED", labelI18nKey="email.notifications.events.once.public.comment.submitted",
    applicableTo = _ => true,
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
  val PROPOSAL_FINAL_APPROVAL_SUBMITTED = NotificationEvent(
    id="PROPOSAL_FINAL_APPROVAL_SUBMITTED", labelI18nKey="email.notifications.events.once.proposal.final.approval.provided",
    applicableTo = user => Webuser.isMember(user.uuid, "cfp"),
    applicableEventTypes = List(
      classOf[SpeakerAcceptedPropositionApprovalEvent]
    )
  )

  val allNotificationEvents = List(
    ONCE_PROPOSAL_SUBMITTED, PROPOSAL_CONTENT_UPDATED, PROPOSAL_RESUBMITTED,
    PROPOSAL_PUBLIC_COMMENT_SUBMITTED, PROPOSAL_INTERNAL_COMMENT_SUBMITTED,
    PROPOSAL_SPEAKERS_LIST_ALTERED, PROPOSAL_FINAL_APPROVAL_SUBMITTED
  )
}

case class NotificationUserPreference(autowatchId: AutoWatch.AutoWatchId, autowatchFilterForTrackIds: Option[List[String]], digestFrequency: Digest.Frequency, eventIds: List[NotificationEvent.NotificationEventId]){}
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

case class ProposalUserWatchPreference(proposalId: String, webUserId: String, isWatcher: Boolean, watchingEvents: List[NotificationEvent])
object ProposalUserWatchPreference {

  def proposalWatchers(proposalId: String): Set[String] = Redis.pool.withClient {
    implicit client =>
      client.smembers(s"""Watchers:${proposalId}""")
  }

  def addProposalWatcher(proposalId: String, webUserId: String, automatic: Boolean) = Redis.pool.withClient {
    implicit client =>
      if(client.sadd(s"""Watchers:${proposalId}""", webUserId) == 1) {
        val watchEvent = if(automatic) {
          ProposalAutoWatchedEvent(webUserId, proposalId)
        } else {
          ProposalManuallyWatchedEvent(webUserId, proposalId)
        }
        Event.storeEvent(watchEvent)
      }
  }

  def removeProposalWatcher(proposalId: String, webUserId: String) = Redis.pool.withClient {
    implicit client =>
      Event.storeEvent(ProposalUnwatchedEvent(webUserId, proposalId))
      client.del(s"""Watchers:${proposalId}""", webUserId)
  }

  def proposalUserWatchPreference(proposalId: String, webUserId: String): ProposalUserWatchPreference = {
      val userPrefs = NotificationUserPreference.load(webUserId)
      val watchers = this.proposalWatchers(proposalId)
      ProposalUserWatchPreference(proposalId, webUserId, isWatcher = watchers.contains(webUserId), userPrefs.eventIds.map{
        eventId => NotificationEvent.allNotificationEvents.find(_.id == eventId).get
      })
  }

  private def applyProposalUserPrefsAutowatch(userPreferences: Set[(String, NotificationUserPreference)], proposalId: String, havingAutowatch: AutoWatch) = Redis.pool.withClient {
    implicit client =>
      Proposal.findProposalTrack(proposalId).map { track =>
        val allMatchingPreferences = userPreferences.filter{ prefAndKey =>
          prefAndKey._2.autowatchId == havingAutowatch.id && (prefAndKey._2.autowatchFilterForTrackIds.isEmpty || prefAndKey._2.autowatchFilterForTrackIds.get.contains(track.id))
        }

        val tx = client.multi()
        allMatchingPreferences.foreach{ prefAndKey =>
          addProposalWatcher(proposalId, prefAndKey._1, true)
        }
        tx.exec()
      }
  }

  def applyAllUserProposalAutowatch(proposalId: String, havingAutowatch: AutoWatch) {
    applyProposalUserPrefsAutowatch(NotificationUserPreference.loadAllForCurrentYear(), proposalId, havingAutowatch)
  }

  def applyUserProposalAutowatch(webUserId: String, proposalId: String, havingAutowatch: AutoWatch) {
    applyProposalUserPrefsAutowatch(Set((webUserId, NotificationUserPreference.load(webUserId))), proposalId, havingAutowatch)
  }
}

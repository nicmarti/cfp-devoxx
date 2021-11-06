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

case class NotificationEvent(id: NotificationEvent.NotificationEventId, labelI18nKey: String, applicableTo: Function[Webuser, Boolean], onlyForAutoWatches: Option[List[AutoWatch]]=None){
}

object NotificationEvent {
  type NotificationEventId = String

  val ONCE_PROPOSAL_SUBMITTED = NotificationEvent("ONCE_PROPOSAL_SUBMITTED", "email.notifications.events.once.proposal.submitted", _ => true, Option(List(AutoWatch.ONCE_PROPOSAL_SUBMITTED)))
  val PROPOSAL_CONTENT_UPDATED = NotificationEvent("PROPOSAL_CONTENT_UPDATED", "email.notifications.events.once.proposal.content.updated", _ => true)
  val PROPOSAL_RESUBMITTED = NotificationEvent("PROPOSAL_RESUBMITTED", "email.notifications.events.once.proposal.is.submitted.again", _ => true)
  val PROPOSAL_PUBLIC_COMMENT_SUBMITTED = NotificationEvent("PROPOSAL_GT_COMMENT_SUBMITTED", "email.notifications.events.once.public.comment.submitted", _ => true)
  val PROPOSAL_INTERNAL_COMMENT_SUBMITTED = NotificationEvent("PROPOSAL_INTERNAL_COMMENT_SUBMITTED", "email.notifications.events.once.internal.comment.submitted", user => Webuser.isMember(user.uuid, "cfp"))
  val PROPOSAL_SPEAKERS_LIST_ALTERED = NotificationEvent("PROPOSAL_SPEAKERS_LIST_ALTERED", "email.notifications.events.once.proposal.speakers.altered", user => Webuser.isMember(user.uuid, "cfp"))
  val PROPOSAL_FINAL_APPROVAL_SUBMITTED = NotificationEvent("PROPOSAL_FINAL_APPROVAL_SUBMITTED", "email.notifications.events.once.proposal.final.approval.provided", user => Webuser.isMember(user.uuid, "cfp"))

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

  def load(webUserId: String): NotificationUserPreference = Redis.pool.withClient {
    implicit client =>
      val json = client.get(s"""NotificationUserPreference:${ConferenceDescriptor.current().eventCode}:${webUserId}""")
      json.map { Json.parse(_).as[NotificationUserPreference] }.getOrElse(DEFAULT_FALLBACK_PREFERENCES)
  }
}

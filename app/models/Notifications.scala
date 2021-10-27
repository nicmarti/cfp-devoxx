package models

import play.api.libs.json.{JsArray, JsString, JsValue, Json}

case class AutoWatch(id: String, labelI18nKey: String){}

object AutoWatch {

  val ONCE_PROPOSAL_SUBMITTED = AutoWatch("ONCE_PROPOSAL_SUBMITTED", "autowatch.options.once.proposal.submitted")
  val AFTER_INTERACTION = AutoWatch("AFTER_INTERACTION", "autowatch.options.after.interaction")
  val MANUAL_WATCH_ONLY = AutoWatch("MANUAL_WATCH_ONLY", "autowatch.options.manual.watch.only")

  val allAutowatches = List(ONCE_PROPOSAL_SUBMITTED, AFTER_INTERACTION, MANUAL_WATCH_ONLY)

}

case class NotificationEvent(id: String, labelI18nKey: String, applicableTo: Function[Webuser, Boolean], onlyForAutoWatches: Option[List[AutoWatch]]=None){
}

object NotificationEvent {

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

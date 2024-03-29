package controllers

import models.{AutoWatch, NotificationEvent, NotificationUserPreference}
import play.api.data.Forms.{list, mapping, nonEmptyText, optional, text}
import play.api.i18n.Messages

object CFPPreferencesController extends SecureCFPController {

  def index() = SecuredAction(IsMemberOfGroups(List("gticket", "cfp"))) {
    implicit request =>
      val notificationPreferences = NotificationUserPreference.load(request.webuser.uuid)
      Ok(views.html.CFPPreferences.showPreferences(request.webuser, notificationPreferences))
  }

  val notificationPreferencesForm = play.api.data.Form(mapping(
    "autowatchId" -> nonEmptyText(),
    "autoWatchTracks" -> nonEmptyText(),
    "autowatchFilterForTrackIds" -> optional(list(text)),
    "digestFrequency" -> nonEmptyText(),
    "eventIds" -> list(text)
  )(NotificationUserPreference.applyForm)(NotificationUserPreference.unapplyForm))

  def saveNotificationPreferences() = SecuredAction(IsMemberOfGroups(List("gticket", "cfp"))) {
    implicit request =>
      notificationPreferencesForm.bindFromRequest().fold(
        hasErrors => Redirect(routes.CFPPreferencesController.index()).flashing("error" -> Messages("email.notifications.invalid")),
        notificationPrefs => {
          NotificationUserPreference.save(request.webuser.uuid, notificationPrefs.copy(
            // Resetting autowatch to manual watch if user is not supposed to have access (from a security POV) to provided autowatch
            autowatchId = notificationPrefs.autoWatch.flatMap{ autoWatch =>
              if(autoWatch.applicableTo(request.webuser)){ Some(autoWatch.id) }else{ None }
            }.getOrElse(AutoWatch.MANUAL_WATCH_ONLY.id),
            eventIds = notificationPrefs.eventIds.filter{ eventId => NotificationEvent.valueOf(eventId).find(_.applicableTo(request.webuser)).map(_ => true).getOrElse(false) }
          ))
          Redirect(routes.CFPPreferencesController.index()).flashing("success" -> Messages("email.notifications.success"))
        }
      )
  }

}

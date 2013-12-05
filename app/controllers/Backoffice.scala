package controllers

import models.{Speaker, Event, Proposal, Webuser}
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import play.api.data.validation.Constraints._
import library.{DraftReminder, ZapActor}

/**
 * Backoffice actions, for maintenance and validation.
 *
 * Author: nicolas martignole
 * Created: 02/12/2013 21:34
 */
object Backoffice extends Controller with Secured {
  def allSpeakers = IsMemberOf("admin") {
    implicit uuid => implicit request =>
      Ok(views.html.Backoffice.allSpeakers(Webuser.allSpeakers.sortBy(_.email)))
  }

  def switchCFPAdmin(uuidSpeaker: String) = IsMemberOf("admin") {
    implicit uuid => implicit request =>
      if (Webuser.hasAccessToCFPAdmin(uuidSpeaker)) {
        Webuser.removeFromCFPAdmin(uuidSpeaker)
      } else {
        Webuser.addToCFPAdmin(uuidSpeaker)
      }
      Redirect(routes.Backoffice.allSpeakers)
  }

  def allDraftProposals() = IsMemberOf("admin") {
    implicit uuid => implicit request =>
      val proposals = Proposal.allDrafts()
      Ok(views.html.Backoffice.allDraftProposals(proposals))
  }

  def allSubmittedProposals() = IsMemberOf("admin") {
    implicit uuid => implicit request =>
      val proposals = Proposal.allSubmitted()
      Ok(views.html.Backoffice.allSubmittedProposals(proposals))
  }

  def moveProposalToTrash(proposalId: String) = IsMemberOf("admin") {
    implicit uuid => implicit request =>
      Proposal.delete(uuid, proposalId)
      val undoDelete = routes.Backoffice.moveProposalToDraft(proposalId).url
      Redirect(routes.Backoffice.allDraftProposals()).flashing("success" -> s"Deleted Proposal. <a href='$undoDelete'>Undo delete</a>")
  }

  def moveSubmittedProposalToTrash(proposalId: String) = IsMemberOf("admin") {
    implicit uuid => implicit request =>
      Proposal.delete(uuid, proposalId)
      val undoDelete = routes.Backoffice.moveProposalToDraft(proposalId).url
      Redirect(routes.Backoffice.allSubmittedProposals()).flashing("success" -> s"Deleted Proposal. <a href='$undoDelete'>Undo delete</a>")
  }

  def moveProposalToDraft(proposalId: String) = IsMemberOf("admin") {
    implicit uuid => implicit request =>
      Proposal.draft(uuid, proposalId)
      Redirect(routes.Backoffice.allSubmittedProposals()).flashing("success" -> s"Undeleted proposal ${proposalId}")
  }

  def moveProposalToSubmit(proposalId: String) = IsMemberOf("admin") {
    implicit uuid => implicit request =>
      Proposal.submit(uuid, proposalId)
      val undoSubmit = routes.Backoffice.moveProposalToDraft(proposalId).url
      Redirect(routes.Backoffice.allDraftProposals()).flashing("success" -> s"Proposal ${proposalId} was submitted. <a href='$undoSubmit'>Cancel this submission?</a>")
  }

  def sendReminderToSpeakersForDraft() = IsMemberOf("admin") {
    implicit uuid => implicit request =>
    // Send a message to an Actor
      ZapActor.actor ! DraftReminder()
      // Then redirect
      Redirect(routes.Backoffice.allDraftProposals()).flashing("success" -> "An email will be sent to Speakers with Draft proposal")
  }

  val formSecu = Form("secu" -> nonEmptyText())

  def deleteSpeaker(speakerUUIDToDelete: String) = IsMemberOf("admin") {
    implicit uuid => implicit request =>
      if (Webuser.isMember(speakerUUIDToDelete, "cfp") || Webuser.isMember(speakerUUIDToDelete, "admin")) {
        Redirect(routes.Backoffice.allDraftProposals()).flashing("error" -> s"We cannot delete CFP admin user...")
      } else {
        formSecu.bindFromRequest.fold(invalid => {
          Redirect(routes.CFPAdmin.index()).flashing("error" -> "You did not enter DEL... are you drunk?")
        }, _ => {
          Speaker.delete(speakerUUIDToDelete)
          Webuser.findByUUID(speakerUUIDToDelete).foreach {
            w =>
              Webuser.delete(w)
              Event.storeEvent(Event(speakerUUIDToDelete, uuid, s"Deleted webuser ${w.cleanName} ${w.uuid}"))
          }
          Redirect(routes.CFPAdmin.index()).flashing("success" -> s"Speaker $speakerUUIDToDelete deleted")
        })
      }
  }

}



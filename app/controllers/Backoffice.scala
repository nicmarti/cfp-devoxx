package controllers

import models.{Proposal, Webuser}
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._

/**
 * Backoffice actions, for maintenance and validation.
 *
 * Author: nicolas martignole
 * Created: 02/12/2013 21:34
 */
object Backoffice extends Controller with Secured{
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

  def allDraftProposals()=IsMemberOf("admin"){
    implicit uuid => implicit request=>
      val proposals = Proposal.allDrafts()
      Ok(views.html.Backoffice.allDraftProposals(proposals))
  }

  def allSubmittedProposals()=IsMemberOf("admin"){
    implicit uuid => implicit request=>
      val proposals = Proposal.allSubmitted()
      Ok(views.html.Backoffice.allSubmittedProposals(proposals))
  }

  def moveProposalToTrash(proposalId:String)=IsMemberOf("admin"){
    implicit uuid => implicit request=>
      Proposal.delete(uuid, proposalId)
      val undoDelete = routes.Backoffice.moveProposalToDraft(proposalId).url
      Redirect(routes.Backoffice.allDraftProposals()).flashing("success"->s"Deleted Proposal. <a href='$undoDelete'>Undo delete</a>")
  }

  def moveSubmittedProposalToTrash(proposalId:String)=IsMemberOf("admin"){
    implicit uuid => implicit request=>
      Proposal.delete(uuid, proposalId)
      val undoDelete = routes.Backoffice.moveProposalToDraft(proposalId).url
      Redirect(routes.Backoffice.allSubmittedProposals()).flashing("success"->s"Deleted Proposal. <a href='$undoDelete'>Undo delete</a>")
  }

  def moveProposalToDraft(proposalId:String)=IsMemberOf("admin"){
    implicit uuid => implicit request=>
      Proposal.draft(uuid, proposalId)
      Redirect(routes.Backoffice.allSubmittedProposals()).flashing("success"->s"Undeleted proposal ${proposalId}")
  }

  def moveProposalToSubmit(proposalId:String)=IsMemberOf("admin"){
    implicit uuid => implicit request=>
      Proposal.submit(uuid, proposalId)
      val undoSubmit = routes.Backoffice.moveProposalToDraft(proposalId).url
      Redirect(routes.Backoffice.allDraftProposals()).flashing("success"->s"Proposal ${proposalId} was submitted. <a href='$undoSubmit'>Cancel this submission?</a>")
  }
}



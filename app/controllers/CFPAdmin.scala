package controllers

import play.api.mvc._
import models.{Proposal, Event, Review, Comment}
import play.api.data._
import play.api.data.Forms._
import library.{SendMessageInternal, SendMessageToSpeaker, ZapActor}

/**
 * The backoffice controller for the CFP technical commitee.
 *
 * Author: @nmartignole
 * Created: 11/11/2013 09:09 in Thalys, heading to Devoxx2013
 */
object CFPAdmin extends Controller with Secured {

  def index() = IsMemberOf("cfp") {
    email => implicit request =>
      val twentyEvents = Event.loadEvents(20)
      val allProposalsForReview = Review.allProposalsNotReviewed(email)
      Ok(views.html.CFPAdmin.cfpAdminIndex(twentyEvents, allProposalsForReview))
  }

  val messageForm: Form[String] = Form("msg" -> nonEmptyText(maxLength = 1000))

  def openForReview(proposalId: String) = IsMemberOf("cfp") {
    email => implicit request =>
      Proposal.findById(proposalId) match {
        case Some(proposal) => {
          val speakerDiscussion = Comment.allSpeakerComments(proposal.id.get)
          val internalDiscussion = Comment.allInternalComments(proposal.id.get)
          Ok(views.html.CFPAdmin.showProposal(proposal, speakerDiscussion, internalDiscussion, messageForm, messageForm))
        }
        case None => NotFound("Proposal not found").as("text/html")
      }
  }

  def sendMessageToSpeaker(proposalId: String) = IsMemberOf("cfp") {
    email => implicit request =>
      Proposal.findById(proposalId) match {
        case Some(proposal) => {
          messageForm.bindFromRequest.fold(
            hasErrors => {
              val speakerDiscussion = Comment.allSpeakerComments(proposal.id.get)
              val internalDiscussion = Comment.allInternalComments(proposal.id.get)
              BadRequest(views.html.CFPAdmin.showProposal(proposal, speakerDiscussion, internalDiscussion, hasErrors, messageForm))
            },
            validMsg => {
              Comment.saveCommentForSpeaker(proposal.id.get, email, validMsg) // Save here so that it appears immediatly
              ZapActor.actor ! SendMessageToSpeaker(email, proposal, validMsg)
              Redirect(routes.CFPAdmin.openForReview(proposalId)).flashing("success" -> "Message sent to speaker.")
            }
          )
        }
        case None => NotFound("Proposal not found").as("text/html")
      }
  }

  // Post an internal message that is visible only for program committe
  def postInternalMessage(proposalId: String) = IsMemberOf("cfp") {
    email => implicit request =>
      Proposal.findById(proposalId) match {
        case Some(proposal) => {
          messageForm.bindFromRequest.fold(
            hasErrors => {
              val speakerDiscussion = Comment.allSpeakerComments(proposal.id.get)
              val internalDiscussion = Comment.allInternalComments(proposal.id.get)
              BadRequest(views.html.CFPAdmin.showProposal(proposal, speakerDiscussion, internalDiscussion, messageForm, hasErrors))
            },
            validMsg => {
              Comment.saveInternalComment(proposal.id.get, email, validMsg)  // Save here so that it appears immediatly
              ZapActor.actor ! SendMessageInternal(email, proposal, validMsg)
              Redirect(routes.CFPAdmin.openForReview(proposalId)).flashing("success" -> "Message sent to program commitee.")
            }
          )
        }
        case None => NotFound("Proposal not found").as("text/html")
      }
  }


}



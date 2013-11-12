package controllers

import play.api.mvc._
import models.{Proposal, Event, Review}
import play.api.data._
import play.api.data.Forms._
import library.{SendMessageToSpeaker, ZapActor}

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
        case Some(proposal) => Ok(views.html.CFPAdmin.showProposal(proposal, messageForm))
        case None => NotFound("Proposal not found").as("text/html")
      }
  }

  def sendMessageToSpeaker(proposalId: String) = IsMemberOf("cfp") {
    email => implicit request =>
      Proposal.findById(proposalId) match {
        case Some(proposal) => {
          messageForm.bindFromRequest.fold(
            hasErrors => BadRequest(views.html.CFPAdmin.showProposal(proposal, hasErrors)),
            validMsg => {
              ZapActor.actor ! SendMessageToSpeaker(email, proposal, validMsg)
              Redirect(routes.CFPAdmin.openForReview(proposalId)).flashing("success" -> "Message sent")
            }
          )
        }
        case None => NotFound("Proposal not found").as("text/html")
      }
  }


}



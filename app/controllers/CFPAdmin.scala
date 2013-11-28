package controllers

import play.api.mvc._
import models._
import play.api.data._
import play.api.data.Forms._
import library.{SendMessageInternal, SendMessageToSpeaker, ZapActor}
import library.SendMessageInternal
import library.SendMessageToSpeaker
import scala.Some

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
          Ok(views.html.CFPAdmin.showProposal(proposal, speakerDiscussion, internalDiscussion, messageForm, messageForm, voteForm))
        }
        case None => NotFound("Proposal not found").as("text/html")
      }
  }

  def showVotesForProposal(proposalId: String) = IsMemberOf("cfp") {
    email => implicit request =>
      Proposal.findById(proposalId) match {
        case Some(proposal) => {
          val score = Review.currentScore(proposalId)
          val countVotesCast = Review.totalVoteCastFor(proposalId) // votes exprimes (sans les votes a zero)
          val countVotes = Review.totalVoteFor(proposalId)
          val allVotes=Review.allVotesFor(proposalId)
          Ok(views.html.CFPAdmin.showVotesForProposal(proposal, score, countVotesCast, countVotes, allVotes))
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
              BadRequest(views.html.CFPAdmin.showProposal(proposal, speakerDiscussion, internalDiscussion, hasErrors, messageForm, voteForm))
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
              BadRequest(views.html.CFPAdmin.showProposal(proposal, speakerDiscussion, internalDiscussion, messageForm, hasErrors, voteForm))
            },
            validMsg => {
              Comment.saveInternalComment(proposal.id.get, email, validMsg) // Save here so that it appears immediatly
              ZapActor.actor ! SendMessageInternal(email, proposal, validMsg)
              Redirect(routes.CFPAdmin.openForReview(proposalId)).flashing("success" -> "Message sent to program commitee.")
            }
          )
        }
        case None => NotFound("Proposal not found").as("text/html")
      }
  }

  val voteForm: Form[Int] = Form("vote" -> number(min = 0, max = 10))

  def voteForProposal(proposalId: String) = IsMemberOf("cfp") {
    email => implicit request =>
      Proposal.findById(proposalId) match {
        case Some(proposal) => {
          voteForm.bindFromRequest.fold(
            hasErrors => {
              val speakerDiscussion = Comment.allSpeakerComments(proposal.id.get)
              val internalDiscussion = Comment.allInternalComments(proposal.id.get)
              BadRequest(views.html.CFPAdmin.showProposal(proposal, speakerDiscussion, internalDiscussion, messageForm, messageForm, hasErrors))
            },
            validVote => {
              Review.voteForProposal(proposalId, email, validVote)
              Redirect(routes.CFPAdmin.showVotesForProposal(proposalId)).flashing("vote" -> "Ok, vote submitted")
            }
          )
        }
        case None => NotFound("Proposal not found").as("text/html")
      }
  }

  def showSpeaker(speakerEmail: String) = IsMemberOf("cfp") {
    email => implicit request =>
      SpeakerHelper.findByEmail(speakerEmail) match {
        case Some(speaker) => Ok(views.html.CFPAdmin.showSpeaker(speaker))
        case None => NotFound("Speaker not found")
      }
  }

  def leaderBoard=IsMemberOf("cfp"){
    email => implicit request=>
      val totalSpeakers = SpeakerHelper.countAll()
      val totalProposals = Proposal.countAll()
      val totalVotes = Review.countAll()
      val totalWithVotes = Review.countWithVotes()
      val totalNoVotes   = Review.countWithNoVotes()
      val maybeMostVoted   = Review.mostReviewed()
      val bestReviewer = Review.bestReviewer()
      Ok(views.html.CFPAdmin.leaderBoard(totalSpeakers, totalProposals, totalVotes, totalWithVotes, totalNoVotes, maybeMostVoted, bestReviewer))
  }
}



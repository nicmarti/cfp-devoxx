package controllers

import play.api.mvc._
import models._
import play.api.data._
import play.api.data.Forms._
import library.ZapActor
import library.SendMessageInternal
import library.SendMessageToSpeaker

/**
 * The backoffice controller for the CFP technical commitee.
 *
 * Author: @nmartignole
 * Created: 11/11/2013 09:09 in Thalys, heading to Devoxx2013
 */
object CFPAdmin extends Controller with Secured {

  def index(page:Int) = IsMemberOf("cfp") {
    implicit uuid => implicit request =>
      val twentyEvents = Event.loadEvents(20,page)
      val allProposalsForReview = Review.allProposalsNotReviewed(uuid)
      Ok(views.html.CFPAdmin.cfpAdminIndex(twentyEvents, allProposalsForReview, Event.totalEvents(), page))
  }

  val messageForm: Form[String] = Form("msg" -> nonEmptyText(maxLength = 1000))

  def openForReview(proposalId: String) = IsMemberOf("cfp") {
    implicit uuid => implicit request =>
      Proposal.findById(proposalId) match {
        case Some(proposal) => {
          val speakerDiscussion = Comment.allSpeakerComments(proposal.id)
          val internalDiscussion = Comment.allInternalComments(proposal.id)
          val maybeMyVote = Review.lastVoteByUserForOneProposal(uuid, proposalId)
          Ok(views.html.CFPAdmin.showProposal(proposal, speakerDiscussion, internalDiscussion, messageForm, messageForm, voteForm, maybeMyVote))
        }
        case None => NotFound("Proposal not found").as("text/html")
      }
  }

  def showVotesForProposal(proposalId: String) = IsMemberOf("cfp") {
    implicit uuid => implicit request =>
      Proposal.findById(proposalId) match {
        case Some(proposal) => {
          val score = Review.currentScore(proposalId)
          val countVotesCast = Review.totalVoteCastFor(proposalId) // votes exprimes (sans les votes a zero)
          val countVotes = Review.totalVoteFor(proposalId)
          val allVotes = Review.allVotesFor(proposalId)

          Ok(views.html.CFPAdmin.showVotesForProposal(proposal, score, countVotesCast, countVotes, allVotes))
        }
        case None => NotFound("Proposal not found").as("text/html")
      }
  }

  def sendMessageToSpeaker(proposalId: String) = IsMemberOf("cfp") {
    implicit uuid => implicit request =>
      Proposal.findById(proposalId) match {
        case Some(proposal) => {
          messageForm.bindFromRequest.fold(
            hasErrors => {
              val speakerDiscussion = Comment.allSpeakerComments(proposal.id)
              val internalDiscussion = Comment.allInternalComments(proposal.id)
              val maybeMyVote = Review.lastVoteByUserForOneProposal(uuid, proposalId)
              BadRequest(views.html.CFPAdmin.showProposal(proposal, speakerDiscussion, internalDiscussion, hasErrors, messageForm, voteForm, maybeMyVote))
            },
            validMsg => {
              Comment.saveCommentForSpeaker(proposal.id, uuid, validMsg) // Save here so that it appears immediatly
              ZapActor.actor ! SendMessageToSpeaker(uuid, proposal, validMsg)
              Redirect(routes.CFPAdmin.openForReview(proposalId)).flashing("success" -> "Message sent to speaker.")
            }
          )
        }
        case None => NotFound("Proposal not found").as("text/html")
      }
  }

  // Post an internal message that is visible only for program committe
  def postInternalMessage(proposalId: String) = IsMemberOf("cfp") {
    implicit uuid => implicit request =>
      Proposal.findById(proposalId) match {
        case Some(proposal) => {
          messageForm.bindFromRequest.fold(
            hasErrors => {
              val speakerDiscussion = Comment.allSpeakerComments(proposal.id)
              val internalDiscussion = Comment.allInternalComments(proposal.id)
              val maybeMyVote = Review.lastVoteByUserForOneProposal(uuid, proposalId)
              BadRequest(views.html.CFPAdmin.showProposal(proposal, speakerDiscussion, internalDiscussion, messageForm, hasErrors, voteForm, maybeMyVote))
            },
            validMsg => {
              Comment.saveInternalComment(proposal.id, uuid, validMsg) // Save here so that it appears immediatly
              ZapActor.actor ! SendMessageInternal(uuid, proposal, validMsg)
              Redirect(routes.CFPAdmin.openForReview(proposalId)).flashing("success" -> "Message sent to program commitee.")
            }
          )
        }
        case None => NotFound("Proposal not found").as("text/html")
      }
  }

  val voteForm: Form[Int] = Form("vote" -> number(min = 0, max = 10))

  def voteForProposal(proposalId: String) = IsMemberOf("cfp") {
    implicit uuid => implicit request =>
      Proposal.findById(proposalId) match {
        case Some(proposal) => {
          voteForm.bindFromRequest.fold(
            hasErrors => {
              val speakerDiscussion = Comment.allSpeakerComments(proposal.id)
              val internalDiscussion = Comment.allInternalComments(proposal.id)
              val maybeMyVote = Review.lastVoteByUserForOneProposal(uuid, proposalId)
              BadRequest(views.html.CFPAdmin.showProposal(proposal, speakerDiscussion, internalDiscussion, messageForm, messageForm, hasErrors, maybeMyVote))
            },
            validVote => {
              Review.voteForProposal(proposalId, uuid, validVote)
              Redirect(routes.CFPAdmin.showVotesForProposal(proposalId)).flashing("vote" -> "Ok, vote submitted")
            }
          )
        }
        case None => NotFound("Proposal not found").as("text/html")
      }
  }

  def showSpeaker(uuidSpeaker: String) = IsMemberOf("cfp") {
    implicit uuid => implicit request =>
      Speaker.findByUUID(uuidSpeaker) match {
        case Some(speaker) => Ok(views.html.CFPAdmin.showSpeaker(speaker))
        case None => NotFound("Speaker not found")
      }
  }

  def leaderBoard = IsMemberOf("cfp") {
    implicit uuid => implicit request =>
      val totalSpeakers = Speaker.countAll()
      val totalProposals = Proposal.countAll()
      val totalVotes = Review.countAll()
      val totalWithVotes = Review.countWithVotes()
      val totalNoVotes = Review.countWithNoVotes()
      val maybeMostVoted = Review.mostReviewed()
      val bestReviewer = Review.bestReviewer()
      Ok(views.html.CFPAdmin.leaderBoard(totalSpeakers, totalProposals, totalVotes, totalWithVotes, totalNoVotes, maybeMostVoted, bestReviewer))
  }

  def allMyVotes = IsMemberOf("cfp"){
    implicit uuid => implicit request =>
      val result = Review.allVotesFromUser(uuid)
      Ok(views.html.CFPAdmin.allMyVotes(result))
  }


}



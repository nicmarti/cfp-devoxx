package controllers

import play.api.mvc._
import models._
import play.api.data._
import play.api.data.Forms._
import library._
import library.search.ElasticSearch
import play.api.Routes
import play.api.libs.json.{JsObject, JsValue, Json}
import library.SendMessageInternal
import scala.Some
import library.SendMessageToSpeaker
import play.api.libs.json.JsObject
import play.api.i18n.Messages
import models.Review.ScoreAndTotalVotes

/**
 * The backoffice controller for the CFP technical committee.
 *
 * Author: @nmartignole
 * Created: 11/11/2013 09:09 in Thalys, heading to Devoxx2013
 */
object CFPAdmin extends Controller with Secured {

  def index(page: Int, sort: Option[String], ascdesc: Option[String]) = IsMemberOf("cfp") {
    implicit uuid => implicit request =>
      val sorter = proposalSorter(sort)
      val orderer = proposalOrder(ascdesc)
      val allProposalsForReview = sortProposals(Review.allProposalsNotReviewed(uuid), sorter, orderer)
      val twentyEvents = Event.loadEvents(20, page)

      val etag = allProposalsForReview.hashCode() + "_" + twentyEvents.hashCode()

      request.headers.get("If-None-Match") match {
        case Some(tag) if tag == etag => NotModified
        case _ => Ok(views.html.CFPAdmin.cfpAdminIndex(twentyEvents, allProposalsForReview, Event.totalEvents(), page, sort, ascdesc)).withHeaders("ETag" -> etag)
      }
  }

  def sortProposals(ps: List[Proposal], sorter: Option[Proposal => String], orderer: Ordering[String]) =
    sorter match {
      case None => ps
      case Some(s) => ps.sortBy(s)(orderer)
    }

  def proposalSorter(sort: Option[String]): Option[Proposal => String] = {
    sort match {
      case Some("title") => Some(_.title)
      case Some("mainSpeaker") => Some(_.mainSpeaker)
      case Some("track") => Some(_.track.label)
      case Some("talkType") => Some(_.talkType.label)
      case _ => None
    }
  }

  def proposalOrder(ascdesc: Option[String]) = ascdesc match {
    case Some("desc") => Ordering[String].reverse
    case _ => Ordering[String]
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

          // The next proposal I should review
          val nextToBeReviewed = Review.allProposalsNotReviewed(uuid).headOption

          Ok(views.html.CFPAdmin.showVotesForProposal(proposal, score, countVotesCast, countVotes, allVotes, nextToBeReviewed))
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
              Redirect(routes.CFPAdmin.openForReview(proposalId)).flashing("success" -> "Message sent to program committee.")
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

  def clearVoteForProposal(proposalId: String) = IsMemberOf("cfp") {
    implicit uuid => implicit request =>
      Proposal.findById(proposalId) match {
        case Some(proposal) => {
          Review.removeVoteForProposal(proposalId, uuid)
          Redirect(routes.CFPAdmin.showVotesForProposal(proposalId)).flashing("vote" -> "Removed your vote")
        }
        case None => NotFound("Proposal not found").as("text/html")
      }
  }

  def leaderBoard = IsMemberOf("cfp") {
    implicit uuid => implicit request =>
      val totalSpeakers = Leaderboard.totalSpeakers()
      val totalProposals = Leaderboard.totalProposals()
      val totalVotes = Leaderboard.totalVotes()
      val totalWithVotes =  Leaderboard.totalWithVotes()
      val totalNoVotes =  Leaderboard.totalNoVotes()
      val maybeMostVoted = Leaderboard.mostReviewed()
      val bestReviewer = Leaderboard.bestReviewer()
      val worstReviewer = Leaderboard.worstReviewer()
      val totalByCategories = Leaderboard.totalByCategories()
      val totalByType = Leaderboard.totalByType()
      val devoxx2013=AcceptService.getDevoxx2013Total

      Ok(
        views.html.CFPAdmin.leaderBoard(
          totalSpeakers, totalProposals, totalVotes, totalWithVotes,
          totalNoVotes, maybeMostVoted, bestReviewer,worstReviewer, totalByCategories,
          totalByType,devoxx2013
        )
      )
  }

  def allReviewersAndStats = IsMemberOf("cfp"){
    implicit uuid => implicit request=>
      Ok(views.html.CFPAdmin.allReviewersAndStats(Review.allReviewersAndStats()))
  }

  def doComputeLeaderBoard()=IsMemberOf("cfp"){
    implicit uuid => implicit request =>
    library.ZapActor.actor ! ComputeLeaderboard()
    Redirect(routes.CFPAdmin.index()).flashing("success"->Messages("leaderboard.compute"))
  }

  def allMyVotes = IsMemberOf("cfp") {
    implicit uuid => implicit request =>
      val result = Review.allVotesFromUser(uuid)
      val allProposalIDs = result.map(_._1)
      val allProposals = Proposal.loadAndParseProposals(allProposalIDs)

      Ok(views.html.CFPAdmin.allMyVotes(result, allProposals))
  }

  def search(q: String) = IsMemberOf("cfp") {
    _ => implicit request =>
      import play.api.libs.concurrent.Execution.Implicits.defaultContext

      Async {
        ElasticSearch.doSearch(q).map {
          case r if r.isSuccess => {
            val json = Json.parse(r.get)

            val total = (json \ "hits" \ "total").as[Int]
            val hitContents = (json \ "hits" \ "hits").as[List[JsObject]]

            val results = hitContents.map {
              jsvalue =>
                val index = (jsvalue \ "_index").as[String]
                val source = (jsvalue \ "_source")
                index match {
                  case "events" => {
                    val objRef = (source \ "objRef").as[String]
                    val uuid = (source \ "uuid").as[String]
                    val msg = (source \ "msg").as[String]
                    s"<i class='icon-stackexchange'></i> Event <a href='${routes.CFPAdmin.openForReview(objRef)}'>${objRef}</a> by ${uuid} ${msg}"
                  }
                  case "proposals" => {
                    val id = (source \ "id").as[String]
                    val title = (source \ "title").as[String]
                    s"<i class='icon-folder-open'></i> Proposal <a href='${routes.CFPAdmin.openForReview(id)}'>$title</a>"
                  }
                  case "speakers" => {
                    val uuid = (source \ "uuid").as[String]
                    val name = (source \ "name").as[String]
                    s"<i class='icon-user'></i> Speaker <a href='${routes.CFPAdmin.showSpeakerAndTalks(uuid)}'>$name</a>"
                  }
                  case other => "Unknown"
                }
            }

            Ok(views.html.CFPAdmin.renderSearchResult(total, results)).as("text/html")
          }
          case r if r.isFailure => {
            InternalServerError(r.get)
          }
        }
      }
  }

  def allSponsorTalks=IsMemberOf("cfp"){
    implicit uuid => implicit request =>
      val proposals = Proposal.allSponsorsTalk()
      Ok(views.html.CFPAdmin.allSponsorTalks(proposals))
  }

  def showSpeakerAndTalks(uuidSpeaker: String) = IsMemberOf("cfp") {
    implicit uuid => implicit request =>
      Speaker.findByUUID(uuidSpeaker) match {
        case Some(speaker) => {
          val proposals = Proposal.allProposalsByAuthor(speaker.uuid)
          Ok(views.html.CFPAdmin.showSpeakerAndTalks(speaker, proposals))
        }
        case None => NotFound("Speaker not found")
      }
  }

  def allVotes(confType:String) = IsMemberOf("admin") {
    implicit uuid => implicit request =>

      val result = Review.allVotes().toList.sortBy(_._2._1).reverse

      val allProposalIDs=result.map(_._1)
      val allProposalWithVotes = Proposal.loadAndParseProposals(allProposalIDs.toSet)

      val listOfProposals:List[(Proposal, ScoreAndTotalVotes)] = result.flatMap{
        case(proposalId,scoreAndVotes)=>
          allProposalWithVotes.get(proposalId).map{
            proposal:Proposal=>
              (proposal, scoreAndVotes)
          }
      }.filter{case(proposal,_)=>
        proposal.state==ProposalState.SUBMITTED
      }

      val listToDisplay = confType match {
        case "all"=>listOfProposals
        case filterType=>listOfProposals.filter(_._1.talkType.id==filterType)
      }

      val totalAccepted = AcceptService.countAccepted(confType)
      val totalRemaining = AcceptService.remainingSlots(confType)

      Ok(views.html.CFPAdmin.allVotes(listToDisplay, totalAccepted, totalRemaining))
  }

  def doComputeVotesTotal()=IsMemberOf("cfp"){
    implicit uuid=>implicit request=>
      ZapActor.actor ! ComputeVotesAndScore()
      Redirect(routes.CFPAdmin.allVotes("all")).flashing("success"->"Recomputing votes and scores...")
  }

  def removeSponsorTalkFlag(proposalId:String)=IsMemberOf("admin"){
    implicit uuid=>
      implicit request=>
      Proposal.removeSponsorTalkFlag(uuid,proposalId)
      Redirect(routes.CFPAdmin.allSponsorTalks).flashing("success"->s"Removed sponsor talk on $proposalId")
  }

  def allProposalsByTrack(track:String)=IsMemberOf("cfp"){
    implicit uuid=>
      implicit request=>
      val proposals = Proposal.allSubmitted().filter(_.track.id==track)
      Ok(views.html.CFPAdmin.allProposalsByTrack(proposals,track))
  }

  def allProposalsByType(confType:String)=IsMemberOf("cfp"){
    implicit uuid=>
      implicit request=>
      val proposals = Proposal.allSubmitted().filter(_.talkType.id==confType)
      Ok(views.html.CFPAdmin.allProposalsByType(proposals,confType))
  }
}



package controllers

import play.api.mvc._
import models._
import play.api.data._
import play.api.data.Forms._
import library.{Benchmark, ZapActor, SendMessageInternal, SendMessageToSpeaker}
import library.search.ElasticSearch
import play.api.Routes
import play.api.libs.json.{JsObject, JsValue, Json}

/**
 * The backoffice controller for the CFP technical commitee.
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
      val totalSpeakers = Benchmark.measure(() => Speaker.countAll() , "count all speakers")
      val totalProposals = Benchmark.measure(() =>Proposal.countAll(), "count all proposals")
      val totalVotes = Benchmark.measure(() =>Review.countAll(), "***** count all reviews")
      val totalWithVotes =  Benchmark.measure(() =>Review.countWithVotes(), "count all review with votes")
      val totalNoVotes =  Benchmark.measure(() =>Review.countWithNoVotes(), "count all review with no votes")
      val maybeMostVoted = Benchmark.measure(() =>Review.mostReviewed(), "most reviewer")
      val bestReviewer = Benchmark.measure(() =>Review.bestReviewer(), "best reviewer")
      val worstReviewer = Benchmark.measure(() =>Review.worstReviewer(), "lanterne rouge")
      val totalByCategories = Benchmark.measure(()=>Proposal.totalSubmittedByTrack(), "total submitted by track")
      val totalByType = Benchmark.measure(() =>Proposal.totalSubmittedByType(), "total submitted by type")
      val devoxx2013=Benchmark.measure(() =>Proposal.getDevoxx2013Total(), "devoxx 2013 total")

      Ok(
        views.html.CFPAdmin.leaderBoard(
          totalSpeakers, totalProposals, totalVotes, totalWithVotes,
          totalNoVotes, maybeMostVoted, bestReviewer,worstReviewer, totalByCategories,
          totalByType,devoxx2013
        )
      )
  }

  def allMyVotes = IsMemberOf("cfp") {
    implicit uuid => implicit request =>
      val result = Review.allVotesFromUser(uuid)
      val allProposalIDs = result.map(_._1)
      val allProposals = Proposal.loadAndParseProposals(allProposalIDs)

      Ok(views.html.CFPAdmin.allMyVotes(result, allProposals))
  }

  // Pour l'instant, je n'ai pas envie que les membres du CFP
  // utilise la page de résultat pour décider s'ils votent ou non pour un talk
  def allVotes(sortBy:Option[String]) = IsMemberOf("admin") {
    implicit uuid => implicit request =>

      val result = sortBy match {
        case Some(s) if s=="-score"=>Review.allVotes().toList.sortBy(_._2._1).reverse
        case Some(s) if s=="+score"=>Review.allVotes().toList.sortBy(_._2._1)
        case Some(s) if s=="-vote"=>Review.allVotes().toList.sortBy(_._2._2).reverse
        case Some(s) if s=="+vote"=>Review.allVotes().toList.sortBy(_._2._2)
        case Some(s) if s=="id"=>Review.allVotes().toList.sortBy(_._1)
        case None=>Review.allVotes().toList.sortBy(_._2._1).reverse
      }
      Ok(views.html.CFPAdmin.allVotes(result,sortBy.getOrElse("-score")))
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
}



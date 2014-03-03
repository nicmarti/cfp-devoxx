package controllers

import models._
import play.api.data._
import play.api.data.Forms._
import library._
import library.search.ElasticSearch
import play.api.libs.json.Json
import play.api.i18n.Messages
import models.Review.ScoreAndTotalVotes
import play.api.data.validation.Constraints._
import scala.Some
import library.ComputeVotesAndScore
import library.ComputeLeaderboard
import library.SendMessageInternal
import library.SendMessageToSpeaker
import play.api.libs.json.JsObject

/**
 * The backoffice controller for the CFP technical committee.
 *
 * Author: @nmartignole
 * Created: 11/11/2013 09:09 in Thalys, heading to Devoxx2013
 */
object CFPAdmin extends SecureCFPController {

  def index(page: Int, sort: Option[String], ascdesc: Option[String]) = SecuredAction(IsMemberOf("cfp")) {
    implicit request =>
      val uuid = request.webuser.uuid
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

  def openForReview(proposalId: String) = SecuredAction(IsMemberOf("cfp")) {
    implicit request =>
      val uuid = request.webuser.uuid
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

  def showVotesForProposal(proposalId: String) = SecuredAction(IsMemberOf("cfp")) {
    implicit request =>
      val uuid = request.webuser.uuid
      Proposal.findById(proposalId) match {
        case Some(proposal) => {
          val score = Review.currentScore(proposalId)
          val countVotesCast = Review.totalVoteCastFor(proposalId) // votes exprimes (sans les votes a zero)
          val countVotes = Review.totalVoteFor(proposalId)
          val allVotes = Review.allVotesFor(proposalId)

          // The next proposal I should review
          val nextToBeReviewed = Review.allProposalsNotReviewed(uuid).headOption

          Ok(views.html.CFPAdmin.showVotesForProposal(uuid, proposal, score, countVotesCast, countVotes, allVotes, nextToBeReviewed))
        }
        case None => NotFound("Proposal not found").as("text/html")
      }
  }

  def sendMessageToSpeaker(proposalId: String) = SecuredAction(IsMemberOf("cfp")) {
    implicit request =>
      val uuid = request.webuser.uuid
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
  def postInternalMessage(proposalId: String) = SecuredAction(IsMemberOf("cfp")) {
    implicit request =>
      val uuid = request.webuser.uuid
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

  def voteForProposal(proposalId: String) = SecuredAction(IsMemberOf("cfp")) {
    implicit request =>
      val uuid = request.webuser.uuid
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

  def clearVoteForProposal(proposalId: String) = SecuredAction(IsMemberOf("cfp")) {
    implicit request =>
      val uuid = request.webuser.uuid
      Proposal.findById(proposalId) match {
        case Some(proposal) => {
          Review.removeVoteForProposal(proposalId, uuid)
          Redirect(routes.CFPAdmin.showVotesForProposal(proposalId)).flashing("vote" -> "Removed your vote")
        }
        case None => NotFound("Proposal not found").as("text/html")
      }
  }

  def leaderBoard = SecuredAction(IsMemberOf("cfp")) {
    implicit request =>

      val totalSpeakers = Leaderboard.totalSpeakers()
      val totalProposals = Leaderboard.totalProposals()
      val totalVotes = Leaderboard.totalVotes()
      val totalWithVotes = Leaderboard.totalWithVotes()
      val totalNoVotes = Leaderboard.totalNoVotes()
      val maybeMostVoted = Leaderboard.mostReviewed()
      val bestReviewer = Leaderboard.bestReviewer()
      val worstReviewer = Leaderboard.worstReviewer()

      val totalSubmittedByCategories = Leaderboard.totalSubmittedByCategories()
      val totalSubmittedByType = Leaderboard.totalSubmittedByType()
      val totalAcceptedByCategories = Leaderboard.totalAcceptedByCategories()
      val totalAcceptedByType = Leaderboard.totalAcceptedByType()

      val devoxx2013 = ApprovedProposal.getDevoxx2013Total
      val totalApprovedSpeakers = Leaderboard.totalApprovedSpeakers()
      val totalWithTickets = Leaderboard.totalWithTickets()
      val totalWithOneProposal = Leaderboard.totalWithOneProposal()
      val totalRefusedSpeakers = Leaderboard.totalRefusedSpeakers()


      Ok(
        views.html.CFPAdmin.leaderBoard(
          totalSpeakers, totalProposals, totalVotes, totalWithVotes,
          totalNoVotes, maybeMostVoted, bestReviewer, worstReviewer,
          totalSubmittedByCategories, totalSubmittedByType,
          totalAcceptedByCategories, totalAcceptedByType,
          devoxx2013, totalApprovedSpeakers, totalWithTickets, totalWithOneProposal, totalRefusedSpeakers
        )
      )
  }

  def allReviewersAndStats = SecuredAction(IsMemberOf("cfp")) {
    implicit request =>

      Ok(views.html.CFPAdmin.allReviewersAndStats(Review.allReviewersAndStats()))
  }

  def doComputeLeaderBoard() = SecuredAction(IsMemberOf("cfp")) {
    implicit request =>

      library.ZapActor.actor ! ComputeLeaderboard()
      Redirect(routes.CFPAdmin.index()).flashing("success" -> Messages("leaderboard.compute"))
  }

  def allMyVotes = SecuredAction(IsMemberOf("cfp")) {
    implicit request =>
      val uuid = request.webuser.uuid
      val result = Review.allVotesFromUser(uuid)
      val allProposalIDs = result.map(_._1)
      val allProposals = Proposal.loadAndParseProposals(allProposalIDs)

      Ok(views.html.CFPAdmin.allMyVotes(result, allProposals))
  }

  def search(q: String) = SecuredAction(IsMemberOf("cfp")).async {
    implicit request =>

      import play.api.libs.concurrent.Execution.Implicits.defaultContext

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

  def allSponsorTalks = SecuredAction(IsMemberOf("cfp")) {
    implicit request =>

      val proposals = Proposal.allSponsorsTalk()
      Ok(views.html.CFPAdmin.allSponsorTalks(proposals))
  }

  def showSpeakerAndTalks(uuidSpeaker: String) = SecuredAction {
    implicit request =>

      Speaker.findByUUID(uuidSpeaker) match {
        case Some(speaker) => {
          val proposals = Proposal.allProposalsByAuthor(speaker.uuid)
          Ok(views.html.CFPAdmin.showSpeakerAndTalks(speaker, proposals, request.webuser.uuid))
        }
        case None => NotFound("Speaker not found")
      }
  }

  def allVotes(confType: String, track: Option[String]) = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>

      val reviews = Review.allVotes()
      val totalApproved = ApprovedProposal.countApproved(confType)

      val result = reviews.toList.sortBy(_._2._1).reverse
      val allProposalIDs = result.map(_._1)
      val allProposalWithVotes = Proposal.loadAndParseProposals(allProposalIDs.toSet)

      val listOfProposals: List[(Proposal, ScoreAndTotalVotes)] = result.flatMap {
        case (proposalId, scoreAndVotes) =>
          allProposalWithVotes.get(proposalId).map {
            proposal: Proposal =>
              (proposal, scoreAndVotes)
          }
      }.filterNot {
        case (proposal, _) =>
          proposal.state == ProposalState.DRAFT ||
            proposal.state == ProposalState.DELETED
      }

      val listToDisplay1 = confType match {
        case "all" => listOfProposals
        case filterType => listOfProposals.filter(_._1.talkType.id == filterType)
      }
      val listToDisplay = track match {
        case None => listToDisplay1
        case Some(trackId) => listToDisplay1.filter(_._1.track.id == trackId)
      }

      val totalRemaining = ApprovedProposal.remainingSlots(confType)
      Ok(views.html.CFPAdmin.allVotes(listToDisplay, totalApproved, totalRemaining, confType))
  }

  def doComputeVotesTotal() = SecuredAction(IsMemberOf("cfp")) {
    implicit request =>
      ZapActor.actor ! ComputeVotesAndScore()
      Redirect(routes.CFPAdmin.allVotes("conf", None)).flashing("success" -> "Recomputing votes and scores...")
  }

  def removeSponsorTalkFlag(proposalId: String) = SecuredAction(IsMemberOf("admin")) {
    implicit request =>
      val uuid = request.webuser.uuid
      Proposal.removeSponsorTalkFlag(uuid, proposalId)
      Redirect(routes.CFPAdmin.allSponsorTalks).flashing("success" -> s"Removed sponsor talk on $proposalId")
  }

  def allProposalsByTrack(track: String) = SecuredAction(IsMemberOf("cfp")) {
    implicit request =>
      val proposals = Proposal.allSubmitted().filter(_.track.id == track)
      Ok(views.html.CFPAdmin.allProposalsByTrack(proposals, track))
  }

  def allProposalsByType(confType: String) = SecuredAction(IsMemberOf("cfp")) {
    implicit request =>
      val proposals = Proposal.allSubmitted().filter(_.talkType.id == confType)
      Ok(views.html.CFPAdmin.allProposalsByType(proposals, confType))
  }

  def showProposalsNotReviewedCompareTo(maybeReviewer: Option[String]) = SecuredAction(IsMemberOf("cfp")) {
    implicit request =>
      val uuid = request.webuser.uuid
      maybeReviewer match {
        case None =>
          Ok(views.html.CFPAdmin.showCFPUsers(Webuser.allCFPAdmin()))
        case Some(otherReviewer) =>
          val diffProposalIDs = Review.diffReviewBetween(otherReviewer, uuid)
          Ok(views.html.CFPAdmin.showProposalsNotReviewedCompareTo(diffProposalIDs, otherReviewer))
      }
  }

  // Returns all speakers
  def allSpeakers(export: Boolean = false, rejected: Boolean = true, accepted: Boolean = true, onlyWithSpeakerPass: Boolean = false) = SecuredAction(IsMemberOf("cfp")) {
    implicit request =>

      val allSpeakers = Speaker.allSpeakers()

      val speakers1 = (accepted, onlyWithSpeakerPass) match {
        case (true, false) => allSpeakers.filter(s => Proposal.hasOneAcceptedOrApprovedProposal(s.uuid)).filterNot(s => Webuser.isMember(s.uuid, "cfp"))
        case (_, true) => allSpeakers.filter(s => Proposal.hasOneProposalWithSpeakerTicket(s.uuid)).filterNot(s => Webuser.isMember(s.uuid, "cfp"))
        case other => allSpeakers
      }

      val speakers = rejected match {
        case true => allSpeakers.filter(s => Proposal.hasOnlyRejectedProposals(s.uuid)).filterNot(s => Webuser.isMember(s.uuid, "cfp"))
        case false => speakers1
      }

      export match {
        case true => {
          val buffer = new StringBuffer("email,name,lang,uuid,code\n")
          speakers.foreach {
            s =>
              buffer.append(s.email.toLowerCase)
              buffer.append(",")
              buffer.append(s.cleanName)
              buffer.append(",")
              buffer.append(s.cleanLang)
              buffer.append(",")
              buffer.append(s.uuid)
              buffer.append(",")
              buffer.append("SPK-")
              buffer.append(s.uuid.substring(7, 15).toUpperCase)
              buffer.append("\n")
          }
          Ok(buffer.toString).as("text/csv")
        }
        case false => Ok(views.html.CFPAdmin.allSpeakers(speakers.sortBy(_.cleanName)))
      }

  }

  def allWebusers() = SecuredAction(IsMemberOf("cfp")) {
    implicit request =>
      val allSpeakers = Webuser.allSpeakers.sortBy(_.cleanName)
      Ok(views.html.CFPAdmin.allWebusers(allSpeakers))
  }

  val editSpeakerForm = Form(
    tuple(
      "uuid" -> text.verifying(nonEmpty, maxLength(50)),
      "firstName" -> text.verifying(nonEmpty, maxLength(30)),
      "lastName" -> text.verifying(nonEmpty, maxLength(30))
    )
  )

  val speakerForm = play.api.data.Form(mapping(
    "uuid" -> optional(text),
    "email" -> (email verifying nonEmpty),
    "lastName" -> text,
    "bio2" -> nonEmptyText(maxLength = 750),
    "lang2" -> optional(text),
    "twitter2" -> optional(text),
    "avatarUrl2" -> optional(text),
    "company2" -> optional(text),
    "blog2" -> optional(text),
    "firstName"->text
  )(Speaker.createOrEditSpeaker)(Speaker.unapplyFormEdit))


  def newOrEditSpeaker(speakerUUID: Option[String]) = SecuredAction(IsMemberOf("cfp")) {
    implicit request =>
      speakerUUID match {
        case Some(uuid) => {
          Speaker.findByUUID(uuid).map {
            speaker: Speaker =>
              Ok(views.html.CFPAdmin.newSpeaker(speakerForm.fill(speaker))).flashing("success" -> "You are currently editing an existing speaker")
          }.getOrElse {
            Ok(views.html.CFPAdmin.newSpeaker(speakerForm)).flashing("error" -> "Speaker not found")
          }
        }
        case None => Ok(views.html.CFPAdmin.newSpeaker(speakerForm))
      }

  }

  def saveNewSpeaker() = SecuredAction(IsMemberOf("cfp")) {
    implicit request =>
      speakerForm.bindFromRequest.fold(
        invalidForm => BadRequest(views.html.CFPAdmin.newSpeaker(invalidForm)).flashing("error" -> "Invalid form, please check and correct errors. "),
        validSpeaker => {
          Option(validSpeaker.uuid) match {
            case Some(existingUUID)=>{
              play.Logger.of("application.CFPAdmin").debug("Updating existing speaker "+existingUUID)
              Webuser.findByUUID(existingUUID).map{
                existingWebuser=>
                  Webuser.updateNames(existingUUID, validSpeaker.firstName.getOrElse("?"), validSpeaker.name.getOrElse("?"))
              }.getOrElse{
                play.Logger.error("Unable to retrieve Webuser for speaker uuid "+existingUUID)
              }
              Speaker.save(validSpeaker)
              Event.storeEvent(Event( validSpeaker.cleanName , request.webuser.uuid, "updated a speaker ["+validSpeaker.uuid+"]"))
              Redirect(routes.CFPAdmin.showSpeakerAndTalks(existingUUID)).flashing("success" -> "Profile updated")
            }
            case None=>  {
             val webuser = Webuser.createSpeaker(validSpeaker.email, validSpeaker.firstName.getOrElse("Firstname"), validSpeaker.name.getOrElse("Lastname") )
              Webuser.saveNewSpeakerEmailNotValidated(webuser)
              val newUUID=Webuser.validateEmailForSpeaker(webuser)
              Speaker.save(validSpeaker.copy(uuid=newUUID))
              Event.storeEvent(Event( validSpeaker.cleanName , request.webuser.uuid, "created a speaker ["+validSpeaker.uuid+"]"))
              Redirect(routes.CFPAdmin.showSpeakerAndTalks(newUUID)).flashing("success" -> "Profile saved")
            }
          }

        }
      )
  }

  def setPreferredDay(proposalId:String, day:String)= SecuredAction(IsMemberOf("cfp")) {
    implicit request =>
      Proposal.setPreferredDay(proposalId:String, day:String)
      Redirect(routes.CFPAdmin.openForReview(proposalId)).flashing("success"->("Preferred day set to "+day))
  }

  def resetPreferredDay(proposalId:String)= SecuredAction(IsMemberOf("cfp")) {
    implicit request =>
      Proposal.resetPreferredDay(proposalId:String)
      Redirect(routes.CFPAdmin.openForReview(proposalId)).flashing("success"->"No preferences")
  }
}



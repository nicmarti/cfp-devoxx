package controllers

import java.io.{File, PrintWriter}

import library.{ComputeLeaderboard, ComputeVotesAndScore, SendMessageInternal, SendMessageToSpeaker, _}
import library.search.ElasticSearch
import models.Review.ScoreAndTotalVotes
import models._
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import play.api.data.Forms._
import play.api.data._
import play.api.data.validation.Constraints._
import play.api.i18n.Messages
import play.api.libs.json.{JsObject, Json}

/**
 * The backoffice controller for the CFP technical committee.
 *
 * Author: @nmartignole
 * Created: 11/11/2013 09:09 in Thalys, heading to Devoxx2013
 */
object CFPAdmin extends SecureCFPController {

  def index(page: Int, sort: Option[String], ascdesc: Option[String], track: Option[String]) = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      val uuid = request.webuser.uuid
      val sorter = proposalSorter(sort)
      val orderer = proposalOrder(ascdesc)
      val allNotReviewed = Review.allProposalsNotReviewed(uuid)
      val maybeFilteredProposals = track match {
        case None => allNotReviewed
        case Some(trackLabel) => allNotReviewed.filter(_.track.id.equalsIgnoreCase(StringUtils.trimToEmpty(trackLabel)))
      }
      val allProposalsForReview = sortProposals(maybeFilteredProposals, sorter, orderer)
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

  private def proposalSorter(sort: Option[String]): Option[Proposal => String] = {
    sort match {
      case Some("title") => Some(_.title)
      case Some("mainSpeaker") => Some(_.mainSpeaker)
      case Some("track") => Some(_.track.label)
      case Some("talkType") => Some(_.talkType.label)
      case _ => None
    }
  }

  private def proposalOrder(ascdesc: Option[String]) = ascdesc match {
    case Some("desc") => Ordering[String].reverse
    case _ => Ordering[String]
  }

  val messageForm: Form[String] = Form("msg" -> nonEmptyText(maxLength = 1000))

  def openForReview(proposalId: String) = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      val uuid = request.webuser.uuid
      Proposal.findById(proposalId) match {
        case Some(proposal) => {
          val speakerDiscussion = Comment.allSpeakerComments(proposal.id)
          val internalDiscussion = Comment.allInternalComments(proposal.id)
          val maybeMyVote = Review.lastVoteByUserForOneProposal(uuid, proposalId)
          val proposalsByAuths = allProposalByProposal(proposal)
          Ok(views.html.CFPAdmin.showProposal(proposal, proposalsByAuths, speakerDiscussion, internalDiscussion, messageForm, messageForm, voteForm, maybeMyVote, uuid))
        }
        case None => NotFound("Proposal not found").as("text/html")
      }
  }
  def allProposalByProposal(proposal:Proposal):Map[String, Map[String, models.Proposal]] ={
    val authorIds :List[String] = proposal.mainSpeaker :: proposal.secondarySpeaker.toList ::: proposal.otherSpeakers
    authorIds.map {
          case id => id -> Proposal.allProposalsByAuthor(id)
        }.toMap

  }

  def showVotesForProposal(proposalId: String) = SecuredAction(IsMemberOf("cfp")).async {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      import scala.concurrent.ExecutionContext.Implicits.global
      val uuid = request.webuser.uuid
      scala.concurrent.Future {
        Proposal.findById(proposalId) match {
          case Some(proposal) => {
            val score = Review.currentScore(proposalId)
            val countVotesCast = Review.totalVoteCastFor(proposalId) // votes exprimes (sans les votes a zero)
            val countVotes = Review.totalVoteFor(proposalId)
            val allVotes = Review.allVotesFor(proposalId)

            // The next proposal I should review
            val allNotReviewed = Review.allProposalsNotReviewed(uuid)
            val (sameTracks, otherTracks) = allNotReviewed.partition(_.track.id == proposal.track.id)

            val nextToBeReviewed = (sameTracks.sortBy(_.talkType.id) ++ otherTracks).headOption

            Ok(views.html.CFPAdmin.showVotesForProposal(uuid, proposal, score, countVotesCast, countVotes, allVotes, nextToBeReviewed))
          }
          case None => NotFound("Proposal not found").as("text/html")
        }
      }
  }

  def sendMessageToSpeaker(proposalId: String) = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      val uuid = request.webuser.uuid
      Proposal.findById(proposalId) match {
        case Some(proposal) => {
          messageForm.bindFromRequest.fold(
            hasErrors => {
              val speakerDiscussion = Comment.allSpeakerComments(proposal.id)
              val internalDiscussion = Comment.allInternalComments(proposal.id)
              val maybeMyVote = Review.lastVoteByUserForOneProposal(uuid, proposalId)
              val proposals = allProposalByProposal(proposal)
              BadRequest(views.html.CFPAdmin.showProposal(proposal, proposals, speakerDiscussion, internalDiscussion, hasErrors, messageForm, voteForm, maybeMyVote, uuid))
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
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      val uuid = request.webuser.uuid
      Proposal.findById(proposalId) match {
        case Some(proposal) => {
          messageForm.bindFromRequest.fold(
            hasErrors => {
              val speakerDiscussion = Comment.allSpeakerComments(proposal.id)
              val internalDiscussion = Comment.allInternalComments(proposal.id)
              val maybeMyVote = Review.lastVoteByUserForOneProposal(uuid, proposalId)
              val proposals = allProposalByProposal(proposal)
              BadRequest(views.html.CFPAdmin.showProposal(proposal, proposals, speakerDiscussion, internalDiscussion, messageForm, hasErrors, voteForm, maybeMyVote, uuid))
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
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      val uuid = request.webuser.uuid
      Proposal.findById(proposalId) match {
        case Some(proposal) => {
          voteForm.bindFromRequest.fold(
            hasErrors => {
              val speakerDiscussion = Comment.allSpeakerComments(proposal.id)
              val internalDiscussion = Comment.allInternalComments(proposal.id)
              val maybeMyVote = Review.lastVoteByUserForOneProposal(uuid, proposalId)
              val proposals = allProposalByProposal(proposal)
              BadRequest(views.html.CFPAdmin.showProposal(proposal, proposals, speakerDiscussion, internalDiscussion, messageForm, messageForm, hasErrors, maybeMyVote, uuid))
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
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
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
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>

      val totalSpeakers = Leaderboard.totalSpeakers()
      val totalProposals = Leaderboard.totalProposals()
      val totalVotes = Leaderboard.totalVotes()
      val totalWithVotes = Leaderboard.totalWithVotes()
      val totalNoVotes = Leaderboard.totalNoVotes()
      val maybeMostVoted = Leaderboard.mostReviewed()
      val bestReviewer = Leaderboard.bestReviewer()
      val lazyOnes = Leaderboard.lazyOnes()

      val totalSubmittedByTrack = Leaderboard.totalSubmittedByTrack()
      val totalSubmittedByType = Leaderboard.totalSubmittedByType()
      val totalAcceptedByTrack = Leaderboard.totalAcceptedByTrack()
      val totalAcceptedByType = Leaderboard.totalAcceptedByType()

      val totalSlotsToAllocate = ApprovedProposal.getTotal
      val totalApprovedSpeakers = Leaderboard.totalApprovedSpeakers()
      val totalWithTickets = Leaderboard.totalWithTickets()
      val totalRefusedSpeakers = Leaderboard.totalRefusedSpeakers()


      Ok(
        views.html.CFPAdmin.leaderBoard(
          totalSpeakers, totalProposals, totalVotes, totalWithVotes,
          totalNoVotes, maybeMostVoted, bestReviewer, lazyOnes,
          totalSubmittedByTrack, totalSubmittedByType,
          totalAcceptedByTrack, totalAcceptedByType,
          totalSlotsToAllocate, totalApprovedSpeakers, totalWithTickets,
          totalRefusedSpeakers
        )
      )
  }

  def allReviewersAndStats = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>

      Ok(views.html.CFPAdmin.allReviewersAndStats(Review.allReviewersAndStats()))
  }

  def doComputeLeaderBoard() = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>

      library.ZapActor.actor ! ComputeLeaderboard()
      Redirect(routes.CFPAdmin.index()).flashing("success" -> Messages("leaderboard.compute"))
  }

  def allMyVotes = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      val uuid = request.webuser.uuid
      val result = Review.allVotesFromUser(uuid)
      val allProposalIDs = result.map(_._1)
      val allProposals = Proposal.loadAndParseProposals(allProposalIDs)

      Ok(views.html.CFPAdmin.allMyVotes(result, allProposals))
  }

  def advancedSearch(q: Option[String] = None, p: Option[Int] = None)= SecuredAction(IsMemberOf("cfp")).async {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>

      import play.api.libs.concurrent.Execution.Implicits.defaultContext

     ElasticSearch.doAdvancedSearch("speakers,proposals", q, p).map {
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
                  val talkType = (source \ "talkType" \ "id").as[String]
                  val code = (source \ "state" \ "code").as[String]
                  s"<i class='icon-folder-open'></i> Proposal <a href='${routes.CFPAdmin.openForReview(id)}'>$title</a> <strong>$code</strong> - $talkType"
                }
                case "speakers" => {
                  val uuid = (source \ "uuid").as[String]
                  val name = (source \ "name").as[String]
                  val firstName = (source \ "firstName").as[String]
                  val email = (source \ "email").as[String]
                  s"<i class='icon-user'></i> Speaker <a href='${routes.CFPAdmin.showSpeakerAndTalks(uuid)}'>$firstName $name</a> $email"
                }
                case other => "Unknown format " + index
              }
          }

          Ok(views.html.CFPAdmin.renderSearchResult(total, results, q, p)).as("text/html")
        }
        case r if r.isFailure => {
          InternalServerError(r.get)
        }
      }

  }

  def allSponsorTalks = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>

      val proposals = Proposal.allSponsorsTalk()
      Ok(views.html.CFPAdmin.allSponsorTalks(proposals))
  }

  def showSpeakerAndTalks(uuidSpeaker: String) = SecuredAction {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>

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
      // TODO a proposal with no votes will not be loaded
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
          proposal.state == ProposalState.ARCHIVED ||
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
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      ZapActor.actor ! ComputeVotesAndScore()
      Redirect(routes.CFPAdmin.allVotes("conf", None)).flashing("success" -> "Recomputing votes and scores...")
  }

  def removeSponsorTalkFlag(proposalId: String) = SecuredAction(IsMemberOf("admin")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      val uuid = request.webuser.uuid
      Proposal.removeSponsorTalkFlag(uuid, proposalId)
      Redirect(routes.CFPAdmin.allSponsorTalks).flashing("success" -> s"Removed sponsor talk on $proposalId")
  }

  def allProposalsByTrack(track: String) = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      val proposals = Proposal.allSubmitted().filter(_.track.id == track)
      Ok(views.html.CFPAdmin.allProposalsByTrack(proposals, track))
  }

  def allProposalsByType(confType: String) = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      val proposals = Proposal.allSubmitted().filter(_.talkType.id == confType)
      Ok(views.html.CFPAdmin.allProposalsByType(proposals, confType))
  }

  def showProposalsNotReviewedCompareTo(maybeReviewer: Option[String]) = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      val uuid = request.webuser.uuid
      maybeReviewer match {
        case None =>
          Ok(views.html.CFPAdmin.selectAnotherWebuser(Webuser.allCFPWebusers()))
        case Some(otherReviewer) =>
          val diffProposalIDs = Review.diffReviewBetween(otherReviewer, uuid)
          Ok(views.html.CFPAdmin.showProposalsNotReviewedCompareTo(diffProposalIDs, otherReviewer))
      }
  }

  // Returns all speakers
  def allSpeakers(export: Boolean = false, rejected: Boolean = true, accepted: Boolean = true, onlyWithSpeakerPass: Boolean = false) = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>

      val allSpeakers = Speaker.allSpeakers()

      val speakers1 = (accepted, onlyWithSpeakerPass) match {
        case (true, false) => allSpeakers.filter(s => Proposal.hasOneAcceptedProposal(s.uuid))
        case (_, true) => allSpeakers.filter(s => Proposal.hasOneProposalWithSpeakerTicket(s.uuid)).filter(s => Proposal.hasOneAcceptedProposal(s.uuid))
        case other => allSpeakers
      }

      val speakers = rejected match {
        case true => allSpeakers.filter(s => Proposal.hasOnlyRejectedProposals(s.uuid))
        case false => speakers1
      }
      export match {
        case true => {

          val dir = new File("./public/speakers")
          FileUtils.forceMkdir(dir)

          val file = new File(dir, "speakers_devoxxBE2014_all_macroman.csv")
          val writer = new PrintWriter(file, "Macroman")

          writer.println("email,firstName,lastName,company,hasSpeakerBadge,hasOneAccepted,isCFPMember,totalApproved,Proposal details separated by ;")
          speakers.sortBy(_.email).foreach {
            s =>
              writer.print(s.email.toLowerCase)
              writer.print(",")
              writer.print(s.firstName.getOrElse("?"))
              writer.print(",")
              writer.print(s.name.getOrElse("?"))
              writer.print(",")
              writer.print(s.company.map(c=>"\""+c.toUpperCase+"\"").getOrElse(""))
              writer.print(",")

              // freepass
              writer.print(Proposal.hasOneProposalWithSpeakerTicket(s.uuid))
              writer.print(",")

              writer.print(Proposal.hasOneAcceptedProposal(s.uuid))
              writer.print(",")

              writer.print(Webuser.isMember(s.uuid, "cfp"))
              writer.print(",")

              // number of talks
              val allAccepted = Proposal.allAcceptedForSpeaker(s.uuid)

              writer.print(allAccepted.size)
              writer.print(",")

              Proposal.allAcceptedForSpeaker(s.uuid).map{p=>
                ScheduleConfiguration.findSlotForConfType(p.talkType.id, p.id).map{ slot=>
                  writer.print(Messages(p.talkType.id))
                  writer.print(" \"" + p.title.replaceAll(","," ") + "\"")
                  writer.print(s" scheduled on ${slot.day.capitalize} ${slot.room.name} ")
                  writer.print(s"from ${slot.from.toString("HH:mm")} to ${slot.to.toString("HH:mm")}")
                }.getOrElse{
                  writer.print("\"")
                  writer.print( p.title.replaceAll(","," "))
                  writer.print("\"")
                  writer.print(s" ${p.talkType.label}}] not yet scheduled")

                }

                writer.print(",")
              }


              writer.println()
          }
          writer.close()
         Ok.sendFile(file, inline = false)
        }
        case false => Ok(views.html.CFPAdmin.allSpeakers(speakers.sortBy(_.cleanName)))
      }
  }

  def allWebusers() = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      val allSpeakers = Webuser.allSpeakers.sortBy(_.cleanName)
      Ok(views.html.CFPAdmin.allWebusers(allSpeakers))
  }


  import play.api.data.Form
  import play.api.data.Forms._

  def allCFPWebusers() = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      Ok(views.html.CFPAdmin.showCFPUsers(Webuser.allCFPAdminUsers()))
  }

  def updateTrackLeaders() = SecuredAction(IsMemberOf("cfp")) {
    implicit req: SecuredRequest[play.api.mvc.AnyContent] =>

      req.request.body.asFormUrlEncoded.map {
        mapsByTrack =>
          TrackLeader.updateAllTracks(mapsByTrack)
          Redirect(routes.CFPAdmin.allCFPWebusers).flashing("success" -> "List of track leaders updated")
      }.getOrElse {
        Redirect(routes.CFPAdmin.allCFPWebusers).flashing("error" -> "No value received")
      }
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
    "bio2" -> nonEmptyText(maxLength = 1200),
    "lang2" -> optional(text),
    "twitter2" -> optional(text),
    "avatarUrl2" -> optional(text),
    "company2" -> optional(text),
    "blog2" -> optional(text),
    "firstName" -> text,
    "acceptTermsConditions" -> boolean,
    "qualifications2" -> nonEmptyText(maxLength = 750)
  )(Speaker.createOrEditSpeaker)(Speaker.unapplyFormEdit))


  def newOrEditSpeaker(speakerUUID: Option[String]) = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
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
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      speakerForm.bindFromRequest.fold(
        invalidForm => BadRequest(views.html.CFPAdmin.newSpeaker(invalidForm)).flashing("error" -> "Invalid form, please check and correct errors. "),
        validSpeaker => {
          Option(validSpeaker.uuid) match {
            case Some(existingUUID) => {
              play.Logger.of("application.CFPAdmin").debug("Updating existing speaker " + existingUUID)
              Webuser.findByUUID(existingUUID).map {
                existingWebuser =>
                  Webuser.updateNames(existingUUID, validSpeaker.firstName.getOrElse("?"), validSpeaker.name.getOrElse("?"))
              }.getOrElse {
                val newWebuser = Webuser.createSpeaker(validSpeaker.email, validSpeaker.firstName.getOrElse("?"), validSpeaker.name.getOrElse("?"))
                val newUUID = Webuser.saveAndValidateWebuser(newWebuser)
                play.Logger.warn("Created missing webuser " + newUUID)
              }
              Speaker.save(validSpeaker)
              Event.storeEvent(Event(validSpeaker.cleanName, request.webuser.uuid, "updated a speaker [" + validSpeaker.uuid + "]"))
              Redirect(routes.CFPAdmin.showSpeakerAndTalks(existingUUID)).flashing("success" -> "Profile updated")
            }
            case None => {
              val webuser = Webuser.createSpeaker(validSpeaker.email, validSpeaker.firstName.getOrElse("Firstname"), validSpeaker.name.getOrElse("Lastname"))
              Webuser.saveNewSpeakerEmailNotValidated(webuser)
              val newUUID = Webuser.saveAndValidateWebuser(webuser)
              Speaker.save(validSpeaker.copy(uuid = newUUID))
              Event.storeEvent(Event(validSpeaker.cleanName, request.webuser.uuid, "created a speaker [" + validSpeaker.uuid + "]"))
              Redirect(routes.CFPAdmin.showSpeakerAndTalks(newUUID)).flashing("success" -> "Profile saved")
            }
          }
        }
      )
  }

  def setPreferredDay(proposalId: String, day: String) = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      Proposal.setPreferredDay(proposalId: String, day: String)
      Redirect(routes.CFPAdmin.openForReview(proposalId)).flashing("success" -> ("Preferred day set to " + day))
  }

  def resetPreferredDay(proposalId: String) = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      Proposal.resetPreferredDay(proposalId: String)
      Redirect(routes.CFPAdmin.openForReview(proposalId)).flashing("success" -> "No preferences")
  }

  def showProposalsWithNoVotes() = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      val proposals = Review.allProposalsWithNoVotes
      Ok(views.html.CFPAdmin.showProposalsWithNoVotes(proposals))
  }

}



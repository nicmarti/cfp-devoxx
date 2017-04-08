package controllers

import java.io.{File, FileOutputStream, OutputStreamWriter, PrintWriter}

import library.search.ElasticSearch
import library.{ComputeLeaderboard, ComputeVotesAndScore, SendMessageInternal, SendMessageToSpeaker, _}
import models.Review._
import models._
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import org.joda.time.DateTimeZone
import play.api.data.Forms._
import play.api.data._
import play.api.data.validation.Constraints._
import play.api.i18n.Messages
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.Cookie

/**
  * The backoffice controller for the CFP technical committee.
  *
  * Author: @nmartignole
  * Created: 11/11/2013 09:09 in Thalys, heading to Devoxx2013
  */
object CFPAdmin extends SecureCFPController {

  val messageForm: Form[String] = Form("msg" -> nonEmptyText(maxLength = 1000))
  val voteForm: Form[Int] = Form("vote" -> number(min = 0, max = 10))
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
    "qualifications2" -> nonEmptyText(maxLength = 750),
    "questionAndAnswers2" -> optional(seq(
      mapping(
        "question" -> optional(text),
        "answer" -> optional(text)
      )(QuestionAndAnswer.apply)(QuestionAndAnswer.unapply))
    )
  )(Speaker.createOrEditSpeaker)(Speaker.unapplyFormEdit))

  def index(page: Int,
            sort: Option[String],
            ascdesc: Option[String],
            track: Option[String]) = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>

      val uuid = request.webuser.uuid
      val sorter = proposalSorter(sort)
      val orderer = proposalOrder(ascdesc)
      val allNotReviewed = Review.allProposalsNotReviewed(uuid)

      // Get a default track to filter on and save it in cookie
      var trackValue : String = Track.allIDs.take(1).last
      val trackCookie = request.cookies.  get("track")

      if (track.isDefined) {
        trackValue = track.get
      } else if (trackCookie.isDefined) {
          trackValue = trackCookie.get.value
      }

      val maybeFilteredProposals = allNotReviewed.filter(_.track.id.equalsIgnoreCase(StringUtils.trimToEmpty(trackValue)))
      val allProposalsForReview = sortProposals(maybeFilteredProposals, sorter, orderer)

      val twentyEvents = Event.loadEvents(20, page)

      val etag = allProposalsForReview.hashCode() + "_" + twentyEvents.hashCode()

      Ok(views.html.CFPAdmin.cfpAdminIndex(twentyEvents, allProposalsForReview, Event.totalEvents(), page, sort, ascdesc, Option(trackValue)))
        .withHeaders("ETag" -> etag)
        .withCookies(Cookie("track", trackValue, Option(2592000)))  // Expires in one month
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

  def openForReview(proposalId: String) = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      val uuid = request.webuser.uuid
      Proposal.findById(proposalId) match {
        case Some(proposal) =>
          val speakerDiscussion = Comment.allSpeakerComments(proposal.id)
          val internalDiscussion = Comment.allInternalComments(proposal.id)
          val maybeMyVote = Review.lastVoteByUserForOneProposal(uuid, proposalId)
          val proposalsByAuths = allProposalByProposal(proposal)
          Ok(views.html.CFPAdmin.showProposal(proposal, proposalsByAuths, speakerDiscussion, internalDiscussion, messageForm, messageForm, voteForm, maybeMyVote, uuid))
        case None => NotFound("Proposal not found").as("text/html")
      }
  }

  def allProposalByProposal(proposal: Proposal): Map[String, Map[String, models.Proposal]] = {
    val authorIds: List[String] = proposal.mainSpeaker :: proposal.secondarySpeaker.toList ::: proposal.otherSpeakers
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
          case Some(proposal) =>
            val currentAverageScore = Review.averageScore(proposalId)
            val countVotesCast = Review.totalVoteCastFor(proposalId)
            // votes exprimes (sans les votes a zero)
            val countVotes = Review.totalVoteFor(proposalId)
            val allVotes = Review.allVotesFor(proposalId)

            // The next proposal I should review
            val allNotReviewed = Review.allProposalsNotReviewed(uuid)
            val (sameTracks, otherTracks) = allNotReviewed.partition(_.track.id == proposal.track.id)
            val (sameTalkType, otherTalksType) = allNotReviewed.partition(_.talkType.id == proposal.talkType.id)

            val nextToBeReviewedSameTrack = (sameTracks.sortBy(_.talkType.id) ++ otherTracks).headOption
            val nextToBeReviewedSameFormat = (sameTalkType.sortBy(_.track.id) ++ otherTalksType).headOption

            // If Golden Ticket is active
            if (ConferenceDescriptor.isGoldenTicketActive) {
              val averageScoreGT = ReviewByGoldenTicket.averageScore(proposalId)
              val countVotesCastGT: Option[Long] = Option(ReviewByGoldenTicket.totalVoteCastFor(proposalId))
              Ok(views.html.CFPAdmin.showVotesForProposal(uuid, proposal, currentAverageScore, countVotesCast, countVotes, allVotes, nextToBeReviewedSameTrack, nextToBeReviewedSameFormat, averageScoreGT, countVotesCastGT))
            } else {
              Ok(views.html.CFPAdmin.showVotesForProposal(uuid, proposal, currentAverageScore, countVotesCast, countVotes, allVotes, nextToBeReviewedSameTrack, nextToBeReviewedSameFormat, 0, None))
            }
          case None => NotFound("Proposal not found").as("text/html")
        }
      }
  }

  def sendMessageToSpeaker(proposalId: String) = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      val uuid = request.webuser.uuid
      Proposal.findById(proposalId) match {
        case Some(proposal) =>
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
        case None => NotFound("Proposal not found").as("text/html")
      }
  }

  // Post an internal message that is visible only for program committee
  def postInternalMessage(proposalId: String) = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      val uuid = request.webuser.uuid
      Proposal.findById(proposalId) match {
        case Some(proposal) =>
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
        case None => NotFound("Proposal not found").as("text/html")
      }
  }

  def voteForProposal(proposalId: String) = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      val uuid = request.webuser.uuid
      Proposal.findById(proposalId) match {
        case Some(proposal) =>
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
        case None => NotFound("Proposal not found").as("text/html")
      }
  }

  def clearVoteForProposal(proposalId: String) = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      val uuid = request.webuser.uuid
      Proposal.findById(proposalId) match {
        case Some(proposal) =>
          Review.removeVoteForProposal(proposalId, uuid)
          Redirect(routes.CFPAdmin.showVotesForProposal(proposalId)).flashing("vote" -> "Removed your vote")
        case None => NotFound("Proposal not found").as("text/html")
      }
  }

  def allMyVotes(talkType: String) = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>

      ConferenceDescriptor.ConferenceProposalTypes.ALL.find(_.id == talkType).map {
        pType =>
          val uuid = request.webuser.uuid
          val allMyVotes = Review.allVotesFromUser(uuid)
          val allProposalIDs = allMyVotes.map(_._1)
          val allProposalsForProposalType = Proposal.loadAndParseProposals(allProposalIDs).filter(_._2.talkType == pType)
          val allProposalsIdsProposalType = allProposalsForProposalType.keySet

          val allMyVotesForSpecificProposalType = allMyVotes.filter {
            proposalIdAndVotes => allProposalsIdsProposalType.contains(proposalIdAndVotes._1)
          }

          val allScoresForProposals: Map[String, Double] = allProposalsIdsProposalType.map {
            pid: String => (pid, Review.averageScore(pid))
          }.toMap

          val sortedListOfProposals = allMyVotesForSpecificProposalType.toList.sortBy {
            case (proposalID, maybeScore) =>
              maybeScore.getOrElse(0.toDouble)
          }.reverse

          Ok(views.html.CFPAdmin.allMyVotes(sortedListOfProposals, allProposalsForProposalType, talkType, allScoresForProposals))
      }.getOrElse {
        BadRequest("Invalid proposal type")
      }
  }

  def advancedSearch(q: Option[String] = None, p: Option[Int] = None) = SecuredAction(IsMemberOf("cfp")).async {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>

      import play.api.libs.concurrent.Execution.Implicits.defaultContext

      ElasticSearch.doAdvancedSearch("speakers,proposals", q, p).map {
        case r if r.isSuccess =>
          val json = Json.parse(r.get)
          val total = (json \ "hits" \ "total").as[Int]
          val hitContents = (json \ "hits" \ "hits").as[List[JsObject]]

          val results = hitContents.sortBy {
            jsvalue =>
              val index = (jsvalue \ "_index").as[String]
              index
          }.map {
            jsvalue =>
              val index = (jsvalue \ "_index").as[String]
              val source = jsvalue \ "_source"
              index match {
                case "proposals" =>
                  val id = (source \ "id").as[String]
                  val title = (source \ "title").as[String]
                  val talkType = Messages((source \ "talkType" \ "id").as[String])
                  val code = (source \ "state" \ "code").as[String]
                  val mainSpeaker = (source \ "mainSpeaker").as[String]
                  s"<p class='searchProposalResult'><i class='icon-folder-open'></i> Proposal <a href='${routes.CFPAdmin.openForReview(id)}'>$title</a> <strong>$code</strong> - by $mainSpeaker - $talkType</p>"
                case "speakers" =>
                  val uuid = (source \ "uuid").as[String]
                  val name = (source \ "name").as[String]
                  val firstName = (source \ "firstName").as[String]
                  s"<p class='searchSpeakerResult'><i class='icon-user'></i> Speaker <a href='${routes.CFPAdmin.showSpeakerAndTalks(uuid)}'>$firstName $name</a></p>"
                case other => "Unknown format " + index
              }
          }

          Ok(views.html.CFPAdmin.renderSearchResult(total, results, q, p)).as("text/html")
        case r if r.isFailure =>
          InternalServerError(r.get)
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
        case Some(speaker) =>
          val proposals = Proposal.allProposalsByAuthor(speaker.uuid)
          Ok(views.html.CFPAdmin.showSpeakerAndTalks(speaker, proposals, request.webuser.uuid))
        case None => NotFound("Speaker not found")
      }
  }

  def allVotes(confType: String, track: Option[String]) = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>

      val reviews: Map[String, (Score, TotalVoter, TotalAbst, AverageNote, StandardDev)] = Review.allVotes()
      val totalApproved = ApprovedProposal.countApproved(confType)

      val allProposals = Proposal.loadAndParseProposals(reviews.keySet)

      val listOfProposals = reviews.flatMap {
        case (proposalId, scoreAndVotes) =>
          val maybeProposal = allProposals.get(proposalId)
          maybeProposal match {
            case None => play.Logger.of("CFPAdmin").error(s"Unable to load proposal id $proposalId")
              None
            case Some(p) =>
              val goldenTicketScore: Double = ReviewByGoldenTicket.averageScore(p.id)
              val gtVoteCast: Long = ReviewByGoldenTicket.totalVoteCastFor(p.id)
              Option(p, scoreAndVotes, goldenTicketScore, gtVoteCast)
          }
      }

      val tempListToDisplay = confType match {
        case "all" => listOfProposals
        case filterType => listOfProposals.filter(_._1.talkType.id == filterType)
      }
      val listToDisplay = track match {
        case None => tempListToDisplay
        case Some(trackId) => tempListToDisplay.filter(_._1.track.id == trackId)
      }

      val totalRemaining = ApprovedProposal.remainingSlots(confType)
      Ok(views.html.CFPAdmin.allVotes(listToDisplay.toList, totalApproved, totalRemaining, confType))
  }

  def removeSponsorTalkFlag(proposalId: String) = SecuredAction(IsMemberOf("admin")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      val uuid = request.webuser.uuid
      Proposal.removeSponsorTalkFlag(uuid, proposalId)
      Redirect(routes.CFPAdmin.allSponsorTalks()).flashing("success" -> s"Removed sponsor talk on $proposalId")
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
  def allSpeakersExport() = SecuredAction(IsMemberOf("admin")) {

    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>

      val allSpeakers = Speaker.allSpeakers();

      val dir = new File("./public/speakers")
      FileUtils.forceMkdir(dir)

      val conferenceNameSpaces = Messages("CONF.title").replaceAll(" ", "")
      val file = new File(dir, s"speakers${conferenceNameSpaces}.csv")

      val writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"), true)

      allSpeakers.sortBy(_.email).foreach {
        s =>

          val proposals: List[Proposal] = Proposal.allAcceptedForSpeaker(s.uuid)

          if (proposals.nonEmpty) {

            writer.print(s.email.toLowerCase)
            writer.print(",")
            writer.print(s.cleanTwitter.getOrElse("").toLowerCase)
            writer.print(",")
            writer.print(s.firstName.getOrElse("?").capitalize)
            writer.print(",")
            writer.print(s.name.getOrElse("?").capitalize)
            writer.print(",")

            proposals.foreach { p =>
              val proposalUrl = "http://" + ConferenceDescriptor.current().conferenceUrls.cfpHostname +
                routes.Publisher.showDetailsForProposal(p.id, p.escapedTitle)

              ScheduleConfiguration.findSlotForConfType(p.talkType.id, p.id).map { slot =>
                writer.print(Messages(p.talkType.id))
                writer.print(": \"" + p.title.replaceAll(",", " ") + "\"")
                writer.print("\", ")
                writer.print("\"" + proposalUrl + "\", ")
                writer.print(s" scheduled on ${slot.day.capitalize} ${slot.room.name} ")
                writer.print(s"from ${slot.from.toDateTime(DateTimeZone.forID(ConferenceDescriptor.timeZone)).toString("HH:mm")} to ${slot.to.toDateTime(DateTimeZone.forID(ConferenceDescriptor.timeZone)).toString("HH:mm")}")
              }.getOrElse {
                writer.print("\"")
                writer.print(p.title.replaceAll(",", " "))
                writer.print("\", ")
                writer.print("\"" + proposalUrl + "\", ")
                writer.print(s" ${Messages(p.talkType.label)} not yet scheduled")
              }

              writer.print(",")
            }

            writer.println()
          }
      }
      writer.close()
      Ok.sendFile(file, inline = false)
  }

  def allSpeakers() = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      Ok(views.html.CFPAdmin.allSpeakersHome())
  }

  def duplicateSpeakers() = SecuredAction(IsMemberOf("cfp")) {
    var uniqueSpeakers = scala.collection.mutable.Set[Speaker]()

    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      val allApprovedSpeakers = ApprovedProposal.allApprovedSpeakers()
      val allRefusedSpeakers = ApprovedProposal.allRefusedSpeakers()
      val allSpeakers = allApprovedSpeakers ++ allRefusedSpeakers

      val speakersSortedByUUID = allSpeakers.toList
        .sortBy(_.uuid)
        .groupBy(_.uuid)
        .filter(_._2.size == 1)
        .flatMap { uuid => uuid._2}


      val uniqueSpeakersSortedByName = speakersSortedByUUID.toList
        .sortBy(_.cleanName)
        .groupBy(_.cleanName)
        .filter(_._2.size != 1)
        .flatMap { name => name._2}

      Ok(views.html.CFPAdmin.duplicateSpeakers(uniqueSpeakersSortedByName.toList))
  }

  def allDevoxxians() = SecuredAction(IsMemberOf("admin")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      val devoxxians = Webuser.allDevoxxians()
      Ok(views.html.Backoffice.allDevoxxians(devoxxians))
  }

  def allSpeakersWithApprovedTalks() = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      val allSpeakers = ApprovedProposal.allApprovedSpeakers()
      Ok(views.html.CFPAdmin.allSpeakers(allSpeakers.toList.sortBy(_.cleanName)))
  }

  def allApprovedSpeakersByCompany(showQuickiesAndBof: Boolean) = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      val speakers = ApprovedProposal.allApprovedSpeakers()
        .groupBy(_.company.map(_.toLowerCase.trim).getOrElse("Pas de société"))
        .toList
        .sortBy(_._2.size)
        .reverse

      val proposals = speakers.map {
        case (company, subSpeakers) =>
          val setOfProposals = subSpeakers.toList.flatMap {
            s =>
              Proposal.allApprovedProposalsByAuthor(s.uuid).values
          }.toSet.filterNot { p: Proposal =>
            if (showQuickiesAndBof) {
              p == null
            } else {
              p.talkType == ConferenceDescriptor.ConferenceProposalTypes.BOF ||
                p.talkType == ConferenceDescriptor.ConferenceProposalTypes.QUICK
            }
          }
          (company, setOfProposals)
      }

      Ok(views.html.CFPAdmin.allApprovedSpeakersByCompany(speakers, proposals))
  }

  // All speakers that accepted to present a talk (including BOF and Quickies)
  def allSpeakersThatForgetToAccept() = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      val speakers = ApprovedProposal.allApprovedSpeakers()

      val proposals: Set[(Speaker, Iterable[Proposal])] = speakers.map {
        speaker =>
          (speaker, Proposal.allThatForgetToAccept(speaker.uuid).values)
      }.filter(_._2.nonEmpty)

      Ok(views.html.CFPAdmin.allSpeakersThatForgetToAccept(proposals))
  }

  // All speakers with a speaker's badge (it does not include Quickies, BOF and 3rd, 4th speakers)
  def allSpeakersWithAcceptedTalksAndBadge() = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      val speakers = ApprovedProposal.allApprovedSpeakers()
      val proposals: List[(Speaker, Iterable[Proposal])] = speakers.toList.map {
        speaker =>
          val allProposalsForThisSpeaker = Proposal.allApprovedAndAcceptedProposalsByAuthor(speaker.uuid).values
          val onIfFirstOrSecondSpeaker = allProposalsForThisSpeaker.filter(p => p.mainSpeaker == speaker.uuid || p.secondarySpeaker == Some(speaker.uuid))
            .filter(p => ProposalConfiguration.doesProposalTypeGiveSpeakerFreeEntrance(p.talkType))
          (speaker, onIfFirstOrSecondSpeaker)
      }.filter(_._2.nonEmpty)

      Ok(views.html.CFPAdmin.allSpeakersWithAcceptedTalksAndBadge(proposals))
  }

  // All speakers with a speaker's badge
  def allSpeakersWithAcceptedTalks() = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      val speakers = ApprovedProposal.allApprovedSpeakers()
      val proposals: List[(Speaker, Iterable[Proposal])] = speakers.toList.map {
        speaker =>
          val allProposalsForThisSpeaker = Proposal.allApprovedAndAcceptedProposalsByAuthor(speaker.uuid).values
          val onIfFirstOrSecondSpeaker = allProposalsForThisSpeaker.filter(p => p.mainSpeaker == speaker.uuid || p.secondarySpeaker == Some(speaker.uuid))
          (speaker, onIfFirstOrSecondSpeaker)
      }.filter(_._2.nonEmpty)

      Ok(views.html.CFPAdmin.allSpeakersWithAcceptedTalksAndBadge(proposals))
  }

  def allSpeakersWithRejectedTalks() = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      val refusedSpeakers = ApprovedProposal.allRefusedSpeakerIDs()
      val approvedSpeakers = ApprovedProposal.allApprovedSpeakerIDs()

      val diffRejectedSpeakers: Set[String] = refusedSpeakers.diff(approvedSpeakers)

      val proposals: List[(Speaker, Iterable[Proposal])] = diffRejectedSpeakers.toList.map {
        speakerId =>
          val allProposalsForThisSpeaker = Proposal.allRejectedForSpeaker(speakerId)
          val onIfFirstOrSecondSpeaker = allProposalsForThisSpeaker.filter(p => p.mainSpeaker == speakerId || p.secondarySpeaker == Some(speakerId))
          (Speaker.findByUUID(speakerId).get, onIfFirstOrSecondSpeaker)
      }.filter(_._2.nonEmpty)

     Ok(views.html.CFPAdmin.allSpeakersWithRejectedProposals(proposals))
  }

  def allSpeakersWithAcceptedTalksForExport() = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      val speakers = ApprovedProposal.allApprovedSpeakers()
      val proposals: List[(Speaker, Iterable[Proposal])] = speakers.toList.map {
        speaker =>
          val allProposalsForThisSpeaker = Proposal.allApprovedAndAcceptedProposalsByAuthor(speaker.uuid).values
          val onIfFirstOrSecondSpeaker = allProposalsForThisSpeaker.filter(p => p.mainSpeaker == speaker.uuid || p.secondarySpeaker == Some(speaker.uuid))
          (speaker, onIfFirstOrSecondSpeaker)
      }.filter(_._2.nonEmpty)

      Ok(views.html.CFPAdmin.allSpeakersWithAcceptedTalksForExport(proposals))
  }

  def allWebusers() = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      val allSpeakers = Webuser.allSpeakers.sortBy(_.cleanName)
      Ok(views.html.CFPAdmin.allWebusers(allSpeakers))
  }

  def allCFPWebusers() = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      Ok(views.html.CFPAdmin.showCFPUsers(Webuser.allCFPAdminUsers()))
  }

  def updateTrackLeaders() = SecuredAction(IsMemberOf("cfp")) {
    implicit req: SecuredRequest[play.api.mvc.AnyContent] =>

      req.request.body.asFormUrlEncoded.map {
        mapsByTrack =>
          TrackLeader.updateAllTracks(mapsByTrack)
          Redirect(routes.CFPAdmin.allCFPWebusers()).flashing("success" -> "List of track leaders updated")
      }.getOrElse {
        Redirect(routes.CFPAdmin.allCFPWebusers()).flashing("error" -> "No value received")
      }
  }

  def newOrEditSpeaker(speakerUUID: Option[String]) = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      speakerUUID match {
        case Some(uuid) =>
          Speaker.findByUUID(uuid).map {
            speaker: Speaker =>
              Ok(views.html.CFPAdmin.newSpeaker(speakerForm.fill(speaker))).flashing("success" -> "You are currently editing an existing speaker")
          }.getOrElse {
            Ok(views.html.CFPAdmin.newSpeaker(speakerForm)).flashing("error" -> "Speaker not found")
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
            case Some(existingUUID) =>
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
            case None =>
              val webuser = Webuser.createSpeaker(validSpeaker.email, validSpeaker.firstName.getOrElse("Firstname"), validSpeaker.name.getOrElse("Lastname"))
              Webuser.saveNewWebuserEmailNotValidated(webuser)
              val newUUID = Webuser.saveAndValidateWebuser(webuser)
              Speaker.save(validSpeaker.copy(uuid = newUUID))
              Event.storeEvent(Event(validSpeaker.cleanName, request.webuser.uuid, "created a speaker [" + validSpeaker.uuid + "]"))
              Redirect(routes.CFPAdmin.showSpeakerAndTalks(newUUID)).flashing("success" -> "Profile saved")
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

  def showProposalsByTagId(tagId: String) = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>

      val tag = Tag.findById(tagId)
      if (tag.isDefined) {
        val proposals = Tags.allProposalsByTagId(tagId)

        Ok(views.html.CFPAdmin.showProposalsByTag(tag.get, proposals))
      } else {
        BadRequest("Invalid tag")
      }
  }

  def history(proposalId: String) = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      Proposal.findById(proposalId).map {
        proposal: Proposal =>
          Ok(views.html.CFPAdmin.history(proposal))
      }.getOrElse(NotFound("Proposal not found"))
  }
}

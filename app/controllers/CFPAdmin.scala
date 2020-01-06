package controllers

import java.io.{File, FileOutputStream, OutputStreamWriter, PrintWriter}

import library.search.ElasticSearch
import library.{SendMessageInternal, SendMessageToSpeaker, _}
import models.Review._
import models._
import org.apache.commons.io.FileUtils
import org.joda.time.DateTimeZone
import play.api.data.Forms._
import play.api.data._
import play.api.data.validation.Constraints._
import play.api.i18n.Messages
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, AnyContent}

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
    "qualifications2" -> nonEmptyText(maxLength = 750)
  )(Speaker.createOrEditSpeaker)(Speaker.unapplyFormEdit))

  def index(page: Int,
            pageReview: Int,
            sort: Option[String],
            ascdesc: Option[String],
            track: Option[String]): Action[AnyContent] = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>

      val uuid = request.webuser.uuid
      val sorter = ProposalUtil.proposalSorter(sort)
      val orderer = ProposalUtil.proposalOrder(ascdesc)
      val (totalToReviewFiltered,allNotReviewed) = Review.allProposalsNotReviewed(uuid, pageReview, 25, track)

      val totalReviewed = Review.totalNumberOfReviewedProposals(uuid)
      val totalVoted = Review.totalProposalsVotedForUser(uuid)
      val allProposalsForReview = ProposalUtil.sortProposals(allNotReviewed, sorter, orderer)

      val twentyEvents = Event.loadEvents(20, page)

      val etag = allProposalsForReview.hashCode() + "_" + twentyEvents.hashCode()

      val totalToReview = Review.countProposalNotReviewed(uuid)

      track.map {
        trackValue: String =>
          Ok(views.html.CFPAdmin.cfpAdminIndex(twentyEvents, allProposalsForReview, Event.totalEvents(), page, sort, ascdesc, Some(trackValue), totalReviewed, totalVoted, totalToReview, pageReview, totalToReviewFiltered))
            .withHeaders("ETag" -> etag)
      }.getOrElse {
        Ok(views.html.CFPAdmin.cfpAdminIndex(twentyEvents, allProposalsForReview, Event.totalEvents(), page, sort, ascdesc, None, totalReviewed, totalVoted,totalToReview, pageReview, totalToReviewFiltered))
          .withHeaders("ETag" -> etag)
      }

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
            val (sameTrackAndFormats, otherTracksOrFormats) = allNotReviewed.partition(p => p.track.id == proposal.track.id && p.talkType.id == proposal.talkType.id)
            val (sameTracks, otherTracks) = allNotReviewed.partition(_.track.id == proposal.track.id)
            val (sameTalkType, otherTalksType) = allNotReviewed.partition(_.talkType.id == proposal.talkType.id)

            // Note: not appending otherTracksOrFormats here, as we want to show the button in the
            // template only if there are some remaining talks to be reviewed for same track & talkType
            val nextToBeReviewedSameTrackAndFormat = (sameTrackAndFormats.sortBy(_.track.id)).headOption
            val nextToBeReviewedSameTrack = (sameTracks.sortBy(_.talkType.id) ++ otherTracks).headOption
            val nextToBeReviewedSameFormat = (sameTalkType.sortBy(_.track.id) ++ otherTalksType).headOption

            // The Reviewer leaderboard, remove if the user did not vote for any talks and sort by number of talks reviewed
            val bestReviewers: List[(String, Int, Int)] = Review.allReviewersAndStats().filterNot(br => br._3 < 1).sortBy(_._3)


            // Find the current authenticated user (with uuid), the user that is before, and the one that is after
            val listOfReviewers: List[List[(String, Int, Int)]] = bestReviewers
              .sliding(3) // This iterate the list 3 by 3
              .filter(subList => subList.exists(_._1 == uuid)) // we are only intersted if the element 1 is our uuid.
              .toList // else since it's an iterator... we won't be able to apply methods

            // So now, listOfReviewers should have 3 elements  :

            // Element 1 =>
            // (mike, 1, 20)
            // (bob, 1, 100)
            // (nic, 1, 200)

            // Element 2 =>
            // (bob, 1, 100)
            // (nic, 1, 200)
            // (theBoss, 1, 300)

            // Element 3 =>
            // (nic, 1, 200)
            // (theBoss, 1, 300)
            // (theKing, 1, 500)

            // We take the 2 first element (or the only element if we're first or second in the list of reviewers order by nb of reviews)
            // Because this list might be empty we use headOption
            val maybeTwoFirstTuples =  listOfReviewers.take(2)

            val meAndMyFollowers: Option[List[(String, Int, Int)]] = maybeTwoFirstTuples.size match {
              case 1 => maybeTwoFirstTuples.headOption
              case other => maybeTwoFirstTuples.drop(1).headOption
            }

            // If Golden Ticket is active
            if (ConferenceDescriptor.isGoldenTicketActive) {
              val averageScoreGT = ReviewByGoldenTicket.averageScore(proposalId)
              val countVotesCastGT: Option[Long] = Option(ReviewByGoldenTicket.totalVoteCastFor(proposalId))
              Ok(views.html.CFPAdmin.showVotesForProposal(uuid, proposal, currentAverageScore, countVotesCast, countVotes, allVotes, nextToBeReviewedSameTrackAndFormat, nextToBeReviewedSameTrack, nextToBeReviewedSameFormat, averageScoreGT, countVotesCastGT, meAndMyFollowers))
            } else {
              Ok(views.html.CFPAdmin.showVotesForProposal(uuid, proposal, currentAverageScore, countVotesCast, countVotes, allVotes, nextToBeReviewedSameTrackAndFormat, nextToBeReviewedSameTrack, nextToBeReviewedSameFormat, 0, None, meAndMyFollowers))
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

  // Post an internal message that is visible only for program committe
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

  def allMyVotes(talkType: String, selectedTrack: Option[String]) = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>

      ConferenceDescriptor.ConferenceProposalTypes.ALL.find(_.id == talkType).map {
        pType =>
          val uuid = request.webuser.uuid
          val allMyVotesIncludingAbstentions = Review.allVotesFromUser(uuid)
          val allProposalIDsWhereIVoted = allMyVotesIncludingAbstentions.map(_._1)
          val allProposalsMatchingCriteriaWhereIVoted = Proposal.loadAndParseProposals(allProposalIDsWhereIVoted)
            .filter(p => p._2.talkType == pType && selectedTrack.map(p._2.track.id == _).getOrElse(true))
          val allProposalsIdsMatchingCriteriaWhereIVoted = allProposalsMatchingCriteriaWhereIVoted.keySet

          val allMyVotesIncludingAbstentionsMatchingCriteria = allMyVotesIncludingAbstentions.filter {
            proposalIdAndVotes => allProposalsIdsMatchingCriteriaWhereIVoted.contains(proposalIdAndVotes._1)
          }
          val sortedAllMyVotesIncludingAbstentionsMatchingCriteria = allMyVotesIncludingAbstentionsMatchingCriteria.toList.sortBy(_._2).reverse
          val sortedAllMyVotesExcludingAbstentionsMatchingCriteria = sortedAllMyVotesIncludingAbstentionsMatchingCriteria.filter(_._2 != 0)

          val allScoresForProposals: Map[String, Double] = allProposalsIdsMatchingCriteriaWhereIVoted.map {
            pid: String => (pid, Review.averageScore(pid))
          }.toMap

          val proposalsNotReviewed = Review.allProposalsNotReviewed(uuid).filter(_.talkType == pType)
          val proposalsNotReviewedCount = proposalsNotReviewed.size
          val firstProposalNotReviewed = proposalsNotReviewed.headOption

          Ok(views.html.CFPAdmin.allMyVotes(sortedAllMyVotesIncludingAbstentionsMatchingCriteria, sortedAllMyVotesExcludingAbstentionsMatchingCriteria, allProposalsMatchingCriteriaWhereIVoted, talkType, selectedTrack, allScoresForProposals, proposalsNotReviewedCount, firstProposalNotReviewed))
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
                  s"<p class='searchProposalResult'><i class='fas fa-folder-open'></i> Proposal <a href='${routes.CFPAdmin.openForReview(id)}'>$title</a> <strong>$code</strong> - by $mainSpeaker - $talkType</p>"
                case "speakers" =>
                  val uuid = (source \ "uuid").as[String]
                  val name = (source \ "name").as[String]
                  val firstName = (source \ "firstName").as[String]
                  s"<p class='searchSpeakerResult'><i class='fas fa-user'></i> Speaker <a href='${routes.CFPAdmin.showSpeakerAndTalks(uuid)}'>$firstName $name</a></p>"
                case _ => "Unknown format " + index
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

      play.Logger.of("application.Benchmark").debug(s"******* CFPAdmin allVotes for $confType and $track")
      val reviews: Map[String, (Score, TotalVoter, TotalAbst, AverageNote, StandardDev)] = Review.allVotes()

      val totalApproved = Benchmark.measure(
        () => ApprovedProposal.countApproved(confType), "count approved talks"
      )

      val allProposals = Benchmark.measure(() => Proposal.loadAndParseProposals(reviews.keySet), "load and parse proposals")

      val listOfProposals = Benchmark.measure(() => reviews.flatMap {
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
      }, "create list of Proposals")

      val tempListToDisplay = Benchmark.measure(() => confType match {
        case "all" => listOfProposals
        case filterType => listOfProposals.filter(_._1.talkType.id == filterType)
      }, "list to display")

      val listToDisplay = Benchmark.measure(() => track match {
        case None => tempListToDisplay
        case Some(trackId) => tempListToDisplay.filter(_._1.track.id == trackId)
      }, "filter by track")

      val totalRemaining = Benchmark.measure(() => ApprovedProposal.remainingSlots(confType), "calculate remaining slots")
      Ok(views.html.CFPAdmin.allVotes(listToDisplay.toList, totalApproved, totalRemaining, confType))
  }

  def allVotesVersion2(confType: String, page: Int = 0, resultats: Int = 25, sortBy: String = "gt_and_cfp") = SecuredAction(IsMemberOf("admin")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>

      play.Logger.of("application.Benchmark").debug(s"******* CFPAdmin Version 2 for $confType")

      val reviews: Map[String, (Score, TotalVoter, TotalAbst, AverageNote, StandardDev)] = Benchmark.measure(() => Review.allVotes(), "v2 - All votes")

      val allProposals = Benchmark.measure(() => {
        Proposal.loadAndParseProposals(reviews.keySet, ProposalType.parse(confType))
      }, "v2 - Load and parse proposals for " + ProposalType.parse(confType))

      val listOfProposals =
        Benchmark.measure(() => reviews.flatMap {
          case (proposalId, scoreAndVotes) =>
            val maybeProposal = allProposals.get(proposalId)
            maybeProposal match {
              case Some(p) =>
                val (gtVoteCast, goldenTicketScore) = ReviewByGoldenTicket.totalVotesAndAverageScoreFor(p.id)
                Option(p, scoreAndVotes, goldenTicketScore, gtVoteCast)
              case None => // We ignore here cause Review loaded all Proposals.
                None
            }
        }, "v2 - Create list of Proposals")

      val totalApproved = ApprovedProposal.countApproved(confType)
      val totalRemaining = ApprovedProposal.remainingSlots(confType)

      val toSlide = listOfProposals.toList.sortBy {
        case (_, voteAndTotalVotes, gtScore, _) if sortBy == "gt_and_cfp" => library.Stats.average(List(gtScore, voteAndTotalVotes._4.n))
        case (_, voteAndTotalVotes, _, _) => voteAndTotalVotes._4.n
      }.reverse
      val sliced = toSlide.slice(page * resultats, (page + 1) * resultats)

      Ok(views.html.CFPAdmin.allVotesVersion2(sliced, totalApproved, totalRemaining, confType, page, resultats, sortBy))

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
                writer.print(s"from ${slot.from.toDateTime(DateTimeZone.forID("Europe/Paris")).toString("HH:mm")} to ${slot.to.toDateTime(DateTimeZone.forID("Europe/Paris")).toString("HH:mm")}")
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
        .flatMap { uuid => uuid._2 }

      val uniqueSpeakersSortedByName = speakersSortedByUUID.toList
        .sortBy(_.cleanName)
        .groupBy(_.cleanName)
        .filter(_._2.size != 1)
        .flatMap { name => name._2 }

      val speakersSortedByEmail = allSpeakers.toList
        .sortBy(_.email)
        .groupBy(_.email)
        .filter(_._2.size != 1)
        .flatMap { email => email._2 }

      val uniqueSpeakersSortedByEmail = speakersSortedByEmail.toList
        .sortBy(_.email)
        .groupBy(_.email)
        .filter(_._2.size != 1)
        .flatMap { email => email._2 }

      val combinedList = uniqueSpeakersSortedByName.toList ++ uniqueSpeakersSortedByEmail.toList

      Ok(views.html.CFPAdmin.duplicateSpeakers(combinedList))
  }

  def allDevoxxians() = SecuredAction(IsMemberOf("admin")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      val devoxxians = Webuser.allDevoxxians().sortBy(_.email)
      Ok(views.html.Backoffice.allDevoxxians(devoxxians))
  }

  def invalidDevoxxians() = SecuredAction(IsMemberOf("admin")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      val devoxxians = Webuser.allDevoxxians().sortBy(_.email)

      val duplicateEmails = devoxxians.groupBy(_.email).filter(_._2.size > 1)
      val removeNoEmails = duplicateEmails.filterNot(s => s._1 == "no_email_defined")

      Ok(views.html.Backoffice.invalidDevoxxians(removeNoEmails.values.flatten.toList))
  }


  def allSpeakersWithApprovedTalks() = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      val allSpeakers = ApprovedProposal.allApprovedSpeakers()
      val toReturn = allSpeakers.map {
        s =>
          (s, Proposal.allNonArchivedProposalsByAuthor(s.uuid))
      }
      Ok(views.html.CFPAdmin.allSpeakers(toReturn))
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
          val onIfFirstOrSecondSpeaker = allProposalsForThisSpeaker.filter(p => p.mainSpeaker == speaker.uuid || p.secondarySpeaker.contains(speaker.uuid))
            .filter(p => ConferenceDescriptor.ConferenceProposalConfigurations.doesProposalTypeGiveSpeakerFreeEntrance(p.talkType))
          (speaker, onIfFirstOrSecondSpeaker)
      }.filter(_._2.nonEmpty).map {
        case (speaker, zeProposals) =>
          val updated = zeProposals.filter {
            proposal =>
              Proposal.findProposalState(proposal.id).contains(ProposalState.ACCEPTED)
          }
          if (updated.size != zeProposals.size) {
            play.Logger.debug(s"Removed rejected proposal for speaker ${speaker.cleanName}")
          }

          (speaker, updated)
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

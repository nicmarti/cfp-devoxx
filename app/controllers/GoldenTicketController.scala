/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2013 Association du Paris Java User Group.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package controllers

import models.ConferenceDescriptor.ConferenceTracks
import models.{Webuser, _}
import org.apache.commons.lang3.StringUtils
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation.Constraints._
import play.api.i18n.Messages
import play.api.libs.Crypto
import play.api.mvc.{SimpleResult, _}

import scala.concurrent.Future

/**
  *
  * @author created by N.Martignole, Innoteria, on 25/11/2015.
  */

object GoldenTicketController extends SecureCFPController {

  private val securityGroups = List("admin", "gticket")
  val loginForm = Form(tuple("email" -> (email verifying nonEmpty), "password" -> nonEmptyText))

  def authenticate() = Action {
    implicit request =>
      Ok(views.html.GoldenTicketController.authenticate(loginForm))
  }

  def doAuthenticate() = Action {
    implicit request =>
      loginForm.bindFromRequest.fold(
        invalidForm => BadRequest(views.html.GoldenTicketController.authenticate(invalidForm)),
        validForm =>
          Webuser.checkPassword(validForm._1, validForm._2) match {
            case Some(webuser) =>
              val cookie = createCookie(webuser)
              Redirect(routes.GoldenTicketController.showAllProposals()).withSession("uuid" -> webuser.uuid).withCookies(cookie)

            case None =>
              Redirect(routes.GoldenTicketController.authenticate()).flashing("error" -> Messages("login.error"))
          }
      )
  }

  def showAllProposals(page: Int, sort: Option[String], ascdesc: Option[String], track: Option[String]) = SecuredAction(IsMemberOfGroups(securityGroups)) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      val uuid = request.webuser.uuid
      val sorter = ProposalUtil.proposalSorter(sort)
      val orderer = ProposalUtil.proposalOrder(ascdesc)
      val innerPage:Int = if(page<1){ 1 } else { page }
      val pageSize:Int=30

      val allNotReviewed = ReviewByGoldenTicket.allAllowedProposalsNotReviewed(uuid)

      val delayedReviewProposalIds = ReviewByGoldenTicket.allProposalIdsHavingDelayedReviewsForUser(uuid)

      val maybeFilteredProposals = (track match {
        case None => allNotReviewed
        case Some(trackLabel) => allNotReviewed.filter(_.track.id.equalsIgnoreCase(StringUtils.trimToEmpty(trackLabel)))
      }).filterNot(prop => delayedReviewProposalIds.contains(prop.id))

      val totalToReview = maybeFilteredProposals.size
      val totalDelayedReviews = delayedReviewProposalIds.size

      val totalPages = (totalToReview / pageSize) + (if (totalToReview % pageSize > 0) 1 else 0)
      val currentPage = if(innerPage>totalPages){
        totalPages
      }else{
        innerPage
      }
      val allProposalsForReview = ProposalUtil.sortProposals(maybeFilteredProposals, sorter, orderer).slice(pageSize * (currentPage - 1), pageSize * (currentPage - 1) + pageSize)
      Ok(views.html.GoldenTicketController.showAllProposalsGT(allProposalsForReview, currentPage, sort, ascdesc, track, totalToReview, totalDelayedReviews))
  }

  def pageCalc(page: Int, pageSize: Int, totalItems: Int) = {
    val innerPage = if(page < 1 ) { 1 } else { page}
    val from = ((innerPage - 1) * pageSize) + 1
    val to = totalItems min (from + pageSize - 1)
    val totalPages = (totalItems / pageSize) + (if (totalItems % pageSize > 0) 1 else 0)
    (from, to, totalPages)
  }

  val delayedReviewForm: Form[String] = Form("delayedReviewReason" -> text(maxLength = 1000))
  val voteForm: Form[Int] = Form("vote" -> number(min = 0, max = 10))

  private def renderShowProposal(userId: String, proposal: Proposal, voteForm: Form[Int])(implicit request: SecuredRequest[play.api.mvc.AnyContent]) = {
    val maybeMyVote = ReviewByGoldenTicket.lastVoteByUserForOneProposal(proposal.id, userId)
    val userWatchPref = ProposalUserWatchPreference.proposalUserWatchPreference(proposal.id, userId)
    val maybeDelayedReviewReason = ReviewByGoldenTicket.proposalDelayedReviewReason(userId, proposal.id)
    views.html.GoldenTicketController.showProposal(proposal, voteForm, maybeMyVote, userWatchPref, maybeDelayedReviewReason)
  }

  def openForReview(proposalId: String) = SecuredAction(IsMemberOfGroups(securityGroups)) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      val uuid = request.webuser.uuid
      Proposal.findById(proposalId) match {
        case Some(proposal) =>
          Ok(renderShowProposal(uuid, proposal, voteForm))

        case None => NotFound("Proposal not found").as("text/html")
      }
  }

  def watchProposal(proposalId: String) = SecuredAction(IsMemberOfGroups(securityGroups)) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      val uuid = request.webuser.uuid
      Proposal.findById(proposalId) match {
        case Some(proposal) =>
          Watcher.addProposalWatcher(proposal.id, uuid, false)
          Ok(renderShowProposal(uuid, proposal, voteForm)).flashing("success" -> "Started watching proposal")
        case None => NotFound("Proposal not found").as("text/html")
      }
  }

  def delayReview(proposalId: String) = SecuredAction(IsMemberOfGroups(securityGroups)) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      val uuid = request.webuser.uuid
      Proposal.findById(proposalId) match {
        case Some(proposal) =>
          delayedReviewForm.bindFromRequest.fold(
            hasErrors => BadRequest(renderShowProposal(uuid, proposal, voteForm)),
            validMsg => {
              ReviewByGoldenTicket.markProposalReviewAsDelayed(uuid, proposal.id, validMsg)
              Redirect(routes.GoldenTicketController.showVotesForProposal(proposalId)).flashing("vote" -> "OK, review delayed for this proposal")
            }
          )
        case None => NotFound("Proposal not found")
      }
  }

  def removeProposalDelayedReview(proposalId: String) = SecuredAction(IsMemberOfGroups(securityGroups)) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      val uuid = request.webuser.uuid
      Proposal.findById(proposalId) match {
        case Some(proposal) =>
          ReviewByGoldenTicket.removeProposalDelayedReview(uuid, proposalId)
          Redirect(routes.GoldenTicketController.delayedReviews()).flashing("success" -> "OK, delayed review removed")
        case None => NotFound("Proposal not found")
      }
  }

  def unwatchProposal(proposalId: String) = SecuredAction(IsMemberOfGroups(securityGroups)) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      val uuid = request.webuser.uuid
      Proposal.findById(proposalId) match {
        case Some(proposal) =>
          Watcher.removeProposalWatcher(proposal.id, uuid)
          Ok(renderShowProposal(uuid, proposal, voteForm)).flashing("success" -> "Started unwatching proposal")
        case None => NotFound("Proposal not found").as("text/html")
      }
  }

  def voteForProposal(proposalId: String) = SecuredAction(IsMemberOfGroups(securityGroups)) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      val uuid = request.webuser.uuid
      Proposal.findById(proposalId) match {
        case Some(proposal) =>
          voteForm.bindFromRequest.fold(
            hasErrors => {
              BadRequest(renderShowProposal(uuid, proposal, hasErrors))
            },
            validVote => {
              if (Proposal.isSpeaker(proposalId, uuid)) {
                ReviewByGoldenTicket.voteForProposal(proposalId, uuid, 0)
                Redirect(routes.GoldenTicketController.showVotesForProposal(proposalId)).flashing("vote" -> Messages("gt.vote.foryou"))
              } else {
                ReviewByGoldenTicket.voteForProposal(proposalId, uuid, validVote)
                Redirect(routes.GoldenTicketController.showVotesForProposal(proposalId)).flashing("vote" -> Messages("gt.vote.submitted"))
              }
            }
          )

        case None => NotFound("Proposal not found").as("text/html")
      }
  }

  def clearVoteForProposal(proposalId: String) = SecuredAction(IsMemberOfGroups(securityGroups)) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      val uuid = request.webuser.uuid
      Proposal.findById(proposalId) match {
        case Some(proposal) =>
          ReviewByGoldenTicket.removeVoteForProposal(proposalId, uuid)
          Redirect(routes.GoldenTicketController.showVotesForProposal(proposalId)).flashing("vote" -> "Removed your vote")

        case None => NotFound("Proposal not found").as("text/html")
      }
  }

  def showVotesForProposal(proposalId: String) = SecuredAction(IsMemberOfGroups(securityGroups)).async {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      import scala.concurrent.ExecutionContext.Implicits.global
      val uuid = request.webuser.uuid
      scala.concurrent.Future {
        Proposal.findById(proposalId) match {
          case Some(proposal) =>
            val proposalIdsWithDelayedReview = ReviewByGoldenTicket.allProposalIdsHavingDelayedReviewsForUser(uuid)
            val currentProposalReviewHasBeenDelayed = proposalIdsWithDelayedReview.contains(proposal.id)
            val delayedReviewsCount = proposalIdsWithDelayedReview.size

            // The next proposal I should review
            val allNotReviewed = ReviewByGoldenTicket.allAllowedProposalsNotReviewed(uuid).filterNot(p => proposalIdsWithDelayedReview.contains(p.id))
            val (sameTrackAndFormats, otherTracksOrFormats) = allNotReviewed.partition(p => p.track.id == proposal.track.id && p.talkType.id == proposal.talkType.id)
            val (sameTracks, otherTracks) = allNotReviewed.partition(_.track.id == proposal.track.id)
            val (sameTalkType, otherTalksType) = allNotReviewed.partition(_.talkType.id == proposal.talkType.id)

            // Note: not appending otherTracksOrFormats here, as we want to show the button in the
            // template only if there are some remaining talks to be reviewed for same track & talkType
            val nextToBeReviewedSameTrackAndFormat = (sameTrackAndFormats.sortBy(_.track.id)).headOption
            val nextToBeReviewedSameTrack = (sameTracks.sortBy(_.talkType.id) ++ otherTracks).headOption
            val nextToBeReviewedSameFormat = (sameTalkType.sortBy(_.track.id) ++ otherTalksType).headOption

            Ok(views.html.GoldenTicketController.showVotesForProposal(uuid, proposal, currentProposalReviewHasBeenDelayed, delayedReviewsCount, nextToBeReviewedSameTrackAndFormat, nextToBeReviewedSameTrack, nextToBeReviewedSameFormat))
          case None => NotFound("Proposal not found").as("text/html")
        }
      }
  }

  // From SecureCFPController
  override def notAuthenticatedResult[A](implicit request: Request[A]): Future[Result] = {
    Future.successful {
      Redirect(routes.GoldenTicketController.authenticate())
    }
  }

  def allMyGoldenTicketVotes(talkType: String, selectedTrack:Option[String]) = SecuredAction(IsMemberOfGroups(securityGroups)) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>

      ConferenceDescriptor.ConferenceProposalTypes.ALL.find(_.id == talkType).map {
        pType =>
          val uuid = request.webuser.uuid
          val allMyVotesIncludingAbstentions = ReviewByGoldenTicket.allVotesFromUser(uuid)
          val myVotesIncludingAbstentionsPerProposalId = allMyVotesIncludingAbstentions.toMap
          val allProposalIDs = allMyVotesIncludingAbstentions.map(_._1)
          val proposalWhereIVoted = Proposal.loadAndParseProposals(allProposalIDs)
          val allProposalsMatchingCriteria = proposalWhereIVoted
            .filter(p => p._2.talkType == pType && selectedTrack.map(p._2.track.id == _).getOrElse(true))
          val allProposalsIdsMatchingCriteria = allProposalsMatchingCriteria.keySet

          val allMyVotesIncludingAbstentionsMatchingCriteria = allMyVotesIncludingAbstentions.filter(proposalIdAndVotes => allProposalsIdsMatchingCriteria.contains(proposalIdAndVotes._1))

          val proposalsNotReviewedByType = ReviewByGoldenTicket.allAllowedProposalsNotReviewed(uuid).groupBy(_.talkType.id)
          val proposalNotReviewedCountByType = proposalsNotReviewedByType.mapValues(_.size)
          val proposalsNotReviewedForCurrentType = proposalsNotReviewedByType.get(pType.id).getOrElse(List())
          val proposalNotReviewedCountForCurrentTypeByTrack = proposalsNotReviewedForCurrentType.groupBy(_.track.id).mapValues(_.size)
          val proposalsMatchingCriteriaNotReviewed = proposalsNotReviewedForCurrentType.filter(p => selectedTrack.map(p.track.id == _).getOrElse(true))
          val firstProposalNotReviewedAndMatchingCriteria = proposalsMatchingCriteriaNotReviewed.headOption

          val sortedAllMyVotesIncludingAbstentionsMatchingCriteria = allMyVotesIncludingAbstentionsMatchingCriteria.toList.sortBy(_._2).reverse
          val sortedAllMyVotesExcludingAbstentionsMatchingCriteria = sortedAllMyVotesIncludingAbstentionsMatchingCriteria.filter(_._2 != 0)

          val proposalsPerTrackWhereIVoted = proposalWhereIVoted.values.groupBy(_.track)
          val reviewerStats = allMyVotesIncludingAbstentions.size match {
            case 0 => None
            case _ => Some(
              List("track.all.title" -> ConferenceTracks.ALL_KNOWN)
                .++(ConferenceTracks.ALL_KNOWN.map(track => track.label -> List(track)).toMap)
                .flatMap { case (labelKey: String, tracks: List[Track]) =>
                  val votesForConcernedTracks = tracks.map(t =>
                    proposalsPerTrackWhereIVoted.getOrElse(t, List())
                      .flatMap(p => myVotesIncludingAbstentionsPerProposalId.get(p.id))
                  ).flatten
                  NamedReviewerStats.from(labelKey, votesForConcernedTracks.toArray)
                }
            )
          }

          val delayedReviewsCount = ReviewByGoldenTicket.countDelayedReviews(uuid)

          Ok(views.html.GoldenTicketController.allMyGoldenTicketVotes(sortedAllMyVotesIncludingAbstentionsMatchingCriteria, sortedAllMyVotesExcludingAbstentionsMatchingCriteria, allProposalsMatchingCriteria, talkType, selectedTrack, delayedReviewsCount, proposalNotReviewedCountByType, proposalNotReviewedCountForCurrentTypeByTrack, firstProposalNotReviewedAndMatchingCriteria, reviewerStats))
      }.getOrElse {
        BadRequest("Invalid proposal type")
      }
  }

  def delayedReviews() = SecuredAction(IsMemberOfGroups(securityGroups)) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>

      val uuid = request.webuser.uuid
      val delayedReviewReasonByProposalId = ReviewByGoldenTicket.delayedReviewsReasons(uuid)

      val delayedProposals = Proposal.loadAndParseProposals(delayedReviewReasonByProposalId.keySet)
        .values.toList
        .sortBy(p => s"${p.talkType}__${p.track}")

      Ok(views.html.GoldenTicketController.delayedReviews(delayedProposals, delayedReviewReasonByProposalId))
  }

  private def createCookie(webuser: Webuser) = {
    Cookie("cfp_rm", value = Crypto.encryptAES(webuser.uuid), maxAge = Some(588000))
  }
}

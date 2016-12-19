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
      val sorter = CFPAdmin.proposalSorter(sort)
      val orderer = CFPAdmin.proposalOrder(ascdesc)

      val allNotReviewed = if (ConferenceDescriptor.isCFPOpen) {
        ReviewByGoldenTicket.allProposalsNotReviewed(uuid)
          .filterNot(p => p.talkType == ConferenceDescriptor.ConferenceProposalTypes.KEY)
          .filterNot(_.sponsorTalk)
      } else {
        ReviewByGoldenTicket.allProposalsNotReviewed(uuid)
          .filter(p => p.talkType == ConferenceDescriptor.ConferenceProposalTypes.CONF)
          .filterNot(_.sponsorTalk)
      }

      val maybeFilteredProposals = track match {
        case None => allNotReviewed
        case Some(trackLabel) => allNotReviewed.filter(_.track.id.equalsIgnoreCase(StringUtils.trimToEmpty(trackLabel)))
      }
      val allProposalsForReview = CFPAdmin.sortProposals(maybeFilteredProposals, sorter, orderer)

      val etag = "gt2_" + allProposalsForReview.hashCode()

      request.headers.get(IF_NONE_MATCH) match {
        case Some(tag) if tag == etag.toString => NotModified
        case _ => Ok(views.html.GoldenTicketController.showAllProposals(allProposalsForReview, page, sort, ascdesc, track)).withHeaders(ETAG -> etag.toString)
      }

  }

  val voteForm: Form[Int] = Form("vote" -> number(min = 0, max = 10))

  def openForReview(proposalId: String) = SecuredAction(IsMemberOfGroups(securityGroups)) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      val uuid = request.webuser.uuid
      Proposal.findById(proposalId) match {
        case Some(proposal) =>
          val maybeMyVote = ReviewByGoldenTicket.lastVoteByUserForOneProposal(uuid, proposalId)
          Ok(views.html.GoldenTicketController.showProposal(proposal, voteForm, maybeMyVote))

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
              val maybeMyVote = ReviewByGoldenTicket.lastVoteByUserForOneProposal(uuid, proposalId)
              BadRequest(views.html.GoldenTicketController.showProposal(proposal, hasErrors, maybeMyVote))
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
            // The next proposal I should review
            val allNotReviewed = ReviewByGoldenTicket.allProposalsNotReviewed(uuid)
            val (sameTracks, otherTracks) = allNotReviewed.partition(_.track.id == proposal.track.id)
            val (sameTalkType, otherTalksType) = allNotReviewed.partition(_.talkType.id == proposal.talkType.id)

            val nextToBeReviewedSameTrack = (sameTracks.sortBy(_.talkType.id) ++ otherTracks).headOption
            val nextToBeReviewedSameFormat = (sameTalkType.sortBy(_.track.id) ++ otherTalksType).headOption

            Ok(views.html.GoldenTicketController.showVotesForProposal(uuid, proposal, nextToBeReviewedSameTrack, nextToBeReviewedSameFormat))
          case None => NotFound("Proposal not found").as("text/html")
        }
      }
  }

  // From SecureCFPController
  override def notAuthenticatedResult[A](implicit request: Request[A]): Future[SimpleResult] = {
    Future.successful {
      Redirect(routes.GoldenTicketController.authenticate())
    }
  }

  def allMyGoldenTicketVotes(talkType: String) = SecuredAction(IsMemberOfGroups(securityGroups)) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>

      ConferenceDescriptor.ConferenceProposalTypes.ALL.find(_.id == talkType).map {
        pType =>
          val uuid = request.webuser.uuid
          val allMyVotes = ReviewByGoldenTicket.allVotesFromUser(uuid)
          val allProposalIDs = allMyVotes.map(_._1)
          val allProposalsForProposalType = Proposal.loadAndParseProposals(allProposalIDs).filter(_._2.talkType == pType)
          val allProposalsIdsProposalType = allProposalsForProposalType.keySet
          val allMyVotesForSpecificProposalType = allMyVotes.filter(proposalIdAndVotes => allProposalsIdsProposalType.contains(proposalIdAndVotes._1))

          Ok(views.html.GoldenTicketController.allMyGoldenTicketVotes(allMyVotesForSpecificProposalType, allProposalsForProposalType, talkType))
      }.getOrElse {
        BadRequest("Invalid proposal type")
      }
  }

  private def createCookie(webuser: Webuser) = {
    Cookie("cfp_rm", value = Crypto.encryptAES(webuser.uuid), maxAge = Some(588000))
  }
}
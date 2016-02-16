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

import akka.util.Crypt
import library._
import models.Review._
import models._
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages

import scala.concurrent.Future

/**
  * Sans doute le controller le plus sadique du monde qui accepte ou rejette les propositions
  * Created by nmartignole on 30/01/2014.
  */
object ApproveOrRefuse extends SecureCFPController {

  def doApprove(proposalId: String) = SecuredAction(IsMemberOf("cfp")).async {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      Proposal.findById(proposalId).map {
        proposal =>
          ApprovedProposal.approve(proposal)
          Event.storeEvent(Event(proposalId, request.webuser.uuid, s"Approved ${Messages(proposal.talkType.id)} [${proposal.title}] in track [${Messages(proposal.track.id)}]"))
          Future.successful(Redirect(routes.CFPAdmin.allVotes(proposal.talkType.id, None)).flashing("success" -> s"Talk ${proposal.id} has been accepted."))
      }.getOrElse {
        Future.successful(Redirect(routes.CFPAdmin.allVotes("all", None)).flashing("error" -> "Talk not found"))
      }
  }

  def doRefuse(proposalId: String) = SecuredAction(IsMemberOf("cfp")).async {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      Proposal.findById(proposalId).map {
        proposal =>
          ApprovedProposal.refuse(proposal)
          Event.storeEvent(Event(proposalId, request.webuser.uuid, s"Refused ${Messages(proposal.talkType.id)} [${proposal.title}] in track [${Messages(proposal.track.id)}]"))
          Future.successful(Redirect(routes.CFPAdmin.allVotes(proposal.talkType.id, None)).flashing("success" -> s"Talk ${proposal.id} has been refused."))
      }.getOrElse {
        Future.successful(Redirect(routes.CFPAdmin.allVotes("all", None)).flashing("error" -> "Talk not found"))
      }
  }

  def cancelApprove(proposalId: String) = SecuredAction(IsMemberOf("cfp")).async {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      Proposal.findById(proposalId).map {
        proposal =>
          val confType: String = proposal.talkType.id
          ApprovedProposal.cancelApprove(proposal)
          Event.storeEvent(Event(proposalId, request.webuser.uuid, s"Cancel Approved on ${Messages(proposal.talkType.id)} [${proposal.title}] in track [${Messages(proposal.track.id)}]"))
          Future.successful(Redirect(routes.CFPAdmin.allVotes(proposal.talkType.id, Some(confType))).flashing("success" -> s"Talk ${proposal.id} has been removed from Approved list."))
      }.getOrElse {
        Future.successful(Redirect(routes.CFPAdmin.allVotes("all", None)).flashing("error" -> "Talk not found"))
      }
  }

  def cancelRefuse(proposalId: String) = SecuredAction(IsMemberOf("cfp")).async {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      Proposal.findById(proposalId).map {
        proposal =>
          val confType: String = proposal.talkType.id
          ApprovedProposal.cancelRefuse(proposal)
          Event.storeEvent(Event(proposalId, request.webuser.uuid, s"Cancel Refused on ${Messages(proposal.talkType.id)} [${proposal.title}] in track [${Messages(proposal.track.id)}]"))
          Future.successful(Redirect(routes.CFPAdmin.allVotes(proposal.talkType.id, Some(confType))).flashing("success" -> s"Talk ${proposal.id} has been removed from Refused list."))
      }.getOrElse {
        Future.successful(Redirect(routes.CFPAdmin.allVotes("all", None)).flashing("error" -> "Talk not found"))
      }
  }

  def allApprovedByTalkType(talkType: String) = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      Ok(views.html.ApproveOrRefuse.allApprovedByTalkType(ApprovedProposal.allApprovedByTalkType(talkType), talkType))
  }

  def allRefusedByTalkType(talkType: String) = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      Ok(views.html.ApproveOrRefuse.allRefusedByTalkType(ApprovedProposal.allRefusedByTalkType(talkType), talkType))
  }

  def notifyApprove(talkType: String, proposalId: String) = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      Proposal.findById(proposalId).map {
        proposal: Proposal =>
          ZapActor.actor ! ProposalApproved(request.webuser.uuid, proposal)
      }
      Redirect(routes.ApproveOrRefuse.allApprovedByTalkType(talkType)).flashing("success" -> s"Notified speakers for Proposal ID $proposalId")
  }

  def notifyRefused(talkType: String, proposalId: String) = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      Proposal.findById(proposalId).map {
        proposal: Proposal =>
          ZapActor.actor ! ProposalRefused(request.webuser.uuid, proposal)
      }
      Redirect(routes.ApproveOrRefuse.allRefusedByTalkType(talkType)).flashing("success" -> s"Notified speakers for Proposal ID $proposalId")
  }

  val formApprove = Form(
    "accept.chk" -> checked("accept.term.checked")
  )

  def showAcceptTerms() = SecuredAction {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      if (Speaker.needsToAccept(request.webuser.uuid)) {
        Ok(views.html.ApproveOrRefuse.showAcceptTerms(formApprove))
      } else {
        Redirect(routes.ApproveOrRefuse.showAcceptOrRefuseTalks()).flashing("success" -> Messages("acceptedTerms.msg"))
      }
  }

  def acceptTermsAndConditions() = SecuredAction {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      formApprove.bindFromRequest().fold(
        hasErrors => BadRequest(views.html.ApproveOrRefuse.showAcceptTerms(hasErrors)),
        successForm => {
          Speaker.doAcceptTerms(request.webuser.uuid)
          Event.storeEvent(Event("speaker", request.webuser.uuid, "has accepted Terms and conditions"))
          Redirect(routes.ApproveOrRefuse.showAcceptOrRefuseTalks())
        }
      )
  }

  def showAcceptOrRefuseTalks() = SecuredAction {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      import org.apache.commons.lang3.RandomStringUtils
      val allMyProposals = Proposal.allMyProposals(request.webuser.uuid)
      val cssrf = RandomStringUtils.randomAlphanumeric(24)

      val (accepted, rejected) = allMyProposals.partition(p => p.state == ProposalState.APPROVED || p.state == ProposalState.DECLINED || p.state == ProposalState.ACCEPTED || p.state == ProposalState.BACKUP)
      Ok(views.html.ApproveOrRefuse.acceptOrRefuseTalks(accepted, rejected.filter(_.state == ProposalState.REJECTED), cssrf))
        .withSession(session.+(("CSSRF", Crypt.sha1(cssrf))))
  }

  val formAccept = Form(tuple("proposalId" -> nonEmptyText(maxLength = 8), "dec" -> nonEmptyText, "cssrf_t" -> nonEmptyText))

  def doAcceptOrRefuseTalk() = SecuredAction {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>

      formAccept.bindFromRequest().fold(hasErrors =>
        Redirect(routes.ApproveOrRefuse.showAcceptOrRefuseTalks()).flashing("error" -> "Invalid form, please check and validate again")
        , validForm => {
          val cssrf = Crypt.sha1(validForm._3)
          val fromSession = session.get("CSSRF")
          if (Some(cssrf) != fromSession) {
            Redirect(routes.ApproveOrRefuse.showAcceptOrRefuseTalks()).flashing("error" -> "Invalid CSSRF token")
          } else {

            val proposalId = validForm._1
            val choice = validForm._2
            val maybeProposal = Proposal.findById(proposalId)
            maybeProposal match {
              case None => Redirect(routes.ApproveOrRefuse.showAcceptOrRefuseTalks()).flashing("error" -> Messages("ar.proposalNotFound"))
              case Some(p) if Proposal.isSpeaker(proposalId, request.webuser.uuid) => {

                choice match {
                  case "accept" => {
                    if (List(ProposalState.APPROVED, ProposalState.BACKUP, ProposalState.ACCEPTED, p.state == ProposalState.DECLINED).contains(p.state)) {
                      Proposal.accept(request.webuser.uuid, proposalId)
                      val validMsg = "Speaker has set the status of this proposal to ACCEPTED"
                      Comment.saveCommentForSpeaker(proposalId, request.webuser.uuid, validMsg)
                      ZapActor.actor ! SendMessageToCommitte(request.webuser.uuid, p, validMsg)
                    } else {
                      ZapActor.actor ! SendMessageToCommitte(request.webuser.uuid, p, "un utilisateur a essayé de changer le status de son talk... User:" + request.webuser.cleanName + " talk:" + p.id + " state:" + p.state.code)
                    }
                  }
                  case "decline" => {
                    if (List(ProposalState.APPROVED, ProposalState.BACKUP, ProposalState.ACCEPTED, p.state == ProposalState.DECLINED).contains(p.state)) {
                      Proposal.decline(request.webuser.uuid, proposalId)
                      val validMsg = "Speaker has set the status of this proposal to DECLINED"
                      Comment.saveCommentForSpeaker(proposalId, request.webuser.uuid, validMsg)
                      ZapActor.actor ! SendMessageToCommitte(request.webuser.uuid, p, validMsg)
                    } else {
                      ZapActor.actor ! SendMessageToCommitte(request.webuser.uuid, p, "un utilisateur a essayé de changer le status de son talk... User:" + request.webuser.cleanName + " talk:" + p.id + " state:" + p.state.code)
                    }
                  }
                  case "backup" => {
                    val validMsg = "Speaker has set the status of this proposal to BACKUP"
                    Comment.saveCommentForSpeaker(proposalId, request.webuser.uuid, validMsg)
                    ZapActor.actor ! SendMessageToCommitte(request.webuser.uuid, p, validMsg)
                    Proposal.backup(request.webuser.uuid, proposalId)
                  }
                  case other => play.Logger.error("Invalid choice for ApproveOrRefuse doAcceptOrRefuseTalk for proposalId " + proposalId + " choice=" + choice)
                }


                Redirect(routes.ApproveOrRefuse.showAcceptOrRefuseTalks()).flashing("success" -> Messages("ar.choiceRecorded", proposalId, choice))
              }
              case other => Redirect(routes.ApproveOrRefuse.showAcceptOrRefuseTalks()).flashing("error" -> "Hmmm not a good idea to try to update someone else proposal... this event has been logged.")
            }


          }
        }
      )
  }


  def prepareMassRefuse(confType: String) = SecuredAction(IsMemberOf("admin")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>

      ProposalType.all.find(_.id == confType).map {
        proposalType =>
          val reviews: Map[String, (Score, TotalVoter, TotalAbst, AverageNote, StandardDev)] = Review.allVotes()

          val onlyReviewedButNotApproved:Set[String]=reviews.keySet.diff(ApprovedProposal.allApprovedProposalIDs()).diff(ApprovedProposal.allRefusedProposalIDs())

          val allProposals = Proposal.loadAndParseProposals(onlyReviewedButNotApproved, proposalType)

          val listOfProposals = reviews.flatMap {
            case (proposalId, scoreAndVotes) =>
              val maybeProposal = allProposals.get(proposalId)
              if (maybeProposal.isDefined) {
                Option(maybeProposal.get, scoreAndVotes._4)
              } else {
                // It's ok to discard other talk than the confType requested
                None
              }
          }

          val sortedList = listOfProposals.toList.sortBy {
            case (proposal, score) => score.n
          }

          Ok(views.html.ApproveOrRefuse.prepareMassRefuse(sortedList, confType))
      }.getOrElse(NotFound("Proposal not found"))

  }

  def doRefuseAndRedirectToMass(proposalId:String, confType:String)=SecuredAction(IsMemberOf("admin")).async{
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
       Proposal.findById(proposalId).map {
        proposal =>
          ApprovedProposal.refuse(proposal)
          Event.storeEvent(Event(proposalId, request.webuser.uuid, s"Refused ${Messages(proposal.talkType.id)} [${proposal.title}] in track [${Messages(proposal.track.id)}]"))
          Future.successful(Redirect(routes.ApproveOrRefuse.prepareMassRefuse(confType)))
      }.getOrElse {
        Future.successful(NotFound("Talk not found for this proposalId "+proposalId))
      }

  }


}


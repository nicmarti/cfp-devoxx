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

import models._
import play.api.i18n.Messages
import library.ZapActor
import library.ProposalApproved

import play.api.data.Form
import play.api.data.Forms._
import scala.concurrent.{Future, Promise}

/**
 * Sans doute le controller le plus sadique du monde qui accepte ou rejette les propositions
 * Created by nmartignole on 30/01/2014.
 */
object ApproveOrRefuse extends SecureCFPController {

  def acceptHome() = SecuredAction(IsMemberOf("admin")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      Ok("todo")
  }

  def doApprove(proposalId: String) = SecuredAction(IsMemberOf("admin")).async {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      Proposal.findById(proposalId).map {
        proposal =>
          proposal.state match {
            case ProposalState.SUBMITTED =>
              ApprovedProposal.approve(proposalId, proposal.talkType.id)
              Event.storeEvent(Event(proposalId, request.webuser.uuid, s"Approved ${Messages(proposal.talkType.id)} [${proposal.title}] in track [${Messages(proposal.track.id)}]"))
              Future.successful(Redirect(routes.CFPAdmin.allVotes(proposal.talkType.id)).flashing("success" -> s"Talk ${proposal.id} has been accepted."))
            case _ =>
              Future.successful(Redirect(routes.CFPAdmin.allVotes(proposal.talkType.id)).flashing("success" -> s"Talk ${proposal.id} is not in state SUBMITTED, current state is ${proposal.state}"))
          }
      }.getOrElse {
        Future.successful(Redirect(routes.CFPAdmin.allVotes("all")).flashing("error" -> "Talk not found"))
      }
  }

  def cancelApprove(proposalId: String) = SecuredAction(IsMemberOf("admin")).async {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      Proposal.findById(proposalId).map {
        proposal =>
          proposal.state match {
            case ProposalState.SUBMITTED =>

              ApprovedProposal.cancelApprove(proposalId, proposal.talkType.id)
              Event.storeEvent(Event(proposalId, request.webuser.uuid, s"Cancel Approved on ${Messages(proposal.talkType.id)} [${proposal.title}] in track [${Messages(proposal.track.id)}]"))
              Future.successful(Redirect(routes.CFPAdmin.allVotes(proposal.talkType.id)).flashing("success" -> s"Talk ${proposal.id} has been removed from Approved list."))
            case _ =>
              Future.successful(Redirect(routes.CFPAdmin.allVotes(proposal.talkType.id)).flashing("success" -> s"Talk ${proposal.id} is not in state SUBMITTED, current state is ${proposal.state}"))
          }
      }.getOrElse {
        Future.successful(Redirect(routes.CFPAdmin.allVotes("all")).flashing("error" -> "Talk not found"))
      }
  }

  def allApprovedByTalkType(talkType: String) = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      Ok(views.html.ApproveOrRefuse.allApprovedByTalkType(ApprovedProposal.allApprovedByTalkType(talkType), talkType))
  }

  def notifyApprove(talkType: String, proposalId: Option[String]) = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      proposalId match {
        case None => ApprovedProposal.allApprovedByTalkType(talkType).foreach {
          proposal: Proposal =>
            ZapActor.actor ! ProposalApproved(request.webuser.uuid, proposal)
        }
        case Some(pid) =>
          Proposal.findById(pid).map {
            proposal =>
              ZapActor.actor ! ProposalApproved(request.webuser.uuid, proposal)
          }
      }
      Redirect(routes.Backoffice.homeBackoffice())
  }

  val formApprove = Form(
    "accept.chk" -> checked("accept.term.checked")
  )

  def showAcceptTerms() = SecuredAction {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      if (Speaker.needsToAccept(request.webuser.uuid)) {
         Ok(views.html.ApproveOrRefuse.showAcceptTerms(formApprove))
      } else {
        Redirect(routes.ApproveOrRefuse.acceptOrRefuseTalks())
      }
  }

  def acceptTermsAndConditions() = SecuredAction {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
        formApprove.bindFromRequest().fold(
          hasErrors => BadRequest(views.html.ApproveOrRefuse.showAcceptTerms(hasErrors)),
          successForm => {
            Speaker.acceptTerms(request.webuser.uuid)
            Event.storeEvent(Event(request.webuser.uuid, request.webuser.uuid, "has accepted Terms and conditions"))
            Redirect(routes.ApproveOrRefuse.showAcceptOrRefuseTalks())
          }
        )
  }



  def showAcceptOrRefuseTalks() = SecuredAction {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>

      val proposals=Proposal.allMyProposals(request.webuser.uuid).filter(_.state==ProposalState.SUBMITTED).filter(p=>ApprovedProposal.isApproved(p.id,p.talkType))

      Ok(views.html.ApproveOrRefuse.acceptOrRefuseTalks(proposals))

  }

  def acceptOrRefuseTalks() = SecuredAction {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      Ok("top")
  }

}


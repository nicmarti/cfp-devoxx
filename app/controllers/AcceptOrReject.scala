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
import library.ProposalAccepted

import play.api.data.Form
import play.api.data.Forms._
import scala.concurrent.{Future, Promise}

/**
 * Sans doute le controller le plus sadique du monde qui accepte ou rejette les propositions
 * Created by nmartignole on 30/01/2014.
 */
object AcceptOrReject extends SecureCFPController {

  def acceptHome() = SecuredAction(IsMemberOf("admin")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      Ok("todo")
  }

  def doAccept(proposalId: String) = SecuredAction(IsMemberOf("admin")).async {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      Proposal.findById(proposalId).map {
        proposal =>
          proposal.state match {
            case ProposalState.SUBMITTED =>
              AcceptService.accept(proposalId, proposal.talkType.id)
              Event.storeEvent(Event(proposalId, request.webuser.uuid, s"Accepted ${Messages(proposal.talkType.id)} [${proposal.title}] in track [${Messages(proposal.track.id)}]"))
              Future.successful(Redirect(routes.CFPAdmin.allVotes(proposal.talkType.id)).flashing("success" -> s"Talk ${proposal.id} has been accepted."))
            case _ =>
              Future.successful(Redirect(routes.CFPAdmin.allVotes(proposal.talkType.id)).flashing("success" -> s"Talk ${proposal.id} is not in state SUBMITTED, current state is ${proposal.state}"))
          }
      }.getOrElse {
        Future.successful(Redirect(routes.CFPAdmin.allVotes("all")).flashing("error" -> "Talk not found"))
      }
  }

  def cancelAccept(proposalId: String) = SecuredAction(IsMemberOf("admin")).async {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      Proposal.findById(proposalId).map {
        proposal =>
          proposal.state match {
            case ProposalState.SUBMITTED =>

              AcceptService.cancelAccept(proposalId, proposal.talkType.id)
              Event.storeEvent(Event(proposalId, request.webuser.uuid, s"Cancel Accepted on ${Messages(proposal.talkType.id)} [${proposal.title}] in track [${Messages(proposal.track.id)}]"))
              Future.successful(Redirect(routes.CFPAdmin.allVotes(proposal.talkType.id)).flashing("success" -> s"Talk ${proposal.id} has been removed from Accepted list."))
            case _ =>
              Future.successful(Redirect(routes.CFPAdmin.allVotes(proposal.talkType.id)).flashing("success" -> s"Talk ${proposal.id} is not in state SUBMITTED, current state is ${proposal.state}"))
          }
      }.getOrElse {
       Future.successful( Redirect(routes.CFPAdmin.allVotes("all")).flashing("error" -> "Talk not found"))
      }
  }

  def allAcceptedByTalkType(talkType: String) = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      Ok(views.html.AcceptOrReject.allAcceptedByTalkType(AcceptService.allAcceptedByTalkType(talkType), talkType))
  }

  def notifyAccept(talkType: String, proposalId: Option[String]) = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      proposalId match {
        case None => AcceptService.allAcceptedByTalkType(talkType).foreach {
          proposal: Proposal =>
            ZapActor.actor ! ProposalAccepted(request.webuser.uuid, proposal)
        }
        case Some(pid) =>
          Proposal.findById(pid).map {
            proposal =>
              ZapActor.actor ! ProposalAccepted(request.webuser.uuid, proposal)
          }
      }
      Redirect(routes.Backoffice.homeBackoffice())
  }

  val formAccept = Form(
    "accept.chk" -> checked("accept.term.checked")
  )

  def showApproveTerms(proposalId: String) = SecuredAction {
    implicit request =>
      if (Speaker.needsToAccept(request.webuser.uuid)) {
        Proposal.findById(proposalId).map {
          proposal =>
            Ok(views.html.AcceptOrReject.showApproveTerms(formAccept, proposal))
        }.getOrElse {
          NotFound("Sorry, this proposal does not exist or you are not the owner.")
        }
      } else {
        Redirect(routes.AcceptOrReject.acceptOrRefuseTalk(proposalId))
      }
  }

  def acceptTermsAndConditions(proposalId: String) = SecuredAction {
    implicit request =>
      Proposal.findById(proposalId).filter(_.allSpeakerUUIDs.contains(request.webuser.uuid)).map {
        proposal =>
          formAccept.bindFromRequest().fold(
            hasErrors => BadRequest(views.html.AcceptOrReject.showApproveTerms(hasErrors, proposal)),
            successForm => {
              Speaker.acceptTerms(request.webuser.uuid)
              Event.storeEvent(Event(proposalId, request.webuser.uuid, "has accepted Terms and conditions"))
              Redirect(routes.AcceptOrReject.showApproveTerms(proposalId))
            }
          )
      }.getOrElse {
        NotFound("Sorry, this proposal does not exist or you are not the owner.")
      }
  }

  def acceptOrRefuseTalk(proposalId: String) = SecuredAction {
    implicit request =>
      Ok("acceptOrRefuseTalk")
  }

}


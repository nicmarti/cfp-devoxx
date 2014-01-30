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

import play.api.mvc.Controller
import models.{Event, AcceptService, ProposalState, Proposal}
import play.api.i18n.Messages

/**
 * Sans doute le controller le plus sadique du monde qui accepte ou rejette les propositions
 * Created by nmartignole on 30/01/2014.
 */
object AcceptOrReject extends Controller with Secured{

  def acceptHome()=IsMemberOf("admin"){
    implicit uuid => implicit request =>
    Ok("todo")
  }

  def doAccept(proposalId: String) = IsMemberOf("admin") {
    implicit uuid => implicit request =>
      Proposal.findById(proposalId).map {
        proposal =>
          proposal.state match {
            case ProposalState.SUBMITTED =>
              AcceptService.accept(proposalId, proposal.talkType.id)
              Event.storeEvent(Event(proposalId, uuid, s"Accepted ${Messages(proposal.talkType.id)} [${proposal.title}] in track [${Messages(proposal.track.id)}]"))
              Redirect(routes.CFPAdmin.allVotes(proposal.talkType.id)).flashing("success" -> s"Talk ${proposal.id} has been accepted.")
            case _ =>
              Redirect(routes.CFPAdmin.allVotes(proposal.talkType.id)).flashing("success" -> s"Talk ${proposal.id} is not in state SUBMITTED, current state is ${proposal.state}")
          }
      }.getOrElse {
        Redirect(routes.CFPAdmin.allVotes("all")).flashing("error" -> "Talk not found")
      }
  }

  def cancelAccept(proposalId: String) = IsMemberOf("admin") {
    implicit uuid => implicit request =>
      Proposal.findById(proposalId).map {
        proposal =>
          proposal.state match {
            case ProposalState.SUBMITTED =>
              AcceptService.cancelAccept(proposalId, proposal.talkType.id)
              Event.storeEvent(Event(proposalId, uuid, s"Cancel Accepted on ${Messages(proposal.talkType.id)} [${proposal.title}] in track [${Messages(proposal.track.id)}]"))

              Redirect(routes.CFPAdmin.allVotes(proposal.talkType.id)).flashing("success" -> s"Talk ${proposal.id} has been removed from Accepted list.")
            case _ =>
              Redirect(routes.CFPAdmin.allVotes(proposal.talkType.id)).flashing("success" -> s"Talk ${proposal.id} is not in state SUBMITTED, current state is ${proposal.state}")
          }
      }.getOrElse {
        Redirect(routes.CFPAdmin.allVotes("all")).flashing("error" -> "Talk not found")
      }
  }

}

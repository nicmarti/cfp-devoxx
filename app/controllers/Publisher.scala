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
import play.api.mvc._

/**
 * Simple content publisher
 * Created by nicolas on 12/02/2014.
 */
object Publisher extends Controller {
  def homePublisher = Action {
    implicit request =>
      Ok(views.html.Publisher.homePublisher())
  }

  def showAllSpeakers = Action {
    implicit request =>
      val speakers = Speaker.allSpeakersWithAcceptedTerms()
      Ok(views.html.Publisher.showAllSpeakers(speakers))
  }

  def showSpeaker(uuid: String, name: String) = Action {
    implicit request =>
      val maybeSpeaker=Speaker.findByUUID(uuid)
      maybeSpeaker match {
        case Some(speaker)=>{
          val acceptedProposals = ApprovedProposal.allAcceptedTalksForSpeaker(speaker.uuid)
          Ok(views.html.Publisher.showSpeaker(speaker,acceptedProposals))
        }
        case None=>NotFound("Speaker not found")
      }
  }

  def showByTalkType(talkType:String)=Action{
    implicit request=>
      // TODO a changer avant passage prod
//      val proposals=ApprovedProposal.allApprovedByTalkType(talkType)
      val proposals=ApprovedProposal.allAcceptedByTalkType(talkType)
      Ok(views.html.Publisher.showByTalkType(proposals,talkType))
  }

  def showDetailsForProposal(proposalId:String, proposalTitle:String)=Action{
    implicit request=>
      Proposal.findById(proposalId) match {
        case None=>NotFound("Proposal not found")
        case Some(proposal)=>
          Ok(views.html.Publisher.showProposal(proposal))
      }

  }
}

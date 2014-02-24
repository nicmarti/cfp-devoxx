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
import org.apache.commons.lang3.StringEscapeUtils

/**
 * Used for Printed program
 * Created by nicolas on 22/02/2014.
 */
object PaperGuide  extends Controller {
  def showAllSpeakers = Action {
    implicit request =>
      val speakers = Speaker.allSpeakersWithAcceptedTerms()

      val sb:StringBuilder=new StringBuilder()
      sb.append("\u00EF\u00BB\u00BF")
      sb.append("id,prénom,nom,twitter,société,blog,bio,lang,@photo")
      sb.append("\n")
      speakers.map{speaker=>
        sb.append(speaker.uuid).append(",")
        sb.append(speaker.firstName.getOrElse("")).append(",")
        sb.append(speaker.name.map(s=>StringEscapeUtils.escapeCsv(s)).getOrElse("")).append(",")
        sb.append(speaker.twitter.map(s=>StringEscapeUtils.escapeCsv(s)).getOrElse("")).append(",")
        sb.append(speaker.company.map(s=>StringEscapeUtils.escapeCsv(s)).getOrElse("")).append(",")
        sb.append(speaker.blog.map(s=>StringEscapeUtils.escapeCsv(s)).getOrElse("")).append(",")
        sb.append(StringEscapeUtils.escapeCsv(speaker.bio.replaceAll("\r","").replaceAll("\n"," "))).append(",")

        sb.append(speaker.lang.getOrElse("fr")).append(",")
        sb.append(speaker.avatarUrl.map(s=>StringEscapeUtils.escapeCsv(s)).getOrElse("ERROR no photo"))
        sb.append("\n")
      }

      // echo "\xEF\xBB\xBF"; // UTF-8 BOM

      Ok(sb.toString()).withHeaders(("Content-Encoding", "UTF-8"),("Content-Disposition", "attachment;filename=speakers.csv"),("Cache-control", "private"),("Content-type","text/csv; charset=UTF-8"))

  }
//
//  def showSpeaker(uuid: String, name: String) = Action {
//    implicit request =>
//      val maybeSpeaker=Speaker.findByUUID(uuid)
//      maybeSpeaker match {
//        case Some(speaker)=>{
//          val acceptedProposals = ApprovedProposal.allAcceptedTalksForSpeaker(speaker.uuid)
//          ZapActor.actor ! LogURL("showSpeaker",uuid,name)
//          Ok(views.html.Publisher.showSpeaker(speaker,acceptedProposals))
//        }
//        case None=>NotFound("Speaker not found")
//      }
//  }
//
//  def showByTalkType(talkType:String)=Action{
//    implicit request=>
//      val proposals=Proposal.allAcceptedByTalkType(talkType)
//      Ok(views.html.Publisher.showByTalkType(proposals,talkType))
//  }
//
//  def showDetailsForProposal(proposalId:String, proposalTitle:String)=Action{
//    implicit request=>
//      Proposal.findById(proposalId) match {
//        case None=>NotFound("Proposal not found")
//        case Some(proposal)=>
//          ZapActor.actor ! LogURL("showTalk",proposalId, proposalTitle)
//          Ok(views.html.Publisher.showProposal(proposal))
//      }
//
//  }
}

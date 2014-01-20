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
import models.{Webuser, Proposal}
import org.apache.commons.lang3.{StringEscapeUtils, StringUtils}

/**
 * Controller created to build and to export the Program.
 *
 * Created by nicolas on 20/01/2014.
 */
object ProgramBuilder extends Controller with Secured {

  def index = IsMemberOf(List("admin")) {
    implicit uuid: String =>
      implicit request =>

        Ok(views.html.ProgramBuilder.index())
  }

  def exportToCSV(sortBy: Option[String]) = IsMemberOf(List("admin")) {
    implicit uuid: String =>
      implicit request =>

        val proposals = sortBy match {
          case None => Proposal.allSubmitted()
          case Some("id") => Proposal.allSubmitted().sortBy(_.id)
          case Some("track") => Proposal.allSubmitted().sortBy(_.track.id)
          case Some("type") => Proposal.allSubmitted().sortBy(_.talkType.id)
          case Some("author") => Proposal.allSubmitted().sortBy(_.mainSpeaker)
          case other => Proposal.allSubmitted()
        }

      def escape(s:String):String={
        val t=StringUtils.trimToEmpty(s)
        if(t==""){
          t
        }else{
          StringEscapeUtils.escapeCsv(t)
        }
      }

      val toReturn=new StringBuilder()
      toReturn.append("id,title,format,track,lang,author_name,author_picture")
      toReturn.append("\n")
      proposals.map{proposal:Proposal=>
        toReturn.append(proposal.id)
        toReturn.append(",")
        toReturn.append(escape(proposal.title))
        toReturn.append(",")
        toReturn.append(escape(proposal.talkType.id))
        toReturn.append(",")
        toReturn.append(proposal.track.id)
        toReturn.append(",")
        toReturn.append(proposal.lang)
        toReturn.append(",")
        toReturn.append(Webuser.findByUUID(proposal.mainSpeaker).map(_.cleanName).getOrElse("?"))
        toReturn.append("\n")
      }

      Ok(toReturn.toString()).as("text/plain; charset=utf-8")
  }
}

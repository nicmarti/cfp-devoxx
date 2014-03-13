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
import org.apache.commons.lang3.{StringUtils, StringEscapeUtils}
import play.api.libs.ws._
import play.api.libs.ws.WS.WSRequestHolder
import scala.concurrent.Future
import play.api.libs.iteratee._
import java.io._
import org.apache.commons.io.FileUtils
import play.api.libs.ws.WS.WSRequestHolder
import play.api.i18n.Messages


/**
 * Used for Printed program
 * Created by nicolas on 22/02/2014.
 */
object PaperGuide extends SecureCFPController {

  def exportSpeakers() = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      val speakers = Speaker.allSpeakersWithAcceptedTerms()

      val sb: StringBuilder = new StringBuilder()

      sb.append("id,email,prénom,nom,twitter,société,blog,bio,lang,@photo\n")
      speakers.sortBy(_.firstName.getOrElse("?")).map {
        speaker =>
          sb.append(speaker.uuid).append(",")
          sb.append(speaker.email).append(",")
          sb.append(speaker.firstName.getOrElse("")).append(",")
          sb.append(speaker.name.map(s => StringEscapeUtils.escapeCsv(s)).getOrElse("")).append(",")
          sb.append(speaker.twitter.map(s => StringEscapeUtils.escapeCsv(s)).getOrElse("")).append(",")
          sb.append(speaker.company.map(s => StringEscapeUtils.escapeCsv(s)).getOrElse("")).append(",")
          sb.append(speaker.blog.map(s => StringEscapeUtils.escapeCsv(s)).getOrElse("")).append(",")
          sb.append(StringEscapeUtils.escapeCsv(speaker.bio.replaceAll("\r", "").replaceAll("\n", " "))).append(",")
          sb.append(speaker.lang.map(s => StringEscapeUtils.escapeCsv(s)).getOrElse("fr")).append(",")
          sb.append(speaker.cleanName.replaceAll(" ", "_").toLowerCase)
          sb.append("\n")
      }

      // On Mac, Microsoft Excel or Adobe InDesign are able to import only MacRoman encoded files...
      // 1. MacRoman
      Ok(sb.toString().getBytes("MacRoman")).withHeaders(("Content-Encoding", "MacRoman"), ("Content-Disposition", "attachment;filename=speakers_mac_roman_encoding_v2.csv"), ("Cache-control", "private"), ("Content-type", "text/csv; charset=MacRoman"))

    // 2. UTF-8
    //    Ok(sb.toString()).withHeaders(("Content-Encoding", "UTF-8"), ("Content-Disposition", "attachment;filename=speaker_utf8.csv"), ("Cache-control", "private"), ("Content-type", "text/csv; charset=utf-8"))
  }

  def downloadAllSpeakersPhotos() = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      implicit val context = scala.concurrent.ExecutionContext.Implicits.global

      val speakers = Speaker.allSpeakersWithAcceptedTerms()
      val dir = new File("./target/guide")
      FileUtils.forceMkdir(dir)

      speakers.foreach {
        speaker =>
          speaker.avatarUrl.map {
            url: String =>
              val finalUrl = url match {
                case s if s.toLowerCase.startsWith("http://www.gravatar.com/avatar") && s.contains("?") == false => {
                  s + "?size=512"
                }
                case other => other
              }
              val holder: WSRequestHolder = WS.url(finalUrl.trim).withRequestTimeout(30000)
              val file = new File(dir, speaker.cleanName.replaceAll(" ", "_").toLowerCase)
              val outputStream: OutputStream = new BufferedOutputStream(new FileOutputStream(file))
              val futureResponse = holder.get {
                headers =>
                  fromStream(outputStream)
              }.map(_.run)
          }
      }

      Ok("Done.")
  }


  def fromStream(stream: OutputStream): Iteratee[Array[Byte], Unit] = Cont {
    case e@Input.EOF =>
      stream.close()
      Done((), e)
    case Input.El(data) =>
      stream.write(data)
      fromStream(stream)
    case Input.Empty =>
      fromStream(stream)
  }

  def exportProgram() = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      implicit val context = scala.concurrent.ExecutionContext.Implicits.global


      val allScheduledConf: List[ScheduleConfiguration] = for (confType <- ProposalType.all;
                                                               scheduleId <- ScheduleConfiguration.getPublishedSchedule(confType.id);
                                                               scheduledConf <- ScheduleConfiguration.loadScheduledConfiguration(scheduleId)
      ) yield scheduledConf




      allScheduledConf.foreach {
        scheduleConf: ScheduleConfiguration =>
          val dir = new File("./target/guide/" + scheduleConf.confType)
        FileUtils.forceMkdir(dir)
          val file = new File(dir, scheduleConf.confType + "_" + scheduleConf.hashCode() + ".csv")
          val writer = new PrintWriter(file, "MacRoman")
          writer.println("id,name,day,from,to,roomName,proposalId,proposalTitle,proposalLang,track,summary,speakerName,@speakerPhoto,secSpeakerName,@secSpeakerPhoto,thirdSpeaker,@thirdSpeaker,fourthSpeaker,@fourthSpeaker")
          scheduleConf.slots.foreach {
            slot: Slot =>
              writer.print(slot.id)
              writer.print(",")
              writer.print(slot.name)
              writer.print(",")
              writer.print(slot.day)
              writer.print(",")
              writer.print(slot.from)
              writer.print(",")
              writer.print(slot.to)
              writer.print(",")
              writer.print(slot.room.name)
              writer.print(",")
              slot.proposal.map {
                proposal =>
                  writer.print(proposal.id)
                  writer.print(",")
                  writer.print(StringEscapeUtils.escapeCsv(proposal.title))
                  writer.print(",")
                  writer.print(proposal.lang)
                  writer.print(",")
                  writer.print(StringEscapeUtils.escapeCsv(Messages(proposal.track.label)))
                  writer.print(",")
                  writer.print(StringEscapeUtils.escapeCsv(proposal.summary.replaceAll("\r", "").replaceAll("\n", " ").replaceAll("\\s+", " ")))
                  writer.print(",")

                  Speaker.findByUUID(proposal.mainSpeaker).map {
                    speaker: Speaker =>
                      writer.print(StringEscapeUtils.escapeCsv(speaker.cleanName))
                      writer.print(",")
                      writer.print(StringEscapeUtils.escapeCsv(StringUtils.stripAccents(speaker.cleanName.replaceAll(" ", "_").toLowerCase)))
                      writer.print(",")
                  }.getOrElse(writer.print(",,"))
                
                  proposal.secondarySpeaker.map {
                    secSpeaker =>
                      Speaker.findByUUID(secSpeaker).map {
                        s: Speaker =>
                          writer.print(StringEscapeUtils.escapeCsv(s.cleanName))
                          writer.print(",")
                          writer.print(StringEscapeUtils.escapeCsv(StringUtils.stripAccents(s.cleanName.replaceAll(" ", "_").toLowerCase)))
                          writer.print(",")
                      }.getOrElse(writer.print(",,"))
                  }.getOrElse(writer.print(",,"))

                  proposal.otherSpeakers.size match {
                    case 0 => writer.print(",,,,")
                    case 1 => {
                      proposal.otherSpeakers.map {
                        otherSpeakerId =>
                          Speaker.findByUUID(otherSpeakerId).map {
                            s2: Speaker =>
                              writer.print(StringEscapeUtils.escapeCsv(s2.cleanName))
                              writer.print(",")
                              writer.print(StringEscapeUtils.escapeCsv(StringUtils.stripAccents(s2.cleanName.replaceAll(" ", "_").toLowerCase)))
                              writer.print(",")
                          }.getOrElse(writer.print(",,"))
                      }
                      writer.print(",,")
                    }
                    case 2=>{
                       proposal.otherSpeakers.map {
                        otherSpeakerId =>
                          Speaker.findByUUID(otherSpeakerId).map {
                            s2: Speaker =>
                              writer.print(StringEscapeUtils.escapeCsv(s2.cleanName))
                              writer.print(",")
                              writer.print(StringEscapeUtils.escapeCsv(StringUtils.stripAccents(s2.cleanName.replaceAll(" ", "_").toLowerCase)))
                              writer.print(",")
                          }.getOrElse(writer.print(",,"))
                      }
                    }
                  }

                  proposal.allSpeakers.map {
                    speaker =>
                      speaker.avatarUrl.foreach {
                        url: String =>
                          val finalUrl = url match {
                            case s if s.toLowerCase.startsWith("http://www.gravatar.com/avatar") && s.contains("?") == false => {
                              s + "?size=512"
                            }
                            case other => other
                          }
                          val holder: WSRequestHolder = WS.url(finalUrl.trim).withRequestTimeout(30000)
                          val file = new File(dir, StringUtils.stripAccents(speaker.cleanName.replaceAll(" ", "_").toLowerCase))
                          val outputStream: OutputStream = new BufferedOutputStream(new FileOutputStream(file))
                          val futureResponse = holder.get {
                            headers =>
                              fromStream(outputStream)
                          }.map(_.run)
                      }
                  }


              }.getOrElse {
                writer.print(",,,,,,,")
              }
              writer.println("")


          }
          writer.close()
      }

      Ok("Done")

  }

}

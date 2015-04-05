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
 * Used for Printed program.
 * This simple controller exports the full list of talks to a CSV file.
 * Created by nicolas on 22/02/2014.
 */
object PaperGuide extends SecureCFPController {

  def exportProgram() = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      implicit val context = scala.concurrent.ExecutionContext.Implicits.global


      val allScheduledConf: List[ScheduleConfiguration] = for (confType <- ConferenceDescriptor.ConferenceProposalTypes.ALL;
                                                               scheduleId <- ScheduleConfiguration.getPublishedSchedule(confType.id);
                                                               scheduledConf <- ScheduleConfiguration.loadScheduledConfiguration(scheduleId)
      ) yield scheduledConf

      val file = new File("./target/guide", "DEVOXX_" + allScheduledConf.hashCode() + ".csv")
      val writer = new PrintWriter(file, "MacRoman") // !!! ENCODING !!!
      writer.println("id,name,day,from,to,roomName,proposalId,proposalTitle,proposalLang,track,speakerName,secSpeakerName,thirdSpeaker,fourthSpeaker")

      allScheduledConf.foreach {
        scheduleConf: ScheduleConfiguration =>
          scheduleConf.slots.foreach {
            slot: Slot =>
              writer.print(slot.id)
              writer.print(",")
              writer.print(slot.name)
              writer.print(",")
              writer.print(slot.day)
              writer.print(",")
              writer.print(slot.from.toString("HH:mm"))
              writer.print(",")
              writer.print(slot.to.toString("HH:mm"))
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

                  Speaker.findByUUID(proposal.mainSpeaker).map {
                    speaker: Speaker =>
                      writer.print(StringEscapeUtils.escapeCsv(speaker.cleanName))
                      writer.print(",")
                  }.getOrElse(writer.print(",,"))

                  proposal.secondarySpeaker.map {
                    secSpeaker =>
                      Speaker.findByUUID(secSpeaker).map {
                        s: Speaker =>
                          writer.print(StringEscapeUtils.escapeCsv(s.cleanName))
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
                          }.getOrElse(writer.print(",,"))
                      }
                      writer.print(",,")
                    }
                    case 2 => {
                      proposal.otherSpeakers.map {
                        otherSpeakerId =>
                          Speaker.findByUUID(otherSpeakerId).map {
                            s2: Speaker =>
                              writer.print(StringEscapeUtils.escapeCsv(s2.cleanName))
                              writer.print(",")
                          }.getOrElse(writer.print(",,"))
                      }
                    }
                    case 3 => {
                      proposal.otherSpeakers.map {
                        otherSpeakerId =>
                          Speaker.findByUUID(otherSpeakerId).map {
                            s2: Speaker =>
                              writer.print(StringEscapeUtils.escapeCsv(s2.cleanName))
                              writer.print(",")
                          }.getOrElse(writer.print(",,"))
                      }
                    }
                    case 4 => {
                      proposal.otherSpeakers.map {
                        otherSpeakerId =>
                          Speaker.findByUUID(otherSpeakerId).map {
                            s2: Speaker =>
                              writer.print(StringEscapeUtils.escapeCsv(s2.cleanName))
                              writer.print(",")
                          }.getOrElse(writer.print(",,"))
                      }
                    }
                  }

              }.getOrElse {
                writer.print(",,,,,,,")
              }
              writer.println("")

          }
      }
      writer.close()

      Ok("Done - Check targer CFP folder")

  }

}

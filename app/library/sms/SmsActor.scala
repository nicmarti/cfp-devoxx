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

package library.sms

import akka.actor._
import models._
import play.libs.Akka

case class SendWelcomeAndHelp(phoneNumber: String)

case class SendListOfTalks(phoneNumber: String)

case class SendHelpMessage(phoneNumber: String, msg:String)

object SmsActor {
  val actor = Akka.system.actorOf(Props[SmsActor])
}

class SmsActor extends Actor {
  def receive = {
    case SendListOfTalks(phone) => sendListOfTalks(phone)
    case SendWelcomeAndHelp(phone) => sendWelcomeAndHelp(phone)
    case SendHelpMessage(phone, msg) => sendHelpMessage(phone,msg)

    case other => play.Logger.of("library.sms.SmsActor").error("Received an invalid actor message: " + other)
  }


  def sendWelcomeAndHelp(phoneNumber: String) {
    play.Logger.of("library.sms.SmsActor").debug(s"sendWelcomeAndHelp to $phoneNumber")

    val welcomeMessage = "Welcome to Devoxx France 2017. \nSupported commands :\nTALK : send details about your talk(s)\nHELP + [txt msg] : send an emergency message to the team of volunteers (red coat)\n    \nSend STOP to unsubscribe."

    TwilioSender.send(phoneNumber, welcomeMessage)

  }

  def sendListOfTalks(phoneNumber: String) {

    play.Logger.of("library.sms.SmsActor").debug(s"sendListOfTalks to $phoneNumber")

    val allSpeakers = Speaker.allSpeakers().filter(_.phoneNumber.isDefined)
    val searchForSpeaker = allSpeakers.find(s => s.phoneNumber.get == phoneNumber || "+33"+s.phoneNumber.get.substring(1) == phoneNumber)

    searchForSpeaker.map {
      speaker =>

        val proposals = ApprovedProposal.allAcceptedTalksForSpeaker(speaker.uuid)

        proposals match {
          case e if e.isEmpty =>
            TwilioSender.send(phoneNumber, "We did not find any talk published on Devoxx France for your cell phone number. Please contact our team by email info@devoxx.fr if you think this is an error.")
          case oneTalk if proposals.size == 1 =>
            val theProposal = proposals.head

            ScheduleConfiguration.findSlotForConfType(theProposal.talkType.id, theProposal.id).map {
              slot =>
                val day = slot.day
                val hour = slot.from.toDateTime(org.joda.time.DateTimeZone.forID("Europe/Brussels")).toString("HH:mm")
                val room = slot.room.name
                TwilioSender.send(phoneNumber, s"Devoxx FR 2017\nYour presentation [${theProposal.title}] is scheduled on ${day} at ${hour} in room [${room}].\n\nRoom might change until 2nd, April 2017")
            }.getOrElse {
              TwilioSender.send(phoneNumber, s"Devoxx FR 2017\nYour presentation [${theProposal.title}] is not yet scheduled.")
            }

          case someTalks =>
            val shortMessage = someTalks.map {
              theProposal =>
                ScheduleConfiguration.findSlotForConfType(theProposal.talkType.id, theProposal.id).map {
                  slot =>
                    val day = slot.day
                    val hour = slot.from.toDateTime(org.joda.time.DateTimeZone.forID("Europe/Brussels")).toString("HH:mm")
                    val room = slot.room.name
                    s"- presentation [${theProposal.title}] is scheduled on ${day} at ${hour} in room [${room}]"
                }.getOrElse {
                  s"- presentation [${theProposal.title}] is not yet scheduled."
                }
            }
            val message = shortMessage.mkString("\n")
            TwilioSender.send(phoneNumber,"Devoxx FR 2017\n"+ message+"\n\nRooms might change until 2nd, April 2017.")
        }

    }.getOrElse {
      TwilioSender.send(phoneNumber, s"Devoxx FR 2017\nSorry, we cannot find your Speaker profile from your cell phone number. Check that you entered $phoneNumber on the Devoxx CFP and try again")
    }

  }

  def sendHelpMessage(from:String, message:String ) ={
    val allSpeakers = Speaker.allSpeakers().filter(_.phoneNumber.isDefined)
    val searchForSpeaker = allSpeakers.find(s => s.phoneNumber.get == from || "+33"+s.phoneNumber.get.substring(1) == from)

    searchForSpeaker.map{
      speaker=>
      TwilioSender.send("+33663204850",s"HELP envoyé par [${speaker.cleanName}].\nMessage du speaker: $message\nTel du speaker: $from")
    }.getOrElse{
      TwilioSender.send("+33663204850",s"HELP envoyé par un speaker inconnu\nMessage du speaker: $message\nTel du speaker: $from")
    }
  }
}
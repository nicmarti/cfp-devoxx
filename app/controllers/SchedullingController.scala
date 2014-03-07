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

import play.api.mvc.Action
import models._
import play.api.libs.json.Json
import library.ZapActor
import library.SaveSlots
import play.api.libs.json.JsString
import play.api.libs.json.JsNumber
import com.google.api.client.util.DateTime


/**
 * Schedulling Controller.
 * Plannification et création des agendas par type de conférence.
 * Created by nicolas martignole on 07/02/2014.
 */
object SchedullingController extends SecureCFPController {
  def slots(confType: String) = Action {
    implicit request =>
      import Slot.slotFormat

      val jsSlots = confType match {
        case ProposalType.UNI.id => Json.toJson(Slot.universitySlots)
        case "confThursday" => Json.toJson(Slot.conferenceSlotsThursday)
        case "confFriday" => Json.toJson(Slot.conferenceSlotsFriday)

        case "quickThursday" => Json.toJson(Slot.quickiesSlotsThursday)
        case "quickFriday" => Json.toJson(Slot.quickiesSlotsFriday)

        case ProposalType.BOF.id => Json.toJson(Slot.bofSlotsThursday)
        case ProposalType.TIA.id => Json.toJson(Slot.toolsInActionSlots)
        case ProposalType.LAB.id => Json.toJson(Slot.labsSlots)
        case other => {
          play.Logger.warn("Unable to load a slot type ["+confType+"]")
          Json.toJson(List.empty[Slot])
        }
      }
      Ok(Json.stringify(Json.toJson(Map("allSlots" -> jsSlots)))).as("application/json")
  }

  def approvedTalks(confType: String) = SecuredAction(IsMemberOf("admin")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      import models.Proposal.proposalFormat
      val proposals = confType match {
        case "confThursday" => {
          Proposal.allAcceptedByTalkType(ProposalType.CONF.id).filter {
            proposal =>
              val preferredDay = Proposal.getPreferredDay(proposal.id)
              preferredDay == None || preferredDay == Some("Thu")
          }
        }
        case "quickThursday" => {
          Proposal.allAcceptedByTalkType(ProposalType.QUICK.id).filter {
            proposal =>
              val preferredDay = Proposal.getPreferredDay(proposal.id)
              preferredDay == None || preferredDay == Some("Thu")
          }
        }
        case "confFriday" => {
          Proposal.allAcceptedByTalkType(ProposalType.CONF.id).filter {
            proposal =>
              val preferredDay = Proposal.getPreferredDay(proposal.id)
              preferredDay == None || preferredDay == Some("Fri")
          }
        }
        case "quickFriday" => {
          Proposal.allAcceptedByTalkType(ProposalType.QUICK.id).filter {
            proposal =>
              val preferredDay = Proposal.getPreferredDay(proposal.id)
              preferredDay == None || preferredDay == Some("Fri")
          }
        }
        case other => Proposal.allAcceptedByTalkType(confType)
      }

      val proposalsWithSpeaker = proposals.map {
        p: Proposal =>
          val mainWebuser = Speaker.findByUUID(p.mainSpeaker)
          val secWebuser = p.secondarySpeaker.flatMap(Speaker.findByUUID(_))
          val oSpeakers = p.otherSpeakers.map(Speaker.findByUUID(_))

          // Transform speakerUUID to Speaker name, this simplify Angular Code
          p.copy(
            mainSpeaker = mainWebuser.map(_.cleanName).getOrElse(""),
            secondarySpeaker = secWebuser.map(_.cleanName),
            otherSpeakers = oSpeakers.flatMap(s => s.map(_.cleanName))
          )
      }

      val json = Json.toJson(
        Map("approvedTalks" -> Json.toJson(
          Map("confType" -> JsString(confType),
            "total" -> JsNumber(proposals.size),
            "talks" -> Json.toJson(proposalsWithSpeaker))
        )
        )
      )
      Ok(Json.stringify(json)).as("application/json")
  }

  def saveSlots(confType: String) = SecuredAction(IsMemberOf("admin")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>

      request.body.asJson.map {
        json =>
          val newSlots = json.as[List[Slot]]
          val saveSlotsWithSpeakerUUIDs = newSlots.map {
            slot: Slot =>
              slot.proposal match {
                case Some(proposal) => {
                  // Transform back speaker name to speaker UUID when we store the slots
                  slot.copy(proposal = Proposal.findById(proposal.id))
                }
                case other => slot
              }
          }

          ZapActor.actor ! SaveSlots(confType, saveSlotsWithSpeakerUUIDs, request.webuser)

          Ok("{\"status\":\"success\"}").as("application/json")
      }.getOrElse {
        BadRequest("{\"status\":\"expecting json data\"}").as("application/json")
      }
  }

  def allScheduledConfiguration() = SecuredAction(IsMemberOf("admin")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      import ScheduleConfiguration.scheduleSavedFormat

      val scheduledSlotsKey = ScheduleConfiguration.allScheduledConfiguration()
      val json = Json.toJson(Map("scheduledConfigurations" -> Json.toJson(
        scheduledSlotsKey.map {
          case (key, dateAsDouble) =>
            val scheduledSaved = Json.parse(key).as[ScheduleSaved]
            Map("key" -> Json.toJson(scheduledSaved),
              "date" -> Json.toJson(new DateTime(dateAsDouble.toLong * 1000).toString()))
        })
      )
      )
      Ok(Json.stringify(json)).as("application/json")
  }

  def loadScheduledConfiguration(id: String) = SecuredAction(IsMemberOf("admin")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      import ScheduleConfiguration.scheduleConfFormat

      val maybeScheduledConfiguration = ScheduleConfiguration.loadScheduledConfiguration(id)
      maybeScheduledConfiguration match {
        case None => NotFound
        case Some(config) => {
          val configWithSpeakerNames = config.slots.map {
            slot: Slot =>
              slot.proposal match {
                case Some(definedProposal) => {
                  val proposalWithSpeakerNames = {
                    val mainWebuser = Speaker.findByUUID(definedProposal.mainSpeaker)
                    val secWebuser = definedProposal.secondarySpeaker.flatMap(Speaker.findByUUID(_))
                    val oSpeakers = definedProposal.otherSpeakers.map(Speaker.findByUUID(_))
                    // Transform speakerUUID to Speaker name, this simplify Angular Code
                    definedProposal.copy(
                      mainSpeaker = mainWebuser.map(_.cleanName).getOrElse(""),
                      secondarySpeaker = secWebuser.map(_.cleanName),
                      otherSpeakers = oSpeakers.flatMap(s => s.map(_.cleanName))
                    )
                  }
                  slot.copy(proposal = Option(proposalWithSpeakerNames))
                }
                case None => slot
              }
          }
          Ok(Json.toJson(config.copy(slots = configWithSpeakerNames))).as(JSON)
        }
      }
  }

  def deleteScheduleConfiguration(id:String)=SecuredAction(IsMemberOf("admin")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      ScheduleConfiguration.delete(id)
      Ok("{\"status\":\"deleted\"}").as("application/json")
  }

}

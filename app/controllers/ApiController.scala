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
import models.{Proposal, ApprovedProposal, Speaker, Slot}
import play.api.libs.json.{JsNumber, JsString, Json}
import library.{SaveSlots, ZapActor}


/**
 * API Controller
 * Created by nicolas on 07/02/2014.
 */
object ApiController extends SecureCFPController {
  def slots(confType: String) = Action {
    implicit request =>
      import Slot.slotFormat

      val jsSlots = confType match {
        case "uni" => Json.toJson(Slot.universitySlots)
        case "confThursday" => Json.toJson(Slot.conferenceSlotsThursday)
        case "confFriday" => Json.toJson(Slot.conferenceSlotsFriday)
        case "bof" => Json.toJson(Slot.bofSlotsThursday)
        case "tia" => Json.toJson(Slot.toolsInActionSlots)
        case "labs" => Json.toJson(Slot.labsSlots)
        case other => Json.toJson(Slot.universitySlots)
      }


      Ok(Json.stringify(Json.toJson(Map("allSlots" -> jsSlots)))).as("application/json")
  }

  def approvedTalks(confType: String) = SecuredAction(IsMemberOf("admin")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      import models.Proposal.proposalFormat
      val proposals = ApprovedProposal.allApprovedByTalkType(confType)

      val proposalsWithSpeaker = proposals.map {
        p:Proposal =>
          val mainWebuser = Speaker.findByUUID(p.mainSpeaker)
          val secWebuser = p.secondarySpeaker.flatMap(Speaker.findByUUID(_))
          // (p, mainWebuser.map(_.cleanName), secWebuser.map(_.cleanName))
          p.copy(
            mainSpeaker = mainWebuser.map(_.cleanName).getOrElse(""),
            secondarySpeaker = secWebuser.map(_.cleanName)
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
          val newSlots=json.as[List[Slot]]

          ZapActor.actor ! SaveSlots(newSlots,request.webuser)

          Ok("{\"status\":\"success\"}").as("application/json")
      }.getOrElse {
        BadRequest("{\"status\":\"expecting json data\"}").as("application/json")
      }



  }

}

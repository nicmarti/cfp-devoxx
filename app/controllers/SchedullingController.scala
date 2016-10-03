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

import library.{NotifyMobileApps, SaveSlots, ZapActor}
import models._
import org.joda.time.{DateTime, DateTimeZone}
import play.api.i18n.Messages
import play.api.libs.json.{JsNumber, JsString, Json}
import play.api.mvc.Action

/**
 * Scheduling Controller.
 * Plannification et création des agendas par type de conférence.
 * Created by nicolas martignole on 07/02/2014.
 */
object SchedullingController extends SecureCFPController {
  def slots(confType: String) = Action {
    implicit request =>
      import Slot.slotFormat

      val jsSlots = Json.toJson(Slot.byType(ProposalType.parse(confType)))
      Ok(Json.stringify(Json.toJson(Map("allSlots" -> jsSlots)))).as("application/json")
  }

  def approvedTalks(confType: String) = SecuredAction(IsMemberOf("admin")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      import models.Proposal.proposalFormat
      val proposals = ApprovedProposal.allApprovedByTalkType(confType)

      val proposalsWithSpeaker = proposals.map {
        p: Proposal =>
          val mainWebuser = Speaker.findByUUID(p.mainSpeaker)
          val secWebuser = p.secondarySpeaker.flatMap(Speaker.findByUUID)
          val oSpeakers = p.otherSpeakers.map(Speaker.findByUUID)
          val preferredDay = Proposal.getPreferredDay(p.id)
          val newTitleWithStars: String = s"[${FavoriteTalk.countForProposal(p.id)}★] ${p.title}"

          // Transform speakerUUID to Speaker name, this simplify Angular Code
          // Add the number of stars to the title so that we don't break the AngularJS application before Devoxx BE 2015
          // A better solution would be to return a new JSON Map with the proposal and the stars
          // but this introduced too many bugs on the Angular JS app.
          p.copy(
            title = newTitleWithStars
            , mainSpeaker = mainWebuser.map(_.cleanName).getOrElse("")
            , secondarySpeaker = secWebuser.map(_.cleanName)
            , otherSpeakers = oSpeakers.flatMap(s => s.map(_.cleanName))
            , privateMessage = preferredDay.getOrElse("")
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
                case Some(proposal) =>
                  // Transform back speaker name to speaker UUID when we store the slots
                  slot.copy(proposal = Proposal.findById(proposal.id))
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

      val scheduledSlotsKey = ScheduleConfiguration.allScheduledConfigurationWithLastModified()
      val json = Json.toJson(Map("scheduledConfigurations" -> Json.toJson(
        scheduledSlotsKey.map {
          case (key, dateAsDouble) =>
            val scheduledSaved = Json.parse(key).as[ScheduleSaved]
            Map("key" -> Json.toJson(scheduledSaved),
              "date" -> Json.toJson(new DateTime(dateAsDouble.toLong * 1000).toDateTime(DateTimeZone.forID("Europe/Brussels")))
            )
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
        case Some(config) =>
          val configWithSpeakerNames = config.slots.map {
            slot: Slot =>
              slot.proposal match {
                case Some(definedProposal) =>
                  // Create a copy of the proposal, but with clean name
                  val proposalWithSpeakerNames = {
                    val mainWebuser = Speaker.findByUUID(definedProposal.mainSpeaker)
                    val secWebuser = definedProposal.secondarySpeaker.flatMap(Speaker.findByUUID)
                    val oSpeakers = definedProposal.otherSpeakers.map(Speaker.findByUUID)
                    val preferredDay = Proposal.getPreferredDay(definedProposal.id)

                    val newTitleWithStars: String = s"[${FavoriteTalk.countForProposal(definedProposal.id)}★] ${definedProposal.title}"


                    // Transform speakerUUID to Speaker name, this simplify Angular Code
                    val copiedProposal = definedProposal.copy(
                      title = newTitleWithStars
                      , mainSpeaker = mainWebuser.map(_.cleanName).getOrElse("")
                      , secondarySpeaker = secWebuser.map(_.cleanName)
                      , otherSpeakers = oSpeakers.flatMap(s => s.map(_.cleanName))
                      , privateMessage = preferredDay.getOrElse("")
                    )

                    // Check also if the proposal is still "approved" and not refused
                    // Cause if the talk has been added to schedule, but then refused, we need
                    // to show this as a visual HINT to the admin guy (being Stephan, Antonio or me)
                    if (ApprovedProposal.isApproved(copiedProposal)) {
                      copiedProposal
                    } else {
                      copiedProposal.copy(title = "[Not Approved] " + copiedProposal.title)
                    }

                  }
                  slot.copy(proposal = Option(proposalWithSpeakerNames))
                case None => slot
              }
          }
          Ok(Json.toJson(config.copy(slots = configWithSpeakerNames))).as(JSON)
      }
  }

  def deleteScheduleConfiguration(id: String) = SecuredAction(IsMemberOf("admin")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      ScheduleConfiguration.delete(id)
      Ok("{\"status\":\"deleted\"}").as("application/json")
  }

  def publishScheduleConfiguration() = SecuredAction(IsMemberOf("admin")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>

      request.body.asJson.map {
        json =>
          val id = json.\("id").as[String]
          val confType = json.\("confType").as[String]

          ScheduleConfiguration.publishConf(id, confType)

          // Notify the mobile apps via AWS SNS that a new schedule has been published
          ZapActor.actor ! NotifyMobileApps(confType)

          Ok("{\"status\":\"success\"}").as("application/json")
      }.getOrElse {
        BadRequest("{\"status\":\"expecting json data\"}").as("application/json")
      }
  }

  def getPublishedSchedule(confType: String, day: Option[String]) = Action {
    implicit request =>
      ScheduleConfiguration.getPublishedSchedule(confType) match {
        case Some(id) => Redirect(routes.Publisher.showAgendaByConfType(confType, Option(id), day.getOrElse("wednesday")))
        case None => Redirect(routes.Publisher.homePublisher()).flashing("success" -> Messages("not.published"))
      }
  }
}
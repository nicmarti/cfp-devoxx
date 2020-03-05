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
import play.api.libs.json.{JsBoolean, JsNumber, JsString, JsValue, Json}
import play.api.mvc.Action

/**
 * Scheduling Controller.
 * Plannification et création des agendas par type de conférence.
 * Created by nicolas martignole on 07/02/2014.
 */
object SchedullingController extends SecureCFPController {
  def slots(confType: String) = Action {
    implicit request =>
      import Slot.slotWithRoomFormat

      val jsSlots = Json.toJson(Slot.byType(ProposalType.parse(confType)).map(_.toSlowWithRoom))
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
      import Slot.slotWithRoomFormat

      request.body.asJson.map {
        json =>
          val newSlots = json.as[List[SlotWithRoom]]
          val saveSlotsWithSpeakerUUIDs = newSlots.map {
            slot: SlotWithRoom =>
              slot.proposal match {
                case Some(proposal) =>
                  // Transform back speaker name to speaker UUID when we store the slots
                  slot.copy(proposal = Proposal.findById(proposal.id)).toRawSlot()
                case _ => slot.toRawSlot()
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

      val scheduledSlots = ScheduleConfiguration.allScheduledConfigurationWithLastModified().map {
        case (key, dateAsDouble) =>
          val savedSchedule = Json.parse(key).as[ScheduleSaved]
          (savedSchedule, dateAsDouble)
      }

      val deletableSlotIds = Slot.keepDeletableSlotIdsFrom(scheduledSlots.map(_._1.id))
      val json = Json.toJson(Map("scheduledConfigurations" -> Json.toJson(
        scheduledSlots.map {
          case (savedSchedule, dateAsDouble) =>
            Map(
              "key" -> Json.toJson(savedSchedule),
              "date" -> Json.toJson(new DateTime(dateAsDouble.toLong * 1000).toDateTime(ConferenceDescriptor.current().timezone)),
              "deletable" -> JsBoolean(deletableSlotIds.contains(savedSchedule.id))
            )
        })
      )
      )
      Ok(Json.stringify(json)).as("application/json")
  }

  def allProgramSchedules() = SecuredAction(IsMemberOf("admin")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      import ScheduleConfiguration.scheduleSavedFormat

      val programSchedules = ProgramSchedule.allProgramSchedulesForCurrentEvent();
      val scheduleConfigurationMapByUUID = ScheduleConfiguration.allScheduledConfigurationWithLastModified()
        .map {
          case(scheduleSavedStr, dateAsDouble) =>
            val scheduleSaved = Json.parse(scheduleSavedStr).as[ScheduleSaved]
            (scheduleSaved.id, Map(
              "id" -> JsString(scheduleSaved.id),
              "confType" -> JsString(scheduleSaved.confType),
              "createdBy" -> JsString(scheduleSaved.createdBy),
              "latestModification" -> JsNumber(dateAsDouble.toLong * 1000)
            ))
        }.toMap

      val json = Json.toJson(
        Map(
          "programSchedules" -> Json.toJson(
            programSchedules.map { programSchedule =>
              Map(
                "id" -> JsString(programSchedule.id),
                "name" -> JsString(programSchedule.name),
                "isEditable" -> JsBoolean(programSchedule.isEditable),
                "favoritesActivated" -> JsBoolean(programSchedule.favoritesActivated),
                "showSchedule" -> JsBoolean(programSchedule.showSchedule),
                "showRooms" -> JsBoolean(programSchedule.showRooms),
                "isTheOnePublished" -> JsBoolean(programSchedule.isTheOnePublished),
                "lastModifiedByName" -> JsString(programSchedule.lastModifiedByName),
                "lastModified" -> Json.toJson(programSchedule.lastModified),
                "specificScheduleCSSSnippet" -> Json.toJson(programSchedule.specificScheduleCSSSnippet.getOrElse("")),
                "scheduleConfigurations" -> Json.toJson(programSchedule.scheduleConfigurations.map {
                  case (proposalType, scheduleConfigurationId) => (proposalType.id) -> JsString(scheduleConfigurationId)
                })
              )
            }
          ),
          "savedConfigurations" -> Json.toJson(scheduleConfigurationMapByUUID.values),
          "slottableProposalTypes" -> Json.toJson(ConferenceDescriptor.ConferenceProposalTypes.slottableTypes.map { t => Map(
            "id" -> t.id,
            "label" -> Messages(t.simpleLabel)
          )})
        )
      )
      Ok(Json.stringify(json)).as("application/json")
  }

  def createAndPublishEmptyProgramSchedule() = SecuredAction(IsMemberOf("admin")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>

      ProgramSchedule.createAndPublishEmptyProgramSchedule(request.webuser);

      Ok("{\"status\":\"success\"}").as("application/json")
  }

  def createProgramSchedule() = SecuredAction(IsMemberOf("admin")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      import ProgramSchedule.persistedProgramScheduleFormat

      request.body.asJson.map {
        json =>
          val programSchedule = json.as[PersistedProgramSchedule]

          val persistedProgramSchedule = ProgramSchedule.createProgramSchedule(programSchedule, request.webuser)

          Ok(Json.toJson(persistedProgramSchedule)).as("application/json")
      }.getOrElse {
        BadRequest("{\"status\":\"expecting json data\"}").as("application/json")
      }
  }

  def updateProgramSchedule(uuid: String) = SecuredAction(IsMemberOf("admin")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      import ProgramSchedule.persistedProgramScheduleFormat

      ProgramSchedule.findById(uuid) match {
        case None => NotFound
        case Some(dbProgramSchedule) => {
          request.body.asJson.map {
            json =>
              val programSchedule = json.as[PersistedProgramSchedule]

              val persistedProgramSchedule = ProgramSchedule.updateProgramSchedule(uuid, programSchedule, Some(request.webuser))

              Ok(Json.toJson(persistedProgramSchedule)).as("application/json")
          }.getOrElse {
            BadRequest("{\"status\":\"expecting json data\"}").as("application/json")
          }
        }
      }
  }

  def deleteProgramSchedule(uuid: String) = SecuredAction(IsMemberOf("admin")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      val mayTargetProgramScheduleBePublished = for(
        dbProgramSchedule <- ProgramSchedule.findById(uuid);
        publishedSchedule <- ProgramSchedule.publishedProgramSchedule()
      ) yield {
        dbProgramSchedule.id == publishedSchedule.id
      }

      mayTargetProgramScheduleBePublished match {
        case None => NotFound
        case Some(true) => NotFound
        case Some(false) => {
          ProgramSchedule.deleteProgramSchedule(uuid)
          Ok("{\"status\":\"success\"}").as("application/json")
        }
      }
  }

  def publishProgramSchedule(uuid: String) = SecuredAction(IsMemberOf("admin")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      import ProgramSchedule.persistedProgramScheduleFormat

      ProgramSchedule.findById(uuid) match {
        case None => NotFound
        case Some(dbProgramSchedule) => {
          ProgramSchedule.publishProgramSchedule(uuid)

          // Notify the mobile apps via Gluon that a new schedule has been published
          ZapActor.actor ! NotifyMobileApps("refresh", Some(true))

          Ok("{\"status\":\"success\"}").as("application/json")
        }
      }
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
      Slot.keepDeletableSlotIdsFrom(List(id)).contains(id) match {
        case true => {
          ScheduleConfiguration.delete(id)
          Ok("{\"status\":\"deleted\"}").as("application/json")
        }
        case false => NotFound
      }
  }

  // TODO: This is only a temporary endpoint
  // Remove it once migration occured
  def migratePrograms() = SecuredAction(IsMemberOf("admin")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      import ProgramSchedule.persistedProgramScheduleFormat

      Ok(Json.toJson(OldPersistedProgramSchedule.migrateProgramSchedules())).as(JSON)
  }
}
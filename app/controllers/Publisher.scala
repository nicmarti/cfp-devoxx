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

import akka.util.Crypt
import library.search.ElasticSearch
import library.{LogURL, ZapActor}
import models._
import play.api.cache.Cache
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation.Constraints._
import play.api.libs.json.{JsObject, Json}
import play.api.mvc._


/**
  * Publisher is the controller responsible for the Web content of your conference Program.
  * Created by nicolas on 12/02/2014.
  */
object Publisher extends Controller {
  def homePublisher = Action {
    implicit request =>
      val result = views.html.Publisher.homePublisher()
      val etag = Crypt.md5(result.toString() + "dvx").toString
      val maybeETag = request.headers.get(IF_NONE_MATCH)

      maybeETag match {
        case Some(oldEtag) if oldEtag == etag => NotModified
        case other => Ok(result).withHeaders(ETAG -> etag)
      }
  }

  def showAllSpeakers = Action {
    implicit request =>

      // First load published slots
      val publishedConf = ScheduleConfiguration.loadAllPublishedSlots().filter(_.proposal.isDefined)
      val allSpeakersIDs = publishedConf.flatMap(_.proposal.get.allSpeakerUUIDs).toSet
      val etag = allSpeakersIDs.hashCode.toString

      request.headers.get(IF_NONE_MATCH) match {
        case Some(tag) if tag == etag =>
          NotModified

        case other =>
          val onlySpeakersThatAcceptedTerms: Set[String] = allSpeakersIDs.filterNot(uuid => Speaker.needsToAccept(uuid))
          val speakers = Speaker.loadSpeakersFromSpeakerIDs(onlySpeakersThatAcceptedTerms)
          Ok(views.html.Publisher.showAllSpeakers(speakers)).withHeaders(ETAG -> etag)
      }
  }

  def showSpeakerByName(name: String) = Action {
    implicit request =>
      import play.api.Play.current
      val speakers = Cache.getOrElse[List[Speaker]]("allSpeakersWithAcceptedTerms", 600) {
        Speaker.allSpeakersWithAcceptedTerms()
      }
      val speakerNameAndUUID = Cache.getOrElse[Map[String, String]]("allSpeakersName", 600) {
        speakers.map {
          speaker =>
            (speaker.urlName, speaker.uuid)
        }.toMap
      }
      val maybeSpeaker = speakerNameAndUUID.get(name).flatMap(id => Speaker.findByUUID(id))
      maybeSpeaker match {
        case Some(speaker) => {
          val acceptedProposals = ApprovedProposal.allApprovedTalksForSpeaker(speaker.uuid)
          // Log which speaker is hot or not
          ZapActor.actor ! LogURL("showSpeaker", speaker.uuid, speaker.cleanName)
          Ok(views.html.Publisher.showSpeaker(speaker, acceptedProposals))
        }
        case None => NotFound(views.html.Publisher.speakerNotFound())
      }
  }

  def showSpeaker(uuid: String, name: String) = Action {
    implicit request =>
      val maybeSpeaker = Speaker.findByUUID(uuid)
      maybeSpeaker match {
        case Some(speaker) => {
          val acceptedProposals = ApprovedProposal.allApprovedTalksForSpeaker(speaker.uuid)
          ZapActor.actor ! LogURL("showSpeaker", uuid, name)
          Ok(views.html.Publisher.showSpeaker(speaker, acceptedProposals))
        }
        case None => NotFound("Speaker not found")
      }
  }

  def showByTalkType(talkType: String) = Action {
    implicit request =>
      talkType match {
        case ConferenceDescriptor.ConferenceProposalTypes.CONF.id =>
          Ok(views.html.Publisher.showByTalkType(Proposal.allAcceptedByTalkType(List(ConferenceDescriptor.ConferenceProposalTypes.CONF.id,
            ConferenceDescriptor.ConferenceProposalTypes.CONF.id)), talkType))
        case other =>
          Ok(views.html.Publisher.showByTalkType(Proposal.allAcceptedByTalkType(talkType), talkType))
      }
  }

  def showAgendaByConfType(confType: String, slotId: Option[String], day: String = "wednesday") = Action {
    implicit request =>
      val realSlotId = slotId.orElse {
        ScheduleConfiguration.getPublishedSchedule(confType)
      }
      if (realSlotId.isEmpty) {
        NotFound(views.html.Publisher.agendaNotYetPublished())
      } else {
        val maybeScheduledConfiguration = ScheduleConfiguration.loadScheduledConfiguration(realSlotId.get)
        maybeScheduledConfiguration match {
          case Some(slotConfig) if day == null => {
            val updatedConf = slotConfig.copy(slots = slotConfig.slots)
            Ok(views.html.Publisher.showAgendaByConfType(updatedConf.slots, confType, "wednesday"))
          }
          case Some(slotConfig) if day == "monday" => {
            val updatedConf = slotConfig.copy(slots = slotConfig.slots.filter(_.day == "monday")
              , timeSlots = slotConfig.timeSlots.filter(_.start.getDayOfWeek == 1))
            Ok(views.html.Publisher.showAgendaByConfType(updatedConf.slots, confType, "monday"))
          }
          case Some(slotConfig) if day == "tuesday" => {
            val updatedConf = slotConfig.copy(
              slots = slotConfig.slots.filter(_.day == "tuesday")
              , timeSlots = slotConfig.timeSlots.filter(_.start.getDayOfWeek == 2)
            )
            Ok(views.html.Publisher.showAgendaByConfType(updatedConf.slots, confType, "tuesday"))
          }
          case Some(slotConfig) if day == "wednesday" => {
            val updatedConf = slotConfig.copy(
              slots = slotConfig.slots.filter(_.day == "wednesday")
              , timeSlots = slotConfig.timeSlots.filter(_.start.getDayOfWeek == 3)
            )
            Ok(views.html.Publisher.showAgendaByConfType(updatedConf.slots, confType, "wednesday"))
          }
          case Some(slotConfig) if day == "thursday" => {
            val updatedConf = slotConfig.copy(
              slots = slotConfig.slots.filter(_.day == "thursday")
              , timeSlots = slotConfig.timeSlots.filter(_.start.getDayOfWeek == 4)
            )
            Ok(views.html.Publisher.showAgendaByConfType(updatedConf.slots, confType, "thursday"))
          }
          case Some(slotConfig) if day == "friday" => {
            val updatedConf = slotConfig.copy(
              slots = slotConfig.slots.filter(_.day == "friday")
              , timeSlots = slotConfig.timeSlots.filter(_.start.getDayOfWeek == 5)
            )
            Ok(views.html.Publisher.showAgendaByConfType(updatedConf.slots, confType, "friday"))
          }

          case None => NotFound(views.html.Publisher.agendaNotYetPublished())
        }
      }
  }

  def showByDay(day: String) = Action {
    implicit request =>
      def _showDay(slots: List[Slot], day: String) = {
        val rooms = slots.groupBy(_.room).keys.toList
        val allSlots = ScheduleConfiguration.getPublishedScheduleByDay(day)
        Ok(views.html.Publisher.showOneDay(allSlots, rooms, day))
      }

      day match {
        case d if Set("mon", "monday", "lundi").contains(d) => _showDay(models.ConferenceDescriptor.ConferenceSlots.wednesday, "monday")
        case d if Set("tue", "tuesday", "mardi").contains(d) => _showDay(models.ConferenceDescriptor.ConferenceSlots.wednesday, "tuesday")
        case d if Set("wed", "wednesday", "mercredi").contains(d) => _showDay(models.ConferenceDescriptor.ConferenceSlots.wednesday, "wednesday")
        case d if Set("thu", "thursday", "jeudi").contains(d) => _showDay(models.ConferenceDescriptor.ConferenceSlots.thursday, "thursday")
        case d if Set("fri", "friday", "vendredi").contains(d) => _showDay(models.ConferenceDescriptor.ConferenceSlots.friday, "friday")
        case other => NotFound("Day not found")
      }
  }

  val speakerMsg = Form(
    tuple(
      "msg_pub" -> nonEmptyText(maxLength = 1500),
      "fullname" -> nonEmptyText(maxLength = 40),
      "email_pub" -> email.verifying(nonEmpty),
      "email_pub2" -> email.verifying(nonEmpty)
    ) verifying("Email does not match the confirmation email", constraint => constraint match {
      case (_, _, e1, e2) => e1 == e2
    })
  )

  def showDetailsForProposal(proposalId: String, proposalTitle: String) = Action {
    implicit request =>
      Proposal.findById(proposalId) match {
        case None => NotFound("Proposal not found")
        case Some(proposal) =>
          val publishedConfiguration = ScheduleConfiguration.getPublishedSchedule(proposal.talkType.id)
          val maybeSlot = ScheduleConfiguration.findSlotForConfType(proposal.talkType.id, proposal.id)

          ZapActor.actor ! LogURL("showTalk", proposalId, proposalTitle)

          Ok(views.html.Publisher.showProposal(proposal, publishedConfiguration, maybeSlot, speakerMsg))
      }
  }

  def search(q: Option[String] = None, p: Option[Int] = None) = Action.async {
    implicit request =>

      import play.api.libs.concurrent.Execution.Implicits.defaultContext

      ElasticSearch.doPublisherSearch(q, p).map {
        case r if r.isSuccess => {
          val json = Json.parse(r.get)
          val total = (json \ "hits" \ "total").as[Int]
          val hitContents = (json \ "hits" \ "hits").as[List[JsObject]]

          val results = hitContents.map {
            jsvalue =>
              val index = (jsvalue \ "_index").as[String]
              val source = (jsvalue \ "_source")
              val id = (source \ "id").as[String]
              val proposal = source.as[Proposal]
              proposal
          }

          Ok(views.html.Publisher.searchResult(total, results, q, p)).as("text/html")
        }
        case r if r.isFailure => {
          InternalServerError(r.get)
        }
      }
  }
}

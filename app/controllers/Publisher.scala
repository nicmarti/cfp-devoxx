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
import akka.util.Crypt
import library.{LogURL, ZapActor}
import play.api.cache.Cache
import play.api.Play

/**
 * Simple content publisher
 * Created by nicolas on 12/02/2014.
 */
object Publisher extends Controller {
  def homePublisher = Action {
    implicit request =>
      val result = views.html.Publisher.homePublisher()
      val etag = Crypt.md5(result.toString()).toString
      val maybeETag = request.headers.get(IF_NONE_MATCH)

      maybeETag match {
        case Some(oldEtag) if oldEtag == etag => NotModified
        case other => Ok(result).withHeaders(ETAG -> etag)
      }
  }

  def showAllSpeakers = Action {
    implicit request =>
      import play.api.Play.current
      val speakers = Cache.getOrElse[List[Speaker]]("allSpeakersWithAcceptedTerms", 600) {
        Speaker.allSpeakersWithAcceptedTerms()
      }
      val etag = speakers.hashCode().toString + "_2"
      val maybeETag = request.headers.get(IF_NONE_MATCH)
      maybeETag match {
        case Some(oldEtag) if oldEtag == etag => NotModified
        case other => Ok(views.html.Publisher.showAllSpeakers(speakers)).withHeaders(ETAG -> etag)
      }
  }

  def showSpeakerByName(name: String) = Action {
    implicit request =>
       import play.api.Play.current
      val speakers = Cache.getOrElse[List[Speaker]]("allSpeakersWithAcceptedTerms", 600) {
        Speaker.allSpeakersWithAcceptedTerms()
      }
      val speakerNameAndUUID = Cache.getOrElse[Map[String,String]]("allSpeakersName",600){
        speakers.map{
          speaker=>
            println(speaker.urlName)
            (speaker.urlName,speaker.uuid)
        }.toMap
      }
      val maybeSpeaker = speakerNameAndUUID.get(name).flatMap(id=>Speaker.findByUUID(id))
      maybeSpeaker match {
        case Some(speaker) => {
          val acceptedProposals = ApprovedProposal.allAcceptedTalksForSpeaker(speaker.uuid)
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
          val acceptedProposals = ApprovedProposal.allAcceptedTalksForSpeaker(speaker.uuid)
          ZapActor.actor ! LogURL("showSpeaker", uuid, name)
          Ok(views.html.Publisher.showSpeaker(speaker, acceptedProposals))
        }
        case None => NotFound("Speaker not found")
      }
  }

  def showByTalkType(talkType: String) = Action {
    implicit request =>
      val proposals = Proposal.allAcceptedByTalkType(talkType)
      Ok(views.html.Publisher.showByTalkType(proposals, talkType))
  }

  def showAgendaByConfType(confType: String, slotId: Option[String], day: String="wednesday") = Action {
    implicit request =>
      val realSlotId = slotId.orElse{
        ScheduleConfiguration.getPublishedSchedule(confType)
      }
      if(realSlotId.isEmpty){
        NotFound(views.html.Publisher.agendaNotYetPublished())
      }else {
        val maybeScheduledConfiguration = ScheduleConfiguration.loadScheduledConfiguration(realSlotId.get)
        maybeScheduledConfiguration match {
            case Some(slotConfig)  if day==null => {
            val updatedConf = slotConfig.copy(slots = slotConfig.slots)
            Ok(views.html.Publisher.showAgendaByConfType(updatedConf, confType, "wednesday"))
          }
          case Some(slotConfig) if day == "monday" => {
            val updatedConf = slotConfig.copy(slots = slotConfig.slots.filter(_.day == "monday")
              , timeSlots = slotConfig.timeSlots.filter(_.start.getDayOfWeek == 1))
            Ok(views.html.Publisher.showAgendaByConfType(updatedConf, confType, "monday"))
          }
          case Some(slotConfig) if day == "tuesday" => {
            val updatedConf = slotConfig.copy(
              slots = slotConfig.slots.filter(_.day == "tuesday")
              , timeSlots = slotConfig.timeSlots.filter(_.start.getDayOfWeek == 2)
            )
            Ok(views.html.Publisher.showAgendaByConfType(updatedConf, confType, "tuesday"))
          }
          case Some(slotConfig) if day == "wednesday" => {
            val updatedConf = slotConfig.copy(
              slots = slotConfig.slots.filter(_.day == "wednesday")
              , timeSlots = slotConfig.timeSlots.filter(_.start.getDayOfWeek == 3)
            )
            Ok(views.html.Publisher.showAgendaByConfType(updatedConf, confType, "wednesday"))
          }
          case Some(slotConfig) if day == "thursday" => {
            val updatedConf = slotConfig.copy(
              slots = slotConfig.slots.filter(_.day == "thursday")
              , timeSlots = slotConfig.timeSlots.filter(_.start.getDayOfWeek == 4)
            )
            Ok(views.html.Publisher.showAgendaByConfType(updatedConf, confType, "thursday"))
          }
          case Some(slotConfig) if day == "friday" => {
            val updatedConf = slotConfig.copy(
              slots = slotConfig.slots.filter(_.day == "friday")
              , timeSlots = slotConfig.timeSlots.filter(_.start.getDayOfWeek == 5)
            )
            Ok(views.html.Publisher.showAgendaByConfType(updatedConf, confType, "friday"))
          }

          case None => NotFound(views.html.Publisher.agendaNotYetPublished())
        }
      }
  }

  def showByDay(day: String) = Action {
    implicit request =>
      day match {
        case d if Set("mon","monday","lundi").contains(d) => Ok(views.html.Publisher.showMonday())
        case d if Set("tue","tuesday","mardi").contains(d) => Ok(views.html.Publisher.showTuesday())
        case d if Set("wed","wednesday","mercredi").contains(d) => Ok(views.html.Publisher.showWednesday())
        case d if Set("thu","thursday","jeudi").contains(d) => Ok(views.html.Publisher.showThursday())
        case d if Set("fri","friday","vendredi").contains(d) => Ok(views.html.Publisher.showFriday())
        case other => NotFound("Day not found")
      }
  }

  def showDetailsForProposal(proposalId: String, proposalTitle: String) = Action {
    implicit request =>
      Proposal.findById(proposalId) match {
        case None => NotFound("Proposal not found")
        case Some(proposal) =>
          val publishedConfiguration = ScheduleConfiguration.getPublishedSchedule(proposal.talkType.id)
          val maybeSlot = ScheduleConfiguration.findSlotForConfType(proposal.talkType.id, proposal.id)
          ZapActor.actor ! LogURL("showTalk", proposalId, proposalTitle)
          Ok(views.html.Publisher.showProposal(proposal, publishedConfiguration, maybeSlot))
      }
  }
}

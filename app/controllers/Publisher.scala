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
object Publisher extends Controller  {
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
        case None => NotFound("Speaker not found")
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



  def showAgendaByConfType(confType: String, slotId: String, day: Option[String]) = Action {
    implicit request =>
      val maybeScheduledConfiguration = ScheduleConfiguration.loadScheduledConfiguration(slotId)
      maybeScheduledConfiguration match {
        case Some(slotConfig) if day == Some("jeudi") => {
          val updatedConf = slotConfig.copy(slots = slotConfig.slots.filter(_.day == "jeudi")
            , timeSlots = slotConfig.timeSlots.filter(_.start.getDayOfWeek == 4))
          Ok(views.html.Publisher.showAgendaByConfType(updatedConf, confType, "jeudi"))
        }
        case Some(slotConfig) if day == Some("vendredi") => {
          val updatedConf = slotConfig.copy(
            slots = slotConfig.slots.filter(_.day == "vendredi")
            , timeSlots = slotConfig.timeSlots.filter(_.start.getDayOfWeek == 5)
          )
          Ok(views.html.Publisher.showAgendaByConfType(updatedConf, confType, "vendredi"))
        }
        case Some(slotConfig) if day == Some("mercredi") => {
          val updatedConf = slotConfig.copy(
            slots = slotConfig.slots.filter(_.day == "mercredi")
            , timeSlots = slotConfig.timeSlots.filter(_.start.getDayOfWeek == 3)
          )
          Ok(views.html.Publisher.showAgendaByConfType(updatedConf, confType, "mercredi"))
        }
        case Some(slotConfig) => {
          val updatedConf = slotConfig.copy(slots = slotConfig.slots)
          Ok(views.html.Publisher.showAgendaByConfType(updatedConf, confType, "mercredi"))
        }
        case None => NotFound
      }
  }

  def showByDay(day: String) = Action {
    implicit request =>
      day match {
        case "wed" => Ok(views.html.Publisher.showWednesday())
        case "wednesday" => Ok(views.html.Publisher.showWednesday())
        case "mercredi" => Ok(views.html.Publisher.showWednesday())

        case "thu" => Ok(views.html.Publisher.showThursday())
        case "thursday" => Ok(views.html.Publisher.showThursday())
        case "jeudi" => Ok(views.html.Publisher.showThursday())

        case "fri" => Ok(views.html.Publisher.showFriday())
        case "friday" => Ok(views.html.Publisher.showFriday())
        case "vendredi" => Ok(views.html.Publisher.showFriday())
        case other => NotFound("Day not found " + day)
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

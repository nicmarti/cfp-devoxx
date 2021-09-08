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

import library.search.{ElasticSearchPublisher, ProposalSearchResult}
import library.{Crypt, LogURL, ZapActor}
import models.{ConferenceDescriptor, _}
import play.api.mvc._

/**
  * Publisher is the controller responsible for the Web content of your conference Program.
  * Created by nicolas on 12/02/2014.
  */
object Publisher extends Controller {

  def homePublisher = Action {
    implicit request =>
      val result = views.html.Publisher.homePublisher()
      val eTag = Crypt.md5(result.toString() + "dvx").toString
      val maybeETag = request.headers.get(IF_NONE_MATCH)

      maybeETag match {
        case Some(oldEtag) if oldEtag == eTag => NotModified
        case _ => Ok(result).withHeaders(ETAG -> eTag)
      }
  }

  def showAllSpeakers = Action {
    implicit request =>
      // Show all speakers from accepted proposals instead of scheduled!
      val accepted: List[Proposal] = Proposal.allAccepted()
      val allSpeakersIDs = accepted.flatMap(_.allSpeakerUUIDs).toSet

      val eTag = allSpeakersIDs.hashCode.toString

      request.headers.get(IF_NONE_MATCH) match {
        case Some(tag) if tag == eTag =>
          NotModified

        case _ =>
          val onlySpeakersThatAcceptedTerms: Set[String] = allSpeakersIDs.filterNot(uuid => Speaker.needsToAccept(uuid))
          val speakers = Speaker.loadSpeakersFromSpeakerIDs(onlySpeakersThatAcceptedTerms)
          Ok(views.html.Publisher.showAllSpeakers(speakers)).withHeaders(ETAG -> eTag)
      }
  }

  def showSpeakerByName(name: String) = Action {
    implicit request =>

      val speakers = Speaker.withOneProposal(Speaker.allSpeakersWithAcceptedTerms())
      val speakerNameAndUUID = speakers.map {
        speaker => (speaker.urlName, speaker.uuid)
      }.toMap
      val maybeSpeaker = speakerNameAndUUID.get(name).flatMap(id => speakers.find(_.uuid == id))
      maybeSpeaker match {
        case Some(speaker) =>
          val acceptedProposals = ApprovedProposal.allAcceptedTalksForSpeaker(speaker.uuid)
          // Log which speaker is hot or not
          ZapActor.actor ! LogURL("showSpeaker", speaker.uuid, speaker.cleanName)
          Ok(views.html.Publisher.showSpeaker(speaker, acceptedProposals))

        case None => NotFound(views.html.Publisher.speakerNotFound())
      }
  }

  def showSpeaker(uuid: String, name: String) = Action {
    implicit request =>
      val maybeSpeaker = Speaker.findByUUID(uuid)
      maybeSpeaker match {
        case Some(speaker) =>
          val acceptedProposals = ApprovedProposal.allApprovedTalksForSpeaker(speaker.uuid)
          ZapActor.actor ! LogURL("showSpeaker", uuid, name)
          Ok(views.html.Publisher.showSpeaker(speaker, acceptedProposals))

        case None => NotFound("Speaker not found")
      }
  }

  def showByTalkType(talkType: String) = Action {
    implicit request =>
      talkType match {
        case ConferenceDescriptor.ConferenceProposalTypes.CONF.id =>
          Ok(views.html.Publisher.showByTalkType(Proposal.allAcceptedByTalkType(List(ConferenceDescriptor.ConferenceProposalTypes.CONF.id,
            ConferenceDescriptor.ConferenceProposalTypes.CONF.id)), talkType))

        case _ =>
          Ok(views.html.Publisher.showByTalkType(Proposal.allAcceptedByTalkType(talkType), talkType))
      }
  }

  def showByTag(tagId: String) = Action {
    implicit request =>
      val tag = Tag.findById(tagId)
      if (tag.isDefined) {
        val acceptedProposalsByTag = Tags.allProposalsByTagId(tagId).filter(entry => entry._2.state == ProposalState.ACCEPTED)
        Ok(views.html.Publisher.showByTag(tag.get.value, acceptedProposalsByTag.values))
      } else {
        BadRequest("Tag not found")
      }
  }

  private val monday: String = "monday"
  private val tuesday: String = "tuesday"
  private val wednesday: String = "wednesday"
  private val thursday: String = "thursday"
  private val friday: String = "friday"

  def showByDay(day: String, secretPublishKey: Option[String], hideUselessRooms: Boolean = true, includeTypes: Option[String], excludeTypes: Option[String] = Some("bof")) = Action {
    implicit request =>

      val maybeProgramSchedule = ProgramSchedule.findByPublishKey(secretPublishKey)

      def _showDay(day: String) = {
        val allSlots = Slot.fillWithFillers(ScheduleConfiguration.getPublishedScheduleByDay(day, secretPublishKey))
        val allSlotsWithBofMaybeFiltered = allSlots.filter(s => {
          (includeTypes.isEmpty || includeTypes.get.split(",").contains(s.name)) && (excludeTypes.isEmpty || !excludeTypes.get.split(",").contains(s.name))
        })
        val rooms = allSlotsWithBofMaybeFiltered.groupBy(_.room).filter { entry =>
          val result = !hideUselessRooms || entry._2.count(_.proposal.isDefined) > 0
          result
        }.keys.toList
        Ok(views.html.Publisher.showOneDay(allSlotsWithBofMaybeFiltered, rooms, day, maybeProgramSchedule, secretPublishKey, hideUselessRooms, includeTypes, excludeTypes))
      }

      (maybeProgramSchedule.map(_.showSchedule).getOrElse(false), day) match {
        case (false, _) => NotFound("Schedule not published yet")
        case (true, d) if Set("mon", monday, "lundi").contains(d) => _showDay(monday)
        case (true, d) if Set("tue", tuesday, "mardi").contains(d) => _showDay(tuesday)
        case (true, d) if Set("wed", wednesday, "mercredi").contains(d) => _showDay(wednesday)
        case (true, d) if Set("thu", thursday, "jeudi").contains(d) => _showDay(thursday)
        case (true, d) if Set("fri", friday, "vendredi").contains(d) => _showDay(friday)
        case _ => NotFound("Day not found")
      }
  }

  def showDetailsForProposal(proposalId: String, proposalTitle: String, secretPublishKey: Option[String] = None) =
    Action {
      implicit request =>
        Proposal.findById(proposalId) match {
          case None => NotFound("Proposal not found")
          case Some(proposal) =>
            if (proposal.state == ProposalState.ACCEPTED) {
              val maybeProgramSchedule = ProgramSchedule.findByPublishKey(secretPublishKey)
              val publishedConfiguration = ScheduleConfiguration.getPublishedScheduleSlotConfigurationId(proposal.talkType.id, secretPublishKey)
              val maybeSlot = ScheduleConfiguration.findSlotForConfType(proposal.talkType.id, proposal.id)

              ZapActor.actor ! LogURL("showTalk", proposalId, proposalTitle)

              Ok(views.html.Publisher.showProposal(proposal, publishedConfiguration, maybeSlot, maybeProgramSchedule))
            } else {
              NotFound("Proposal not found")
            }
        }
    }

  def search(q: Option[String] = None, p: Option[Int] = None) = Action.async {
    implicit request =>

      import play.api.libs.concurrent.Execution.Implicits.defaultContext

      ElasticSearchPublisher.doPublisherSearch(q, p).map{
        case Left(failure) => InternalServerError("Unable to search on ElasticSearch " + failure.error)
        case Right(results) =>
          import library.search.ProposalSearchResult.ProposalHitReader

          val total=results.totalHits
          Ok(views.html.Publisher.searchResult(total, results.to[ProposalSearchResult], q, p)).as("text/html")
      }
  }

}

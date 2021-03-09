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

package library.search

import akka.actor._
import models._

/**
  * ElasticSearch Akka Actor. Yes, I should write more doc, I know.
  * Give me a beer and I'll explain how does it work.
  *
  * Author: nicolas martignole
  * Created: 20 dec 2013.
  * Updated to elastic4s : 8th march 2021
  */
object ElasticSearchActor {
  val system = ActorSystem("ElasticSearch")
  val masterActor: ActorRef = system.actorOf(Props[IndexMaster], "masterActorIndex")
}

case class DoIndexProposal(proposal: Proposal)

case object DoIndexAllProposals

case object DoIndexAllReviews

case class DoIndexSpeaker(speaker: Speaker)

case object DoIndexAllSpeakers

case object DoIndexAllAccepted

case object DoIndexSchedule

case object StopIndex

case object DoCreateConfigureIndex

class IndexMaster extends Actor {
  def receive: PartialFunction[Any, Unit] = {
    case DoCreateConfigureIndex =>
      doCreateConfigureIndex()
      ElasticSearchPublisher.createConfigureIndex()
    case DoIndexSpeaker(speaker: Speaker) =>
      doIndexSpeaker(speaker)
    case DoIndexProposal(proposal: Proposal) =>
      doIndexProposal(proposal)
    case DoIndexAllSpeakers =>
      doIndexAllSpeakers()
    case DoIndexAllProposals =>
      doIndexAllProposals()
    case DoIndexAllAccepted =>
      ElasticSearchPublisher.doIndexAllAccepted()
    case DoIndexSchedule =>
      play.Logger.warn("---> !!! Index Schedule is deactivated")
    // doIndexSchedule() // TODO NMA

    case other => play.Logger.of("application.IndexMaster").error("Received an invalid actor message: " + other)
  }

  def doIndexSpeaker(speaker: Speaker) {
    ESIndexer.indexSpeaker(speaker)
  }

  def doIndexAllSpeakers() {
    // TODO there are a lot of invalid accounts that could be deleted
    val allSpeakers = Speaker.allSpeakers()
      .filterNot(s => s.bio.equals("...") || s.name.contains("CFP") || s.firstName.contains("Devoxx"))
    ESIndexer.indexAllSpeakers(allSpeakers)
  }

  def doIndexProposal(proposal: Proposal) {
    ESIndexer.indexProposal(proposal)
  }

  def doIndexAllProposals() {
    val allSubmitted = Proposal.allSubmitted()
    val allApproved = Proposal.allApproved()
    val allAccepted = Proposal.allAccepted()
    val allSpeakers = Speaker.allSpeakers()
    ESIndexer.indexAllProposals(allSubmitted ++ allApproved ++ allAccepted, allSpeakers)
  }

  // Create an ES index with the agenda
  def doIndexSchedule(): Unit = {
    //    val allAgendas = ScheduleConfiguration.loadAllConfigurations()
    //    val slots = allAgendas.flatMap(_.slots).filterNot(_.break.isDefined)
    //    val indexName = "schedule_" + ConferenceDescriptor.current().confUrlCode
    //
    //    if (slots.isEmpty) {
    //      play.Logger.of("application.IndexMaster").warn("There is no published schedule to index")
    //    } else {
    //
    //      play.Logger.of("application.IndexMaster").debug(s"Send to index [$indexName] ${slots.size} slots for schedule")
    //      val sb = new StringBuilder
    //      slots.foreach {
    //        slot: Slot =>
    //
    //          val secondarySpeaker: Option[String] = slot.proposal.flatMap {
    //            p =>
    //              p.secondarySpeaker.flatMap {
    //                uuid: String =>
    //                  Speaker.findByUUID(uuid).map {
    //                    speaker =>
    //                      speaker.cleanName
    //                  }
    //              }
    //          }
    //          val otherSpeakers: Option[String] = slot.proposal.map {
    //            p =>
    //              p.otherSpeakers.map {
    //                uuid: String =>
    //                  Speaker.findByUUID(uuid).map {
    //                    speaker =>
    //                      speaker.cleanName
    //                  }
    //              }.mkString(",")
    //          }
    //
    //          sb.append("{\"index\":{\"_index\":\"")
    //          sb.append(indexName)
    //          sb.append("\",\"_type\":\"schedule\",\"_id\":\"")
    //          sb.append(slot.id)
    //          sb.append("\"}}")
    //          sb.append("\n")
    //          sb.append(
    //            s"""{
    //               | "name":"${slot.name}",
    //               | "day":"${slot.day}",
    //               | "from":"${slot.from}",
    //               | "to":"${slot.to}",
    //               | "room":"${slot.room.name}",
    //               | "title":"${StringEscapeUtils.escapeJson(slot.proposal.map(_.title).getOrElse(""))}",
    //               | "summary":"${StringEscapeUtils.escapeJson(slot.proposal.map(_.summary.replaceAll("\r\n", "")).getOrElse(""))}",
    //               | "track":${slot.proposal.map(p => Json.toJson(p.track).toString).getOrElse("")},
    //               | "talkType":${slot.proposal.map(p => Json.toJson(p.talkType).toString).getOrElse("")},
    //               | "mainSpeaker":${slot.proposal.flatMap(p => Speaker.findByUUID(p.mainSpeaker).map(s => "\"" + s.cleanName + "\"")).getOrElse("")},
    //               | "secondarySpeaker": "${secondarySpeaker.getOrElse("")}",
    //               | "otherSpeakers": "${otherSpeakers.getOrElse("")}",
    //               | "company": "${slot.proposal.map(p => p.allSpeakers.map(s => s.company.getOrElse("")).mkString(", ")).getOrElse("")}"
    //               |}
    //          """.stripMargin.replaceAll("\n", ""))
    //          sb.append("\n")
    //      }
    //      sb.append("\n")
    //      ElasticSearch.indexBulk(indexName,sb.toString()).map {
    //        case Success(ok) =>
    //          play.Logger.of("application.IndexMaster").debug(s"Indexed ${slots.size} to $indexName")
    //        case Failure(ex) =>
    //          play.Logger.of("application.IndexMaster").error(s"Could not indexed ${slots.size} to $indexName due to ${ex.getMessage}", ex)
    //      }
    //
    //      play.Logger.of("application.IndexMaster").debug(s"Done indexing schedule to index $indexName")
    //    }
  }

  def doCreateConfigureIndex() = {
    ESIndexUtils.createIndexForProposal()
    ESIndexUtils.createIndexForSpeaker()
    val scheduleIndexName = "schedule_" + ConferenceDescriptor.current().confUrlCode
    ESIndexUtils.createIndexForSchedule(scheduleIndexName)
  }

}



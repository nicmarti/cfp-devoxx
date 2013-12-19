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

package library


import akka.actor._
import play.api.Play.current
import scala.Predef._
import models._
import org.joda.time._
import play.libs.Akka
import play.api.Play
import notifiers.Mails
import play.api.libs.json.Json


/**
 * Akka actor that is in charge to process batch operations and long running queries
 *
 * Author: nicolas martignole
 * Created: 07/11/2013 16:20
 */

// This is a simple Akka event
case class ReportIssue(issue: Issue)

case class SendMessageToSpeaker(reporterUUID: String, proposal: Proposal, msg: String)

case class SendMessageToComite(reporterUUID: String, proposal: Proposal, msg: String)

case class SendMessageInternal(reporterUUID: String, proposal: Proposal, msg: String)

case class DraftReminder()

case class CleanupInvalidReviews()

case class CreateMissingIndexOnRedis()

// Defines an actor (no failover strategy here)
object ZapActor {
  val actor = Akka.system.actorOf(Props[ZapActor])
}

class ZapActor extends Actor {
  def receive = {
    case ReportIssue(issue) => publishBugReport(issue)
    case SendMessageToSpeaker(reporterUUID, proposal, msg) => sendMessageToSpeaker(reporterUUID, proposal, msg)
    case SendMessageToComite(reporterUUID, proposal, msg) => sendMessageToComite(reporterUUID, proposal, msg)
    case SendMessageInternal(reporterUUID, proposal, msg) => postInternalMessage(reporterUUID, proposal, msg)
    case DraftReminder() => sendDraftReminder()
    case CleanupInvalidReviews() => cleanupInvalidReviews()
    case CreateMissingIndexOnRedis() => createMissingIndexOnRedis()
    case other => play.Logger.of("application.ZapActor").error("Received an invalid actor message: " + other)
  }

  def publishBugReport(issue: Issue) {
    if (play.Logger.of("application.ZapActor").isDebugEnabled) {
      play.Logger.of("application.ZapActor").debug(s"Posting a new bug report to Bitbucket")
    }

    // All the functional code should be outside the Actor, so that we can test it separately
    Issue.publish(issue)
  }

  def sendMessageToSpeaker(reporterUUID: String, proposal: Proposal, msg: String) {

    for (reporter <- Webuser.findByUUID(reporterUUID);
         speaker <- Webuser.findByUUID(proposal.mainSpeaker)) yield {
      Event.storeEvent(Event(proposal.id, reporterUUID, s"Sending a message to ${speaker.cleanName} about ${} ${proposal.title}"))
      Mails.sendMessageToSpeakers(reporter, speaker, proposal, msg)
    }
  }

  def sendMessageToComite(reporterUUID: String, proposal: Proposal, msg: String) {
    Event.storeEvent(Event(proposal.id, reporterUUID, s"Sending a message to committee about ${proposal.id} ${proposal.title}"))
    Webuser.findByUUID(reporterUUID).map {
      reporterWebuser: Webuser =>
        Mails.sendMessageToComite(reporterWebuser, proposal, msg)
    }.getOrElse {
      play.Logger.error("User not found with uuid " + reporterUUID)
    }
  }

  def postInternalMessage(reporterUUID: String, proposal: Proposal, msg: String) {
    Event.storeEvent(Event(proposal.id, reporterUUID, s"Posted an internal message fpr ${proposal.id} ${proposal.title}"))
    Webuser.findByUUID(reporterUUID).map {
      reporterWebuser: Webuser =>
        Mails.postInternalMessage(reporterWebuser, proposal, msg)
    }.getOrElse {
      play.Logger.error("User not found with uuid " + reporterUUID)
    }
  }

  def sendDraftReminder() {
    val allProposalBySpeaker = Proposal.allDrafts().groupBy(_.mainSpeaker)
    allProposalBySpeaker.foreach {
      case (speaker: String, draftProposals: List[Proposal]) => {
        Webuser.findByUUID(speaker).map {
          speakerUser =>
            Mails.sendReminderForDraft(speakerUser, draftProposals)
        }.getOrElse {
          play.Logger.warn("User not found")
        }
      }
    }
  }

  val ReviewerAndVote = "(\\w+)__(\\d+)".r

  def cleanupInvalidReviews() {
    if (play.Logger.of("library.ZapActor").isDebugEnabled()) {
      play.Logger.of("library.ZapActor").debug("Cleanup invalid reviews")
    }

    // If we delete an admin, we need to also remove all reviews created by this admin
    Redis.pool.withClient {
      client =>
      // do not use the keys function in the web app, it is a very slow command
      // There is a SCAN command on Redis 2.8 but we are using 2.6
      // Since this code runs in an Actor, it is ok to use the Keys here
        val allProposalWithReviews = client.keys("Proposals:Reviewed:ByProposal:*")
        allProposalWithReviews.foreach {
          proposalID: String =>
            val speakerIDs = client.smembers(proposalID)

            val invalidSpeakerIDs = speakerIDs.filterNot(id => client.hexists("Speaker", id))
            val invalidWebuserIDs = speakerIDs.filterNot(id => client.hexists("Webuser", id))
            if (play.Logger.of("library.ZapActor").isDebugEnabled()) {
              play.Logger.of("library.ZapActor").debug(s"Checking ${proposalID}")
              play.Logger.of("library.ZapActor").debug(s"invalidSpeakerIDs ${invalidSpeakerIDs}")
              play.Logger.of("library.ZapActor").debug(s"invalidWebuserIDs ${invalidWebuserIDs}")
              if (invalidSpeakerIDs.nonEmpty) {
                client.srem(proposalID, invalidSpeakerIDs)
              }
            }
        }

        val allProposalWithVotes = client.keys("Proposals:Votes:*")
        allProposalWithVotes.foreach {
          proposalID: String =>
            val speakerIDsVotes = client.zrangeByScore(proposalID, 0.toDouble, 10.toDouble)
            val invalidSpeakerIDs2 = speakerIDsVotes.filterNot(id => client.hexists("Speaker", id))
            val invalidWebuserIDs2 = speakerIDsVotes.filterNot(id => client.hexists("Webuser", id))
            if (play.Logger.of("library.ZapActor").isDebugEnabled()) {
              play.Logger.of("library.ZapActor").debug(s"Checking ${proposalID}")
              play.Logger.of("library.ZapActor").debug(s"invalidSpeakerIDs2 ${invalidSpeakerIDs2}")
              play.Logger.of("library.ZapActor").debug(s"invalidWebuserIDs2 ${invalidWebuserIDs2}")
              if (invalidSpeakerIDs2.nonEmpty) {
                client.zrem(proposalID, invalidSpeakerIDs2.toSeq: _*) //transforme un Set vers un varargs pour le driver jedis
              }
            }
        }

        val allVotesTimeStamp = client.keys("Proposals:Dates:*")
        val speakerIDs = client.hkeys("Speaker")

        // Delete all votes from Proposals:Dates if the related speaker was deleted
        allVotesTimeStamp.foreach {
          redisKey: String =>
            client.zrevrangeByScore(redisKey, "+inf", "-inf").map {
              value: String =>
                value match {
                  // Regexp extractor
                  case ReviewerAndVote(reviewer, vote) if !speakerIDs.contains(reviewer) => {
                    client.zrem(redisKey, value)
                  }
                  case _ => None
                }
            }
        }

    }
  }

  def createMissingIndexOnRedis() {
    Redis.pool.withClient {
      client =>
        client.hgetAll("Proposals").foreach {
          case (proposalId, proposalJSON) =>

            val proposal = Json.parse(proposalJSON).validate[Proposal].get

            val tx = client.multi()
            tx.del(s"Proposals:Speakers:$proposalId")
            // 2nd speaker
            proposal.secondarySpeaker.map {
              secondarySpeaker =>
                tx.sadd("Proposals:ByAuthor:" + secondarySpeaker, proposal.id)
            }
            // other speaker
            proposal.otherSpeakers.map {
              otherSpeaker =>
                tx.sadd("Proposals:ByAuthor:" + otherSpeaker, proposal.id)
            }

            tx.exec()
        }
    }
  }


}

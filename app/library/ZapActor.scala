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

import java.util

import akka.actor._
import controllers.LeaderboardController
import models._
import notifiers.Mails
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.HttpPost
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.message.BasicNameValuePair
import play.api.i18n.Messages
import play.api.libs.json.Json
import play.api.libs.ws.WS
import play.libs.Akka
import scala.Predef._

/**
 * Akka actor that is in charge to process batch operations and long running queries
 *
 * Author: nicolas martignole
 * Created: 07/11/2013 16:20
 */

// This is a simple Akka event
case class ReportIssue(issue: Issue)

case class SendMessageToSpeaker(reporterUUID: String, proposal: Proposal, msg: String)

case class SendQuestionToSpeaker(visitorEmail: String, visitorName: String, proposal: Proposal, msg: String)

case class SendMessageToCommittee(reporterUUID: String, proposal: Proposal, msg: String)

case class SendMessageInternal(reporterUUID: String, proposal: Proposal, msg: String)

case class DraftReminder()

case class ComputeLeaderboard()

case class ComputeVotesAndScore()

case class RemoveVotesForDeletedProposal()

case class ProposalApproved(reporterUUID: String, proposal: Proposal)

case class ProposalRefused(reporterUUID: String, proposal: Proposal)

case class SaveSlots(confType: String, slots: List[Slot], createdBy: Webuser)

case class LogURL(url: String, objRef: String, objValue: String)

case class NotifySpeakerRequestToTalk(authorUUiD: String, rtt: RequestToTalk)

case class EditRequestToTalk(authorUUiD: String, rtt: RequestToTalk)

case class NotifyProposalSubmitted(author: String, proposal: Proposal)

case class NotifyGoldenTicket(goldenTicket: GoldenTicket)

case class SendHeartbeat(apiKey: String, name: String)

case class NotifyMobileApps(message: String, scheduleUpdate: Option[Boolean] = None)

case class EmailDigests(digest : Digest)

// Defines an actor (no failover strategy here)
object ZapActor {
  val actor = Akka.system.actorOf(Props[ZapActor])
}

class ZapActor extends Actor {
  def receive = {
    case ReportIssue(issue) => publishBugReport(issue)
    case SendMessageToSpeaker(reporterUUID, proposal, msg) => sendMessageToSpeaker(reporterUUID, proposal, msg)
    case SendMessageToCommittee(reporterUUID, proposal, msg) => sendMessageToCommittee(reporterUUID, proposal, msg)
    case SendMessageInternal(reporterUUID, proposal, msg) => postInternalMessage(reporterUUID, proposal, msg)
    case DraftReminder() => sendDraftReminder()
    case ComputeLeaderboard() => doComputeLeaderboard()
    case ComputeVotesAndScore() => doComputeVotesAndScore()
    case RemoveVotesForDeletedProposal() => doRemoveVotesForDeletedProposal()
    case ProposalApproved(reporterUUID, proposal) => doProposalApproved(reporterUUID, proposal)
    case ProposalRefused(reporterUUID, proposal) => doProposalRefused(reporterUUID, proposal)
    case SaveSlots(confType: String, slots: List[Slot], createdBy: Webuser) => doSaveSlots(confType: String, slots: List[Slot], createdBy: Webuser)
    case LogURL(url: String, objRef: String, objValue: String) => doLogURL(url: String, objRef: String, objValue: String)
    case NotifySpeakerRequestToTalk(authorUUiD: String, rtt: RequestToTalk) => doNotifySpeakerRequestToTalk(authorUUiD, rtt)
    case EditRequestToTalk(authorUUiD: String, rtt: RequestToTalk) => doEditRequestToTalk(authorUUiD, rtt)
    case NotifyProposalSubmitted(author: String, proposal: Proposal) => doNotifyProposalSubmitted(author, proposal)
    case SendHeartbeat(apiKey: String, name: String) => doSendHeartbeat(apiKey, name)
    case NotifyGoldenTicket(goldenTicket: GoldenTicket) => doNotifyGoldenTicket(goldenTicket)
    case NotifyMobileApps(message: String, scheduleUpdate: Option[Boolean]) => doNotifyMobileApps(message, scheduleUpdate)
    case EmailDigests(digest: Digest) => doEmailDigests(digest)
    case other => play.Logger.of("application.ZapActor").error("Received an invalid actor message: " + other)
  }

  def publishBugReport(issue: Issue) {
    if (play.Logger.of("application.ZapActor").isDebugEnabled) {
      play.Logger.of("application.ZapActor").debug(s"Posting a new bug report to Bitbucket")
    }
    notifiers.TransactionalEmails.sendBugReport(issue)

    // All the functional code should be outside the Actor, so that we can test it separately
    Issue.publish(issue)
  }

  def sendMessageToSpeaker(reporterUUID: String, proposal: Proposal, msg: String) {

    for (reporter <- Webuser.findByUUID(reporterUUID);
         speaker <- Webuser.findByUUID(proposal.mainSpeaker)) yield {
      Event.storeEvent(Event(proposal.id, reporterUUID, s"Sending a message to ${speaker.cleanName} about ${proposal.title}"))
      Mails.sendMessageToSpeakers(reporter, speaker, proposal, msg)
    }
  }

  def sendMessageToCommittee(reporterUUID: String, proposal: Proposal, msg: String) {
    Event.storeEvent(Event(proposal.id, reporterUUID, s"Sending a message to committee about ${proposal.id} ${proposal.title}"))
    Webuser.findByUUID(reporterUUID).map {
      reporterWebuser: Webuser =>
        Mails.sendMessageToCommittee(reporterWebuser, proposal, msg)
    }.getOrElse {
      play.Logger.error("User not found with uuid " + reporterUUID)
    }
  }

  def postInternalMessage(reporterUUID: String, proposal: Proposal, msg: String) {
    Event.storeEvent(Event(proposal.id, reporterUUID, s"Posted an internal message for ${proposal.id} ${proposal.title}"))
    Webuser.findByUUID(reporterUUID).map {
      reporterWebuser: Webuser =>
        // try to load the last Message ID that was sent
        val maybeMessageID = Comment.lastMessageIDInternal(proposal.id)
        val newMessageID = Mails.postInternalMessage(reporterWebuser, proposal, msg, maybeMessageID)
        // Overwrite the messageID for the next email (to set the In-Reply-To)
        Comment.storeLastMessageIDInternal(proposal.id,newMessageID)
    }.getOrElse {
      play.Logger.error("Cannot post internal message, User not found with uuid " + reporterUUID)
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

  def doComputeLeaderboard() {
    Leaderboard.computeStats()
  }

  def doComputeVotesAndScore() {
    Review.computeAndGenerateVotes()
    ReviewByGoldenTicket.computeAndGenerateVotes()
  }

  // Delete votes if a proposal was deleted
  def doRemoveVotesForDeletedProposal() {
    Proposal.allProposalIDsDeleted.map {
      proposalId =>
        Review.deleteVoteForProposal(proposalId)
        ReviewByGoldenTicket.deleteVoteForProposal(proposalId)
    }
  }

  def doProposalApproved(reporterUUID: String, proposal: Proposal) {
    for (reporter <- Webuser.findByUUID(reporterUUID);
         speaker <- Webuser.findByUUID(proposal.mainSpeaker)) yield {
      Event.storeEvent(Event(proposal.id, reporterUUID, s"Sent proposal Approved"))
      Mails.sendProposalApproved(speaker, proposal)
      Proposal.approve(reporterUUID, proposal.id)
    }
  }

  def doProposalRefused(reporterUUID: String, proposal: Proposal) {
    for (reporter <- Webuser.findByUUID(reporterUUID);
         speaker <- Webuser.findByUUID(proposal.mainSpeaker)) yield {
      Event.storeEvent(Event(proposal.id, reporterUUID, s"Sent proposal Refused"))
      Mails.sendProposalRefused(speaker, proposal)
      Proposal.reject(reporterUUID, proposal.id)
    }
  }

  def doSaveSlots(confType: String, slots: List[Slot], createdBy: Webuser) {
    ScheduleConfiguration.persist(confType, slots, createdBy)
  }

  def doLogURL(url: String, objRef: String, objValue: String) {
    HitView.storeLogURL(url, objRef, objValue)
  }

  def doNotifySpeakerRequestToTalk(authorUUID: String, rtt: RequestToTalk) {
    RequestToTalk.save(authorUUID, rtt)
    Mails.sendInvitationForSpeaker(rtt.speakerEmail, rtt.message.getOrElse("Hi, we would like to contact you for Devoxx."), rtt.id)
  }

  def doEditRequestToTalk(authorUUID: String, rtt: RequestToTalk) {
    RequestToTalk.save(authorUUID, rtt)
  }

  def doNotifyProposalSubmitted(author: String, proposal: Proposal) {
    Event.storeEvent(Event(proposal.id, author, s"Submitted a proposal ${proposal.id} ${proposal.title}"))
    Webuser.findByUUID(author).map {
      reporterWebuser: Webuser =>
        play.Logger.info(s"About to send out email to ${author}for ${proposal.id} '${proposal.title}'")
        Mails.sendNotifyProposalSubmitted(reporterWebuser, proposal)
        play.Logger.info(s"Email sent out to $author for ${proposal.id} '${proposal.title}'")
    }.getOrElse {
      play.Logger.error("User not found with uuid " + author)
    }
  }

  def doSendHeartbeat(apikey: String, name: String): Unit = {
    import scala.concurrent.ExecutionContext.Implicits.global

    // Check if redis is up... will throw an Exception if unable to connect
    Redis.checkIfConnected()

    // Send a Heartbeat to opsgenie
    val url = "https://api.opsgenie.com/v1/json/heartbeat/send"
    val futureResult = WS.url(url).post(Map("apiKey" -> Seq(apikey), "name" -> Seq(name)))
    futureResult.map {
      result =>
        result.status match {
          case 200 =>
            val json = Json.parse(result.body)
            if(play.Logger.of("library.ZapActor").isDebugEnabled){
              play.Logger.of("library.ZapActor").debug(s"Got an ACK from OpsGenie $json")
            }

          case other =>
            play.Logger.error(s"Unable to read response from OpsGenie server ${result.status} ${result.statusText}")
            play.Logger.error(s"Response body ${result.body}")

        }
    }
  }

  def doNotifyGoldenTicket(gt:GoldenTicket):Unit={
    play.Logger.debug(s"Notify ${Messages("cfp.goldenTicket")} ${gt.ticketId} ${gt.webuserUUID}")

    Webuser.findByUUID(gt.webuserUUID).map {
      invitedWebuser: Webuser =>
         Event.storeEvent(Event(gt.ticketId, gt.webuserUUID, s"New ${Messages("cfp.goldenTicket")} for user ${invitedWebuser.cleanName}"))
        Mails.sendGoldenTicketEmail(invitedWebuser,gt)
    }.getOrElse {
      play.Logger.error(s"${Messages("cfp.goldenTicket")} error : user not found with uuid ${gt.webuserUUID}")
    }
  }

  /**
    * Push mobile schedule notification via Gluon Link
    *
    * method: POST
    * url: https://cloud.gluonhq.com/3/push/enterprise/notification
    * form params:
    *   - title: notification title
    *   - body: notification body
    *   - deliveryDate: when the push notification should be sent (not yet implemented, give 0 for now)
    *   - priority: HIGH of NORMAL
    *   - expirationType: WEEKS, DAYS, HOURS of MINUTES
    *   - expirationAmount: number of units of expirationType: WEEKS [0,4], DAYS: [0,7], HOURS: [0,24], MINUTES: [0,60]
    *   - targetType: ALL_DEVICES or SINGLE_DEVICE
    *   - targetDeviceToken: the device token where to push the notification, only in combination with targetType=SINGLE_DEVICE
    *   - invisible: true or false
    *   authenticatie: Authorization header with value: "Gluon YjJmM2YzNWVmNWU4MTFlNjkyNGEwYTkyZWYxNjBjZTNiMmYzZjM2M2Y1ZTgxMWU2OTI0YTBhOTJlZjE2MGNlM2IyZjNmMzY1ZjVlODExZTY5MjRhMGE5MmVmMTYwY2UzYjJmM2YzNjhmNWU4MTFlNjkyNGEwYTkyZWYxNjBj"
    *
    * Example silent push
    *
    * curl https://cloud.gluonhq.com/3/push/enterprise/notification -i -X POST
    *   -H "Authorization: Gluon YjJmM2YzNWVmNWU4MTFlNjkyNGEwYTkyZWYxNjBjZTNiMmYzZjM2M2Y1ZTgxMWU2OTI0YTBhOTJlZjE2MGNlM2IyZjNmMzY1ZjVlODExZTY5MjRhMGE5MmVmMTYwY2UzYjJmM2YzNjhmNWU4MTFlNjkyNGEwYTkyZWYxNjBj"
    *   -d "title=update"
    *   -d "body=update"
    *   -d "deliveryDate=0"
    *   -d "priority=HIGH"
    *   -d "expirationType=DAYS"
    *   -d "expirationAmount=1"
    *   -d "targetType=ALL_DEVICES"
    *   -d "invisible=true"
    *
    *
    * @param message the notification message
    * @param scheduleUpdate true = invisible message
    */
  def doNotifyMobileApps(message:String, scheduleUpdate: Option[Boolean]): Unit = {

    play.Logger.debug(s"Notify mobile apps (schedule update: $scheduleUpdate)")

    val post = new HttpPost("https://cloud.gluonhq.com/3/push/enterprise/notification")
    post.addHeader("Authorization","Gluon YjJmM2YzNWVmNWU4MTFlNjkyNGEwYTkyZWYxNjBjZTNiMmYzZjM2M2Y1ZTgxMWU2OTI0YTBhOTJlZjE2MGNlM2IyZjNmMzY1ZjVlODExZTY5MjRhMGE5MmVmMTYwY2UzYjJmM2YzNjhmNWU4MTFlNjkyNGEwYTkyZWYxNjBj")

    val urlParameters = new util.ArrayList[BasicNameValuePair]()
    urlParameters.add(new BasicNameValuePair("title", "My Devoxx"))

    if (scheduleUpdate.getOrElse(false)) {
      urlParameters.add(new BasicNameValuePair("body", ConferenceDescriptor.current().confUrlCode))
    } else {
      urlParameters.add(new BasicNameValuePair("body", message))
    }

    urlParameters.add(new BasicNameValuePair("deliveryDate", "0"))
    urlParameters.add(new BasicNameValuePair("priority", "HIGH"))
    urlParameters.add(new BasicNameValuePair("expirationType", "DAYS"))
    urlParameters.add(new BasicNameValuePair("expirationAmount", "1"))
    urlParameters.add(new BasicNameValuePair("targetTopic", ConferenceDescriptor.current().confUrlCode))
    urlParameters.add(new BasicNameValuePair("targetType", "TOPIC"))
    urlParameters.add(new BasicNameValuePair("invisible", scheduleUpdate.getOrElse(false).toString))

    post.setEntity(new UrlEncodedFormEntity(urlParameters))

    val client = new DefaultHttpClient
    client.execute(post)
  }

  /**
    * Handle email digests, including track proposal filters.
    *
    * @param digest the digest to process
    */
  def doEmailDigests(digest: Digest) {

    play.Logger.debug("doEmailDigests for " + digest.value)

    // Retrieve new proposals for digest
    val newProposalsIds = Digest.pendingProposals(digest)

    if (newProposalsIds.nonEmpty) {

      // Filter CFP users on given digest
      val foundUsersIDs = Webuser.allCFPWebusers()
        .filter(webUser => Digest.retrieve(webUser.uuid).equals(digest.value))
        .map(userToNotify => userToNotify.uuid)

      play.Logger.info(s"${foundUsersIDs.size} user(s) for digest ${digest.value}")

      if (foundUsersIDs.nonEmpty) {

        play.Logger.debug(s"${newProposalsIds.size} proposal(s) found for digest ${digest.value}")

        val proposals = newProposalsIds.map(entry => Proposal.findById(entry._1).get).toList

        // Check which users have digest track filters
        val trackDigestUsersIDs = foundUsersIDs.filter(uuid => Digest.getTrackFilters(uuid).nonEmpty)

        val noTrackDigestUsersIDs = trackDigestUsersIDs.diff(foundUsersIDs)

        // Mail digest to users who have no track filter set
        if (noTrackDigestUsersIDs.nonEmpty) {

          Mails.sendDigest(digest, noTrackDigestUsersIDs, proposals, isDigestFilterOn = false, LeaderboardController.getLeaderBoardParams)
        }

        // Handle the digest users that have a track filter
        trackDigestUsersIDs.map{uuid =>

          // Filter the proposals based on digest tracks
          val trackFilterIDs = Digest.getTrackFilters(uuid)

          val trackFilterProposals =
            proposals.filter(proposal => trackFilterIDs.contains(proposal.track.id))

          // If proposals exist, then mail digest to user
          if (trackFilterProposals.nonEmpty) {
            Mails.sendDigest(digest, List(uuid), trackFilterProposals, isDigestFilterOn = true, LeaderboardController.getLeaderBoardParams)
          }
        }

      } else {
        play.Logger.debug("No users found for digest " + digest.value)
      }

      // Empty digest for next interval.
      Digest.purge(digest)

    } else {
      play.Logger.debug("No new proposals found for digest " + digest.value)
    }
  }
}
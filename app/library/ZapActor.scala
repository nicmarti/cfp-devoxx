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
import com.amazonaws.auth.{AWSCredentials, BasicAWSCredentials}
import com.amazonaws.services.sns.AmazonSNSClient
import com.amazonaws.services.sns.model._
import com.amazonaws.{ClientConfiguration, Protocol}
import controllers.CFPAdmin
import models._
import notifiers.Mails
import org.apache.commons.lang3.StringUtils
import play.api.Play
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

case class SendMessageToCommitte(reporterUUID: String, proposal: Proposal, msg: String)

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

case class NotifyMobileApps(confType: String)

case class EmailDigests(digest : Digest)

// Defines an actor (no failover strategy here)
object ZapActor {
  val actor = Akka.system.actorOf(Props[ZapActor])
}

class ZapActor extends Actor {
  def receive = {
    case ReportIssue(issue) => publishBugReport(issue)
    case SendMessageToSpeaker(reporterUUID, proposal, msg) => sendMessageToSpeaker(reporterUUID, proposal, msg)
    case SendMessageToCommitte(reporterUUID, proposal, msg) => sendMessageToCommitte(reporterUUID, proposal, msg)
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
    case NotifyMobileApps(confType: String) => doNotifyMobileApps(confType)
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
      val maybeMessageID = Comment.lastMessageIDForSpeaker(proposal.id)
      val newMessageID = Mails.sendMessageToSpeakers(reporter, speaker, proposal, msg, maybeMessageID)
      // Overwrite the messageID for the next email (to set the In-Reply-To)
      Comment.storeLastMessageIDForSpeaker(proposal.id, newMessageID)
    }
  }

  def sendMessageToCommitte(reporterUUID: String, proposal: Proposal, msg: String) {
    Event.storeEvent(Event(proposal.id, reporterUUID, s"Sending a message to committee about ${proposal.id} ${proposal.title}"))
    Webuser.findByUUID(reporterUUID).map {
      reporterWebuser: Webuser =>
        val maybeMessageID = Comment.lastMessageIDForSpeaker(proposal.id)
        val newMessageID = Mails.sendMessageToCommittee(reporterWebuser, proposal, msg, maybeMessageID)
        // Overwrite the messageID for the next email (to set the In-Reply-To)
        Comment.storeLastMessageIDForSpeaker(proposal.id, newMessageID)
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
        Comment.storeLastMessageIDInternal(proposal.id, newMessageID)
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
      Event.storeEvent(Event(proposal.id, reporterUUID, "Sent proposal Approved"))
      Mails.sendProposalApproved(speaker, proposal)
      Proposal.approve(reporterUUID, proposal.id)
    }
  }

  def doProposalRefused(reporterUUID: String, proposal: Proposal) {
    for (reporter <- Webuser.findByUUID(reporterUUID);
         speaker <- Webuser.findByUUID(proposal.mainSpeaker)) yield {
      Event.storeEvent(Event(proposal.id, reporterUUID, "Sent proposal Refused"))
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
        Mails.sendNotifyProposalSubmitted(reporterWebuser, proposal)
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
            if (play.Logger.of("library.ZapActor").isDebugEnabled) {
              play.Logger.of("library.ZapActor").debug(s"Got an ACK from OpsGenie $json")
            }

          case other =>
            play.Logger.error(s"Unable to read response from OpsGenie server ${result.status} ${result.statusText}")
            play.Logger.error(s"Response body ${result.body}")

        }
    }
  }

  def doNotifyGoldenTicket(gt: GoldenTicket): Unit = {
    play.Logger.debug(s"Notify golden ticket ${gt.ticketId} ${gt.webuserUUID}")

    Webuser.findByUUID(gt.webuserUUID).map {
      invitedWebuser: Webuser =>
        Event.storeEvent(Event(gt.ticketId, gt.webuserUUID, s"New golden ticket for user ${invitedWebuser.cleanName}"))
        Mails.sendGoldenTicketEmail(invitedWebuser, gt)
    }.getOrElse {
      play.Logger.error("Golden ticket error : user not found with uuid " + gt.webuserUUID)
    }
  }

  /**
    * Push mobile schedule notification via AWS SNS.
    *
    * @param confType the event type that has been modified
    */
  def doNotifyMobileApps(confType: String): Unit = {

    play.Logger.debug("Notify mobile apps of a schedule change")

    val awsKey: Option[String] = Play.current.configuration.getString("aws.key")
    val awsSecret: Option[String] = Play.current.configuration.getString("aws.secret")
    val awsRegion: Option[String] = Play.current.configuration.getString("aws.region")

    if (awsKey.isDefined && awsSecret.isDefined && awsRegion.isDefined) {

      val credentials: AWSCredentials with Object = new BasicAWSCredentials(awsKey.get, awsSecret.get)

      val configClient: ClientConfiguration = new ClientConfiguration()
      configClient.setProtocol(Protocol.HTTP)

      val snsClient: AmazonSNSClient = new AmazonSNSClient(credentials, configClient)
      snsClient.setEndpoint(awsRegion.get)

      //create a new SNS topic
      val createTopicRequest: CreateTopicRequest = new CreateTopicRequest("cfp_schedule_updates")
      val createTopicResult: CreateTopicResult = snsClient.createTopic(createTopicRequest)

      play.Logger.debug(createTopicResult.getTopicArn)

      //publish to an SNS topic
      val publishRequest: PublishRequest = new PublishRequest()

      publishRequest.setMessage("{\"default\": \"test-message\", \"GCM\": \"{ \\\"data\\\": { \\\"message\\\": \\\"" + confType + "\\\" } }\", \"ADM\": \"{ \\\"data\\\": { \\\"message\\\": \\\"" + confType + "\\\" } }\", \"WNS\" : \"" + confType + "\"}")

      val messageAttributeValue: MessageAttributeValue = new MessageAttributeValue()
      messageAttributeValue.setStringValue("wns/raw")
      messageAttributeValue.setDataType("String")

      val attributeValueMap: util.HashMap[String, MessageAttributeValue] = new util.HashMap[String, MessageAttributeValue]()
      attributeValueMap.put("AWS.SNS.MOBILE.WNS.Type", messageAttributeValue)

      publishRequest.setMessageAttributes(attributeValueMap)
      publishRequest.setMessageStructure("json")
      publishRequest.setTargetArn(createTopicResult.getTopicArn)

      val publish: PublishResult = snsClient.publish(publishRequest)

      play.Logger.debug(publish.getMessageId)
    } else {
      play.Logger.error("AWS key and secret not configured")
    }
  }

  /**
    * Handle email digests.
    *
    * @param digest the digest to process
    */
  def doEmailDigests(digest: Digest) {

    play.Logger.debug("doEmailDigests for " + digest.value)

    // Retrieve new proposals for digest
    val newProposalsIds = Digest.pendingProposals(digest)

    if (newProposalsIds.nonEmpty) {

      // Filter CFP users on given digest
      val foundUsers = Webuser.allCFPWebusers()
        .filter(webUser => Digest.retrieve(webUser.uuid).equals(digest.value))
        .map(userToNotify => userToNotify.email)

      play.Logger.debug(s"Email Digest ${foundUsers.size} user(s) for digest ${digest.value}")

      if (foundUsers.nonEmpty) {

        play.Logger.debug(s"${newProposalsIds.size} proposal(s) found for digest ${digest.value}")

        val proposals = newProposalsIds.map(entry => Proposal.findById(entry._1).get).toList

        Mails.sendDigest(digest, foundUsers, proposals, CFPAdmin.getLeaderBoardParams)

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
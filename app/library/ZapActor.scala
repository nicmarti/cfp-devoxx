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
import models.ConferenceDescriptor.ConferenceProposalTypes
import models._
import notifiers.Mails
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.HttpPost
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.message.BasicNameValuePair
import play.libs.Akka

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

case class SendBotMessageToCommittee(reporterUUID: String, proposal: Proposal, msg: String)

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

case class NotifyMobileApps(message: String, scheduleUpdate: Option[Boolean] = None)

case class EmailDigests(digest: Digest)

case object CheckSchedules

case class UpdateSchedule(talkType: String, proposalId: String)


// Defines an actor (no failover strategy here)
object ZapActor {
  val actor: ActorRef = Akka.system.actorOf(Props[ZapActor])
}

class ZapActor extends Actor {
  def receive: PartialFunction[Any, Unit] = {
    case ReportIssue(issue) => publishBugReport(issue)
    case SendMessageToSpeaker(reporterUUID, proposal, msg) => sendMessageToSpeaker(reporterUUID, proposal, msg)
    case SendMessageToCommittee(reporterUUID, proposal, msg) => sendMessageToCommittee(reporterUUID, proposal, msg)
    case SendBotMessageToCommittee(reporterUUID, proposal, msg) => sendBotMessageToCommittee(reporterUUID, proposal, msg)
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
    case NotifyGoldenTicket(goldenTicket: GoldenTicket) => doNotifyGoldenTicket(goldenTicket)
    case NotifyMobileApps(message: String, scheduleUpdate: Option[Boolean]) => doNotifyMobileApps(message, scheduleUpdate)
    case EmailDigests(digest: Digest) => doEmailDigests(digest)
    case CheckSchedules => doCheckSchedules()
    case UpdateSchedule(talkType: String, proposalId: String) => doUpdateProposal(talkType: String, proposalId: String)
    case other => play.Logger.of("library.ZapActor").error("Received an invalid actor message: " + other)
  }

  def publishBugReport(issue: Issue) {
    if (play.Logger.of("library.ZapActor").isDebugEnabled) {
      play.Logger.of("library.ZapActor").debug(s"Posting a new bug report to Bitbucket")
    }
    notifiers.TransactionalEmails.sendBugReport(issue)

    // All the functional code should be outside the Actor, so that we can test it separately
    Issue.publish(issue)
  }

  def sendMessageToSpeaker(reporterUUID: String, proposal: Proposal, msg: String) {
    for (reporter <- Webuser.findByUUID(reporterUUID);
         speaker <- Webuser.findByUUID(proposal.mainSpeaker)) yield {
      Event.storeEvent(LegacyEvent(proposal.id, reporterUUID, s"Sending a message to ${speaker.cleanName} about ${proposal.title}"))
      val maybeMessageID = Comment.lastMessageIDForSpeaker(proposal.id)
      val newMessageID = Mails.sendMessageToSpeakers(reporter, speaker, proposal, msg, maybeMessageID)
      // Overwrite the messageID for the next email (to set the In-Reply-To)
      Comment.storeLastMessageIDForSpeaker(proposal.id, newMessageID)
    }
  }

  def sendMessageToCommittee(reporterUUID: String, proposal: Proposal, msg: String) {
    Event.storeEvent(LegacyEvent(proposal.id, reporterUUID, s"Sending a message to committee about ${proposal.id} ${proposal.title}"))
    Webuser.findByUUID(reporterUUID).map {
      reporterWebuser: Webuser =>
        val maybeMessageID = Comment.lastMessageIDForSpeaker(proposal.id)
        val newMessageID = Mails.sendMessageToCommittee(reporterWebuser, proposal, msg, maybeMessageID)
        // Overwrite the messageID for the next email (to set the In-Reply-To)
        Comment.storeLastMessageIDForSpeaker(proposal.id, newMessageID)
    }.getOrElse {
      play.Logger.of("library.ZapActor").error("User not found with uuid " + reporterUUID)
    }
  }

  def sendBotMessageToCommittee(reporterUUID: String, proposal: Proposal, msg: String) {
    Event.storeEvent(LegacyEvent(proposal.id, reporterUUID, s"Sending a message to committee about ${proposal.id} ${proposal.title}"))
    Webuser.findByUUID(reporterUUID).map {
      reporterWebuser: Webuser =>
        val maybeMessageID = Comment.lastMessageIDForSpeaker(proposal.id)
        val newMessageID = Mails.sendBotMessageToCommittee(reporterWebuser, proposal, msg, maybeMessageID)
        // Overwrite the messageID for the next email (to set the In-Reply-To)
        Comment.storeLastMessageIDForSpeaker(proposal.id, newMessageID)
    }.getOrElse {
      play.Logger.of("library.ZapActor").error("Cannot send message to committee, User not found with uuid " + reporterUUID)
    }
  }

  def postInternalMessage(reporterUUID: String, proposal: Proposal, msg: String) {
    Event.storeEvent(LegacyEvent(proposal.id, reporterUUID, s"Posted an internal message for ${proposal.id} ${proposal.title}"))
    Webuser.findByUUID(reporterUUID).map {
      reporterWebuser: Webuser =>
        // try to load the last Message ID that was sent
        val maybeMessageID = Comment.lastMessageIDInternal(proposal.id)
        val newMessageID = Mails.postInternalMessage(reporterWebuser, proposal, msg, maybeMessageID)
        // Overwrite the messageID for the next email (to set the In-Reply-To)
        Comment.storeLastMessageIDInternal(proposal.id, newMessageID)
    }.getOrElse {
      play.Logger.of("library.ZapActor").error("Cannot post internal message, User not found with uuid " + reporterUUID)
    }
  }

  def sendDraftReminder() {
    val allProposalBySpeaker = Proposal.allDrafts().groupBy(_.mainSpeaker)
    allProposalBySpeaker.foreach {
      case (speaker: String, draftProposals: List[Proposal]) =>
        Webuser.findByUUID(speaker).map {
          speakerUser =>
            Mails.sendReminderForDraft(speakerUser, draftProposals)
        }.getOrElse {
          play.Logger.of("library.ZapActor").warn("User not found, unable to send Draft reminder email")
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
    Proposal.allProposalIDsDeleted.foreach {
      proposalId =>
        Review.deleteVoteForProposal(proposalId)
        ReviewByGoldenTicket.deleteVoteForProposal(proposalId)
    }
  }

  def doProposalApproved(reporterUUID: String, proposal: Proposal) {
    for (reporter <- Webuser.findByUUID(reporterUUID);
         speaker <- Webuser.findByUUID(proposal.mainSpeaker)) yield {
      Event.storeEvent(LegacyEvent(proposal.id, reporterUUID, "Sent proposal Approved"))
      Mails.sendProposalApproved(speaker, proposal)
      Proposal.approve(reporterUUID, proposal.id)
    }
  }

  def doProposalRefused(reporterUUID: String, proposal: Proposal) {
    for (reporter <- Webuser.findByUUID(reporterUUID);
         speaker <- Webuser.findByUUID(proposal.mainSpeaker)) yield {
      Event.storeEvent(LegacyEvent(proposal.id, reporterUUID, "Sent proposal Refused"))

      if(ConferenceDescriptor.isSendProposalRefusedEmail) {
        Mails.sendProposalRefused(speaker, proposal)
      }
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
    Event.storeEvent(LegacyEvent(proposal.id, author, s"Submitted a proposal ${proposal.id} ${proposal.title}"))
    Webuser.findByUUID(author).map {
      reporterWebuser: Webuser =>
        Mails.sendNotifyProposalSubmitted(reporterWebuser, proposal)
    }.getOrElse {
      play.Logger.of("library.ZapActor").error("User not found with uuid " + author)
    }
  }

  def doNotifyGoldenTicket(gt: GoldenTicket): Unit = {
    Webuser.findByUUID(gt.webuserUUID).map {
      invitedWebuser: Webuser =>
        Event.storeEvent(LegacyEvent(gt.ticketId, gt.webuserUUID, s"New golden ticket for user ${invitedWebuser.cleanName}"))
        Mails.sendGoldenTicketEmail(invitedWebuser, gt)
    }.getOrElse {
      play.Logger.of("library.ZapActor").error("Golden ticket error : user not found with uuid " + gt.webuserUUID)
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
    * authenticatie: Authorization header with value: "Gluon YjJmM2..."
    *
    * Example silent push
    *
    * curl https://cloud.gluonhq.com/3/push/enterprise/notification -i -X POST
    * -H "Authorization: Gluon Security_HEADER_HERE"
    * -d "title=update"
    * -d "body=update"
    * -d "deliveryDate=0"
    * -d "priority=HIGH"
    * -d "expirationType=DAYS"
    * -d "expirationAmount=1"
    * -d "targetType=ALL_DEVICES"
    * -d "invisible=true"
    *
    * @param message        the notification message
    * @param scheduleUpdate true = invisible message
    */
  def doNotifyMobileApps(message: String, scheduleUpdate: Option[Boolean]): Unit = {
    val securityGluonHeader: String = ConferenceDescriptor.gluonAuthorization()

    val post = new HttpPost("https://cloud.gluonhq.com/3/push/enterprise/notification")
    post.addHeader("Authorization", s"Gluon $securityGluonHeader")

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
    val client = HttpClientBuilder.create().setUserAgent("Devoxx CFP").build()
    val result = client.execute(post)

    if(play.Logger.of("library.ZapActor").isDebugEnabled) {
      play.Logger.of("library.ZapActor").debug(s"Gluon notify mobile result ${result}")
    }
  }

  /**
    * Handle email digests, including track proposal filters.
    *
    * @param digest the digest to process
    */
  def doEmailDigests(digest: Digest) {

    play.Logger.of("library.ZapActor").debug("doEmailDigests for " + digest.value)

    // Retrieve new proposals for digest
    val newProposalsIds = Digest.pendingProposals(digest)

    if (newProposalsIds.nonEmpty) {

      // Filter CFP users on given digest
      val notificationPrefsByFoundUsersIDs = Webuser.allCFPWebusers()
        .map{ webUser => (webUser.uuid, NotificationUserPreference.load(webUser.uuid)) }
        .filter{ _._2.digestFrequency.equals(digest.value) }
        .toMap
      val foundUsersIDs = notificationPrefsByFoundUsersIDs.keySet

      if (foundUsersIDs.nonEmpty) {

        play.Logger.of("library.ZapActor").debug(s"${newProposalsIds.size} proposal(s) found for digest ${digest.value}")

        val proposals = newProposalsIds.map(entry => Proposal.findById(entry._1).get).toList

        // Check which users have digest track filters
        val trackDigestUsersIDs = notificationPrefsByFoundUsersIDs.filter{ _._2.autowatchFilterForTrackIds.isDefined }.keySet

        val noTrackDigestUsersIDs = trackDigestUsersIDs.diff(foundUsersIDs)

        // Mail digest to users who have no track filter set
        if (noTrackDigestUsersIDs.nonEmpty) {
          Mails.sendDigest(digest, noTrackDigestUsersIDs.toList, proposals, isDigestFilterOn = false, LeaderboardController.getLeaderBoardParams)
        }

        // Handle the digest users that have a track filter
        trackDigestUsersIDs.map { uuid =>

          // Filter the proposals based on digest tracks
          val trackFilterIDs = notificationPrefsByFoundUsersIDs.get(uuid).flatMap(_.autowatchFilterForTrackIds).getOrElse(List())

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

  def doCheckSchedules(): Unit = {

    import scalaz._
    import Scalaz._

    val allPublishedSlots: List[Slot] = ScheduleConfiguration.loadAllPublishedSlots().filter(_.proposal.isDefined)
    val approvedOrAccepted = allPublishedSlots.filter(p => p.proposal.get.state == ProposalState.APPROVED || p.proposal.get.state == ProposalState.ACCEPTED)

    val publishedProposalIDs: Set[String] = approvedOrAccepted.map(_.proposal.get.id).toSet

    val allValidProposals = Proposal.loadAndParseProposals(publishedProposalIDs)

    def checkSameTitle: (Proposal, Proposal) => ValidationNel[ProposalAndRelatedError, String] = (p1, p2) => {
      if (p1.title == p2.title) "Same title".successNel
      else ProposalAndRelatedError(p1, "Title was changed", p1.title, p2.title).failureNel
    }

    def checkSameSummary: (Proposal, Proposal) => ValidationNel[ProposalAndRelatedError, String] = (p1, p2) => {
      if (p1.summary == p2.summary) "Same summary".successNel
      else ProposalAndRelatedError(p1, "Summary was changed", p1.summary, p2.summary).failureNel
    }

    def checkSameMainSpeaker: (Proposal, Proposal) => ValidationNel[ProposalAndRelatedError, String] = (p1, p2) => {
      if (p1.mainSpeaker == p2.mainSpeaker) "Same main speaker".successNel
      else ProposalAndRelatedError(p1, "Main speaker was changed", p1.mainSpeaker, p2.mainSpeaker).failureNel
    }

    def checkSameSecondSpeakers: (Proposal, Proposal) => ValidationNel[ProposalAndRelatedError, String] = (p1, p2) => {
      if (p1.secondarySpeaker == p2.secondarySpeaker) "Same secondary speaker".successNel
      else ProposalAndRelatedError(p1, "Secondary speaker was changed", p1.secondarySpeaker.getOrElse("?"), p2.secondarySpeaker.getOrElse("?")).failureNel
    }

    def checkSameOtherSpeakers: (Proposal, Proposal) => ValidationNel[ProposalAndRelatedError, String] = (p1, p2) => {
      if (p1.otherSpeakers == p2.otherSpeakers) "Same other speaker".successNel
      else ProposalAndRelatedError(p1, "Other speaker was changed", p1.otherSpeakers.mkString("/"), p2.otherSpeakers.mkString("/")).failureNel
    }

    val validateProposals = for {
      a <- checkSameTitle
      b <- checkSameSummary
      c <- checkSameMainSpeaker
      d <- checkSameSecondSpeakers
      e <- checkSameOtherSpeakers
    } yield a |@| b |@| c |@| d |@| e // |@| is an Applicative Builder -> it accumulates the combined result as a tuple

    def doNothing(p1: String, p2: String, p3: String, p4: String, p5: String) = s"$p1 $p2 $p3 $p4 $p5"

    val messages: List[ValidationNel[ProposalAndRelatedError, String]] = allPublishedSlots.map {
      s: Slot =>
        val publishedProposal = s.proposal.get
        // cause we did a filter where proposal is Defined
        val fromCFPProposal = allValidProposals(publishedProposal.id)
        validateProposals(publishedProposal, fromCFPProposal)(doNothing)
    }

    val onlyErrors: List[ProposalAndRelatedError] = messages.filter(_.isFailure).flatMap { f: Validation[NonEmptyList[ProposalAndRelatedError], String] =>
      val noEmpty = f.toEither.left.get
      noEmpty.toList
    }

    sender() ! onlyErrors
  }

  def doUpdateProposal(confType: String, proposalId: String): Unit = {
    play.Logger.of("library.ZapActor").debug("Update published Schedule for proposal Id " + proposalId)

    ScheduleConfiguration.findSlotForConfType(confType, proposalId).foreach {
      slot =>
        val proposal = slot.proposal.get
        ScheduleConfiguration.getPublishedScheduleSlotConfigurationId(proposal.talkType.id).foreach {
          id =>
            ScheduleConfiguration.loadScheduledConfiguration(id).foreach { p =>
              val newSlots = p.slots.map {
                s: Slot =>
                  s match {
                    case e if e.proposal.isDefined && e.proposal.get.id == proposalId =>
                      e.copy(proposal = Proposal.findById(proposalId))
                    case other => other
                  }
              }
              val newID = ScheduleConfiguration.persist(confType, newSlots, Webuser.Internal)
              ProgramSchedule.updatePublishedScheduleConfiguration(id, newID, ConferenceProposalTypes.valueOf(confType), None)
            }
        }
    }
  }

}

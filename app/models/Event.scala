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

package models

import com.fasterxml.jackson.annotation.JsonSubTypes.Type
import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo}
import com.fasterxml.jackson.databind.{ObjectMapper, SerializationFeature}
import com.fasterxml.jackson.datatype.joda.JodaModule
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import controllers.routes
import library.Redis
import org.joda.time.{DateTime, Instant}
import play.api.i18n.Messages

import java.lang

/**
  * Entity to store all CFP events generated by speaker such as "I submitted a talk" or
  * "my talk was accepted".
  *
  * Author: @nmartignole / @fcamblor
  *
  * Created: 11/11/2013 09:39
  */

abstract class EventLink {
  def href: String

  def rel: String

  def title: String

  def icon: String
}

case class ProposalLink(proposalId: String, webuser: Webuser) extends EventLink {
  def href: String = ConferenceDescriptor.current().conferenceUrls.cfpURL() + (if (Webuser.isMember(webuser.uuid, "cfp")) {
    routes.CFPAdmin.openForReview(proposalId)
  }
  else {
    routes.GoldenTicketController.openForReview(proposalId)
  }).toString()

  def rel: String = routes.RestAPI.profile("proposal").toString()

  def title: String = proposalId

  def icon = "fa-sign-in-alt"
}

case class SpeakerLink(speakerUuid: String, speakerName: String) extends EventLink {
  def href: String = ConferenceDescriptor.current().conferenceUrls.cfpURL() + routes.CFPAdmin.showSpeakerAndTalks(speakerUuid).toString()

  def rel: String = routes.RestAPI.profile("speaker").toString()

  def title: String = speakerName

  def icon = "fa-user"
}

case class WishlistLink(requestId: String) extends EventLink {
  def href: String = ConferenceDescriptor.current().conferenceUrls.cfpURL() + routes.Wishlist.edit(requestId).toString()

  def rel: String = routes.RestAPI.profile("wishlist").toString()

  def title: String = requestId

  def icon = "fa-sort-amount-down"
}

/**
  * Event.
  *
  * @param objRef is the unique ID of the object that was modified
  * @param uuid   is the author, the person who did this change
  * @param msg    is a description of what one did
  * @param date   is the event ate
  */
@JsonTypeInfo(
  use = JsonTypeInfo.Id.NAME,
  include = JsonTypeInfo.As.PROPERTY,
  property = "kind")
@JsonSubTypes(Array(
  new Type(value = classOf[NewSpeakerEvent], name = "NewSpeaker"),
  new Type(value = classOf[UpdatedSpeakerEvent], name = "UpdatedSpeaker"),
  new Type(value = classOf[GoldenTicketUserCreatedEvent], name = "GoldenTicketUserCreated"),
  new Type(value = classOf[SpeakerRefusedTermsAndConditionsEvent], name = "SpeakerRefusedTermsAndConditions"),
  new Type(value = classOf[SpeakerAcceptedTermsAndConditionsEvent], name = "SpeakerAcceptedTermsAndConditions"),
  new Type(value = classOf[DeletedWebuserEvent], name = "DeletedWebuser"),
  new Type(value = classOf[WebuserAddedToCFPGroupEvent], name = "WebuserAddedToCFPGroup"),
  new Type(value = classOf[WebuserRemovedFromCFPGroupEvent], name = "WebuserRemovedFromCFPGroup"),
  new Type(value = classOf[NewProposalCreatedEvent], name = "NewProposalCreated"),
  new Type(value = classOf[NewProposalPostedByTypeEvent], name = "NewProposalPostedByType"),
  new Type(value = classOf[NewProposalPostedByStateEvent], name = "NewProposalPostedByState"),
  new Type(value = classOf[NewProposalPostedByTrackEvent], name = "NewProposalPostedByTrack"),
  new Type(value = classOf[ChangedTypeOfProposalEvent], name = "ChangedTypeOfProposal"),
  new Type(value = classOf[ChangedStateOfProposalEvent], name = "ChangedStateOfProposal"),
  new Type(value = classOf[ChangedTrackOfProposalEvent], name = "ChangedTrackOfProposal"),
  new Type(value = classOf[EditAttemptOnSomeoneElseProposalEvent], name = "EditAttemptOnSomeoneElseProposal"),
  new Type(value = classOf[ProposalSubmissionEvent], name = "ProposalSubmission"),
  new Type(value = classOf[UpdatedDraftProposalEvent], name = "UpdatedDraftProposal"),
  new Type(value = classOf[UpdatedSubmittedProposalEvent], name = "UpdatedSubmittedProposal"),
  new Type(value = classOf[DeletedProposalEvent], name = "DeletedProposal"),
  new Type(value = classOf[RemovedProposalSponsoredFlagEvent], name = "RemovedProposalSponsoredFlag"),
  new Type(value = classOf[GTVoteForProposalEvent], name = "GTVoteForProposal"),
  new Type(value = classOf[VoteForProposalEvent], name = "VoteForProposal"),
  new Type(value = classOf[RemovedProposalVoteEvent], name = "RemovedProposalVote"),
  new Type(value = classOf[AllProposalVotesResettedEvent], name = "AllProposalVotesResetted"),
  new Type(value = classOf[ProposalAutoWatchedEvent], name = "ProposalAutoWatched"),
  new Type(value = classOf[ProposalManuallyWatchedEvent], name = "ProposalManuallyWatched"),
  new Type(value = classOf[ProposalUnwatchedEvent], name = "ProposalUnwatched"),
  new Type(value = classOf[ProposalPublicCommentSentBySpeakerEvent], name = "ProposalPublicCommentSentBySpeaker"),
  new Type(value = classOf[ProposalPublicCommentSentByReviewersEvent], name = "ProposalPublicCommentSentByReviewers"),
  new Type(value = classOf[ProposalPrivateCommentSentByComiteeEvent], name = "ProposalPrivateCommentSentByComitee"),
  new Type(value = classOf[ProposalPrivateAutomaticCommentSentEvent], name = "ProposalPrivateAutomaticCommentSent"),
  new Type(value = classOf[UpdatedProposalSpeakersListEvent], name = "UpdatedProposalSpeakersList"),
  new Type(value = classOf[AddedSecondarySpeakerToProposalEvent], name = "AddedSecondarySpeakerToProposal"),
  new Type(value = classOf[RemovedSecondarySpeakerFromProposalEvent], name = "RemovedSecondarySpeakerFromProposal"),
  new Type(value = classOf[ReplacedProposalSecondarySpeakerEvent], name = "ReplacedProposalSecondarySpeaker"),
  new Type(value = classOf[SentProposalApprovedEvent], name = "SentProposalApproved"),
  new Type(value = classOf[SentProposalRefusedEvent], name = "SentProposalRefused"),
  new Type(value = classOf[TalkRemovedFromRefuseListEvent], name = "TalkRemovedFromRefuseList"),
  new Type(value = classOf[TalkRemovedFromApprovedListEvent], name = "TalkRemovedFromApprovedList"),
  new Type(value = classOf[TalkRefusedEvent], name = "TalkRefused"),
  new Type(value = classOf[RefuseProposalEvent], name = "RefuseProposal"),
  new Type(value = classOf[ApprovedProposalEvent], name = "ApprovedProposal"),
  new Type(value = classOf[WishlistItemStatusUpdateEvent], name = "WishlistItemStatusUpdate")
))
abstract class Event {
  val creator: String

  val date: DateTime = new DateTime().toDateTime(ConferenceDescriptor.current().timezone)

  def objRef(): Option[String] = None

  def message(): String

  def linksFor(webuser: Webuser): Seq[EventLink] = Seq()

  def timezonedDate(): DateTime = date.toDateTime(ConferenceDescriptor.current().timezone)
}

abstract class ProposalEvent extends Event {
  val proposalId: String

  override def objRef(): Option[String] = Some(proposalId)

  override def linksFor(webuser: Webuser): Seq[EventLink] = Seq(ProposalLink(proposalId, webuser))
}

abstract class SpeakerEvent extends Event {
  val webuserId: String
  val webUsername: String

  override def objRef(): Option[String] = Some(webuserId)

  override def linksFor(webuser: Webuser): Seq[EventLink] = if (Webuser.isMember(webuser.uuid, "cfp")) {
    Seq(SpeakerLink(webuserId, webUsername))
  } else {
    Seq()
  }
}

// User management (speakers/gts) events
case class NewSpeakerEvent(creator: String, webuserId: String, webUsername: String)
  extends SpeakerEvent {
  def message(): String = Messages("events.NewSpeaker", webuserId)
}

case class UpdatedSpeakerEvent(creator: String, webuserId: String, webUsername: String)
  extends SpeakerEvent {
  def message(): String = Messages("events.UpdatedSpeaker", webuserId)
}

case class GoldenTicketUserCreatedEvent(creator: String, webuserId: String, webUsername: String)
  extends Event {
  def message(): String = Messages("events.GoldenTicketUserCreated", webUsername)
}

case class SpeakerRefusedTermsAndConditionsEvent(creator: String)
  extends Event {
  def message(): String = Messages("events.SpeakerRefusedTermsAndConditions")
}

case class SpeakerAcceptedTermsAndConditionsEvent(creator: String)
  extends Event {
  def message(): String = Messages("events.SpeakerAcceptedTermsAndConditions")
}

case class DeletedWebuserEvent(creator: String, webuserId: String, webUsername: String)
  extends Event {
  def message(): String = Messages("events.DeletedWebuser", webuserId, webUsername)
}

case class WebuserAddedToCFPGroupEvent(creator: String, webuserId: String, webUsername: String)
  extends SpeakerEvent {
  def message(): String = Messages("events.WebuserAddedToCFPGroup", webuserId, webUsername)
}

case class WebuserRemovedFromCFPGroupEvent(creator: String, webuserId: String, webUsername: String)
  extends SpeakerEvent {
  def message(): String = Messages("events.WebuserRemovedFromCFPGroup", webuserId, webUsername)
}

// Proposal events
case class NewProposalCreatedEvent(creator: String, proposalId: String, proposalTitle: String)
  extends ProposalEvent {
  def message(): String = Messages("events.NewProposalCreated", proposalId, proposalTitle)
}

case class NewProposalPostedByTypeEvent(creator: String, proposalId: String, proposalType: String)
  extends ProposalEvent {
  def message(): String = Messages("events.NewProposalPostedByType", proposalId, proposalType)
}

case class NewProposalPostedByStateEvent(creator: String, proposalId: String, proposalState: String)
  extends ProposalEvent {
  def message(): String = Messages("events.NewProposalPostedByState", proposalId, proposalState)
}

case class NewProposalPostedByTrackEvent(creator: String, proposalId: String, proposalTitle: String, proposalTrackId: String)
  extends ProposalEvent {
  def message(): String = Messages("events.NewProposalPostedByTrack", proposalId, proposalTitle, Messages(proposalTrackId))
}

case class ChangedTypeOfProposalEvent(creator: String, proposalId: String, oldProposalType: String, newProposalType: String)
  extends ProposalEvent {
  def message(): String = Messages("events.ChangedTypeOfProposal", proposalId, oldProposalType, newProposalType)
}

case class ChangedStateOfProposalEvent(creator: String, proposalId: String, oldProposalState: String, newProposalState: String)
  extends ProposalEvent {
  def message(): String = Messages("events.ChangedStateOfProposal", proposalId, oldProposalState, newProposalState)
}

case class ChangedTrackOfProposalEvent(creator: String, proposalId: String, proposalTitle: String, oldProposalTrackId: String, newProposalTrackId: String)
  extends ProposalEvent {
  def message(): String = Messages("events.ChangedTrackOfProposal", proposalId, proposalTitle, Messages(oldProposalTrackId), Messages(newProposalTrackId))
}

case class EditAttemptOnSomeoneElseProposalEvent(creator: String, proposalId: String)
  extends ProposalEvent {
  def message(): String = Messages("events.EditAttemptOnSomeoneElseProposal", proposalId)
}

// Proposal statuses
case class ProposalSubmissionEvent(creator: String, proposalId: String, proposalTitle: String)
  extends ProposalEvent {
  def message(): String = Messages("events.ProposalSubmission", proposalId, proposalTitle)
}

case class UpdatedDraftProposalEvent(creator: String, proposalId: String, proposalTitle: String)
  extends ProposalEvent {
  def message(): String = Messages("events.UpdatedDraftProposal", proposalId, proposalTitle)
}

case class UpdatedSubmittedProposalEvent(creator: String, proposalId: String, proposalTitle: String, proposalStateCode: String)
  extends ProposalEvent {
  def message(): String = Messages("events.UpdatedSubmittedProposal", proposalId, proposalTitle, proposalStateCode)
}

case class DeletedProposalEvent(creator: String, proposalId: String)
  extends Event {
  def message(): String = Messages("events.DeletedProposal", proposalId)
}

case class RemovedProposalSponsoredFlagEvent(creator: String, proposalId: String, proposalTitle: String)
  extends ProposalEvent {
  def message(): String = Messages("events.RemovedProposalSponsoredFlag", proposalId, proposalTitle)
}

// Proposal votes
case class GTVoteForProposalEvent(creator: String, proposalId: String, vote: Int)
  extends ProposalEvent {
  def message(): String = Messages("events.GTVoteForProposal", vote, proposalId)
}
case class VoteForProposalEvent(creator: String, proposalId: String, vote: Int)
  extends ProposalEvent {
  def message(): String = Messages("events.VoteForProposal", vote, proposalId)
}

case class RemovedProposalVoteEvent(creator: String, proposalId: String)
  extends ProposalEvent {
  def message(): String = Messages("events.RemovedProposalVote", proposalId)
}

case class AllProposalVotesResettedEvent(creator: String, proposalId: String)
  extends ProposalEvent {
  def message(): String = Messages("events.AllProposalVotesResetted", proposalId)
}

// Proposal watches
case class ProposalAutoWatchedEvent(creator: String, proposalId: String)
  extends ProposalEvent {
  def message(): String = Messages("events.ProposalAutoWatched", proposalId)
}

case class ProposalManuallyWatchedEvent(creator: String, proposalId: String)
  extends ProposalEvent {
  def message(): String = Messages("events.ProposalManuallyWatched", proposalId)
}

case class ProposalUnwatchedEvent(creator: String, proposalId: String)
  extends ProposalEvent {
  def message(): String = Messages("events.ProposalUnwatched", proposalId)
}


// Proposal comments events
case class ProposalPublicCommentSentBySpeakerEvent(creator: String, proposalId: String, proposalTitle: String, comment: String)
  extends ProposalEvent {
  def message(): String = Messages("events.ProposalPublicCommentSentBySpeaker", proposalId, proposalTitle)
}

case class ProposalPublicCommentSentByReviewersEvent(creator: String, proposalId: String, proposalTitle: String, speakerName: String, comment: String)
  extends ProposalEvent {
  def message(): String = Messages("events.ProposalPublicCommentSentByReviewers", speakerName, proposalId, proposalTitle)
}

case class ProposalPrivateCommentSentByComiteeEvent(creator: String, proposalId: String, proposalTitle: String, comment: String)
  extends ProposalEvent {
  def message(): String = Messages("events.ProposalPrivateCommentSentByComitee", proposalId, proposalTitle)
}

case class ProposalPrivateAutomaticCommentSentEvent(creator: String, proposalId: String, proposalTitle: String, comment: String)
  extends ProposalEvent {
  def message(): String = Messages("events.ProposalPrivateAutomaticCommentSent", proposalId, proposalTitle)
}

// Proposal secondary speakers events
case class UpdatedProposalSpeakersListEvent(creator: String, proposalId: String, proposalTitle: String)
  extends ProposalEvent {
  def message(): String = Messages("events.UpdatedProposalSpeakersList", proposalId, proposalTitle)
}

case class AddedSecondarySpeakerToProposalEvent(creator: String, proposalId: String, proposalTitle: String, newSecondarySpeakerName: String)
  extends ProposalEvent {
  def message(): String = Messages("events.AddedSecondarySpeakerToProposal", proposalId, proposalTitle, newSecondarySpeakerName)
}

case class RemovedSecondarySpeakerFromProposalEvent(creator: String, proposalId: String, proposalTitle: String, oldSecondarySpeakerName: String)
  extends ProposalEvent {
  def message(): String = Messages("events.RemovedSecondarySpeakerFromProposal", proposalId, proposalTitle, oldSecondarySpeakerName)
}

case class ReplacedProposalSecondarySpeakerEvent(creator: String, proposalId: String, proposalTitle: String, oldSecondarySpeakerName: String, newSecondarySpeakerName: String)
  extends ProposalEvent {
  def message(): String = Messages("events.ReplacedProposalSecondarySpeaker", proposalId, proposalTitle, oldSecondarySpeakerName, newSecondarySpeakerName)
}

// Proposal Approval/Rejection process events
case class SentProposalApprovedEvent(creator: String, proposalId: String)
  extends ProposalEvent {
  def message(): String = Messages("events.SentProposalApproved", proposalId)
}

case class SentProposalRefusedEvent(creator: String, proposalId: String)
  extends ProposalEvent {
  def message(): String = Messages("events.SentProposalRefused", proposalId)
}

case class TalkRemovedFromRefuseListEvent(creator: String, proposalId: String, proposalTitle: String, talkTypeId: String, trackId: String)
  extends ProposalEvent {
  def message(): String = Messages("events.TalkRemovedFromRefuseList", Messages(talkTypeId), proposalTitle, Messages(trackId))
}

case class TalkRemovedFromApprovedListEvent(creator: String, proposalId: String, proposalTitle: String, talkTypeId: String, trackId: String)
  extends ProposalEvent {
  def message(): String = Messages("events.TalkRemovedFromApprovedList", Messages(talkTypeId), proposalTitle, Messages(trackId))
}

case class TalkRefusedEvent(creator: String, proposalId: String, proposalTitle: String, talkTypeId: String, trackId: String)
  extends ProposalEvent {
  def message(): String = Messages("events.TalkRefused", Messages(talkTypeId), proposalTitle, Messages(trackId))
}

case class RefuseProposalEvent(creator: String, proposalId: String, proposalTitle: String, talkTypeId: String, trackId: String)
  extends ProposalEvent {
  def message(): String = Messages("events.RefuseProposal", Messages(talkTypeId), proposalTitle, Messages(trackId))
}

case class ApprovedProposalEvent(creator: String, proposalId: String, proposalTitle: String, talkTypeId: String, trackId: String)
  extends ProposalEvent {
  def message(): String = Messages("events.ApprovedProposal", Messages(talkTypeId), proposalTitle, Messages(trackId))
}

// Misc events
case class WishlistItemStatusUpdateEvent(creator: String, requestId: String, statusCode: String)
  extends Event {
  def message(): String = Messages("events.WishlistItemStatusUpdate", statusCode)

  override def objRef(): Option[String] = Some(requestId)

  override def linksFor(webuser: Webuser): Seq[EventLink] = Seq(WishlistLink(requestId))
}


object Event {
  private val EVENTS_REDIS_KEY = "Events:V3"

  val mapper: ObjectMapper = new ObjectMapper()
    .registerModules(DefaultScalaModule, new JodaModule())
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

  def storeEvent(event: Event): Event = Redis.pool.withClient {
    client =>
      val jsEvent = mapper.writeValueAsString(event)
      val tx = client.multi()
      tx.zadd(EVENTS_REDIS_KEY, event.date.getMillis, jsEvent)
      event.objRef().map { objRef =>
        tx.sadd(s"""${EVENTS_REDIS_KEY}:${objRef}""", jsEvent)
        tx.set(s"""Events:LastUpdated:${objRef}""", new Instant().getMillis.toString)
      }
      tx.exec()
      event
  }

  def loadEvents(items: Int, page: Int): List[Event] = Redis.pool.withClient {
    client =>
      client.zrevrangeWithScores(EVENTS_REDIS_KEY, page * items, (page + 1) * items).map {
        case (json: String, date: Double) =>
          val event = mapper.readValue(json, classOf[Event])
          event
      }
  }

  def totalEvents(): lang.Long = Redis.pool.withClient {
    client =>
      client.zcard(EVENTS_REDIS_KEY)
  }

  def resetEvents(): Set[String] = Redis.pool.withClient {
    client =>
      client.del("Events:V2:") // 2021 !!!
      val allEvents = client.keys("Events:*")
      val tx = client.multi()
      allEvents.foreach { k: String =>
        tx.del(k)
      }
      tx.del("Events")
      tx.exec()
      allEvents
  }

  implicit object mostRecent extends Ordering[DateTime] {
    def compare(o1: DateTime, o2: DateTime): Int = o1.compareTo(o2)
  }

  def loadLatestProposalSubmittedEvent(proposalId: String): Option[Event] = {
    loadEventsForObjRef(proposalId).find {
      case ChangedStateOfProposalEvent(_, _, oldState, newState) if (oldState == "draft" && newState == "submitted") => true
      case _ => false
    }
  }

  def loadEventsForObjRef(objRef: String): List[Event] = Redis.pool.withClient {
    client =>
      client.smembers(s"Events:V3:$objRef").map {
        json: String =>
          mapper.readValue(json, classOf[Event])
      }.toList
  }

  def getMostRecentDateFor(objRef: String): Option[DateTime] = Redis.pool.withClient {
    client =>
      client.get(s"""Events:LastUpdated:${objRef}""").map {
        s =>
          new Instant().withMillis(s.toLong).toDateTime
      }
  }

  def speakerNotified(speaker: Speaker, allApproved: Set[Proposal], allRejected: Set[Proposal], allBackups: Set[Proposal]): Unit = Redis.pool.withClient {
    client =>
      client.sadd("NotifiedSpeakers", speaker.uuid)
      // Pas de backup et rien d'approuvé
      if (allApproved.isEmpty && allBackups.isEmpty && allRejected.nonEmpty) {
        client.sadd("Notified:RefusedSpeakers", speaker.uuid)
      }
      if (allApproved.nonEmpty) {
        client.sadd("Notified:ApprovedSpeakers", speaker.uuid)
      }
      if (allApproved.isEmpty && allBackups.nonEmpty) {
        client.sadd("Notified:BackupSpeakers", speaker.uuid)
      }
  }

  def resetSpeakersNotified(): Long = Redis.pool.withClient {
    client =>
      client.del("NotifiedSpeakers")
      client.del("Notified:RefusedSpeakers")
      client.del("Notified:ApprovedSpeakers")
      client.del("Notified:BackupSpeakers")
  }

}

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

import library.Redis
import org.apache.commons.lang3.RandomStringUtils
import play.api.test.{FakeApplication, PlaySpecification, WithApplication}

/**
  * Tests for archive Proposal
  *
  * @author created by N.Martignole, Innoteria, on 19/08/2014.
  */
class ArchivedProposalSpecs extends PlaySpecification {
  // Use a different Redis Database than the PROD one
  val testRedis = Map("redis.host" -> "localhost", "redis.port" -> "6364", "redis.activeDatabase" -> 1)

  // To avoid Play Cache Exception during tests, check this
  // https://groups.google.com/forum/#!topic/play-framework/PBIfeiwl5rU
  val appWithTestRedis = () => FakeApplication(additionalConfiguration = testRedis)

  "ArchivedProposal" should {
    "prune a DELETED proposal that have never been submitted" in new WithApplication(app = appWithTestRedis()) {

      // WARN : flush the DB, but on Database = 1
      Redis.pool.withClient {
        client =>
          client.select(1)
          client.flushDB()
      }

      // GIVEN
      val uuidTest = "test_user"
      val proposal = Proposal.validateNewProposal(None, "fr", "test proposal deleted", None, Nil,
        ConferenceDescriptor.ConferenceProposalTypes.CONF.id,
        "audience level", "summary", "private message", sponsorTalk = false,
        ConferenceDescriptor.ConferenceTracks.UNKNOWN.id, Option("beginner"),
        userGroup = None)

      val newProposalId = Proposal.save(uuidTest, proposal, ProposalState.DELETED)

      val newProposal = Proposal.findById(newProposalId).get

      // WHEN
      ArchiveProposal.pruneAllDeleted()

      // THEN
      Proposal.allAcceptedForSpeaker("no_main_speaker") mustEqual Nil
      ApprovedProposal.countApproved("all") mustEqual 0
      ApprovedProposal.countApproved(ConferenceDescriptor.ConferenceProposalTypes.CONF.id) mustEqual 0

      Proposal.findById(newProposalId) must beNone
      Proposal.allMyProposals(uuidTest) mustEqual Nil
    }

    "prune a DRAFT proposal that has been submitted then draft" in new WithApplication(app = appWithTestRedis()) {

      // WARN : flush the DB, but on Database = 1
      Redis.pool.withClient {
        client =>
          client.select(1)
          client.flushDB()
      }

      // GIVEN
      val uuidTest = "test_user"
      val proposal = Proposal.validateNewProposal(None, "fr", "test proposal deleted", None, Nil,
        ConferenceDescriptor.ConferenceProposalTypes.CONF.id,
        "audience level", "summary", "private message", sponsorTalk = false,
        ConferenceDescriptor.ConferenceTracks.UNKNOWN.id, Option("beginner"),
        userGroup = None)

      val newProposalId = Proposal.save(uuidTest, proposal, ProposalState.DRAFT)

      Proposal.submit(uuidTest, newProposalId)
      Proposal.draft(uuidTest, newProposalId)

      val newProposal = Proposal.findById(newProposalId).get

      // WHEN
      ArchiveProposal.pruneAllDraft()

      // THEN
      Proposal.allAcceptedForSpeaker("no_main_speaker") mustEqual Nil
      ApprovedProposal.countApproved("all") mustEqual 0
      ApprovedProposal.countApproved(ConferenceDescriptor.ConferenceProposalTypes.CONF.id) mustEqual 0

      Proposal.findById(newProposalId) must beNone
      Proposal.allMyProposals(uuidTest) mustEqual Nil
    }

    "set the ProposalState to archive when we decide to archive a proposal" in new WithApplication(app = appWithTestRedis()) {
      // WARN : flush the DB, but on Database = 1
      Redis.pool.withClient {
        client =>
          client.select(1)
          client.flushDB()
      }

      // GIVEN a submitted proposal
      val uuidTest = "test_user"
      val proposal = Proposal.validateNewProposal(None, "fr", "test proposal submitted", None, Nil,
        ConferenceDescriptor.ConferenceProposalTypes.CONF.id,
        "audience level", "summary", "private message", sponsorTalk = false,
        ConferenceDescriptor.ConferenceTracks.UNKNOWN.id,
        Option("beginner"),
        userGroup = None)

      val proposalId = Proposal.save(uuidTest, proposal, ProposalState.DRAFT)
      Proposal.submit(uuidTest, proposalId)
      val savedProposal = Proposal.findById(proposalId).get

      // WHEN
      ArchiveProposal.archiveAll(savedProposal.talkType.id)

      // THEN
      Proposal.findProposalState(proposalId) mustEqual Some(ProposalState.ARCHIVED)
    }

    "remove an approved Proposal from the list of Approved talk when archived" in new WithApplication(app = appWithTestRedis()) {
      // WARN : flush the DB, but on Database = 1
      Redis.pool.withClient {
        client =>
          client.select(1)
          client.flushDB()
      }

      // GIVEN a submitted proposal
      val uuidTest = "test_user"
      val proposal = Proposal.validateNewProposal(None, "fr", "test proposal accepted", None, Nil,
        ConferenceDescriptor.ConferenceProposalTypes.CONF.id,
        "audience level", "summary", "private message", sponsorTalk = false,
        ConferenceDescriptor.ConferenceTracks.UNKNOWN.id,
        Option("beginner"),
        userGroup = None)

      val newProposalId = Proposal.save(uuidTest, proposal, ProposalState.DRAFT)
      Proposal.submit(uuidTest, newProposalId)
      Proposal.accept(uuidTest, newProposalId)

      val savedProposal = Proposal.findById(newProposalId).get
      ApprovedProposal.approve(savedProposal)

      // WHEN
      ArchiveProposal.archiveAll(savedProposal.talkType.id)

      // THEN
      ApprovedProposal.allApproved() mustEqual Set.empty[Proposal]
      ApprovedProposal.allApprovedByTalkType(ConferenceDescriptor.ConferenceProposalTypes.CONF.id) mustEqual Nil
      ApprovedProposal.countApproved("all") mustEqual 0
      ApprovedProposal.allApprovedProposalIDs() mustEqual Set.empty
      ApprovedProposal.allApprovedTalksForSpeaker(uuidTest).toList mustEqual Nil
    }

    "move comments to attic when a talk is archived" in new WithApplication(app = appWithTestRedis()) {
      // GIVEN
      flushTestDB()
      val speakerUUID = createASpeaker()
      val proposalId = createATestProposalAndSubmitIt(speakerUUID)

      Comment.saveCommentForSpeaker(proposalId, "another_user", "hello")
      Comment.saveInternalComment(proposalId, "another_user", "an internal message")
      val savedProposal = Proposal.findById(proposalId).get

      // WHEN
      ArchiveProposal.archiveAll(savedProposal.talkType.id)

      // THEN
      Comment.countComments(proposalId) mustEqual 0
      Comment.countInternalComments(proposalId) mustEqual 0
    }

    "move votes to attic when a talk is archived" in new WithApplication(app = appWithTestRedis()) {
      // GIVEN
      flushTestDB()
      val speakerUUID = createASpeaker()
      val proposalId = createATestProposalAndSubmitIt(speakerUUID)
      val reviewerUUID = "any-reviewer"

      Review.voteForProposal(proposalId, reviewerUUID, 3)
      Review.computeAndGenerateVotes()
      val savedProposal = Proposal.findById(proposalId).get

      // WHEN
      ArchiveProposal.archiveAll(savedProposal.talkType.id)

      // THEN
      Review.allHistoryOfVotes(proposalId) mustEqual Nil
      Review.allProposalsAndReviews mustEqual Nil
      Review.allProposalsNotReviewed(reviewerUUID) mustEqual Nil
      Review.allProposalsWithNoVotes mustEqual Map.empty[String, Proposal]
      Review.allReviewersAndStats() mustEqual Nil
      Review.allVotes() mustEqual Map.empty
      Review.allVotesFor(proposalId) mustEqual Nil
      Review.allVotesFromUser(reviewerUUID) mustEqual Set.empty
      Review.bestReviewer() mustEqual None
      Review.countAll() mustEqual 0L
      Review.countWithNoVotes() mustEqual 0
      Review.countWithVotes() mustEqual 0
      Review.currentScore(proposalId) mustEqual 0
      Review.lastVoteByUserForOneProposal(reviewerUUID, proposalId) mustEqual None
      Review.mostReviewed() mustEqual None
      Review.totalReviewedByCFPuser() mustEqual Nil
      Review.totalVoteCastFor(proposalId) mustEqual 0
      Review.totalVoteFor(proposalId) mustEqual 0
      Review.worstReviewer() mustEqual None
    }

    "remove the proposal from the sponsor list if the talk was a sponsor one" in new WithApplication(app = appWithTestRedis()) {
      // GIVEN
      flushTestDB()
      val speakerUUID = createASpeaker()
      val proposalId = createATestProposalAndSubmitIt(speakerUUID)
      val reviewerUUID = "any-reviewer"

      Review.voteForProposal(proposalId, reviewerUUID, 3)
      Review.computeAndGenerateVotes()

      val savedProposal = Proposal.findById(proposalId).get
      val updated = savedProposal.copy(sponsorTalk = true)
      Proposal.save(speakerUUID, updated, ProposalState.SUBMITTED)

      val savedProposal2 = Proposal.findById(proposalId).get

      // WHEN
      ArchiveProposal.archiveAll(savedProposal2.talkType.id)

      // THEN
      Proposal.allSponsorsTalk() mustEqual Nil
    }

    "update the list of accepted when a talk is archived" in new WithApplication(app = appWithTestRedis()) {
      // GIVEN
      flushTestDB()
      val speakerUUID = createASpeaker()
      val proposalId = createATestProposalAndSubmitIt(speakerUUID)
      val reviewerUUID = "any-reviewer"

      Review.voteForProposal(proposalId, reviewerUUID, 3)
      Review.computeAndGenerateVotes()
      val savedProposal = Proposal.findById(proposalId).get

      ApprovedProposal.approve(savedProposal)

      Proposal.approve(speakerUUID, proposalId)

      Proposal.accept(speakerUUID, proposalId)

      // WHEN
      ArchiveProposal.archiveAll(savedProposal.talkType.id)

      // THEN
      ApprovedProposal.allAcceptedByTalkType(savedProposal.talkType.id) mustEqual Nil
    }

    "update the stats on the Leaderboard when a talk is archived" in new WithApplication(app = appWithTestRedis()) {
      // GIVEN
      flushTestDB()
      val speakerUUID = createASpeaker()
      val proposalId = createATestProposalAndSubmitIt(speakerUUID)
      val reviewerUUID = "any-reviewer"

      Review.voteForProposal(proposalId, reviewerUUID, 3)
      Review.computeAndGenerateVotes()
      val savedProposal = Proposal.findById(proposalId).get

      ApprovedProposal.approve(savedProposal)
      Proposal.approve(speakerUUID, proposalId)
      Proposal.accept(speakerUUID, proposalId)
      Leaderboard.computeStats()

      // WHEN
      ArchiveProposal.archiveAll(savedProposal.talkType.id)
      Leaderboard.computeStats()

      // THEN
      Leaderboard.totalSpeakers() mustEqual 1L
      Leaderboard.totalProposals() mustEqual 0L
      Leaderboard.totalVotes() mustEqual 0L
      Leaderboard.totalWithVotes() mustEqual 0L
      Leaderboard.totalNoVotes() mustEqual 0L
      Leaderboard.mostReviewed() mustEqual None
      Leaderboard.bestReviewer() mustEqual None
      Leaderboard.worstReviewer() mustEqual None
      Leaderboard.lazyOnes() mustEqual Map.empty

      Leaderboard.totalSubmittedByTrack() mustEqual Map.empty
      Leaderboard.totalSubmittedByType() mustEqual Map.empty
      Leaderboard.totalAcceptedByTrack() mustEqual Map.empty
      Leaderboard.totalAcceptedByType() mustEqual Map.empty

      Leaderboard.totalApprovedSpeakers() mustEqual 0L
      Leaderboard.totalWithTickets() mustEqual 0L
      Leaderboard.totalRefusedSpeakers() mustEqual 0L
    }

  }

  // For testing
  private def flushTestDB() = {
    // WARN : flush the DB, but on Database = 1
    Redis.pool.withClient {
      client =>
        client.select(1)
        client.flushDB()
    }
  }

  private def createATestProposalAndSubmitIt(speakerUUID: String): String = {
    // 3-
    val proposalId = RandomStringUtils.randomAlphabetic(8)
    val someProposal = Proposal.validateNewProposal(
      Some(proposalId),
      "fr",
      "some test Proposal",
      None,
      Nil,
      ConferenceDescriptor.ConferenceProposalTypes.ALL.head.id,
      "audience level",
      "summary",
      "private message",
      sponsorTalk = false,
      ConferenceDescriptor.ConferenceTracks.ALL.head.id,
      Option("beginner"),
      userGroup = None)

    Proposal.save(speakerUUID, someProposal, ProposalState.DRAFT)
    // Submit the proposal
    Proposal.submit(speakerUUID, proposalId)
    proposalId
  }

  private def createASpeaker(): String = {
    val email = RandomStringUtils.randomAlphabetic(10) + "@test.com"
    val webuser = Webuser.createSpeaker(email, "John", "UnitTest")
    Webuser.saveAndValidateWebuser(webuser)
    val uuid = webuser.uuid
    val speaker = Speaker.createSpeaker(uuid, email, "j", "bio", None, Some("Twitter"), None, Some("company"), Some("blog"), "john", "newbie")
    Speaker.save(speaker)
    uuid
  }

}

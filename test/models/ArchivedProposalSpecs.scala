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
import play.api.test.{FakeApplication, PlaySpecification, WithApplication}

/**
 * Tests for archive Proposal
 *
 * @author created by N.Martignole, Innoteria, on 19/08/2014.
 */
class ArchivedProposalSpecs extends PlaySpecification {
  // Use a different Redis Database than the PROD one
  val testRedis = Map("redis.host" -> "localhost", "redis.port" -> "6363", "redis.activeDatabase" -> 1)

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

      val newProposalId = Proposal.save(uuidTest, proposal, ProposalState.DRAFT)
      Proposal.submit(uuidTest, newProposalId)

      // WHEN
      ArchiveProposal.archive(newProposalId)

      // THEN
      Proposal.findProposalState(newProposalId) mustEqual Some(ProposalState.ARCHIVED)
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
      ArchiveProposal.archive(newProposalId)

      // THEN
      ApprovedProposal.allApproved() mustEqual Set.empty[Proposal]
      ApprovedProposal.allApprovedByTalkType(ConferenceDescriptor.ConferenceProposalTypes.CONF.id) mustEqual Nil
      ApprovedProposal.countApproved("all") mustEqual 0
      ApprovedProposal.allAcceptedTalksForSpeaker(uuidTest).toList mustEqual Nil
    }

  }

}

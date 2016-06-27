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
import org.specs2.control.Debug
import play.api.test.{FakeApplication, PlaySpecification, WithApplication}

/**
  * Review test for LUA.
  * Created by nicolas martignole on 10/07/2014.
  */
class ReviewSpecs extends PlaySpecification with Debug {

  // Use a different Redis Database than the PROD one
  val testRedis = Map("redis.host" -> "localhost", "redis.port" -> "6364", "redis.activeDatabase" -> 1)

  // To avoid Play Cache Exception during tests, check this
  // https://groups.google.com/forum/#!topic/play-framework/PBIfeiwl5rU
  val appWithTestRedis = () => FakeApplication(additionalConfiguration = testRedis)


  "Review" should {
    "return one review when a user votes" in new WithApplication(app = appWithTestRedis()) {

      // WARN : flush the DB
      Redis.pool.withClient {
        client =>
          client.flushDB()
      }

      // WHEN
      val proposalId = "TEST"
      val reviewerUUID = "TEST_UUID"
      val vote = 5

      Review.voteForProposal(proposalId, reviewerUUID, vote)

      // THEN
      Review.allHistoryOfVotes(proposalId) mustNotEqual Nil
      Review.allProposalsWithNoVotes must be_==(Map.empty[String, Proposal])

    }

    "should have no more Proposal with no votes" in new WithApplication(app = appWithTestRedis()) {
      // WARN : flush the DB
      Redis.pool.withClient {
        client =>
          client.flushDB()
      }

      // WHEN
      val proposalId = "TEST"
      val reviewerUUID = "TEST_UUID"
      val vote = 5

      Review.voteForProposal(proposalId, reviewerUUID, vote)

      // THEN
      Review.allProposalsWithNoVotes must be_==(Map.empty[String, Proposal])
    }

    "should return one submitted Proposal with no votes" in new WithApplication(app = appWithTestRedis()) {
      // WARN : flush the DB
      Redis.pool.withClient {
        client =>
          client.flushDB()
      }

      // GIVEN
      val proposal = Proposal(id = "TEST", event = "Test", lang = "FR", title = "Demo unit test"
        , mainSpeaker = "123"
        , secondarySpeaker = None
        , otherSpeakers = Nil
        , talkType = ProposalType.UNKNOWN
        , audienceLevel = "test"
        , summary = "Created from test"
        , privateMessage = "Private message"
        , state = ProposalState.SUBMITTED
        , sponsorTalk = false
        , track = Track.UNKNOWN
        , demoLevel = Some("novice")
        , userGroup = None
        , wishlisted = None)


      // WHEN
      Proposal.save("123", proposal, ProposalState.SUBMITTED)

      // THEN
      Review.allProposalsWithNoVotes must haveKey("TEST")
    }

    "should return the Proposal as 'without votes' if we vote then we remove vote on this proposal" in new WithApplication(app = appWithTestRedis()) {
      // WARN : flush the DB
      Redis.pool.withClient {
        client =>
          client.flushDB()
      }

      // GIVEN
      val reviewerUUID = "SUPER_VOTER"
      val proposalId = RandomStringUtils.randomAlphabetic(12)
      val proposal = Proposal(id = proposalId, event = "Test", lang = "FR", title = "Demo unit test"
        , mainSpeaker = "123"
        , secondarySpeaker = None
        , otherSpeakers = Nil
        , talkType = ProposalType.UNKNOWN
        , audienceLevel = "test"
        , summary = "Created from test"
        , privateMessage = "Private message"
        , state = ProposalState.SUBMITTED
        , sponsorTalk = false
        , track = Track.UNKNOWN
        , demoLevel = Some("novice")
        , userGroup = None
        , wishlisted = None)

      Proposal.save("123", proposal, ProposalState.SUBMITTED)

      // WHEN
      Review.voteForProposal(proposalId, reviewerUUID, 0)
      Review.removeVoteForProposal(proposalId, reviewerUUID)

      // THEN
      Review.allProposalsWithNoVotes must haveKey(proposalId)
    }

    "should not return the Proposal from the list of proposals with no votes once we voted for it" in new WithApplication(app = appWithTestRedis()) {
      // WARN : flush the DB
      Redis.pool.withClient {
        client =>
          client.flushDB()
      }

      // GIVEN
      val reviewerUUID = "SUPER_VOTER"
      val proposalId = RandomStringUtils.randomAlphabetic(12)
      val proposal = Proposal(id = proposalId, event = "Test", lang = "FR", title = "Demo unit test"
        , mainSpeaker = "123"
        , secondarySpeaker = None
        , otherSpeakers = Nil
        , talkType = ProposalType.UNKNOWN
        , audienceLevel = "test"
        , summary = "Created from test"
        , privateMessage = "Private message"
        , state = ProposalState.SUBMITTED
        , sponsorTalk = false
        , track = Track.UNKNOWN
        , demoLevel = Some("novice")
        , userGroup = None
        , wishlisted = None)

      Proposal.save("123", proposal, ProposalState.SUBMITTED)

      // WHEN
      Review.voteForProposal(proposalId, reviewerUUID, 3)

      // THEN
      Review.allProposalsWithNoVotes must beEmpty
    }

    "should return the Proposal from the list of proposals with no votes, if all votes have been deleted" in new WithApplication(app = appWithTestRedis()) {
      // WARN : flush the DB
      Redis.pool.withClient {
        client =>
          client.flushDB()
      }

      // GIVEN
      val reviewerUUID = "SUPER_VOTER"
      val reviewerUUID2 = "SUPER_VOTER02"
      val proposalId = RandomStringUtils.randomAlphabetic(12)
      val proposal = Proposal(id = proposalId, event = "Test", lang = "FR", title = "Demo unit test"
        , mainSpeaker = "123"
        , secondarySpeaker = None
        , otherSpeakers = Nil
        , talkType = ProposalType.UNKNOWN
        , audienceLevel = "test"
        , summary = "Created from test"
        , privateMessage = "Private message"
        , state = ProposalState.SUBMITTED
        , sponsorTalk = false
        , track = Track.UNKNOWN
        , demoLevel = Some("novice")
        , userGroup = None
        , wishlisted = None)

      Proposal.save("123", proposal, ProposalState.SUBMITTED)

      // WHEN
      Review.voteForProposal(proposalId, reviewerUUID, 3)
      Review.voteForProposal(proposalId, reviewerUUID2, 3)
      Review.deleteVoteForProposal(proposalId)

      // THEN
      Review.allProposalsWithNoVotes must haveKey(proposalId)
    }

    "should return one Review" in new WithApplication(app = appWithTestRedis()) {
      // WARN : flush the DB
      Redis.pool.withClient {
        client =>
          client.flushDB()
      }

      // GIVEN
      val reviewerUUID = "SUPER_VOTER34"
      val reviewerUUID2 = "ABSTENTION"

      val proposalId = RandomStringUtils.randomAlphabetic(12)

      val proposal = Proposal(id = proposalId, event = "Test", lang = "FR", title = "Demo unit test"
        , mainSpeaker = "123"
        , secondarySpeaker = None
        , otherSpeakers = Nil
        , talkType = ProposalType.UNKNOWN
        , audienceLevel = "test"
        , summary = "Created from test"
        , privateMessage = "Private message"
        , state = ProposalState.SUBMITTED
        , sponsorTalk = false
        , track = Track.UNKNOWN
        , demoLevel = Some("novice")
        , userGroup = None
        , wishlisted = None)

      Proposal.save("123", proposal, ProposalState.SUBMITTED)

      // WHEN
      Review.voteForProposal(proposalId, reviewerUUID, 7)
      Review.voteForProposal(proposalId, reviewerUUID2, 0)

      // THEN
      Review.allHistoryOfVotes(proposalId) must haveLength(2)
      Review.currentScore(proposalId) mustEqual 7
      Review.totalVoteFor(proposalId) mustEqual 2
      Review.totalVoteCastFor(proposalId) mustEqual 1
    }

    "should load the LUA script and compute some Stats" in new WithApplication(app = appWithTestRedis()) {
      // WARN : flush the DB
      Redis.pool.withClient {
        client =>
          client.flushDB()
      }

      // GIVEN
      val reviewerUUID = "SUPER_VOTER 01"
      val reviewerUUID2 = "SUPER_VOTER 02"
      val reviewerUUID3 = "ABSTENTION"

      val author = RandomStringUtils.randomAlphabetic(12)

      val proposalId = RandomStringUtils.randomAlphabetic(12)

      val proposal = Proposal(id = proposalId, event = "Test", lang = "FR", title = "Demo unit test"
        , mainSpeaker = author
        , secondarySpeaker = None
        , otherSpeakers = Nil
        , talkType = ProposalType.UNKNOWN
        , audienceLevel = "test"
        , summary = "Created from test"
        , privateMessage = "Private message"
        , state = ProposalState.SUBMITTED
        , sponsorTalk = false
        , track = Track.UNKNOWN
        , demoLevel = Some("novice")
        , userGroup = None
        , wishlisted = None)

      Proposal.save(author, proposal, ProposalState.SUBMITTED)
      Review.voteForProposal(proposalId, reviewerUUID, 10)
      Review.voteForProposal(proposalId, reviewerUUID2, 5)
      Review.voteForProposal(proposalId, reviewerUUID3, 0)

      // WHEN
      Review.computeAndGenerateVotes()

      // THEN
      Review.allVotes() must haveSize(1)

      val (checkedProposal, scoreAndTotalVotes) = Review.allVotes().head

      checkedProposal mustEqual proposalId

      scoreAndTotalVotes._1.s mustEqual 15
      scoreAndTotalVotes._2.i mustEqual 2
      scoreAndTotalVotes._3.i mustEqual 1
      scoreAndTotalVotes._4.n mustEqual 7.5
      scoreAndTotalVotes._5.d mustEqual 3.536

    }

    "should load the LUA script and not crash if a proposal has no votes" in new WithApplication(app = appWithTestRedis()) {
      // WARN : flush the DB
      Redis.pool.withClient {
        client =>
          client.flushDB()
      }

      // GIVEN
      val author = RandomStringUtils.randomAlphabetic(12)
      val proposalId = RandomStringUtils.randomAlphabetic(12)

      val proposal = Proposal(id = proposalId, event = "Test", lang = "FR", title = "Proposal with no vote"
        , mainSpeaker = author
        , secondarySpeaker = None
        , otherSpeakers = Nil
        , talkType = ProposalType.UNKNOWN
        , audienceLevel = "test"
        , summary = "Created from test"
        , privateMessage = "Private message"
        , state = ProposalState.SUBMITTED
        , sponsorTalk = false
        , track = Track.UNKNOWN
        , demoLevel = Some("novice")
        , userGroup = None
        , wishlisted = None)

      Proposal.save(author, proposal, ProposalState.SUBMITTED)


      // WHEN
      Review.computeAndGenerateVotes()

      // THEN
      Review.allVotes() must haveSize(0)
    }

    "should load the LUA script and compute correctly if proposal has only ABST votes" in new WithApplication(app = appWithTestRedis()) {
      // WARN : flush the DB
      Redis.pool.withClient {
        client =>
          client.flushDB()
      }

      // GIVEN
      val reviewerUUID = "SUPER_VOTER 01"
      val reviewerUUID2 = "SUPER_VOTER 02"

      val author = RandomStringUtils.randomAlphabetic(12)

      val proposalId = RandomStringUtils.randomAlphabetic(12)

      val proposal = Proposal(id = proposalId, event = "Test", lang = "FR", title = "Demo unit test"
        , mainSpeaker = author
        , secondarySpeaker = None
        , otherSpeakers = Nil
        , talkType = ProposalType.UNKNOWN
        , audienceLevel = "test"
        , summary = "Created from test"
        , privateMessage = "Private message"
        , state = ProposalState.SUBMITTED
        , sponsorTalk = false
        , track = Track.UNKNOWN
        , demoLevel = Some("novice")
        , userGroup = None
        , wishlisted = None)

      Proposal.save(author, proposal, ProposalState.SUBMITTED)
      // Both votes 0 for this talk
      Review.voteForProposal(proposalId, reviewerUUID, 0)
      Review.voteForProposal(proposalId, reviewerUUID2, 0)

      // WHEN
      Review.computeAndGenerateVotes()

      // THEN
      Review.allVotes() must haveSize(1)

      val (checkedProposal, scoreAndTotalVotes) = Review.allVotes().head

      checkedProposal mustEqual proposalId

      scoreAndTotalVotes._1.s mustEqual 0
      scoreAndTotalVotes._2.i mustEqual 0
      scoreAndTotalVotes._3.i mustEqual 2
      scoreAndTotalVotes._4.n mustEqual 0
      scoreAndTotalVotes._5.d mustEqual 0

    }

  }
}
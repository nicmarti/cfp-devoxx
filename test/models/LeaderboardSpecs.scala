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
import play.api.test.{WithApplication, FakeApplication, PlaySpecification}
import org.apache.commons.lang3.RandomStringUtils
/**
 * Leader board tests.
 * Created by nicolas martignole on 10/07/2014.
 */
class LeaderboardSpecs extends PlaySpecification {

  // Use a different Redis Database than the PROD one
  val testRedis = Map("redis.host" -> "localhost", "redis.port" -> "6364", "redis.activeDatabase" -> 1)

  // To avoid Play Cache Exception during tests, check this
  // https://groups.google.com/forum/#!topic/play-framework/PBIfeiwl5rU
  val appWithTestRedis = () => FakeApplication(additionalConfiguration = testRedis)

  "Leaderboard" should {
    "return the correct total number of speakers" in new WithApplication(app = appWithTestRedis()) {

      // WARN : flush the DB
      Redis.pool.withClient {
        client =>
          client.select(1)
          client.flushDB()
      }

      // WHEN
      val speaker = Speaker("uuid", "email@test.fr", Some("Nic"), "bio", None, Some("@nmartignole"), None, 
        Some("Innoteria"), Some("http://www.touilleur-express.fr"), Some("Nicolas"), Some("Developpeur"))
      Speaker.save(speaker)

      Leaderboard.computeStats()

      // THEN
      Leaderboard.totalSpeakers() mustEqual 1
    }

    "return the number of proposals" in new WithApplication(app = appWithTestRedis()) {

      // WARN : flush the DB
      Redis.pool.withClient {
        client =>
          client.flushDB()
      }

      // WHEN
      val reviewerUUID = "SUPER_VOTER"
      val proposalId= RandomStringUtils.randomAlphabetic(12)
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
        , demoLevel = Some("l1")
        , userGroup = Some(false)
        , wishlisted = None)

      Proposal.save("123", proposal, ProposalState.SUBMITTED)

      Leaderboard.computeStats()

      // THEN
      Leaderboard.totalProposals() mustEqual 1
    }

     "return the number of votes" in new WithApplication(app = appWithTestRedis()) {

      // WARN : flush the DB
      Redis.pool.withClient {
        client =>
          client.flushDB()
      }

      // WHEN
      val reviewerUUID = "SUPER_VOTER"
      val proposalId= RandomStringUtils.randomAlphabetic(12)
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
        , demoLevel = Some("l1")
        , userGroup = Some(false)
        , wishlisted = None)

      Proposal.save("123", proposal, ProposalState.SUBMITTED)

      Review.voteForProposal(proposalId, reviewerUUID, 2)

      Leaderboard.computeStats()

      // THEN
      Leaderboard.totalVotes() mustEqual 1
    }

     "updates the total number of votes when a proposal is deleted" in new WithApplication(app = appWithTestRedis()) {

      // WARN : flush the DB
      Redis.pool.withClient {
        client =>
          client.flushDB()
      }

      // WHEN
      val reviewerUUID = "SUPER_VOTER"
      val proposalId= RandomStringUtils.randomAlphabetic(12)
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
        , demoLevel = Some("l1")
        , userGroup = Some(false)
        , wishlisted = None)

      Proposal.save("123", proposal, ProposalState.SUBMITTED)

      Review.voteForProposal(proposalId, reviewerUUID, 2)

      Leaderboard.computeStats()

      Leaderboard.totalVotes() mustEqual 1
      Leaderboard.totalProposals() mustEqual 1
      Leaderboard.totalWithVotes() mustEqual 1
      Leaderboard.totalNoVotes() mustEqual 0


      Proposal.delete("test",proposalId)
      Leaderboard.computeStats()

      // THEN
      Leaderboard.totalVotes() mustEqual 0
      Leaderboard.totalProposals() mustEqual 0
      Leaderboard.totalWithVotes() mustEqual 0
      Leaderboard.totalNoVotes() mustEqual 0
     }

     "updates the total withVotes or noVotes when we vote" in new WithApplication(app = appWithTestRedis()) {

      // WARN : flush the DB
      Redis.pool.withClient {
        client =>
          client.flushDB()
      }

      // WHEN
      val reviewerUUID = "SUPER_VOTER"
      val proposalId= RandomStringUtils.randomAlphabetic(12)
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
        , demoLevel = Some("l1")
        , userGroup = Some(false)
        , wishlisted = None)

      Proposal.save("123", proposal, ProposalState.SUBMITTED)

      Leaderboard.computeStats()

      Leaderboard.totalVotes() mustEqual 0
      Leaderboard.totalProposals() mustEqual 1
      Leaderboard.totalWithVotes() mustEqual 0
      Leaderboard.totalNoVotes() mustEqual 1

      Review.voteForProposal(proposalId, reviewerUUID, 2)
      Leaderboard.computeStats()

      Leaderboard.totalVotes() mustEqual 1
      Leaderboard.totalProposals() mustEqual 1
      Leaderboard.totalWithVotes() mustEqual 1
      Leaderboard.totalNoVotes() mustEqual 0



      Proposal.delete("test",proposalId)
      Leaderboard.computeStats()

      // THEN
      Leaderboard.totalVotes() mustEqual 0
      Leaderboard.totalProposals() mustEqual 0
      Leaderboard.totalWithVotes() mustEqual 0
      Leaderboard.totalNoVotes() mustEqual 0
     }


    "returns the correct worst reviewer" in new WithApplication(app = appWithTestRedis()) {

      // WARN : flush the DB
      Redis.pool.withClient {
        client =>
          client.flushDB()
      }

      // WHEN
      val reviewerUUID = "SUPER_VOTER"
      val proposalId= RandomStringUtils.randomAlphabetic(12)
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
        , demoLevel = Some("l1")
        , userGroup = Some(false)
        , wishlisted = None)

      Proposal.save("123", proposal, ProposalState.SUBMITTED)

      val reviewer01 = Webuser(reviewerUUID, "email@test.fr","nic","test","pass","cfp")
      Webuser.saveAndValidateWebuser(reviewer01)

      Review.voteForProposal(proposalId, reviewerUUID, 5)

      Leaderboard.computeStats()

      // THEN
      Leaderboard.bestReviewer() must beSome[(String,String)]
      Leaderboard.bestReviewer().head._1 must beEqualTo(reviewerUUID)
      Leaderboard.bestReviewer().head._2 must beEqualTo("1")
      Leaderboard.lazyOnes() must haveSize(1)
    }

    "returns the correct best worst reviewer with more than one CFP user" in new WithApplication(app = appWithTestRedis()) {

      // WARN : flush the DB
      Redis.pool.withClient {
        client =>
          client.flushDB()
      }

      // WHEN
      val proposalId= RandomStringUtils.randomAlphabetic(12)
      val proposal = Proposal(id = proposalId, event = "Test", lang = "FR", title = "Demo unit test"
        , mainSpeaker = "mainSpeakerUUID"
        , secondarySpeaker = None
        , otherSpeakers = Nil
        , talkType = ProposalType.UNKNOWN
        , audienceLevel = "test"
        , summary = "Created from test"
        , privateMessage = "Private message"
        , state = ProposalState.SUBMITTED
        , sponsorTalk = false
        , track = Track.UNKNOWN
        , demoLevel = Some("l1")
        , userGroup = Some(false)
        , wishlisted = None)
      Proposal.save("mainSpeakerUUID", proposal, ProposalState.SUBMITTED)

            val proposalId2= RandomStringUtils.randomAlphabetic(12)
      val proposal2 = Proposal(id = proposalId2, event = "Test", lang = "FR", title = "Demo unit test 2"
        , mainSpeaker = "mainSpeakerUUID2"
        , secondarySpeaker = None
        , otherSpeakers = Nil
        , talkType = ProposalType.UNKNOWN
        , audienceLevel = "test"
        , summary = "Created from test"
        , privateMessage = "Private message"
        , state = ProposalState.SUBMITTED
        , sponsorTalk = false
        , track = Track.UNKNOWN
        , demoLevel = Some("l1")
        , userGroup = Some(false)
        , wishlisted = None)
      Proposal.save("mainSpeakerUUID2", proposal2, ProposalState.SUBMITTED)

      val reviewerUUID = "SUPER_VOTER"
      val reviewer01 = Webuser(reviewerUUID, "email1@test.fr","nic1","test1","pass1","cfp")
      Webuser.saveAndValidateWebuser(reviewer01)
      Review.voteForProposal(proposalId, reviewerUUID, 5)
      Review.voteForProposal(proposalId2, reviewerUUID, 8)

      val reviewerUUID2 = "SUPER_LAZY"
      val reviewer02 = Webuser(reviewerUUID2, "email2@test.fr","nic2","test2","pass2","cfp")
      Webuser.saveAndValidateWebuser(reviewer02)

      val reviewerUUID3 = "ONE_VOTE"
      val reviewer03= Webuser(reviewerUUID3, "email3@test.fr","nic3","test3","pass3","cfp")
      Webuser.saveAndValidateWebuser(reviewer03)
      Review.voteForProposal(proposalId, reviewerUUID3, 9)

      Leaderboard.computeStats()

      // THEN
      Leaderboard.bestReviewer() must beSome[(String,String)]
      Leaderboard.bestReviewer().head._1 must beEqualTo(reviewerUUID)
      Leaderboard.bestReviewer().head._2 must beEqualTo("2")

      Leaderboard.worstReviewer() must beSome[(String,String)]
      Leaderboard.worstReviewer().head._1 must beEqualTo(reviewerUUID3)
      Leaderboard.worstReviewer().head._2 must beEqualTo("1")

      Leaderboard.lazyOnes() must haveSize(2)
      Leaderboard.lazyOnes() must havePair((reviewerUUID2,"0"))
      Leaderboard.lazyOnes() must havePair((reviewerUUID3,"1"))
    }


    "returns the correct total submitted by categories" in new WithApplication(app = appWithTestRedis()) {

      // WARN : flush the DB
      Redis.pool.withClient {
        client =>
          client.flushDB()
      }

      // WHEN
      val proposalId= RandomStringUtils.randomAlphabetic(12)
      val proposal = Proposal(id = proposalId, event = "Test", lang = "FR", title = "Demo unit test"
        , mainSpeaker = "mainSpeakerUUID"
        , secondarySpeaker = None
        , otherSpeakers = Nil
        , talkType = ProposalType.UNKNOWN
        , audienceLevel = "test"
        , summary = "Created from test"
        , privateMessage = "Private message"
        , state = ProposalState.SUBMITTED
        , sponsorTalk = false
        , track = Track.UNKNOWN
        , demoLevel = Some("l1")
        , userGroup = Some(false)
        , wishlisted = None)
      Proposal.save("mainSpeakerUUID", proposal, ProposalState.SUBMITTED)

      val proposalId2= RandomStringUtils.randomAlphabetic(12)
      val proposal2 = Proposal(id = proposalId2, event = "Test", lang = "FR", title = "Demo unit test 2"
        , mainSpeaker = "mainSpeakerUUID2"
        , secondarySpeaker = None
        , otherSpeakers = Nil
        , talkType = ProposalType.UNKNOWN
        , audienceLevel = "test"
        , summary = "Created from test"
        , privateMessage = "Private message"
        , state = ProposalState.SUBMITTED
        , sponsorTalk = false
        , track = Track.all.filterNot(_.id==Track.UNKNOWN.id).head
        , demoLevel = Some("l1")
        , userGroup = Some(false)
        , wishlisted = None)
      Proposal.save("mainSpeakerUUID2", proposal2, ProposalState.SUBMITTED)

      Leaderboard.computeStats()

      // THEN
      Leaderboard.totalSubmittedByTrack() must haveSize(2)
    }

    "returns the correct total submitted by type" in new WithApplication(app = appWithTestRedis()) {

      // WARN : flush the DB
      Redis.pool.withClient {
        client =>
          client.flushDB()
      }

      // WHEN
      val proposalId= RandomStringUtils.randomAlphabetic(12)
      val proposal = Proposal(id = proposalId, event = "Test", lang = "FR", title = "Demo unit test"
        , mainSpeaker = "mainSpeakerUUID"
        , secondarySpeaker = None
        , otherSpeakers = Nil
        , talkType = ProposalType.UNKNOWN
        , audienceLevel = "test"
        , summary = "Created from test"
        , privateMessage = "Private message"
        , state = ProposalState.SUBMITTED
        , sponsorTalk = false
        , track = Track.UNKNOWN
        , demoLevel = Some("l1")
        , userGroup = Some(false)
        , wishlisted = None)
      Proposal.save("mainSpeakerUUID", proposal, ProposalState.SUBMITTED)

      val proposalId2= RandomStringUtils.randomAlphabetic(12)
      val proposal2 = Proposal(id = proposalId2, event = "Test", lang = "FR", title = "Demo unit test 2"
        , mainSpeaker = "mainSpeakerUUID2"
        , secondarySpeaker = None
        , otherSpeakers = Nil
        , talkType = ProposalType.all.filterNot(_.id==ProposalType.UNKNOWN.id).head
        , audienceLevel = "test"
        , summary = "Created from test"
        , privateMessage = "Private message"
        , state = ProposalState.SUBMITTED
        , sponsorTalk = false
        , track = Track.all.filterNot(_.id==Track.UNKNOWN.id).head
        , demoLevel = Some("l1")
        , userGroup = Some(false)
        , wishlisted = None)
      Proposal.save("mainSpeakerUUID2", proposal2, ProposalState.SUBMITTED)

      Leaderboard.computeStats()

      // THEN
      Leaderboard.totalSubmittedByType() must haveSize(2)
    }
  }

}

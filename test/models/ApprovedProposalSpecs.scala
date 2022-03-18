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
  * Tests for approve/refuse service.
  *
  * @author created by N.Martignole, Innoteria, on 19/08/2014.
  */
class ApprovedProposalSpecs extends PlaySpecification {
  // Use a different Redis Database than the PROD one
  val testRedis = Map("redis.host" -> "127.0.0.1", "redis.port" -> "6464", "redis.activeDatabase" -> 1)

  // To avoid Play Cache Exception during tests, check this
  // https://groups.google.com/forum/#!topic/play-framework/PBIfeiwl5rU
  val appWithTestRedis = () => FakeApplication(additionalConfiguration = testRedis)

  "ApprovedProposal" should {
    "return correct total with countApproved for a BOF" in new WithApplication(app = appWithTestRedis()) {

      // WARN : flush the DB, but on Database = 1
      Redis.pool.withClient {
        client =>
          client.flushDB()
      }

      // GIVEN
      val proposal = Proposal.validateNewProposal(
        None,
        lang = "fr",
        title = "test proposal",
        secondarySpeaker = None,
        otherSpeakers = Nil,
        talkType = ConferenceDescriptor.ConferenceProposalTypes.BOF.id,
        audienceLevel = "audience level",
        summary = "summary",
        privateMessage = "private message",
        sponsorTalk = false,
        track = ConferenceDescriptor.ConferenceTracks.JAVA.id,
        demoLevel = Some("beginner"),
        userGroup = Some(true),
        videoLink = Some("http://www.youtube.fr"),
        tags = None)

      Proposal.save("test", proposal, ProposalState.SUBMITTED)

      // WHEN
      ApprovedProposal.approve(proposal)

      // THEN
      ApprovedProposal.countApproved("all") mustEqual 1
      ApprovedProposal.countApproved(ConferenceDescriptor.ConferenceProposalTypes.BOF.id) mustEqual 1
      ApprovedProposal.countApproved(ConferenceDescriptor.ConferenceProposalTypes.CONF.id) mustEqual 0
    }

    "return the updated total when proposal type is updated from BOF to CONF, fix bug 159" in new WithApplication(app = appWithTestRedis()) {

      // WARN : flush the DB, but on Database = 1
      Redis.pool.withClient {
        client =>
          client.flushDB()
      }

      // GIVEN
      val proposal = Proposal.validateNewProposal(None, "fr", "test proposal", None, Nil,
        ConferenceDescriptor.ConferenceProposalTypes.BOF.id,
        "audience level", "summary", "private message", sponsorTalk = false,
        ConferenceDescriptor.ConferenceTracks.JAVA.id, Some("beginner"),
        userGroup = None,
        videoLink = Some("http://www.youtube.fr"),
        tags = None)

      Proposal.save("test", proposal, ProposalState.SUBMITTED)

      // WHEN
      ApprovedProposal.approve(proposal)
      val proposal2 = proposal.copy(talkType = ConferenceDescriptor.ConferenceProposalTypes.CONF)
      Proposal.save("test", proposal2, ProposalState.SUBMITTED)

      // THEN
      ApprovedProposal.countApproved("all") mustEqual 1
      ApprovedProposal.countApproved(ConferenceDescriptor.ConferenceProposalTypes.BOF.id) mustEqual 0
      ApprovedProposal.countApproved(ConferenceDescriptor.ConferenceProposalTypes.CONF.id) mustEqual 1
    }

    "return the updated total when a refused proposal type is updated from BOF to CONF" in new WithApplication(app = appWithTestRedis()) {

      // WARN : flush the DB, but on Database = 1
      Redis.pool.withClient {
        client =>
          client.flushDB()
      }

      // GIVEN
      val proposal = Proposal.validateNewProposal(None, "fr", "test proposal 2", None, Nil,
        ConferenceDescriptor.ConferenceProposalTypes.BOF.id,
        "audience level", "summary 2", "private message 2", sponsorTalk = false,
        ConferenceDescriptor.ConferenceTracks.JAVA.id, Some("beginner"),
        userGroup = None,
        videoLink = Some("http://www.youtube.fr"),
        tags = None)

      Proposal.save("test", proposal, ProposalState.SUBMITTED)

      // WHEN
      ApprovedProposal.refuse(proposal)
      val proposal2 = proposal.copy(talkType = ConferenceDescriptor.ConferenceProposalTypes.LAB)
      Proposal.save("test", proposal2, ProposalState.SUBMITTED)

      // THEN
      ApprovedProposal.countRefused("all") mustEqual 1
      ApprovedProposal.countRefused(ConferenceDescriptor.ConferenceProposalTypes.BOF.id) mustEqual 0
      ApprovedProposal.countRefused(ConferenceDescriptor.ConferenceProposalTypes.LAB.id) mustEqual 1
    }

    "decrease total approved when an approved proposal is deleted" in new WithApplication(app = appWithTestRedis()) {

      // WARN : flush the DB, but on Database = 1
      Redis.pool.withClient {
        client =>
          client.flushDB()
      }

      // GIVEN
      val proposal = Proposal.validateNewProposal(None, "fr", "test proposal 2", None, Nil,
        ConferenceDescriptor.ConferenceProposalTypes.BOF.id,
        "audience level", "summary 2", "private message 2", sponsorTalk = false,
        ConferenceDescriptor.ConferenceTracks.JAVA.id, Some("beginner"),
        userGroup = None,
        videoLink = Some("http://www.youtube.fr"),
        tags = None)

      Proposal.save("test", proposal, ProposalState.SUBMITTED)

      // WHEN
      ApprovedProposal.approve(proposal)

      Proposal.delete("test2", proposal.id)

      // THEN
      ApprovedProposal.countApproved("all") mustEqual 0
      ApprovedProposal.countApproved(ConferenceDescriptor.ConferenceProposalTypes.BOF.id) mustEqual 0
    }

    "decrease total refused when a refused proposal is deleted" in new WithApplication(app = appWithTestRedis()) {

      // WARN : flush the DB, but on Database = 1
      Redis.pool.withClient {
        client =>
          client.flushDB()
      }

      // GIVEN
      val proposal = Proposal.validateNewProposal(None, "fr", "test proposal 2", None, Nil,
        ConferenceDescriptor.ConferenceProposalTypes.CONF.id,
        "audience level", "summary 2", "private message 2", sponsorTalk = false,
        ConferenceDescriptor.ConferenceTracks.JAVA.id, Some("beginner"),
        userGroup = None,
        videoLink = Some("http://www.youtube.fr"),
        tags = None)

      Proposal.save("test", proposal, ProposalState.SUBMITTED)

      // WHEN
      ApprovedProposal.refuse(proposal)

      Proposal.delete("test2", proposal.id)

      // THEN
      ApprovedProposal.countRefused("all") mustEqual 0
      ApprovedProposal.countRefused(ConferenceDescriptor.ConferenceProposalTypes.CONF.id) mustEqual 0
    }

    "return the main speaker as part of Approved speaker" in new WithApplication(app = appWithTestRedis()) {

      // WARN : flush the DB, but on Database = 1
      Redis.pool.withClient {
        client =>
          client.flushDB()
      }

      // GIVEN
      val proposal = Proposal.validateNewProposal(None, "fr", "test proposal 2",
        None,
        Nil,
        ConferenceDescriptor.ConferenceProposalTypes.BOF.id,
        "audience level", "summary 2", "private message 2", sponsorTalk = false,
        ConferenceDescriptor.ConferenceTracks.JAVA.id, Some("beginner"),
        userGroup = None,
        videoLink = Some("http://www.youtube.fr"),
        tags = None)

      Proposal.save("speaker1", proposal, ProposalState.SUBMITTED)

      val correctProposal = Proposal.findById(proposal.id).get // Cause we need the correct speakerId

      // WHEN
      ApprovedProposal.approve(correctProposal)

      // THEN
      ApprovedProposal.allApprovedTalksForSpeaker("speaker1").toList mustEqual List(correctProposal)
    }

    "return the secondary speaker as part of Approved speaker" in new WithApplication(app = appWithTestRedis()) {

      // WARN : flush the DB, but on Database = 1
      Redis.pool.withClient {
        client =>
          client.flushDB()
      }

      // GIVEN
      val proposal = Proposal.validateNewProposal(None, "fr", "test proposal 2",
        Some("secondarySpeaker"),
        Nil,
        ConferenceDescriptor.ConferenceProposalTypes.BOF.id,
        "audience level", "summary 2", "private message 2", sponsorTalk = false,
        ConferenceDescriptor.ConferenceTracks.JAVA.id, Some("beginner"),
        userGroup = None,
        videoLink = Some("http://www.youtube.fr"),
        tags = None)

      Proposal.save("speaker1", proposal, ProposalState.SUBMITTED)

      val correctProposal = Proposal.findById(proposal.id).get // Cause we need the correct speakerId

      // WHEN
      ApprovedProposal.approve(correctProposal)

      // THEN
      ApprovedProposal.allApprovedTalksForSpeaker("secondarySpeaker").toList mustEqual List(correctProposal)
    }

    "return any other speaker as part of Approved speaker" in new WithApplication(app = appWithTestRedis()) {

      // WARN : flush the DB, but on Database = 1
      Redis.pool.withClient {
        client =>
          client.flushDB()
      }

      // GIVEN
      val proposal = Proposal.validateNewProposal(None, "fr", "test proposal 2",
        None,
        List("someOtherSpeaker"),
        ConferenceDescriptor.ConferenceProposalTypes.BOF.id,
        "audience level", "summary 2", "private message 2", sponsorTalk = false,
        ConferenceDescriptor.ConferenceTracks.JAVA.id, Some("beginner"),
        userGroup = None,
        videoLink = Some("http://www.youtube.fr"),
        tags = None)

      Proposal.save("speaker1", proposal, ProposalState.SUBMITTED)

      val correctProposal = Proposal.findById(proposal.id).get // Cause we need the correct speakerId

      // WHEN
      ApprovedProposal.approve(correctProposal)

      // THEN
      ApprovedProposal.allApprovedTalksForSpeaker("someOtherSpeaker").toList mustEqual List(correctProposal)
    }

    "update the list of Accepted speakers when we change the mainSpeaker on a proposal" in new WithApplication(app = appWithTestRedis()) {
      // WARN : flush the DB, but on Database = 1
      Redis.pool.withClient {
        client =>
          client.flushDB()
      }

      // GIVEN
      val proposal = Proposal.validateNewProposal(None, "fr", "test proposal 2",
        None,
        Nil,
        ConferenceDescriptor.ConferenceProposalTypes.BOF.id,
        "audience level", "summary 2", "private message 2", sponsorTalk = false,
        ConferenceDescriptor.ConferenceTracks.JAVA.id, Some("beginner"),
        userGroup = None,
        videoLink = Some("http://www.youtube.fr"),
        tags = None)

      Proposal.save("speaker1", proposal, ProposalState.SUBMITTED)

      val correctProposal = Proposal.findById(proposal.id).get // Cause we need the correct speakerId

      // WHEN
      ApprovedProposal.approve(correctProposal)

      Proposal.save("newSpeaker", correctProposal, ProposalState.SUBMITTED)

      // THEN
      ApprovedProposal.allApprovedTalksForSpeaker("speaker1").toList must be(Nil)
      ApprovedProposal.allApprovedTalksForSpeaker("newSpeaker").toList mustEqual List(correctProposal.copy(mainSpeaker = "newSpeaker"))
    }

    "update the list of Accepted speakers when we change the secondarySpeaker on a proposal" in new WithApplication(app = appWithTestRedis()) {
      // WARN : flush the DB, but on Database = 1
      Redis.pool.withClient {
        client =>
          client.flushDB()
      }

      // GIVEN
      val proposal = Proposal.validateNewProposal(None, "fr", "test proposal 2",
        Some("secondarySpeaker"),
        Nil,
        ConferenceDescriptor.ConferenceProposalTypes.BOF.id,
        "audience level", "summary 2", "private message 2", sponsorTalk = false,
        ConferenceDescriptor.ConferenceTracks.JAVA.id, Some("beginner"),
        userGroup = None,
        videoLink = Some("http://www.youtube.fr"),
        tags = None)

      Proposal.save("speaker1", proposal, ProposalState.SUBMITTED)

      val correctProposal = Proposal.findById(proposal.id).get // Cause we need the correct speakerId

      // WHEN
      ApprovedProposal.approve(correctProposal)

      Proposal.save("newSpeaker", correctProposal.copy(secondarySpeaker = Some("newSecSpeaker")), ProposalState.SUBMITTED)

      // THEN
      ApprovedProposal.allApprovedTalksForSpeaker("secondarySpeaker").toList must be(Nil)
      ApprovedProposal.allApprovedTalksForSpeaker("newSecSpeaker").toList mustEqual List(correctProposal.copy(mainSpeaker = "newSpeaker", secondarySpeaker = Some("newSecSpeaker")))
    }

    "update the list of Accepted speakers when we change the otherSpeakers on a proposal" in new WithApplication(app = appWithTestRedis()) {
      // WARN : flush the DB, but on Database = 1
      Redis.pool.withClient {
        client =>
          client.flushDB()
      }

      // GIVEN
      val proposal = Proposal.validateNewProposal(None, "fr", "test proposal 2",
        Some("secondarySpeaker"),
        List("firstThirdSpeaker"),
        ConferenceDescriptor.ConferenceProposalTypes.BOF.id,
        "audience level", "summary 2", "private message 2", sponsorTalk = false,
        ConferenceDescriptor.ConferenceTracks.JAVA.id, Some("beginner"),
        userGroup = None,
        videoLink = Some("http://www.youtube.fr"),
        tags = None)

      Proposal.save("speaker1", proposal, ProposalState.SUBMITTED)

      val correctProposal = Proposal.findById(proposal.id).get // Cause we need the correct speakerId

      // WHEN
      ApprovedProposal.approve(correctProposal)

      Proposal.save("newSpeaker", correctProposal.copy(otherSpeakers = List("newThirdSpeaker")), ProposalState.SUBMITTED)

      // THEN
      ApprovedProposal.allApprovedTalksForSpeaker("firstThirdSpeaker").toList must be(Nil)
      ApprovedProposal.allApprovedTalksForSpeaker("newThirdSpeaker").toList mustEqual List(correctProposal.copy(mainSpeaker = "newSpeaker", otherSpeakers = List("newThirdSpeaker")))
    }
  }

  "ApprovedProposal allSpeakersWithAcceptedTalksAndNoBadge" should {
    //    "return the list of Speakers without a speaker badge" in new WithApplication(app = appWithTestRedis()) {
    //      // WARN : flush the DB, but on Database = 1
    //      Redis.pool.withClient {
    //        client =>
    //          client.flushDB()
    //      }
    //
    //      // GIVEN
    //      val speaker1 = Speaker.createSpeaker("speaker1",
    //      "email@speaker1.com",
    //        "speaker 01", "bio", Some("fr"), Some("twitter"),
    //        Some("avatarUrl"), Some("company"), Some("blog"), "firstName", "qualif"
    //      )
    //      Speaker.save(speaker1)
    //      val speaker2 = Speaker.createSpeaker("speaker2",
    //        "email2@speaker.com",
    //        "speaker 02", "bio", Some("fr"), Some("twitter2"),
    //        Some("avatarUrl"), Some("company"), Some("blog"), "firstName 2", "qualif"
    //      )
    //      Speaker.save(speaker2)
    //      val speaker3 = Speaker.createSpeaker("speaker3",
    //        "email3@speaker.com",
    //        "speaker 03", "bio3", Some("fr"), Some("twitter3"),
    //        Some("avatarUrl3"), Some("company"), Some("blog"), "firstName 3", "qualif"
    //      )
    //      Speaker.save(speaker3)
    //
    //      val proposal = Proposal.validateNewProposal(None,
    //        "fr",
    //        "test proposal 2",
    //        Some(speaker2.uuid),
    //        List(speaker3.uuid),
    //        ConferenceDescriptor.ConferenceProposalTypes.CONF.id,
    //        "audience level",
    //        "summary 2",
    //        "private message 2",
    //        sponsorTalk = false,
    //        ConferenceDescriptor.ConferenceTracks.JAVA.id,
    //        Some("beginner"),
    //        userGroup = None,
    //        videoLink = Some("http://www.youtube.fr"),
    //        tags = None)
    //
    //      Proposal.save(speaker1.uuid, proposal, ProposalState.ACCEPTED)
    //
    //      // Cause we need to reload the Proposal
    //      val correctProposal = Proposal.findById(proposal.id).get
    //
    //      // WHEN
    //      ApprovedProposal.approve(correctProposal)
    //
    //      // THEN
    //      ApprovedProposal.allApprovedSpeakers().isEmpty must beFalse
    //      ApprovedProposal.allApprovedSpeakers().size must beEqualTo(3)
    //      ApprovedProposal.allSpeakersWithAcceptedTalksAndNoBadge().size must beEqualTo(1)
    //      ApprovedProposal.allSpeakersWithAcceptedTalksAndNoBadge().head._1 must beEqualTo(speaker3)
    //    }
    //
    //    "return the three Speakers if talk is BOF" in new WithApplication(app = appWithTestRedis()) {
    //      // WARN : flush the DB, but on Database = 1
    //      Redis.pool.withClient {
    //        client =>
    //          client.flushDB()
    //      }
    //
    //      // GIVEN
    //      val speaker1 = Speaker.createSpeaker("speaker1",
    //        "email@speaker1.com",
    //        "speaker 01", "bio", Some("fr"), Some("twitter"),
    //        Some("avatarUrl"), Some("company"), Some("blog"), "firstName", "qualif"
    //      )
    //      Speaker.save(speaker1)
    //      val speaker2 = Speaker.createSpeaker("speaker2",
    //        "email2@speaker.com",
    //        "speaker 02", "bio", Some("fr"), Some("twitter2"),
    //        Some("avatarUrl"), Some("company"), Some("blog"), "firstName 2", "qualif"
    //      )
    //      Speaker.save(speaker2)
    //      val speaker3 = Speaker.createSpeaker("speaker3",
    //        "email3@speaker.com",
    //        "speaker 03", "bio3", Some("fr"), Some("twitter3"),
    //        Some("avatarUrl3"), Some("company"), Some("blog"), "firstName 3", "qualif"
    //      )
    //      Speaker.save(speaker3)
    //
    //      val proposal = Proposal.validateNewProposal(None,
    //        "fr",
    //        "test proposal 2",
    //        Some(speaker2.uuid),
    //        List(speaker3.uuid),
    //        ConferenceDescriptor.ConferenceProposalTypes.BOF.id,
    //        "audience level",
    //        "summary 2",
    //        "private message 2",
    //        sponsorTalk = false,
    //        ConferenceDescriptor.ConferenceTracks.JAVA.id,
    //        Some("beginner"),
    //        userGroup = None,
    //        videoLink = Some("http://www.youtube.fr"),
    //        tags = None)
    //
    //      Proposal.save(speaker1.uuid, proposal, ProposalState.ACCEPTED)
    //
    //      // Cause we need to reload the Proposal
    //      val correctProposal = Proposal.findById(proposal.id).get
    //
    //      // WHEN
    //      ApprovedProposal.approve(correctProposal)
    //
    //      // THEN
    //      ApprovedProposal.allApprovedSpeakers().isEmpty must beFalse
    //      ApprovedProposal.allApprovedSpeakers().size must beEqualTo(3)
    //      ApprovedProposal.allSpeakersWithAcceptedTalksAndNoBadge().size must beEqualTo(3)
    //
    //    }

    "return two Speakers if talk is BOF and one is selected in a CONF" in new WithApplication(app = appWithTestRedis()) {
      // WARN : flush the DB, but on Database = 1
      Redis.pool.withClient {
        client =>
          client.flushDB()
      }

      // GIVEN
      val speaker1 = Speaker.createSpeaker("speaker1",
        "email@speaker1.com",
        "speaker 01", "bio", Some("fr"), Some("twitter"),
        Some("avatarUrl"), Some("company"), Some("blog"), "firstName", "qualif"
      )
      Speaker.save(speaker1)
      val speaker2 = Speaker.createSpeaker("speaker2",
        "email2@speaker.com",
        "speaker 02", "bio", Some("fr"), Some("twitter2"),
        Some("avatarUrl"), Some("company"), Some("blog"), "firstName 2", "qualif"
      )
      Speaker.save(speaker2)
      val speaker3 = Speaker.createSpeaker("speaker3",
        "email3@speaker.com",
        "speaker 03", "bio3", Some("fr"), Some("twitter3"),
        Some("avatarUrl3"), Some("company"), Some("blog"), "firstName 3", "qualif"
      )
      Speaker.save(speaker3)

      val proposal = Proposal.validateNewProposal(None,
        "fr",
        "test proposal 2",
        Some(speaker2.uuid),
        List(speaker3.uuid),
        ConferenceDescriptor.ConferenceProposalTypes.BOF.id,
        "audience level",
        "summary 2",
        "private message 2",
        sponsorTalk = false,
        ConferenceDescriptor.ConferenceTracks.JAVA.id,
        Some("beginner"),
        userGroup = None,
        videoLink = None,
        tags = None)

      Proposal.save(speaker1.uuid, proposal, ProposalState.ACCEPTED)

      val proposalCONF = Proposal.validateNewProposal(None,
        "fr",
        "CONF",
        None,
        List.empty,
        ConferenceDescriptor.ConferenceProposalTypes.CONF.id,
        "audience level",
        "summary 2",
        "private message",
        sponsorTalk = false,
        ConferenceDescriptor.ConferenceTracks.JAVA.id,
        Some("beginner"),
        userGroup = None,
        videoLink = None,
        tags = None)

      Proposal.save(speaker3.uuid, proposalCONF, ProposalState.ACCEPTED)

      // Cause we need to reload the Proposal
      val correctProposal = Proposal.findById(proposal.id).get
      val correctProposal2 = Proposal.findById(proposalCONF.id).get

      // WHEN
      ApprovedProposal.approve(correctProposal)
      ApprovedProposal.approve(correctProposal2)

      // THEN
      ApprovedProposal.allApprovedSpeakers().isEmpty must beFalse
      ApprovedProposal.allApprovedSpeakers().size must beEqualTo(3)
      ApprovedProposal.allSpeakersWithAcceptedTalksAndNoBadge().size must beEqualTo(2)
    }

  }

}

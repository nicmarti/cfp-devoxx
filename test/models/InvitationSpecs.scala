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
import org.joda.time.DateTime
import org.specs2.control.Debug
import play.api.test.{FakeApplication, WithApplication, PlaySpecification}

/**
 * Test for Invitation
 * Created by nicolas martignole on 30/07/2014.
 */
class InvitationSpecs extends PlaySpecification with Debug {

  // Use a different Redis Database than the PROD one
  val testRedis = Map("redis.host" -> "localhost", "redis.port" -> "6364", "redis.activeDatabase" -> 1)

  // To avoid Play Cache Exception during tests, check this
  // https://groups.google.com/forum/#!topic/play-framework/PBIfeiwl5rU
  val appWithTestRedis = () => FakeApplication(additionalConfiguration = testRedis)


  "Invitation" should {
    "returns one invitation" in new WithApplication(app = appWithTestRedis()) {

      // WARN : flush the DB
      Redis.pool.withClient {
        client =>
          client.flushDB()
      }

      // WHEN
      val speakerId = "1234"
      val invitedBy = "222"
      Invitation.inviteSpeaker(speakerId, invitedBy)

      // THEN
      Invitation.isInvited(speakerId) must beTrue
    }

    "returns no invitation for a different speaker" in new WithApplication(app = appWithTestRedis()) {

      // WARN : flush the DB
      Redis.pool.withClient {
        client =>
          client.flushDB()
      }

      // WHEN
      val speakerId = "1234"
      val invitedBy = "222"
      Invitation.inviteSpeaker(speakerId, invitedBy)

      // THEN
      Invitation.isInvited(invitedBy) must beFalse
    }

    "returns who invited a specific speaker" in new WithApplication(app = appWithTestRedis()) {
      // WARN : flush the DB
      Redis.pool.withClient {
        client =>
          client.flushDB()
      }

      // WHEN
      val speakerId = "456"
      val invitedBy = "test"
      Invitation.inviteSpeaker(speakerId, invitedBy)

      // THEN
      Invitation.invitedBy(speakerId) must beEqualTo(Some(invitedBy))
    }

    "returns all invitations" in new WithApplication(app = appWithTestRedis()) {
      // WARN : flush the DB
      Redis.pool.withClient {
        client =>
          client.flushDB()
      }

      // WHEN
      val speaker1 = "speaker one"
      val speaker2 = "speaker two"
      val invitedBy = "test"
      Invitation.inviteSpeaker(speaker1, invitedBy)
      Invitation.inviteSpeaker(speaker2, invitedBy)
      Invitation.inviteSpeaker(speaker2, invitedBy)

      // THEN
      Invitation.all mustEqual Set(speaker1, speaker2)
    }

     "not returns a speakerID if the invitation has been cancelled" in new WithApplication(app = appWithTestRedis()) {
      // WARN : flush the DB
      Redis.pool.withClient {
        client =>
          client.flushDB()
      }

      // WHEN
      val speaker1 = "speaker one"
      val speaker2 = "speaker two"
      val invitedBy = "test"
      Invitation.inviteSpeaker(speaker1, invitedBy)
      Invitation.inviteSpeaker(speaker2, invitedBy)

       Invitation.removeInvitation(speaker2)

      // THEN
      Invitation.all mustEqual Set(speaker1)
    }

  }
}
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

import play.api.test.{WithApplication, FakeApplication, PlaySpecification}
import org.apache.commons.lang3.RandomStringUtils


/**
 * Simple Spec2 test for TrackLeader
 * Created by nicolas on 15/05/2014.
 */
class TrackLeaderSpecs extends PlaySpecification {

  val testRedis = Map("redis.host" -> "localhost", "redis.port" -> "6364")

  // To avoid Play Cache Exception during tests, check this
  // https://groups.google.com/forum/#!topic/play-framework/PBIfeiwl5rU
  val appWithTestRedis = () => FakeApplication(additionalConfiguration = testRedis)

  "TrackLeader" should {

    "associate a user to a Track if the user belongs to CFP group" in new WithApplication(app = appWithTestRedis()) {

      // Given
      val email = RandomStringUtils.randomAlphabetic(9)
      val testWebuser = Webuser.createSpeaker(email, RandomStringUtils.randomAlphabetic(4), RandomStringUtils.randomAlphabetic(2))
      Webuser.saveAndValidateWebuser(testWebuser)
      Webuser.addToCFPAdmin(testWebuser.uuid)

      // When
      TrackLeader.assign(testWebuser, Track.CLOUD)

      // Then the webuser is assigned to the Track
      TrackLeader.getTracks(testWebuser) must have size 1
      TrackLeader.getTracks(testWebuser).head must beEqualTo(Track.CLOUD)

      Webuser.delete(testWebuser)
      // Check that we did a cleanup
      TrackLeader.getTracks(testWebuser) must have size 0
    }

    "not associate a user to a Track if the user does not belong to CFP group" in new WithApplication(app = appWithTestRedis()) {

      // Given
      val email = RandomStringUtils.randomAlphabetic(9)
      val testWebuser = Webuser.createSpeaker(email, RandomStringUtils.randomAlphabetic(2), RandomStringUtils.randomAlphabetic(4))
      Webuser.saveAndValidateWebuser(testWebuser)

      // When
      TrackLeader.assign(testWebuser, Track.JAVA)

      // Then the webuser is assigned to the Track
      TrackLeader.getTracks(testWebuser) must have size 0

      Webuser.delete(testWebuser)
    }

    "unassign a CFP webuser from a track" in new WithApplication(app = appWithTestRedis()) {

      // Given
      val email = RandomStringUtils.randomAlphabetic(9)
      val testWebuser = Webuser.createSpeaker(email, RandomStringUtils.randomAlphabetic(2), RandomStringUtils.randomAlphabetic(4))
      Webuser.saveAndValidateWebuser(testWebuser)

      // When
      Webuser.addToCFPAdmin(testWebuser.uuid)
      TrackLeader.assign(testWebuser, Track.JAVA)
      TrackLeader.unassign(testWebuser, Track.JAVA)

      // Then
      TrackLeader.getTracks(testWebuser) must have size 0

       Webuser.delete(testWebuser)
    }

     "unassign a non-CFP webuser from a track" in new WithApplication(app = appWithTestRedis()) {

      // Given
      val email = RandomStringUtils.randomAlphabetic(9)
      val testWebuser = Webuser.createSpeaker(email, RandomStringUtils.randomAlphabetic(2), RandomStringUtils.randomAlphabetic(4))
      Webuser.saveAndValidateWebuser(testWebuser)

      // When
      TrackLeader.assign(testWebuser, Track.JAVA)
      TrackLeader.unassign(testWebuser, Track.JAVA)

      // Then
      TrackLeader.getTracks(testWebuser) must have size 0

        Webuser.delete(testWebuser)
    }

    "assigns more than one Track to a Webuser" in new WithApplication(app = appWithTestRedis()) {

      // Given
      val email = RandomStringUtils.randomAlphabetic(9)
      val testWebuser = Webuser.createSpeaker(email, RandomStringUtils.randomAlphabetic(2), RandomStringUtils.randomAlphabetic(4))
      Webuser.saveAndValidateWebuser(testWebuser)

      // When
      Webuser.addToCFPAdmin(testWebuser.uuid)
      TrackLeader.assign(testWebuser, Track.JAVA)
      TrackLeader.assign(testWebuser, Track.STARTUP)

      // Then
      TrackLeader.getTracks(testWebuser) must have size 2

       Webuser.delete(testWebuser)
    }


  }
}

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
 * Webuser specs2 test
 * Created by nmartignole on 15/05/2014.
 */
class WebuserSpecs extends PlaySpecification {

  val testRedis = Map("redis.host" -> "localhost", "redis.port" -> "6364", "redis.activeDatabase" -> 1)

  // To avoid Play Cache Exception during tests, check this
  // https://groups.google.com/forum/#!topic/play-framework/PBIfeiwl5rU
  val appWithTestRedis = () => FakeApplication(additionalConfiguration = testRedis)

  "Webuser" should {
    "create and delete a user" in new WithApplication(app = appWithTestRedis()) {

      val email = RandomStringUtils.randomAlphabetic(10)
      val webuser = Webuser.createSpeaker(email, "John", "UnitTest")
      Webuser.doesNotExist(webuser.uuid) must beTrue
      Webuser.doesNotExist(webuser.uuid) must beTrue
      Webuser.saveAndValidateWebuser(webuser)

      val tested = Webuser.findByEmail(email)

      tested must beSome[Webuser]
      // Assert that the Cache behaves as expected
      Webuser.isSpeaker(webuser.uuid) must beTrue // not cached
      Webuser.isSpeaker(webuser.uuid) must beTrue // cached

      Webuser.doesNotExist(webuser.uuid) must beFalse // not cached
      Webuser.doesNotExist(webuser.uuid) must beFalse // cached...

      Webuser.isEmailRegistered(email) must beTrue
      Webuser.isEmailRegistered(email) must beTrue

      Webuser.delete(tested.get)

      Webuser.findByEmail(email) must beNone // cache should be empty
      Webuser.findByEmail(email) must beNone // and still not there

      Webuser.doesNotExist(webuser.uuid) must beTrue
      Webuser.doesNotExist(webuser.uuid) must beTrue
    }

    "create and delete a user" in new WithApplication(app = appWithTestRedis()) {

      val email = RandomStringUtils.randomAlphabetic(10)
      val webuser = Webuser.createSpeaker(email, "John", "UnitTest")
      Webuser.doesNotExist(webuser.uuid) must beTrue
      Webuser.saveAndValidateWebuser(webuser)

      val tested = Webuser.findByEmail(email)

      tested must beSome[Webuser]
      // Assert that the Cache behaves as expected
      Webuser.isSpeaker(webuser.uuid) must beTrue // not cached
      Webuser.allSpeakers.contains(tested.get) must beTrue

      Webuser.delete(tested.get)

      Webuser.allSpeakers.contains(tested.get) must beFalse
      Webuser.isSpeaker(webuser.uuid) must beFalse
      Webuser.doesNotExist(webuser.uuid) must beTrue
    }
  }

}

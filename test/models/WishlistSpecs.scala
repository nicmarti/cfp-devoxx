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
 * Simple Spec2 functional test for Wishlist.
 * Created by nicolas on 13/05/2014.
 */
class WishlistSpecs extends PlaySpecification {
  // Redis is an in-memory server, so there is no point to use a mock or another memory server.
  // Just use a new redis instance for testing. Of course, do not use FLUSHDB ;-)
  val redisTestServer = Map(
    "redis.host" -> "localhost"
    , "redis.port" -> "6364"
  )

  val appWithTestRedis = FakeApplication(additionalConfiguration = redisTestServer)

  "create a RequestToTalk" in new WithApplication(appWithTestRedis) {

    val testId = Some(RandomStringUtils.randomAlphabetic(4))
    RequestToTalk.findById(testId.get) must beNone

    val requestToTalk = RequestToTalk.validateRequestToTalk(testId,"testeur","message", "speakerEmail", "speakerName")

    RequestToTalk.save(requestToTalk)
    RequestToTalk.findById(testId.get) must beSome[RequestToTalk]

    // Delete
    RequestToTalk.delete(testId.get)
    RequestToTalk.findById(testId.get) must beNone
  }

  "a new RequestToTalk status" in new WithApplication(appWithTestRedis) {

    val testId = Some(RandomStringUtils.randomAlphabetic(4))
    RequestToTalk.findById(testId.get) must beNone

    val requestToTalk = RequestToTalk.validateRequestToTalk(testId, "testeur", "message", "speakerEmail", "speakerName")
    RequestToTalk.save(requestToTalk)
    val maybeRequest = RequestToTalkStatus.findCurrentStatus(testId.get)

    maybeRequest.code must beEqualTo(RequestToTalkStatus.CONTACTED.code)


    RequestToTalkStatus.setApproved(testId.get)
    RequestToTalkStatus.findCurrentStatus(testId.get).code must beEqualTo(RequestToTalkStatus.APPROVED.code)

    RequestToTalkStatus.setDeclined(testId.get)
    RequestToTalkStatus.findCurrentStatus(testId.get).code must beEqualTo(RequestToTalkStatus.DECLINED.code)

    RequestToTalk.delete(testId.get)
    RequestToTalkStatus.findCurrentStatus(testId.get).code must beEqualTo(RequestToTalkStatus.UNKNOWN.code)

  }
}


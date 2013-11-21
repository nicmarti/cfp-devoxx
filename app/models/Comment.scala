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

import org.joda.time.{Instant, DateTime}
import library.Redis
import play.api.libs.json.Json

/**
 * A comment object is attached to a Proposal.
 *
 * Author: nicolas martignole
 * Created: 13/11/2013 14:37 created at devoxx
 */
case class Comment(proposalId: String, author: String, msg: String, eventDate: Option[DateTime])

object Comment {

  implicit val commentFormat  = Json.format[Comment]

  def saveCommentForSpeaker(proposalId: String, author: String, msg: String) = Redis.pool.withClient {
    client =>
      val comment = Comment(proposalId, author, msg, None)
      client.zadd("Comments:ForSpeaker:" + proposalId, new Instant().getMillis.toDouble,  Json.toJson(comment).toString())
  }

  def allSpeakerComments(proposalId: String):List[Comment] = Redis.pool.withClient {
    client =>
      val comments= client.zrevrangeWithScores("Comments:ForSpeaker:" + proposalId, 0, -1).map {
        case (json, dateValue) =>
          val c = Json.parse(json).as[Comment]
          val date = new Instant(dateValue.toLong)
          c.copy(eventDate = Option(date.toDateTime))
      }
      comments
  }
}

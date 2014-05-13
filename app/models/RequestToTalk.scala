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

import play.api.libs.json.Json
import library.Redis
import org.apache.commons.lang3.RandomStringUtils
import org.joda.time.DateTime
import play.api.data._
import play.api.data.Forms._
import play.api.templates.HtmlFormat
import java.util.Date

/**
 * Speaker's invitation, request to present a subject for a conference.
 * Created by nicolas martignole on 13/05/2014.
 */
case class RequestToTalk(id: String, creatorId: String, message: String, speakerEmail: String, speakerName: String, createdOn: Long){
  def status:RequestToTalkStatus={
    RequestToTalkStatus.findCurrentStatus(id)
  }
}

object RequestToTalk {
  implicit val requestToTalkFormat = Json.format[RequestToTalk]

  private def generateId:String={
    "req-"+RandomStringUtils.randomNumeric(3)+"-"+RandomStringUtils.randomNumeric(3)
  }

  def validateRequestToTalk(id: Option[String], creatorId: String, message: String, speakerEmail: String, speakerName: String): RequestToTalk = {
    RequestToTalk(id.getOrElse(generateId), creatorId, message, speakerEmail, speakerName, new Date().getTime)
  }

  def unapplyRequestToTalk(rt: RequestToTalk): Option[(Option[String], String, String, String, String)] = {
    Option((Option(rt.id), rt.creatorId, rt.message, rt.speakerEmail, rt.speakerName))
  }

  val newRequestToTalkForm = Form(mapping(
    "id" -> optional(text)
    , "creatorId" -> nonEmptyText
    , "wishlistMessage" -> nonEmptyText(maxLength = 3500)
    , "wishlistSpeakerEmail" -> email
    , "wishlistSpeakerName" -> nonEmptyText
  )(validateRequestToTalk)(unapplyRequestToTalk))


  def save(requestToTalk:RequestToTalk) = Redis.pool.withClient {
    client =>
      val json = Json.toJson(requestToTalk).toString()
      client.hset("RequestToTalk", requestToTalk.id, json)
      RequestToTalkStatus.setContacted(requestToTalk.id)
  }

  def delete(id: String) = Redis.pool.withClient {
    client =>
      client.hdel("RequestToTalk", id)
      RequestToTalkStatus.deleteStatus(id)
  }

  def findById(id: String): Option[RequestToTalk] = Redis.pool.withClient {
    client =>
      client.hget("RequestToTalk", id).flatMap {
        json: String =>
          Json.parse(json).asOpt[RequestToTalk]
      }
  }

  def allRequestsToTalk: List[RequestToTalk] = Redis.pool.withClient {
    client =>
      client.hvals("RequestToTalk").flatMap {
        json: String =>
          Json.parse(json).asOpt[RequestToTalk]
      }
  }

}

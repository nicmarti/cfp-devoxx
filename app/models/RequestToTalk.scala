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
import collection.JavaConverters._

/**
 * Speaker's invitation, request to present a subject for a conference.
 * Created by nicolas martignole on 13/05/2014.
 */
case class RequestToTalk(id: String
                         , note: String
                         , message: Option[String]
                         , speakerEmail: String
                         , speakerName: String
                         , company: String
                         , trackCode: String
                         , tl: Boolean
                         , country: String
                         , statusCode: String
                         , keynote:Option[Boolean]
                          ) {
  def status: RequestToTalkStatus = {
    RequestToTalkStatus.findCurrentStatus(id)
  }

  def track: Track = {
    Track.parse(trackCode)
  }
}

object RequestToTalk {
  implicit val requestToTalkFormat = Json.format[RequestToTalk]

  val IDRegExp = "(req-\\d\\d\\d-\\d\\d\\d)".r

  private def generateId: String = {
    "req-" + RandomStringUtils.randomNumeric(3) + "-" + RandomStringUtils.randomNumeric(3)
  }

  def validateRequestToTalk(id: Option[String], note: String, message: Option[String], speakerEmail: Option[String], speakerName: String,
                            company: String, trackCode: String, travel: Boolean, country: String, statusCode: String, keynote:Option[Boolean]=None): RequestToTalk = {
    RequestToTalk(id.getOrElse(generateId), note, message, speakerEmail.getOrElse(""), speakerName, company, trackCode, travel, country, statusCode, keynote)
  }

  def unapplyRequestToTalk(rt: RequestToTalk): Option[(Option[String], String, Option[String], Option[String], String, String, String, Boolean, String, String, Option[Boolean])] = {
    Option((Option(rt.id), rt.note, rt.message, Option(rt.speakerEmail), rt.speakerName, rt.company, rt.trackCode, rt.tl, rt.country, rt.statusCode, rt.keynote))
  }

  val newRequestToTalkForm = Form(mapping(
    "id" -> optional(text)
    , "wl_note" -> text(maxLength = 3500)
    , "wl_message" -> optional(text(minLength = 0, maxLength = 3500))
    , "wl_speakerEmail" -> optional(email)
    , "wl_speakerName" -> nonEmptyText
    , "wl_company" -> text
    , "wl_trackCode" -> text
    , "wl_travel" -> boolean
    , "wl_country" -> text
    , "wl_statusCode" -> nonEmptyText
    , "wl_keynote" -> optional(boolean)
  )(validateRequestToTalk)(unapplyRequestToTalk))


  def save(authorUUID: String, requestToTalk: RequestToTalk) = Redis.pool.withClient {
    client =>
      val json = Json.toJson(requestToTalk).toString()
      val tx = client.multi()
      tx.hset("RequestToTalk", requestToTalk.id, json)
      tx.zadd("RequestToTalk:IDs", new Date().getTime, requestToTalk.id)
      tx.exec()
      RequestToTalkStatus.changeStatus(authorUUID, requestToTalk.id, requestToTalk.statusCode)
  }

  def findById(id: String): Option[RequestToTalk] = Redis.pool.withClient {
    client =>
      client.hget("RequestToTalk", id).flatMap {
        json: String =>
          Json.parse(json).asOpt[RequestToTalk]
      }
  }

  def findPreviousIdFrom(id: String): Option[String] = Redis.pool.withClient {
    client =>
      val ids = client.zrange("RequestToTalk:IDs", 0, -1).asScala.toList
      ids.takeWhile(_ != id).lastOption
  }

  def findNextIdFrom(id: String): Option[String] = Redis.pool.withClient {
    client =>
      val ids = client.zrange("RequestToTalk:IDs", 0, -1).asScala.toList
      ids.dropWhile(_ != id).drop(1).headOption
  }

  def allRequestsToTalk: List[RequestToTalk] = Redis.pool.withClient {
    client =>
      val start = 0
      val end = -1
      val ids = client.zrange("RequestToTalk:IDs", start, end).asScala.toList
      client.hmget("RequestToTalk", ids).flatMap {
        json =>
          Json.parse(json).asOpt[RequestToTalk]
      }
  }

  def speakerApproved(requestId: String) = Redis.pool.withClient {
    implicit client =>
      findById(requestId).map {
        request =>
          RequestToTalkStatus.changeStatusFromRequest(request.speakerName, requestId, RequestToTalkStatus.ACCEPTED)
      }
  }

  def speakerDeclined(requestId: String) = Redis.pool.withClient {
    implicit client =>
      findById(requestId).map {
        request =>
          RequestToTalkStatus.changeStatusFromRequest(request.speakerName, requestId, RequestToTalkStatus.DECLINED)
      }
  }

  def speakerDiscuss(requestId: String) = Redis.pool.withClient {
    implicit client =>
      findById(requestId).map {
        request =>
          RequestToTalkStatus.changeStatusFromRequest(request.speakerName, requestId, RequestToTalkStatus.DISCUSS)
      }
  }

  def delete(author: String, requestId: String) = Redis.pool.withClient {
    client =>
      client.hdel("RequestToTalk", requestId)
      client.zrem("RequestToTalk:IDs", requestId)
      RequestToTalkStatus.changeStatusFromRequest(author, requestId, RequestToTalkStatus.DELETED)
  }

  def setPersonInCharge(requestId:String, userId:String) = Redis.pool.withClient {
    client =>
      client.hset("RequestToTalk:PersonInCharge",requestId,userId)
  }

  def unsetPersonInCharge(requestId:String) = Redis.pool.withClient{
    client=>
      client.hdel("RequestToTalk:PersonInCharge",requestId)
  }

  def whoIsInChargeOf(requestId:String):Option[Webuser]=Redis.pool.withClient{
    client=>
      client.hget("RequestToTalk:PersonInCharge",requestId).flatMap{
        uuid=>
          Webuser.findByUUID(uuid)
      }
  }

}

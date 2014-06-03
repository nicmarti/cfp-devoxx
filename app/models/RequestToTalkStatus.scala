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
import library.{Dress, Redis}
import org.joda.time.{DateTime, Instant}
import play.api.i18n.Messages.Message
import play.api.i18n.Messages


/**
 * Status holder for Request to present a talk at the Conference.
 * Created by nicolas on 13/05/2014.
 */
case class RequestToTalkStatus(code: String)

case class RequestHistory(author:String, statusCode:String, date:DateTime)

object RequestToTalkStatus {

  implicit val rttFormat = Json.format[RequestToTalkStatus]

  // (discuss, contact, contacted, approved, rejected)
  val SUGGESTED = RequestToTalkStatus("suggested")
  val CONTACTED_US = RequestToTalkStatus("contactedUs")
  val DISCUSS = RequestToTalkStatus("discuss")
  val CONTACT_SPEAKER = RequestToTalkStatus("contactSpeaker")
  val CONTACTED = RequestToTalkStatus("contacted")
  val ACCEPTED = RequestToTalkStatus("accepted")
  val DECLINED = RequestToTalkStatus("declined")
  val ON_HOLD = RequestToTalkStatus("onhold")
  val UNKNOWN = RequestToTalkStatus("unknown")
  val DELETED = RequestToTalkStatus("deleted")

  val all = List(SUGGESTED, CONTACTED_US, DISCUSS, CONTACT_SPEAKER, CONTACTED, ACCEPTED, DECLINED, ON_HOLD, UNKNOWN, DELETED)

    val allAsIdsAndLabels = all.map(a=>(a.code,"wl_"+a.code)).toSeq

  val allAsCode = all.map(_.code)

  def parse(state: String): RequestToTalkStatus = {
    state match {
      case "suggested" => SUGGESTED
      case "contactedUs" => CONTACTED_US
      case "discuss" => DISCUSS
      case "contactSpeaker" => CONTACT_SPEAKER
      case "contacted" => CONTACTED
      case "accepted" => ACCEPTED
      case "declined" => DECLINED
      case "onhold" => ON_HOLD
      case "unknown" => UNKNOWN
      case "deleted" => DELETED
    }
  }

  def changeStatusFromRequest(authorName:String, requestId: String, request: RequestToTalkStatus) = {
    changeStatus(authorName, requestId, request.code)
  }

  def changeStatus(authorName:String, requestId: String, statusCode: String) = Redis.pool.withClient {
    client =>
      val tx = client.multi()
      // Store status
      tx.hset("RequestsToTalk:ById" ,requestId, statusCode)
      // Save a timestamp
      tx.lpush("RequestsToTalk:History:" + requestId, authorName + "|" + statusCode + "|" + new Instant().toString)
      // trim to 100 last elements
      tx.ltrim("RequestsToTalk:History:" + requestId, 0, 100)
      tx.exec()
      Event.storeEvent(Event(requestId, authorName, s"Updated status of a wishlist item to [$statusCode]"))

  }

  def findCurrentStatus(requestId: String): RequestToTalkStatus = Redis.pool.withClient {
    client =>
      client.hget("RequestsToTalk:ById" ,requestId).map{
        code:String=>
        RequestToTalkStatus.parse(code)
      }.getOrElse(RequestToTalkStatus.UNKNOWN)
  }

  def history(requestId:String):List[RequestHistory]=Redis.pool.withClient{
    client=>
      client.lrange("RequestsToTalk:History:"+requestId, 0, 100).map{token:String=>
        val toReturn=token.split("\\|")
        RequestHistory(toReturn(0), toReturn(1), Instant.parse(toReturn(2)).toDateTime)
      }
  }

  def lastEvent(requestId:String):Option[RequestHistory]=Redis.pool.withClient{
    client=>
      client.lrange("RequestsToTalk:History:"+requestId, 0, 1).map{token:String=>
        val toReturn=token.split("\\|")
        RequestHistory(toReturn(0), toReturn(1), Instant.parse(toReturn(2)).toDateTime)
      }.headOption
  }

}

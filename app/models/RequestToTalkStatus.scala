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
import org.joda.time.Instant

/**
 * Status holder for Request to present a talk at the Conference.
 * Created by nicolas on 13/05/2014.
 */
case class RequestToTalkStatus(code: String)

object RequestToTalkStatus {

  implicit val rttFormat = Json.format[RequestToTalkStatus]

  // (discuss, contact, contacted, approved, rejected)
  val CONTACTED = RequestToTalkStatus("contacted")
  val APPROVED = RequestToTalkStatus("approved")
  val DECLINED = RequestToTalkStatus("declined")
  val UNKNOWN = RequestToTalkStatus("unknown")

  val all = List(CONTACTED, APPROVED, DECLINED, UNKNOWN)

  val allAsCode = all.map(_.code)

  def parse(state: String): RequestToTalkStatus = {
    state match {
      case "contacted" => CONTACTED
      case "approved" => APPROVED
      case "declined" => DECLINED
      case other => UNKNOWN
    }
  }
  

  def changeStatus(requestId: String, requestToTalkStatus: RequestToTalkStatus) = Redis.pool.withClient {
    client =>
      val maybeExistingStatus = for (state <- RequestToTalkStatus.allAsCode if client.sismember("RequestsToTalk:Status:" + state, requestId)) yield state

      // Do the operation on the RequestToTalkStatus
      maybeExistingStatus.filterNot(_ == requestToTalkStatus.code).foreach {
        stateOld: String =>
        // SMOVE is also a O(1) so it is faster than a SREM and SADD
          client.smove("RequestsToTalk:Status:" + stateOld, "RequestsToTalk:Status:" + requestToTalkStatus.code, requestId)
      }
      if (maybeExistingStatus.isEmpty) {
        // SADD is O(N)
        client.sadd("RequestsToTalk:Status:" + requestToTalkStatus.code, requestId)
      }
  }

    def findCurrentStatus(requestId: String): RequestToTalkStatus = Redis.pool.withClient {
    client =>
    // I use a for-comprehension to check each of the Set (O(1) operation)
    // when I have found what is the current state, then I stop and I return a Left that here, indicates a success
    // Note that the common behavior for an Either is to indicate failure as a Left and Success as a Right,
    // Here I do the opposite for performance reasons. NMA.
    // This code retrieves the proposalState in less than 20-30ms.
      val thisStatus = for (
        isNotSubmitted <- checkIsNotMember(client, RequestToTalkStatus.CONTACTED, requestId).toRight(RequestToTalkStatus.CONTACTED).right;
        isNotApproved <- checkIsNotMember(client, RequestToTalkStatus.APPROVED, requestId).toRight(RequestToTalkStatus.APPROVED).right;
        isNotDeclined <- checkIsNotMember(client, RequestToTalkStatus.DECLINED, requestId).toRight(RequestToTalkStatus.DECLINED).right
      ) yield RequestToTalkStatus.UNKNOWN // If we reach this code, we could not find what was the proposal state

      thisStatus.fold(identity, notFound => {
        play.Logger.warn(s"Could not find status for RequestToTalk $requestId")
        RequestToTalkStatus.UNKNOWN
      })
  }

    private def checkIsNotMember(client: Dress.Wrap, status: RequestToTalkStatus, requestId: String): Option[Boolean] = {
    client.sismember("RequestsToTalk:Status:" + status.code, requestId) match {
      case java.lang.Boolean.FALSE => Option(true)
      case other => None
    }
  }

  def setContacted(requestId:String)={
    changeStatus(requestId, RequestToTalkStatus.CONTACTED)
  }

  def setApproved(requestId:String)={
    changeStatus(requestId, RequestToTalkStatus.APPROVED)
  }

  def setDeclined(requestId:String)={
    changeStatus(requestId, RequestToTalkStatus.DECLINED)
  }

  def deleteStatus(requestId:String)={
    changeStatus(requestId, RequestToTalkStatus.UNKNOWN)
  }

}

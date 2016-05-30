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

import java.time.LocalDateTime
import java.util.Date

import library.Redis
import org.apache.commons.lang3.StringUtils
import org.joda.time.DateTime
import play.api.libs.json._

/**
  * A Rating is a comment and a rating given by an attendee during the conference.
  * Rating are posted to the CFP by Mobile application.
  *
  * @author created by N.Martignole, Innoteria, on 08/05/2016.
  */
case class RatingVote(talkId:String, user:String, rating: Int)

case class RatingDetail(aspect: String = "default", rating: Int, review: Option[String])

case class Rating(talkId: String, user: String, conference: String, timestamp: Long, details: List[RatingDetail]) {
  def id(): String = {
    StringUtils.trimToEmpty((talkId + user + conference + timestamp).toLowerCase()).hashCode.toString
  }
}

object Rating {

  def createNew(talkId:String, user:Int, rating: Int): Rating = {
    val conference = ConferenceDescriptor.current().eventCode
    val timestamp = new Date().getTime // Cause we want UTC
    Rating(talkId, user.toString, conference, timestamp, List(RatingDetail("default", rating, None)))
  }

  def unapplyRating(r:Rating):Option[(String,Int,Int)]={
    // TODO ici c'est mal fait dans l'ancienne API V1
    r.details.headOption.map{
      rt=>
        (r.talkId, r.user.toInt, rt.rating)
    }

  }

  implicit object RatingDetailFormat extends Format[RatingDetail] {
    def reads(json: JsValue) = JsSuccess(
      RatingDetail(
        (json \ "a").as[String],
        (json \ "r").as[Int],
        (json \ "v").asOpt[String]
      )
    )

    def writes(rd: RatingDetail): JsValue = JsObject(
      Seq(
        "a" -> JsString(rd.aspect),
        "r" -> JsNumber(rd.rating),
        "v" -> rd.review.map(JsString).getOrElse(JsNull)
      )
    )
  }

  implicit object RatingFormat extends Format[Rating] {
    def reads(json: JsValue) = JsSuccess(
      Rating(
        (json \ "t").as[String],
        (json \ "u").as[String],
        (json \ "c").as[String],
        (json \ "tm").as[Long],
        (json \ "dt").as[List[RatingDetail]]
      )
    )

    def writes(rd: Rating): JsValue = JsObject(
      Seq(
        "t" -> JsString(rd.talkId),
        "u" -> JsString(rd.user),
        "c" -> JsString(rd.conference),
        "tm" -> JsNumber(rd.timestamp),
        "dt" -> JsArray(
          rd.details.map(
            detail => JsObject(
              List(
                "a" -> JsString(detail.aspect),
                "r" -> JsNumber(detail.rating),
                "v" -> detail.review.map(JsString).getOrElse(JsNull)
              )
            )
          )
        )
      )
    )
  }

  def saveNewRating(newRating: Rating) = Redis.pool.withClient {
    client =>
      val tx = client.multi
      tx.hset("Rating:2016", newRating.id(), Json.toJson(newRating).toString())
      tx.sadd("Rating:2016:ByTalkId:" + newRating.talkId, newRating.id())
      tx.exec()
  }

  def allRatingsForSpecificTalkId(talkId: String): List[Rating] = Redis.pool.withClient {
    client =>
      val ratingIDs = client.smembers("Rating:2016:ByTalkId:" + talkId)
      client.hmget("Rating:2016", ratingIDs).map {
        json =>
          Json.parse(json).as[Rating]
      }
  }

  def allRatingsForTalks(allProposals: List[Proposal]): Map[Proposal, List[Rating]] = allProposals.map {
    proposal =>
      (proposal, allRatingsForSpecificTalkId(proposal.id))
  }.filter(_._2.nonEmpty).toMap

  def allRatings(): List[Rating] = Redis.pool.withClient {
    client =>
      client.hvals("Rating:2016").map {
        json =>
          Json.parse(json).as[Rating]
      }
  }

}
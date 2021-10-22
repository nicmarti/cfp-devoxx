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

import java.util.Date

import library.Redis
import org.apache.commons.lang3.StringUtils
import play.api.libs.json._

import scala.math.BigDecimal.RoundingMode

/**
  * A Rating is a comment and a rating given by an attendee during the conference.
  * Rating are posted to the CFP by Mobile application.
  *
  * @author created by N.Martignole, Innoteria, on 08/05/2016.
  */

case class RatingDetail(aspect: String = "default", rating: Int, review: Option[String])

case class Rating(talkId: String, user: String, conference: String, timestamp: Long, details: List[RatingDetail]) {
  def id(): String = {
    StringUtils.trimToEmpty((talkId + user + conference).toLowerCase()).hashCode.toString
  }

  def allVotes: List[Int] = details.map(_.rating)

  def sum: Int = allVotes.sum

  def count: Int = allVotes.length

  def average: Double = if (count == 0) {
    0
  } else {
    sum / count
  }

}

object Rating {

  def createNew(talkId: String, user: String, rating: Option[Int], details: Seq[RatingDetail]): Rating = rating match {
    case Some(globalRating) =>
      val conference = ConferenceDescriptor.current().eventCode
      val timestamp = new Date().getTime // Cause we want UTC
      Rating(talkId, user, conference, timestamp, List(RatingDetail("default", globalRating, None)))
    case None =>
      val conference = ConferenceDescriptor.current().eventCode
      val timestamp = new Date().getTime
      Rating(talkId, user, conference, timestamp, details.toList)
  }

  def unapplyRating(r: Rating): Option[(String, String, Option[Int], Seq[RatingDetail])] = {
    if (r.details.size == 1) {
      Option(
        (
          r.talkId,
          r.user,
          r.details.map(_.rating).headOption,
          Seq.empty[RatingDetail]
        )
      )
    } else {
      Some(
        (
          r.talkId,
          r.user,
          None,
          r.details
        )
      )
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

  def findForUserIdAndProposalId(userId: String, talkId: String): Option[Rating] = Redis.pool.withClient {
    client =>
      client.hmget("Rating:2020", client.smembers("Rating:2020:ByTalkId:" + talkId)).map {
        json: String =>
          Json.parse(json).as[Rating]
      }.find(rating => rating.user == userId)
  }

  def saveNewRating(newRating: Rating) = Redis.pool.withClient {
    client =>
      val tx = client.multi
      tx.hset("Rating:2020", newRating.id(), Json.toJson(newRating).toString())
      tx.sadd("Rating:2020:ByTalkId:" + newRating.talkId, newRating.id())
      tx.exec()
  }

  def allRatingsForSpecificTalkId(talkId: String): List[Rating] = Redis.pool.withClient {
    client =>
      val ratingIDs = client.smembers("Rating:2020:ByTalkId:" + talkId)
      client.hmget("Rating:2020", ratingIDs).map {
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
      client.hvals("Rating:2020").map {
        json =>
          Json.parse(json).as[Rating]
      }
  }

  def sortByRating(mapOfProposalsAndRating: Map[Proposal, List[Rating]]): List[(Proposal, List[Rating])] = {
    mapOfProposalsAndRating.toList.sortWith { (left, right) =>
      calculateScore(left._2) > calculateScore(right._2)
    }
  }

  def calculateScore(ratings: List[Rating]): Double = {
    val allAverages = ratings.map(_.average)
    val count = allAverages.size
    val total = allAverages.sum
    val score = if (count == 0) {
      0
    } else {
      total / count
    }
    BigDecimal(score).round(new java.math.MathContext(3)).toDouble
  }

  def attic() = Redis.pool.withClient {
    implicit client =>
      client.del("Rating:2020") // In 2019 I forget to store in a separate collection. No real impact except on stats
      val allKeys = client.keys("Rating:2020:ByTalkId:*")
      val tx = client.multi()
      allKeys.foreach { key: String => tx.del(key) }
      tx.exec()
  }
}

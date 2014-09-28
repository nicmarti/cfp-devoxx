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

import org.apache.commons.lang3.RandomStringUtils
import org.joda.time.{Instant, DateTime}
import library.Redis
import play.api.libs.json.Json

/**
 * A comment object is attached to a Proposal.
 *
 * Author: nicolas martignole
 * Created: 13/11/2013 14:37 created at devoxx
 */
case class Comment(proposalId: String, uuidAuthor: String, msg: String, eventDate: Option[DateTime])

case class Question(id:Option[String], proposalId: String, email: String, author:String, msg: String, eventDate: Option[DateTime])

object Comment {

  implicit val commentFormat = Json.format[Comment]
  implicit val questionFormat = Json.format[Question]

  def saveCommentForSpeaker(proposalId: String, uuidAuthor: String, msg: String) = {
    saveComment(s"Comments:ForSpeaker:$proposalId", proposalId, uuidAuthor, msg)
  }

  def saveQuestion(proposalId: String, visitorEmail: String, author:String, msg: String) = Redis.pool.withClient{
    client=>
    val newId=RandomStringUtils.randomAlphanumeric(10)
    val question = Question(Option(newId), proposalId, visitorEmail, author, msg, None)
    client.zadd(s"Questions:$proposalId", new Instant().getMillis.toDouble, Json.toJson(question).toString())
    client.set(s"QuestionsByID:$newId",proposalId)
  }

  def deleteQuestion(proposalId: String, questionId: String) = Redis.pool.withClient{
    client=>
      val questionsWithoutDates = client.zrevrangeWithScores(s"Questions:$proposalId", 0, -1).map {
        case (json, _) =>
          Json.parse(json).as[Question]
      }
      questionsWithoutDates.find(_.id==Some(questionId)).map{
        q=>
          client.zrem(s"Questions:$proposalId",Json.toJson(q).toString())
      }
  }

  def saveInternalComment(proposalId: String, uuidAuthor: String, msg: String) = {
    saveComment(s"Comments:Internal:$proposalId", proposalId, uuidAuthor, msg)
  }

  def allSpeakerComments(proposalId: String): List[Comment] = {
    allComments(s"Comments:ForSpeaker:$proposalId", proposalId)
  }

  def allInternalComments(proposalId: String): List[Comment] = {
    allComments(s"Comments:Internal:$proposalId", proposalId)
  }

  def allQuestions(proposalId: String): List[Question] = Redis.pool.withClient {
    client =>
      val questions = client.zrevrangeWithScores(s"Questions:$proposalId", 0, -1).map {
        case (json, dateValue) =>
          val c = Json.parse(json).as[Question]
          val date = new Instant(dateValue.toLong)
          c.copy(eventDate = Option(date.toDateTime))
      }
      questions
  }

  def countComments(proposalId: String): Long = Redis.pool.withClient {
    client =>
      client.zcard(s"Comments:ForSpeaker:$proposalId").longValue
  }

  private def saveComment(redisKey: String, proposalId: String, uuidAuthor: String, msg: String) = Redis.pool.withClient {
    client =>
      val comment = Comment(proposalId, uuidAuthor, msg, None)
      client.zadd(redisKey, new Instant().getMillis.toDouble, Json.toJson(comment).toString())
  }

  private def allComments(redisKey: String, proposalId: String): List[Comment] = Redis.pool.withClient {
    client =>
      val comments = client.zrevrangeWithScores(redisKey, 0, -1).map {
        case (json, dateValue) =>
          val c = Json.parse(json).as[Comment]
          val date = new Instant(dateValue.toLong)
          c.copy(eventDate = Option(date.toDateTime))
      }
      comments
  }


}

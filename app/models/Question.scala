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
import org.joda.time.{DateTimeZone, Instant, DateTime}
import library.Redis
import play.api.libs.json.Json

/**
 * A Question is an interaction between a CFP visitor and a speaker
 *
 * Author: nicolas martignole
 * Created: 29/09/2014
 */
case class Question(id:Option[String], proposalId: String, email: String, author:String, msg: String, eventDate: Option[DateTime])

object Question {

  implicit val questionFormat = Json.format[Question]

  def saveQuestion(proposalId: String, visitorEmail: String, author:String, msg: String) = Redis.pool.withClient{
    client=>
    val newId=RandomStringUtils.randomAlphanumeric(10)
    val question = Question(Option(newId), proposalId, visitorEmail, author, msg, Option(new DateTime().toDateTime(DateTimeZone.forID("Europe/Brussels"))))

    val tx=client.multi()
    tx.hset("Questions:v2", newId, Json.toJson(question).toString())
    tx.sadd(s"Questions:ById:$proposalId" , newId)
    tx.exec()
  }

  def deleteQuestion(proposalId: String, questionId: String) = Redis.pool.withClient{
    client=>
      val tx=client.multi()
      tx.hdel("Questions:v2",questionId)
      tx.srem(s"Questions:ById:$proposalId", questionId)
      tx.exec()
  }


  def allQuestionsForProposal(proposalId: String) = Redis.pool.withClient {
    client =>
      client.hmget("Questions:v2", client.smembers(s"Questions:ById:$proposalId")).map{
        json=>
          Json.parse(json).as[Question]
      }
  }

  def allQuestions:List[Question]=Redis.pool.withClient{
    client=>
      client.hvals("Questions:v2").map{
        json=>
          Json.parse(json).as[Question]
      }
  }

  def allQuestionsGroupedByProposal():Map[String,List[Question]] = {
    allQuestions.groupBy(_.proposalId)
  }



}

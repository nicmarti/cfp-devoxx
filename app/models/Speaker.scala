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
import library.{ZapJson, Redis}

/**
 * Speaker
 *
 * Author: nicolas
 * Created: 28/09/2013 11:01
 */
case class Speaker(email: String, bio: String, lang: Option[String], twitter: Option[String], avatarUrl: Option[String],
                   company: Option[String], blog: Option[String])

object SpeakerHelper {
  implicit val speakerFormat = Json.format[Speaker]

  def createSpeaker(email: String, bio: String, lang: Option[String], twitter: Option[String],
                    avatarUrl: Option[String], company: Option[String], blog: Option[String]): Speaker = {
    Speaker(email, bio, lang, twitter, avatarUrl, company, blog)
  }

  def unapplyForm(s: Speaker): Option[(String, String, Option[String], Option[String], Option[String], Option[String], Option[String])] = {
    Some(s.email, s.bio, s.lang, s.twitter, s.avatarUrl, s.company, s.blog)
  }

  def save(speaker: Speaker) = Redis.pool.withClient {
    client =>
      val jsonSpeaker = Json.stringify(Json.toJson(speaker))
      client.hset("Speaker", speaker.email, jsonSpeaker)
  }

  def update(email: String, speaker: Speaker) = Redis.pool.withClient {
    client =>
      val cleanEmail = email.toLowerCase.trim
      val jsonSpeaker = Json.stringify(Json.toJson(speaker))
      client.hset("Speaker", cleanEmail, jsonSpeaker)
  }

  def findByEmail(email: String): Option[Speaker] = Redis.pool.withClient {
    client =>
      client.hget("Speaker", email).flatMap {
        json: String =>
          Json.parse(json).asOpt[Speaker]
      }
  }

  def delete(email: String) = Redis.pool.withClient {
    client =>
      client.hdel("Speaker", email)
  }

  def allSpeakers(): List[Speaker] = Redis.pool.withClient {
    client =>
      client.hvals("Speaker").flatMap {
        jsString =>
          val json = Json.parse(jsString)
          val email = (json \ "email").as[String]
          val bio = (json \ "bio").as[String]
          val lang = (json \ "lang").asOpt[String]
          val twitter = (json \ "twitter").asOpt[String]
          val avatarUrl = (json \ "avatarUrl").asOpt[String]
          val company = (json \ "company").asOpt[String]
          val blog = (json \ "blog").asOpt[String]
          Some(Speaker(email, bio, lang, twitter, avatarUrl, company, blog))
      }

  }

}


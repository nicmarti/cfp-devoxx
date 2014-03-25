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
import play.api.cache.Cache
import play.api.Play.current
import org.joda.time.Instant
import org.joda.time.DateTime
import play.api.templates.HtmlFormat
import com.github.rjeschke.txtmark.Processor
import org.apache.commons.lang3.StringUtils

/**
 * Speaker
 *
 * Author: nicolas
 * Created: 28/09/2013 11:01
 */
case class Speaker(uuid: String, email: String, name: Option[String], bio: String, lang: Option[String],
                   twitter: Option[String], avatarUrl: Option[String],
                   company: Option[String], blog: Option[String],
                   firstName: Option[String]) {

  def cleanName: String = {
    firstName.getOrElse("") + name.map(n => " " + n).getOrElse("")
  }

  def cleanShortName: String = {
    firstName.map(_.charAt(0)).getOrElse("") + name.map(n => "." + n).getOrElse("")
  }


  def cleanLang: String = lang.map {
    l =>
      val cleanL = if (l.contains(",")) {
        l.substring(0, l.indexOf(","))
      } else {
        l.toLowerCase
      }
      if (cleanL.contains("-")) {
        cleanL.substring(0, l.indexOf("-"))
      } else {
        cleanL
      }
  }.getOrElse("fr")


  lazy val bioAsHtml: String = {
    val escapedHtml = HtmlFormat.escape(bio).body // escape HTML code and JS
    val processedMarkdownTest = Processor.process(StringUtils.trimToEmpty(escapedHtml).trim()) // Then do markdown processing
    processedMarkdownTest
  }
}

object Speaker {
  implicit val speakerFormat = Json.format[Speaker]

  def createSpeaker(email: String, name: String, bio: String, lang: Option[String], twitter: Option[String],
                    avatarUrl: Option[String], company: Option[String], blog: Option[String], firstName: String): Speaker = {
    Speaker(Webuser.generateUUID(email), email.trim().toLowerCase, Option(name), bio, lang, twitter, avatarUrl, company, blog, Some(firstName))
  }

  def createOrEditSpeaker(uuid: Option[String], email: String, name: String, bio: String, lang: Option[String], twitter: Option[String],
                          avatarUrl: Option[String], company: Option[String], blog: Option[String], firstName: String, acceptTerms: Boolean): Speaker = {
    uuid match {
      case None =>
        val newUUID = Webuser.generateUUID(email)
        if (acceptTerms) {
          doAcceptTerms(newUUID)
        } else {
          refuseTerms(newUUID)
        }
        Speaker(newUUID, email.trim().toLowerCase, Option(name), bio, lang, twitter, avatarUrl, company, blog, Option(firstName))
      case Some(validUuid) =>
        if (acceptTerms) {
          doAcceptTerms(validUuid)
        } else {
          refuseTerms(validUuid)
        }
        Speaker(validUuid, email.trim().toLowerCase, Option(name), bio, lang, twitter, avatarUrl, company, blog, Option(firstName))
    }

  }

  def unapplyForm(s: Speaker): Option[(String, String, String, Option[String], Option[String], Option[String], Option[String], Option[String], String)] = {
    Some(s.email, s.name.getOrElse(""), s.bio, s.lang, s.twitter, s.avatarUrl, s.company, s.blog, s.firstName.getOrElse(""))
  }

  def unapplyFormEdit(s: Speaker): Option[(Option[String], String, String, String, Option[String], Option[String], Option[String], Option[String], Option[String], String, Boolean)] = {
    Some(Option(s.uuid), s.email, s.name.getOrElse(""), s.bio, s.lang, s.twitter, s.avatarUrl, s.company, s.blog, s.firstName.getOrElse(""), needsToAccept(s.uuid) == false)
  }

  def save(speaker: Speaker) = Redis.pool.withClient {
    client =>
      Cache.remove("speaker:uuid:" + speaker.uuid)
      Cache.remove("allSpeakersWithAcceptedTerms")
      val jsonSpeaker = Json.stringify(Json.toJson(speaker))
      client.hset("Speaker", speaker.uuid, jsonSpeaker)
  }

  def update(uuid: String, speaker: Speaker) = Redis.pool.withClient {
    client =>
      Cache.remove("speaker:uuid:" + uuid)
      Cache.remove("allSpeakersWithAcceptedTerms")
      val jsonSpeaker = Json.stringify(Json.toJson(speaker.copy(uuid = uuid)))
      client.hset("Speaker", uuid, jsonSpeaker)
  }

  def updateName(uuid: String, firstName: String, lastName: String) = {
    Cache.remove("speaker:uuid:" + uuid)
    Cache.remove("allSpeakersWithAcceptedTerms")
    findByUUID(uuid).map {
      speaker =>
        Speaker.update(uuid, speaker.copy(name = Option(lastName), firstName = Option(firstName)))
    }
  }

  def findByUUID(uuid: String): Option[Speaker] = Redis.pool.withClient {
    client =>
      Cache.getOrElse[Option[Speaker]]("speaker:uuid:" + uuid, 3600) {
        client.hget("Speaker", uuid).flatMap {
          json: String =>
            Json.parse(json).validate[Speaker].fold(invalid => {
              play.Logger.error("Speaker error. " + ZapJson.showError(invalid));
              None
            }, validSpeaker => Some(validSpeaker))
        }
      }
  }

  def delete(uuid: String) = Redis.pool.withClient {
    client =>
      Cache.remove("allSpeakersWithAcceptedTerms")
      Cache.remove("speaker:uuid:" + uuid)
      client.hdel("Speaker", uuid)
  }

  def allSpeakers(): List[Speaker] = Redis.pool.withClient {
    client =>
      client.hvals("Speaker").flatMap {
        jsString =>
          val maybeSpeaker = Json.parse(jsString).asOpt[Speaker]
          maybeSpeaker
      }
  }

  def countAll(): Long = Redis.pool.withClient {
    client =>
      client.hlen("Speaker")
  }

  def needsToAccept(speakerId: String) = Redis.pool.withClient {
    client =>
      client.hexists("TermsAndConditions", speakerId) == false
  }

  def doAcceptTerms(speakerId: String) = Redis.pool.withClient {
    client =>
      Cache.remove("allSpeakersWithAcceptedTerms")
      client.hset("TermsAndConditions", speakerId, new Instant().getMillis.toString)
  }

  def refuseTerms(speakerId: String) = Redis.pool.withClient {
    client =>
      Cache.remove("allSpeakersWithAcceptedTerms")
      client.hdel("TermsAndConditions", speakerId)
  }


  def getAcceptedDate(speakerId: String): Option[DateTime] = Redis.pool.withClient {
    client =>
      client.hget("TermsAndConditions", speakerId).map {
        dateStr: String =>
          new org.joda.time.Instant(dateStr).toDateTime
      }
  }

  def allSpeakersWithAcceptedTerms() = Redis.pool.withClient {
    client =>
      val speakerIDs = client.hkeys("TermsAndConditions").filter(uuid => Proposal.hasOneAcceptedProposal(uuid))
      val allSpeakers = client.hmget("Speaker", speakerIDs).flatMap {
        json: String =>
          Json.parse(json).validate[Speaker].fold(invalid => {
            play.Logger.error("Speaker error. " + ZapJson.showError(invalid))
            None
          }, validSpeaker => Some(validSpeaker))
      }
      allSpeakers
  }

}


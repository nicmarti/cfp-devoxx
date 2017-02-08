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

import com.github.rjeschke.txtmark.Processor
import library.{Benchmark, Redis, ZapJson}
import org.apache.commons.lang3.StringUtils
import org.joda.time.{DateTime, Instant}
import play.api.libs.json.Json
import play.api.templates.HtmlFormat

/**
  * Speaker profile, is used mainly to show details.
  *
  * Webuser is the "technical" and internal web user representation.
  *
  * Author: nicolas martignole
  * Created: 28/09/2013 11:01
  */
case class Speaker(uuid: String
                   , email: String
                   , name: Option[String]
                   , bio: String
                   , lang: Option[String]
                   , twitter: Option[String]
                   , avatarUrl: Option[String]
                   , company: Option[String]
                   , blog: Option[String]
                   , firstName: Option[String]
                   , qualifications: Option[String]) {

  def cleanName: String = {
    firstName.getOrElse("").capitalize + name.map(n => " " + n).getOrElse("").capitalize
  }

  def cleanShortName: String = {
    firstName.map(_.charAt(0)).getOrElse("") + name.map(n => "." + n).getOrElse("")
  }

  def urlName: String = {
    StringUtils.stripAccents(cleanName).replaceAll(" ", "_").toLowerCase
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

  def cleanTwitter: Option[String] = twitter.map {
    tw =>
      val trimmed = tw.trim()
      if (!trimmed.startsWith("@")) {
        "@" + trimmed
      } else {
        trimmed
      }
  }

  def hasTwitter = StringUtils.trimToEmpty(twitter.getOrElse("")).nonEmpty

  def hasBio = StringUtils.trimToEmpty(bio).nonEmpty

  def hasCompany = StringUtils.trimToEmpty(company.getOrElse("")).nonEmpty

  def hasAvatar = StringUtils.trimToEmpty(avatarUrl.getOrElse("")).nonEmpty

  def hasBlog = StringUtils.trimToEmpty(blog.getOrElse("")).nonEmpty

  lazy val bioAsHtml: String = {
    val escapedHtml = HtmlFormat.escape(bio).body // escape HTML code and JS
    val processedMarkdownTest = Processor.process(StringUtils.trimToEmpty(escapedHtml).trim()) // Then do markdown processing
    processedMarkdownTest
  }
}

object Speaker {

  implicit val speakerFormat = Json.format[Speaker]

  def createSpeaker(webuserUUID: String, email: String, name: String, bio: String, lang: Option[String], twitter: Option[String],
                    avatarUrl: Option[String], company: Option[String], blog: Option[String], firstName: String,
                    qualifications: String): Speaker = {
    Speaker(webuserUUID, email.trim().toLowerCase, Option(name), bio, lang, twitter, avatarUrl, company, blog, Some(firstName), Option(qualifications))
  }

  def createOrEditSpeaker(uuid: Option[String], email: String, name: String, bio: String, lang: Option[String], twitter: Option[String],
                          avatarUrl: Option[String], company: Option[String], blog: Option[String], firstName: String, acceptTerms: Boolean, qualifications: String): Speaker = {
    uuid match {
      case None =>
        val newUUID = Webuser.generateUUID(email)
        if (acceptTerms) {
          doAcceptTerms(newUUID)
        } else {
          refuseTerms(newUUID)
        }
        Speaker(newUUID, email.trim().toLowerCase, Option(name), bio, lang, twitter, avatarUrl, company, blog, Option(firstName), Option(qualifications))
      case Some(validUuid) =>
        if (acceptTerms) {
          doAcceptTerms(validUuid)
        } else {
          refuseTerms(validUuid)
        }
        Speaker(validUuid, email.trim().toLowerCase, Option(name), bio, lang, twitter, avatarUrl, company, blog, Option(firstName), Option(qualifications))
    }

  }

  def unapplyForm(s: Speaker): Option[(String, String, String, String, Option[String], Option[String], Option[String], Option[String], Option[String], String, String)] = {
    Some("xxx", s.email, s.name.getOrElse(""), s.bio, s.lang, s.twitter, s.avatarUrl, s.company, s.blog, s.firstName.getOrElse(""), s.qualifications.getOrElse("No experience"))
  }

  def unapplyFormEdit(s: Speaker): Option[(Option[String], String, String, String, Option[String], Option[String], Option[String], Option[String], Option[String], String, Boolean, String)] = {
    Some(Option(s.uuid), s.email, s.name.getOrElse(""), s.bio, s.lang, s.twitter, s.avatarUrl, s.company, s.blog, s.firstName.getOrElse(""), !needsToAccept(s.uuid), s.qualifications.getOrElse("No experience"))
  }

  def save(speaker: Speaker) = Redis.pool.withClient {
    client =>
      val jsonSpeaker = Json.stringify(Json.toJson(speaker))
      client.hset("Speaker", speaker.uuid, jsonSpeaker)
  }

  def update(uuid: String, speaker: Speaker) = Redis.pool.withClient {
    client =>
      val jsonSpeaker = Json.stringify(Json.toJson(speaker.copy(uuid = uuid)))
      client.hset("Speaker", uuid, jsonSpeaker)
  }

  def updateName(uuid: String, firstName: String, lastName: String) = {
    findByUUID(uuid).map {
      speaker =>
        Speaker.update(uuid, speaker.copy(name = Option(StringUtils.trimToNull(lastName)), firstName = Option(StringUtils.trimToNull(firstName))))
    }
  }

  def findByUUID(uuid: String): Option[Speaker] = Redis.pool.withClient {
    client =>
      client.hget("Speaker", uuid).flatMap {
        json: String =>
          Json.parse(json).validate[Speaker].fold(invalid => {
            play.Logger.error("Invalid json format for Speaker, unable to unmarshall " + ZapJson.showError(invalid))
            None
          }, validSpeaker => Some(validSpeaker))
      }
  }

  def delete(uuid: String) = Redis.pool.withClient {
    client =>
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

  def withOneProposal(speakers: List[Speaker]) = {
    speakers.filter(s => Proposal.hasOneAcceptedProposal(s.uuid))
  }

  def notMemberOfCFP(speakers: List[Speaker]) = {
    speakers.filterNot(s => Webuser.isMember(s.uuid, "cfp"))
  }

  def allSpeakersUUID(): Set[String] = Redis.pool.withClient {
    client =>
      client.hkeys("Speaker")
  }

  def loadSpeakersFromSpeakerIDs(speakerIDs: Set[String]): List[Speaker] = Redis.pool.withClient {
    client =>
      client.hmget("Speaker", speakerIDs).flatMap {
        js: String =>
          Json.parse(js).asOpt[Speaker]
      }
  }

  def countAll(): Long = Redis.pool.withClient {
    client =>
      client.hlen("Speaker")
  }

  def needsToAccept(speakerId: String) = Redis.pool.withClient {
    client =>
      !client.hexists("TermsAndConditions", speakerId)
  }

  def doAcceptTerms(speakerId: String) = Redis.pool.withClient {
    client =>
      client.hset("TermsAndConditions", speakerId, new Instant().getMillis.toString)
  }

  def refuseTerms(speakerId: String) = Redis.pool.withClient {
    client =>
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

      val termKeys = Benchmark.measure(() =>
        client.hkeys("TermsAndConditions")
        , "termKeys")

      val speakerIDs = Benchmark.measure(() =>
        termKeys.filter(uuid => Proposal.hasOneAcceptedProposal(uuid))
        , "speakerIDs")

      val allSpeakers = Benchmark.measure(() =>
        client.hmget("Speaker", speakerIDs).flatMap {
          json: String =>
            Json.parse(json).validate[Speaker].fold(invalid => {
              play.Logger.error("Speaker error. " + ZapJson.showError(invalid))
              None
            }, validSpeaker => Some(validSpeaker))
        }, "allSpeakers")
      allSpeakers
  }

  def allThatDidNotAcceptedTerms(): Set[String] = Redis.pool.withClient {
    client =>
      val allSpeakerIDs = client.keys("ApprovedSpeakers:*").map(s => s.substring("ApprovedSpeakers:".length))
      val allThatAcceptedConditions = client.hkeys("TermsAndConditions")
      allSpeakerIDs.diff(allThatAcceptedConditions)
  }

  def allSpeakersFromPublishedSlots: List[Speaker] = {
    val publishedConf = ScheduleConfiguration.loadAllPublishedSlots().filter(_.proposal.isDefined)
    val allSpeakersIDs = publishedConf.flatMap(_.proposal.get.allSpeakerUUIDs).toSet
    val onlySpeakersThatAcceptedTerms: Set[String] = allSpeakersIDs.filterNot(uuid => needsToAccept(uuid))
    val speakers: List[Speaker] = loadSpeakersFromSpeakerIDs(onlySpeakersThatAcceptedTerms)
    speakers.sortBy(_.name.getOrElse(""))
  }
}
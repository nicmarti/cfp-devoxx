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

package controllers

import play.api.mvc.{Action, Controller}
import models.{Proposal, ApprovedProposal, Conference, Speaker}
import play.api.libs.json.{JsNull, Json}
import play.api.i18n.Messages

/**
 * A real REST api for men.
 * Created by nicolas on 25/02/2014.
 */

object RestAPI extends Controller {

  def index = Action {
    implicit request =>
      request.headers.get("Accept") match {
        case Some("application/json") => Redirect(routes.RestAPI.showAllConferences)
        case other => Ok(views.html.RestAPI.index())
      }

  }

  def showAllConferences() = Action {
    implicit request =>
      import Conference.confFormat

      val conferences = Conference.all
      val etag = conferences.hashCode.toString

      request.headers.get("If-None-Match") match {
        case Some(tag) if tag == etag => {
          NotModified
        }
        case other => {
          val jsonObject = Json.toJson(
            Map(
              "conferences" -> Json.toJson(conferences)
            )
          )
          Ok(jsonObject).as("application/json").withHeaders("ETag" -> etag)
        }
      }
  }

  def showConference(eventCode: String) = Action {
    implicit request =>
      import Conference.confFormat

      Conference.find(eventCode).map {
        conference: Conference =>

          val etag = conference.eventCode.toString

          request.headers.get("If-None-Match") match {
            case Some(tag) if tag == etag => {
              NotModified
            }
            case other => {
              val jsonObject = Json.toJson(
                Map(
                  "conference" -> Json.toJson(conference),
                  "speakers:ref" -> Json.toJson(routes.RestAPI.showSpeakers(conference.eventCode).toString)
                )
              )
              Ok(jsonObject).as("application/json").withHeaders("ETag" -> etag)
            }
          }
      }.getOrElse(NotFound("Conference not found"))
  }

  def showSpeakers(eventCode: String) = Action {
    implicit request =>
      import Speaker.speakerFormat

      val speakers = Speaker.allSpeakersWithAcceptedTerms().sortBy(_.cleanName)
      val etag = speakers.hashCode.toString

      request.headers.get("If-None-Match") match {
        case Some(tag) if tag == etag => {
          NotModified
        }
        case other => {

          val updatedSpeakers = speakers.map {
            speaker: Speaker =>
              Map(
                "uuid" -> Json.toJson(speaker.uuid),
                "firstName" -> speaker.firstName.map(Json.toJson(_)).getOrElse(JsNull),
                "lastName" -> speaker.name.map(Json.toJson(_)).getOrElse(JsNull),
                "avatarURL" -> speaker.avatarUrl.map(u => Json.toJson(u.trim())).getOrElse(JsNull),
                "ref" -> Json.toJson(routes.RestAPI.showSpeaker(eventCode, speaker.uuid).toString)
              )
          }

          val jsonObject = Json.toJson(
            Map(
              "speakers" -> Json.toJson(updatedSpeakers)
            )
          )
          Ok(jsonObject).as("application/json").withHeaders("ETag" -> etag)
        }
      }
  }

  def showSpeaker(eventCode: String, uuid: String) = Action {
    implicit request =>
      import Speaker.speakerFormat

      Speaker.findByUUID(uuid).map {
        speaker =>
          val etag = speaker.hashCode.toString

          request.headers.get("If-None-Match") match {
            case Some(tag) if tag == etag => {
              NotModified
            }
            case other => {
              val acceptedProposals = ApprovedProposal.allAcceptedTalksForSpeaker(speaker.uuid)

              val updatedTalks = acceptedProposals.map{
                proposal:Proposal=>
                  val allSpeakers = proposal.allSpeakerUUIDs.flatMap{uuid=>Speaker.findByUUID(uuid)}

                  Map(
                  "id"->Json.toJson(proposal.id),
                  "ref"->Json.toJson(routes.RestAPI.showTalk(eventCode, proposal.id).toString),
                  "title"->Json.toJson(proposal.title),
                  "track"->Json.toJson(Messages(proposal.track.label)),
                  "talkType"->Json.toJson(Messages(proposal.talkType.id)),
                  "speakers"->Json.toJson(allSpeakers.map{
                    speaker=>
                      Map(
                        "ref"->Json.toJson(routes.RestAPI.showSpeaker(eventCode,speaker.uuid).toString),
                        "name"->Json.toJson(speaker.cleanName)
                      )
                  })

                )
              }

              val updatedSpeaker =
                Map(
                  "uuid" -> Json.toJson(speaker.uuid),
                  "firstName" -> speaker.firstName.map(Json.toJson(_)).getOrElse(JsNull),
                  "lastName" -> speaker.name.map(Json.toJson(_)).getOrElse(JsNull),
                  "avatarURL" -> speaker.avatarUrl.map(u => Json.toJson(u.trim())).getOrElse(JsNull),
                  "blog" -> speaker.blog.map(u => Json.toJson(u.trim())).getOrElse(JsNull),
                  "company" -> speaker.company.map(u => Json.toJson(u.trim())).getOrElse(JsNull),
                  "lang" -> speaker.lang.map(u => Json.toJson(u.trim())).getOrElse(Json.toJson("fr")),
                  "bio" -> Json.toJson(speaker.bio),
                  "twitter" -> speaker.company.map(u => Json.toJson(u.trim())).getOrElse(JsNull),
                  "acceptedTalks"->Json.toJson(updatedTalks)
                )

              val jsonObject = Json.toJson(updatedSpeaker)
              Ok(jsonObject).as("application/json").withHeaders("ETag" -> etag)
            }
          }
      }.getOrElse(NotFound("Speaker not found"))
  }

  def showTalk(eventCode:String, proposalId:String)=Action{
     implicit request =>
      Proposal.findById(proposalId).map{
        proposal=>
          val etag = proposal.hashCode.toString

          request.headers.get("If-None-Match") match {
            case Some(tag) if tag == etag => {
              NotModified
            }
            case other => {
              val allSpeakers = proposal.allSpeakerUUIDs.flatMap{uuid=>Speaker.findByUUID(uuid)}

              val updatedProposal =
                Map(
                  "id" -> Json.toJson(proposal.id),
                  "title"->Json.toJson(proposal.title),
                  "lang"->Json.toJson(proposal.lang),
                  "summaryAsHtml"->Json.toJson(proposal.summaryAsHtml),
                  "summary"->Json.toJson(proposal.summary),
                  "track"->Json.toJson(Messages(proposal.track.label)),
                  "talkType"->Json.toJson(Messages(proposal.talkType.id)),
                  "speakers"->Json.toJson(allSpeakers.map{
                    speaker=>
                      Map(
                        "ref"->Json.toJson(routes.RestAPI.showSpeaker(eventCode,speaker.uuid).toString),
                        "name"->Json.toJson(speaker.cleanName)
                      )
                  })
                )
              val jsonObject = Json.toJson(updatedProposal)
              Ok(jsonObject).as("application/json").withHeaders("ETag" -> etag)
            }
          }
      }.getOrElse(NotFound("Proposal not found"))
  }

}

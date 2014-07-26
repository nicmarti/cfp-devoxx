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

import play.api.mvc._
import models._
import play.api.libs.json.{JsNull, Json}
import play.api.i18n.Messages
import scala.concurrent.Future
import play.api.mvc.SimpleResult

/**
 * A real REST api for men.
 * Created by Nicolas Martignole on 25/02/2014.
 */
object RestAPI extends Controller {

  def index = UserAgentAction {
    implicit request =>
      Ok(views.html.RestAPI.index())
  }

  def profile(docName: String) = Action {
    implicit request =>

      docName match {
        case "link" => Ok(views.html.RestAPI.docLink())
        case "links" => Ok(views.html.RestAPI.docLink())
        case "speaker" => Ok(views.html.RestAPI.docSpeaker())
        case "list-of-speakers" => Ok(views.html.RestAPI.docSpeakers())
        case "talk" => Ok(views.html.RestAPI.docTalk())
        case "conference" => Ok(views.html.RestAPI.docConference())
        case "conferences" => Ok(views.html.RestAPI.docConferences())
        case "schedules" => Ok(views.html.RestAPI.docSchedules())
        case "schedule" => Ok(views.html.RestAPI.docSchedule())
        case "proposalType" => Ok(views.html.RestAPI.docProposalType())
        case other => NotFound("Sorry, no documentation for this profile")
      }
  }

  def showAllConferences() = UserAgentAction {
    implicit request =>
      import Conference.confFormat

      val conferences = Conference.all
      val etag = conferences.hashCode.toString

      request.headers.get(IF_NONE_MATCH) match {
        case Some(tag) if tag == etag => {
          NotModified
        }
        case other => {
          val jsonObject = Json.toJson(
            Map(
              "content" -> Json.toJson("All conferences"),
              "links" -> Json.toJson {
                Conference.all.map {
                  conference: Conference =>
                    conference.link
                }
              }
            )
          )
          Ok(jsonObject).as(JSON).withHeaders(ETAG -> etag, "Links" -> ("<" + routes.RestAPI.profile("conferences").absoluteURL().toString + ">; rel=\"profile\""))
        }
      }
  }

  def redirectToConferences = UserAgentAction {
    implicit request =>
      Redirect(routes.RestAPI.showAllConferences())
  }

  def showConference(eventCode: String) = UserAgentAction {
    implicit request =>
      import Conference.confFormat

      Conference.find(eventCode).map {
        conference: Conference =>

          val etag = conference.eventCode.toString

          request.headers.get(IF_NONE_MATCH) match {
            case Some(tag) if tag == etag => {
              NotModified
            }
            case other => {
              val jsonObject = Json.toJson(
                Map(
                  "eventCode" -> Json.toJson(conference.eventCode),
                  "label" -> Json.toJson(conference.label),
                  "locale" -> Json.toJson(conference.locale),
                  "localisation" -> Json.toJson(conference.localisation),
                  "links" -> Json.toJson(List(
                    Link(
                      routes.RestAPI.showSpeakers(conference.eventCode).absoluteURL(),
                      routes.RestAPI.profile("list-of-speakers").absoluteURL(),
                      "See all speakers"
                    ),
                    Link(
                      routes.RestAPI.showAllSchedules(conference.eventCode).absoluteURL(),
                      routes.RestAPI.profile("schedules").absoluteURL(),
                      "See the whole agenda"
                    ),
                    Link(
                      routes.RestAPI.showProposalTypes(conference.eventCode).absoluteURL(),
                      routes.RestAPI.profile("proposalType").absoluteURL(),
                      "See the different kind of conferences"
                    )
                  ))
                )
              )
              Ok(jsonObject).as(JSON).withHeaders(ETAG -> etag, "Links" -> ("<" + routes.RestAPI.profile("conference").absoluteURL().toString + ">; rel=\"profile\""))
            }
          }
      }.getOrElse(NotFound("Conference not found"))
  }

  def showSpeakers(eventCode: String) = UserAgentAction {
    implicit request =>
      import Speaker.speakerFormat

      val speakers = Speaker.allSpeakersWithAcceptedTerms().sortBy(_.cleanName)
      val etag = speakers.hashCode.toString

      request.headers.get(IF_NONE_MATCH) match {
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
                "links" -> Json.toJson(List(
                  Link(routes.RestAPI.showSpeaker(eventCode, speaker.uuid).absoluteURL().toString,
                    routes.RestAPI.profile("speaker").absoluteURL().toString,
                    speaker.cleanName)
                )
                )
              )
          }

          val jsonObject = Json.toJson(updatedSpeakers)

          Ok(jsonObject).as(JSON).withHeaders(ETAG -> etag,
            "Links" -> ("<" + routes.RestAPI.profile("list-of-speakers").absoluteURL().toString + ">; rel=\"profile\"")
          )
        }
      }
  }

  def redirectToSpeakers(eventCode: String) = UserAgentAction {
    implicit request =>
      Redirect(routes.RestAPI.showSpeakers(eventCode))
  }

  def showSpeaker(eventCode: String, uuid: String) = UserAgentAction {
    implicit request =>
      import Speaker.speakerFormat

      Speaker.findByUUID(uuid).map {
        speaker =>
          val etag = speaker.hashCode.toString

          request.headers.get(IF_NONE_MATCH) match {
            case Some(tag) if tag == etag => {
              NotModified
            }
            case other => {
              val acceptedProposals = ApprovedProposal.allAcceptedTalksForSpeaker(speaker.uuid)

              val updatedTalks = acceptedProposals.map {
                proposal: Proposal =>
                  val allSpeakers = proposal.allSpeakerUUIDs.flatMap {
                    uuid => Speaker.findByUUID(uuid)
                  }.map {
                    speaker =>
                      Link(routes.RestAPI.showSpeaker(eventCode, speaker.uuid).absoluteURL().toString,
                        routes.RestAPI.profile("speaker").absoluteURL().toString,
                        speaker.cleanName)
                  }

                  Map(
                    "id" -> Json.toJson(proposal.id),
                    "title" -> Json.toJson(proposal.title),
                    "track" -> Json.toJson(Messages(proposal.track.label)),
                    "talkType" -> Json.toJson(Messages(proposal.talkType.id)),
                    "links" -> Json.toJson(
                      List(
                        Link(routes.RestAPI.showTalk(eventCode, proposal.id).absoluteURL().toString,
                          routes.RestAPI.profile("talk").absoluteURL().toString, "More details about this talk"
                        )
                      ).++(allSpeakers)
                    )
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
                  "bioAsHtml" -> Json.toJson(speaker.bioAsHtml),
                  "twitter" -> speaker.twitter.map(u => Json.toJson(u.trim())).getOrElse(JsNull),
                  "acceptedTalks" -> Json.toJson(updatedTalks)
                )

              val jsonObject = Json.toJson(updatedSpeaker)
              Ok(jsonObject).as(JSON).withHeaders(ETAG -> etag, "Links" -> ("<" + routes.RestAPI.profile("speaker").absoluteURL().toString + ">; rel=\"profile\""))
            }
          }
      }.getOrElse(NotFound("Speaker not found"))
  }

  def showTalk(eventCode: String, proposalId: String) = UserAgentAction {
    implicit request =>
      Proposal.findById(proposalId).map {
        proposal =>
          val etag = proposal.hashCode.toString

          request.headers.get(IF_NONE_MATCH) match {
            case Some(tag) if tag == etag => {
              NotModified
            }
            case other => {
              val allSpeakers = proposal.allSpeakerUUIDs.flatMap {
                uuid => Speaker.findByUUID(uuid)
              }

              val updatedProposal =
                Map(
                  "id" -> Json.toJson(proposal.id),
                  "title" -> Json.toJson(proposal.title),
                  "talkType" -> Json.toJson(Messages(proposal.talkType.id)),
                  "lang" -> Json.toJson(proposal.lang),
                  "summary" -> Json.toJson(proposal.summary),
                  "summaryAsHtml" -> Json.toJson(proposal.summaryAsHtml),
                  "track" -> Json.toJson(Messages(proposal.track.label)),
                  "speakers" -> Json.toJson(allSpeakers.map {
                    speaker =>
                      Map(
                        "link" -> Json.toJson(
                          Link(
                            routes.RestAPI.showSpeaker(eventCode, speaker.uuid).absoluteURL().toString,
                            routes.RestAPI.profile("speaker").absoluteURL().toString,
                            speaker.cleanName
                          )
                        ),
                        "name" -> Json.toJson(speaker.cleanName)
                      )
                  })
                )
              val jsonObject = Json.toJson(updatedProposal)
              Ok(jsonObject).as(JSON).withHeaders(ETAG -> etag)
            }
          }
      }.getOrElse(NotFound("Proposal not found"))
  }

  def redirectToTalks(eventCode: String) = UserAgentAction {
    implicit request =>
      Redirect(routes.RestAPI.showTalks(eventCode))
  }

  def showTalks(eventCode: String) = UserAgentAction {
    implicit request =>
      NotImplemented("Not yet implemented")
  }

  def showAllSchedules(eventCode: String) = UserAgentAction {
    implicit request =>

      val ifNoneMatch = request.headers.get(IF_NONE_MATCH)
      val mapOfSchedules = Map(
        "links" -> Json.toJson(List(
          Link(
            routes.RestAPI.showScheduleFor(eventCode, "wednesday").absoluteURL().toString,
            routes.RestAPI.profile("schedule").absoluteURL().toString,
            "Schedule for Wednesday 16th April 2014"
          ),
          Link(
            routes.RestAPI.showScheduleFor(eventCode, "thursday").absoluteURL().toString,
            routes.RestAPI.profile("schedule").absoluteURL().toString,
            "Schedule for Thursday 17th April 2014"
          ),
          Link(
            routes.RestAPI.showScheduleFor(eventCode, "friday").absoluteURL().toString,
            routes.RestAPI.profile("schedule").absoluteURL().toString,
            "Schedule for Friday 18th April 2014"
          )
        ))
      )
      val newEtag = mapOfSchedules.hashCode().toString

      ifNoneMatch match {
        case Some(someEtag) if someEtag == newEtag => NotModified
        case other => {
          val jsonObject = Json.toJson(mapOfSchedules)
          Ok(jsonObject).as(JSON).withHeaders(ETAG -> newEtag, "Links" -> ("<" + routes.RestAPI.profile("schedules").absoluteURL().toString + ">; rel=\"profile\""))
        }
      }
  }

  def showScheduleFor(eventCode: String, day: String) = UserAgentAction {
    implicit request =>

      val ifNoneMatch = request.headers.get(IF_NONE_MATCH)
      val finalListOfSlots = ScheduleConfiguration.getPublishedScheduleByDay(day)
      val newEtag = finalListOfSlots.hashCode().toString

      ifNoneMatch match {
        case Some(someEtag) if someEtag == newEtag => NotModified
        case other => {
          val toReturn = finalListOfSlots.map {
            slot =>
              val upProposal = slot.proposal.map {
                proposal =>
                  val allSpeakers = proposal.allSpeakerUUIDs.flatMap {
                    uuid => Speaker.findByUUID(uuid)
                  }
                  val updatedProposal =
                    Map(
                      "id" -> Json.toJson(proposal.id),
                      "title" -> Json.toJson(proposal.title),
                      "lang" -> Json.toJson(proposal.lang),
                      "summaryAsHtml" -> Json.toJson(proposal.summaryAsHtml),
                      "summary" -> Json.toJson(proposal.summary),
                      "track" -> Json.toJson(Messages(proposal.track.label)),
                      "talkType" -> Json.toJson(Messages(proposal.talkType.id)),
                      "speakers" -> Json.toJson(allSpeakers.map {
                        speaker =>
                          Map(
                            "link" -> Json.toJson(
                              Link(
                                routes.RestAPI.showSpeaker(eventCode, speaker.uuid).absoluteURL().toString,
                                routes.RestAPI.profile("speaker").absoluteURL().toString,
                                speaker.cleanName
                              )
                            ),
                            "name" -> Json.toJson(speaker.cleanName)
                          )
                      })
                    )
                  updatedProposal
              }

              Map(
                "slotId" -> Json.toJson(slot.id)
                , "day" -> Json.toJson(slot.day)
                , "roomId" -> Json.toJson(slot.room.id)
                , "roomName" -> Json.toJson(slot.room.name)
                , "fromTime" -> Json.toJson(slot.from.toString("HH:mm"))
                , "fromTimeMillis" -> Json.toJson(slot.from.getMillis)
                , "toTime" -> Json.toJson(slot.to.toString("HH:mm"))
                , "toTimeMillis" -> Json.toJson(slot.to.getMillis)
                , "talk" -> upProposal.map(Json.toJson(_)).getOrElse(JsNull)
                , "break" -> Json.toJson(slot.break)
                , "roomSetup" -> Json.toJson(slot.room.setup)
                , "roomCapacity" -> Json.toJson(slot.room.capacity)
                , "notAllocated" -> Json.toJson(slot.notAllocated)
              )
          }
          val jsonObject = Json.toJson(
            Map(
              "slots" -> Json.toJson(toReturn)
            )
          )
          Ok(jsonObject).as(JSON).withHeaders(ETAG -> newEtag, "Links" -> ("<" + routes.RestAPI.profile("schedule").absoluteURL().toString + ">; rel=\"profile\""))
        }
      }


  }

  def showProposalTypes(eventCode: String) = UserAgentAction {
    implicit request =>

      val ifNoneMatch = request.headers.get(IF_NONE_MATCH)
      val allProposalTypes = ConferenceDescriptor.ConferenceProposalTypes.ALL.map {
        proposalType =>
          Json.toJson {
            Map(
              "id" -> Json.toJson(proposalType.id)
              , "description" -> Json.toJson(Messages(proposalType.label))
              , "label" -> Json.toJson(Messages(proposalType.id))
            )
          }
      }
      val etag = allProposalTypes.hashCode().toString

      ifNoneMatch match {
        case Some(someEtag) if someEtag == etag => NotModified
        case other => {
          val jsonObject = Json.toJson(
            Map(
              "content" -> Json.toJson("All types of proposal"),
              "proposalTypes" -> Json.toJson(allProposalTypes)
            )
          )

          Ok(jsonObject).as(JSON).withHeaders(ETAG -> etag, "Links" -> ("<" + routes.RestAPI.profile("proposalType").absoluteURL().toString + ">; rel=\"profile\""))
        }
      }
  }
}

object UserAgentAction extends ActionBuilder[Request] with play.api.http.HeaderNames {
  override protected def invokeBlock[A](request: Request[A], block: (Request[A]) => Future[SimpleResult]): Future[SimpleResult] = {
    request.headers.get(USER_AGENT).collect {
      case some => {
        block(request)
      }
    }.getOrElse {
      Future.successful(play.api.mvc.Results.Forbidden("User-Agent is required to interact with " + Messages("longName") + " API"))
    }
  }
}

case class Link(href: String, rel: String, title: String)

object Link {
  implicit val linkFormat = Json.format[Link]
}

case class Conference(eventCode: String, label: String, locale: List[String], localisation: String, link: Link)

object Conference {

  implicit val confFormat = Json.format[Conference]

  def currentConference(implicit req: RequestHeader) = Conference(
    ConferenceDescriptor.current().eventCode,
    Messages("longYearlyName") + ", " + Messages(ConferenceDescriptor.current().timing.datesI18nKey),
    ConferenceDescriptor.current().locale,
    ConferenceDescriptor.current().localisation,
    Link(
      routes.RestAPI.showConference(ConferenceDescriptor.current().eventCode).absoluteURL().toString,
      routes.RestAPI.profile("conference").absoluteURL().toString,
      "See more details about " + Messages("longYearlyName")
    ))

  def all(implicit req: RequestHeader) = {
    List(currentConference)
  }

  // Super fast, super crade, super je m'en fiche pour l'instant
  def find(eventCode: String)(implicit req: RequestHeader): Option[Conference] = Option(currentConference)

}
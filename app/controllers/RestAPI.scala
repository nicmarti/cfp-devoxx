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

import controllers.Link.call2String
import models.Speaker._
import models._
import org.joda.time.{DateTime, DateTimeZone}
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.libs.json.{JsNull, JsObject, Json}
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

/**
  * A real REST api for men.
  * Created by Nicolas Martignole on 25/02/2014.
  */
object RestAPI extends Controller {

  def index = UserAgentActionAndAllowOrigin(implicit request => Ok(views.html.RestAPI.index()))

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
        case "tracks" => Ok(views.html.RestAPI.docTrack())
        case "track" => Ok(views.html.RestAPI.docTrack())
        case "room" => Ok(views.html.RestAPI.docRoom())
        case other => NotFound("Sorry, no documentation for this profile")
      }
  }

  def RSSFeedAcceptedProposals = Action { implicit request =>
    Ok(
      <rss version="2.0">
        <channel>
          <title>Accepted proposals</title>
          <link>{ ConferenceDescriptor.current().conferenceUrls.cfpHostname }</link>
          <description>Accepted Proposals</description>
          { Proposal.allAccepted().map { proposal =>
          <item>
            <title>{ proposal.title } by { proposal.allSpeakers.map(_.cleanName).mkString(", ")}
              { val speaker = Speaker.findByUUID(proposal.mainSpeaker).get
                if(speaker.cleanTwitter.nonEmpty) {
                  "(" + speaker.cleanTwitter.get + ")"
                }
              }
            </title>
            <link>http{if(ConferenceDescriptor.isHTTPSEnabled)"s"}://{ConferenceDescriptor.current().conferenceUrls.cfpHostname }/2018/talk/{proposal.id}</link>
            <description>{ proposal.summary }</description>
          </item>
        }}
        </channel>
      </rss>
    )
  }

  def showAllConferences() = UserAgentActionAndAllowOrigin {
    implicit request =>

      val conferences = Conference.all
      val eTag = conferences.hashCode.toString

      request.headers.get(IF_NONE_MATCH) match {
        case Some(tag) if tag == eTag => NotModified
        case _ =>
          val jsonObject = Json.obj("content" -> "All conferences", "links" -> conferences.map(_.link))

          Ok(jsonObject).withHeaders(ETAG -> eTag, "Links" -> ("<" + routes.RestAPI.profile("conferences").absoluteURL() + ">; rel=\"profile\""))
      }
  }

  def redirectToConferences = UserAgentActionAndAllowOrigin {
    implicit request => Redirect(routes.RestAPI.showAllConferences())
  }

  def showConference(eventCode: String) = UserAgentActionAndAllowOrigin {
    implicit request =>

      Conference.find(eventCode).map {
        conference: Conference =>

          val eTag = conference.eventCode

          request.headers.get(IF_NONE_MATCH) match {
            case Some(tag) if tag == eTag =>
              NotModified

            case _ =>

              val allProposalTypesIds = ConferenceDescriptor.ConferenceProposalTypes.ALL.map(_.id)

              val jsonObject = Json.toJson(conference).as[JsObject] ++ Json.obj(
                  "days" -> ConferenceDescriptor.current().timing.days.map(_.toString("EEEE", ConferenceDescriptor.current().locale.head)).toSeq,
                  "proposalTypesId" -> allProposalTypesIds,
                  //TODO

                  "links" -> List(
                    Link(
                      routes.RestAPI.showSpeakers(conference.eventCode),
                      routes.RestAPI.profile("list-of-speakers"),
                      "See all speakers"
                    ),
                    Link(
                      routes.RestAPI.showAllSchedules(conference.eventCode),
                      routes.RestAPI.profile("schedules"),
                      "See the whole agenda"
                    ),
                    Link(
                      routes.RestAPI.showProposalTypes(conference.eventCode),
                      routes.RestAPI.profile("proposalType"),
                      "See the different kind of conferences"
                    ),
                    Link(
                      routes.RestAPI.showTracks(conference.eventCode),
                      routes.RestAPI.profile("track"),
                      "See the different kind of tracks"
                    )
                  )

              )
              Ok(jsonObject).withHeaders(ETAG -> eTag,
                "Links" -> ("<" + routes.RestAPI.profile("conference").absoluteURL() + ">; rel=\"profile\""))
          }
      }.getOrElse(NotFound("Conference not found"))
  }

  // Load the list of Speakers, from the published Schedule
  def showSpeakers(eventCode: String) = UserAgentActionAndAllowOrigin {
    implicit request =>

      // First load published slots
      val publishedConf = ScheduleConfiguration.loadAllPublishedSlots().filter(_.proposal.isDefined)

      val allSpeakersIDs = publishedConf.flatMap(_.proposal.get.allSpeakerUUIDs).toSet

      val eTag = allSpeakersIDs.hashCode.toString

      request.headers.get(IF_NONE_MATCH) match {
        case Some(tag) if tag == eTag =>
          NotModified

        case other =>
          val onlySpeakersThatAcceptedTerms: Set[String] = allSpeakersIDs.filterNot(uuid => needsToAccept(uuid))
          val speakers = loadSpeakersFromSpeakerIDs(onlySpeakersThatAcceptedTerms)

          val updatedSpeakers = speakers.sortBy(_.name).map {
            speaker: Speaker =>
              Map(
                "uuid" -> Json.toJson(speaker.uuid),
                "firstName" -> speaker.firstName.map(Json.toJson(_)).getOrElse(JsNull),
                "lastName" -> speaker.name.map(Json.toJson(_)).getOrElse(JsNull),
                "avatarURL" -> speaker.avatarUrl.map(u => Json.toJson(u.trim())).getOrElse(JsNull),
                "twitter" -> speaker.twitter.map(u => Json.toJson(u.trim())).getOrElse(JsNull),
                "company" -> speaker.company.map(u => Json.toJson(u.trim())).getOrElse(JsNull),
                "links" -> Json.toJson(List(
                  Link(routes.RestAPI.showSpeaker(eventCode, speaker.uuid),
                    routes.RestAPI.profile("speaker"),
                    speaker.cleanName)
                )
                )
              )
          }

          val jsonObject = Json.toJson(updatedSpeakers)

          Ok(jsonObject).as(JSON).withHeaders(ETAG -> eTag,
            "Links" -> ("<" + routes.RestAPI.profile("list-of-speakers").absoluteURL() + ">; rel=\"profile\"")
          )
      }
  }

  def redirectToSpeakers(eventCode: String) = UserAgentActionAndAllowOrigin {
    implicit request =>
      Redirect(routes.RestAPI.showSpeakers(eventCode))
  }

  def showSpeaker(eventCode: String, uuid: String) = UserAgentActionAndAllowOrigin {
    implicit request =>

      findByUUID(uuid).map {
        speaker =>
          val eTag = speaker.hashCode.toString

          request.headers.get(IF_NONE_MATCH) match {
            case Some(tag) if tag == eTag =>
              NotModified

            case other =>
              val acceptedProposals = ApprovedProposal.allAcceptedTalksForSpeaker(speaker.uuid)

              val updatedTalks = acceptedProposals.map {
                proposal: Proposal =>
                  val allSpeakers = proposal.allSpeakerUUIDs.flatMap(uuid => findByUUID(uuid)).map {
                    speaker =>
                      Link(routes.RestAPI.showSpeaker(eventCode, speaker.uuid),
                        routes.RestAPI.profile("speaker"),
                        speaker.cleanName)
                  }

                  Map(
                    "id" -> Json.toJson(proposal.id),
                    "title" -> Json.toJson(proposal.title),
                    "track" -> Json.toJson(Messages(proposal.track.label)),
                    "talkType" -> Json.toJson(Messages(proposal.talkType.id)),
                    "links" -> Json.toJson(
                      List(
                        Link(routes.RestAPI.showTalk(eventCode, proposal.id),
                          routes.RestAPI.profile("talk"), "More details about this talk"
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
                  "twitter" -> speaker.cleanTwitter.map(Json.toJson(_)).getOrElse(JsNull),
                  "acceptedTalks" -> Json.toJson(updatedTalks)
                )

              val jsonObject = Json.toJson(updatedSpeaker)
              Ok(jsonObject).as(JSON).withHeaders(ETAG -> eTag, "Links" -> ("<" + routes.RestAPI.profile("speaker").absoluteURL() + ">; rel=\"profile\""))
          }
      }.getOrElse(NotFound("Speaker not found"))
  }

  def showTalk(eventCode: String, proposalId: String) = UserAgentActionAndAllowOrigin {
    implicit request =>
      Proposal.findById(proposalId).map {
        proposal =>
          val eTag = proposal.hashCode.toString

          request.headers.get(IF_NONE_MATCH) match {
            case Some(tag) if tag == eTag =>
              NotModified

            case other =>
              val allSpeakers = proposal.allSpeakerUUIDs.flatMap(uuid => findByUUID(uuid))

              val updatedProposal =
                Map(
                  "id" -> Json.toJson(proposal.id),
                  "title" -> Json.toJson(proposal.title),
                  "talkType" -> Json.toJson(Messages(proposal.talkType.id)),
                  "lang" -> Json.toJson(proposal.lang),
                  "summary" -> Json.toJson(proposal.summary),
                  "summaryAsHtml" -> Json.toJson(proposal.summaryAsHtml),
                  "track" -> Json.toJson(Messages(proposal.track.label)),
                  "trackId" -> Json.toJson(proposal.track.id),
                  "speakers" -> Json.toJson(allSpeakers.map {
                    speaker =>
                      Map(
                        "link" -> Json.toJson(
                          Link(
                            routes.RestAPI.showSpeaker(eventCode, speaker.uuid),
                            routes.RestAPI.profile("speaker"),
                            speaker.cleanName
                          )
                        ),
                        "name" -> Json.toJson(speaker.cleanName)
                      )
                  })
                )
              val jsonObject = Json.toJson(updatedProposal)
              Ok(jsonObject).as(JSON).withHeaders(ETAG -> eTag)
          }
      }.getOrElse(NotFound("Proposal not found"))
  }

  def redirectToTalks(eventCode: String) = UserAgentActionAndAllowOrigin {
    implicit request =>
      Redirect(routes.RestAPI.showApprovedTalks(eventCode))
  }

  def showApprovedTalks(eventCode: String) = UserAgentActionAndAllowOrigin {
    implicit request =>
      import models.Proposal.proposalFormat

      // TODO filter on the specified eventCode and not on stupidEventCode when Proposal is updated
      // We cannot right now, as we stored the Proposal with event==Message("longYearlyName") See Proposal.scala in validateNEwProposal
      // So I need to do a temporary filter
      // val proposals = ApprovedProposal.allApproved().filterNot(_.event==eventCode).toList.sortBy(_.title)

      val stupidEventCode = Messages("longYearlyName") // Because the value in the DB for Devoxx BE 2015 is not valid
      val proposals = ApprovedProposal.allApproved().filter(_.event == stupidEventCode).toList.sortBy(_.title)

      val eTag = proposals.hashCode.toString

      request.headers.get(IF_NONE_MATCH) match {
        case Some(tag) if tag == eTag =>
          NotModified

        case other =>
          val proposalsWithSpeaker = proposals.map {
            p: Proposal =>
              val mainWebuser = findByUUID(p.mainSpeaker)
              val secWebuser = p.secondarySpeaker.flatMap(findByUUID)
              val oSpeakers = p.otherSpeakers.map(findByUUID)
              val preferredDay = Proposal.getPreferredDay(p.id)

              // Transform speakerUUID to Speaker name, this simplify Angular Code
              p.copy(
                mainSpeaker = mainWebuser.map(_.cleanName).getOrElse("")
                , secondarySpeaker = secWebuser.map(_.cleanName)
                , otherSpeakers = oSpeakers.flatMap(s => s.map(_.cleanName))
                , privateMessage = preferredDay.getOrElse("")
              )
          }

          val finalJson = Map(
            "talks" -> Json.toJson(
              Map(
                "approved" -> Json.toJson(proposalsWithSpeaker.filter(_.state == ProposalState.APPROVED)),
                "accepted" -> Json.toJson(proposalsWithSpeaker.filter(_.state == ProposalState.ACCEPTED))
              )
            )
          )

          val jsonObject = Json.toJson(finalJson)

          Ok(jsonObject).as(JSON).withHeaders(ETAG -> eTag,
            "Links" -> ("<" + routes.RestAPI.profile("list-of-approved-talks").absoluteURL() + ">; rel=\"profile\"")
          )
      }
  }

  def showAllSchedules(eventCode: String) = UserAgentActionAndAllowOrigin {
    implicit request =>

      val ifNoneMatch = request.headers.get(IF_NONE_MATCH)
      val mapOfSchedules = Map(
        "links" -> Json.toJson(List(
          Link(
            routes.RestAPI.showScheduleFor(eventCode, "monday"),
            routes.RestAPI.profile("schedule"),
            Messages("sw.show.title.mon")
          ), Link(
            routes.RestAPI.showScheduleFor(eventCode, "tuesday"),
            routes.RestAPI.profile("schedule"),
            Messages("sw.show.title.tue")
          ),
          Link(
            routes.RestAPI.showScheduleFor(eventCode, "wednesday"),
            routes.RestAPI.profile("schedule"),
            Messages("sw.show.title.wed")
          ),
          Link(
            routes.RestAPI.showScheduleFor(eventCode, "thursday"),
            routes.RestAPI.profile("schedule"),
            Messages("sw.show.title.thu")
          ),
          Link(
            routes.RestAPI.showScheduleFor(eventCode, "friday"),
            routes.RestAPI.profile("schedule"),
            Messages("sw.show.title.fri")
          )
        ))
      )
      val newEtag = mapOfSchedules.hashCode().toString

      ifNoneMatch match {
        case Some(someEtag) if someEtag == newEtag => NotModified
        case other =>
          val jsonObject = Json.toJson(mapOfSchedules)
          Ok(jsonObject).as(JSON).withHeaders(ETAG -> newEtag, "Links" -> ("<" + routes.RestAPI.profile("schedules").absoluteURL() + ">; rel=\"profile\""))
      }
  }

  def showScheduleForConfType(eventCode: String, confType: String) = UserAgentActionAndAllowOrigin {
    implicit request =>

      val ifNoneMatch = request.headers.get(IF_NONE_MATCH)
      val finalListOfSlots = ScheduleConfiguration.loadSlotsForConfType(confType)
      val newEtag = finalListOfSlots.hashCode().toString

      ifNoneMatch match {
        case Some(someEtag) if someEtag == newEtag => NotModified
        case other =>
          val toReturn = finalListOfSlots.map {
            slot =>
              val upProposal = slot.proposal.map {
                proposal =>
                  val allSpeakers = proposal.allSpeakerUUIDs.flatMap(uuid => findByUUID(uuid))
                  val updatedProposal =
                    Map(
                      "id" -> Json.toJson(proposal.id),
                      "title" -> Json.toJson(proposal.title),
                      "lang" -> Json.toJson(proposal.lang),
                      "summaryAsHtml" -> Json.toJson(proposal.summaryAsHtml),
                      "summary" -> Json.toJson(proposal.summary),
                      "track" -> Json.toJson(Messages(proposal.track.label)),
                      "trackId" -> Json.toJson(proposal.track.id),
                      "talkType" -> Json.toJson(Messages(proposal.talkType.id)),
                      "speakers" -> Json.toJson(allSpeakers.map {
                        speaker =>
                          Map(
                            "link" -> Json.toJson(
                              Link(
                                routes.RestAPI.showSpeaker(eventCode, speaker.uuid),
                                routes.RestAPI.profile("speaker"),
                                speaker.cleanName
                              )
                            ),
                            "name" -> Json.toJson(speaker.cleanName)
                          )
                      })
                    )
                  updatedProposal
              }

              val fromDate = new DateTime(slot.from.getMillis).toDateTime(DateTimeZone.forID("Europe/Brussels"))
              val slotToDate = new DateTime(slot.to.getMillis).toDateTime(DateTimeZone.forID("Europe/Brussels"))

              Map(
                "slotId" -> Json.toJson(slot.id)
                , "day" -> Json.toJson(slot.day)
                , "roomId" -> Json.toJson(slot.room.id)
                , "roomName" -> Json.toJson(slot.room.name)
                , "fromTime" -> Json.toJson(fromDate.toString("HH:mm"))
                , "fromTimeMillis" -> Json.toJson(fromDate.getMillis)
                , "toTime" -> Json.toJson(slotToDate.toString("HH:mm"))
                , "toTimeMillis" -> Json.toJson(slotToDate.getMillis)
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
          Ok(jsonObject).as(JSON).withHeaders(ETAG -> newEtag, "Links" -> ("<" + routes.RestAPI.profile("schedule").absoluteURL() + ">; rel=\"profile\""))
      }
  }

  def showScheduleFor(eventCode: String, day: String) = UserAgentActionAndAllowOrigin {
    implicit request =>

      val ifNoneMatch = request.headers.get(IF_NONE_MATCH)
      val finalListOfSlots = ScheduleConfiguration.getPublishedScheduleByDay(day)
      val newEtag = "v2_" + finalListOfSlots.hashCode().toString

      ifNoneMatch match {
        case Some(someEtag) if someEtag == newEtag => NotModified
        case other =>
          val toReturn = finalListOfSlots.map {
            slot =>
              val upProposal = slot.proposal.map {
                proposal =>
                  val allSpeakers = proposal.allSpeakerUUIDs.flatMap(uuid => findByUUID(uuid))
                  val updatedProposal =
                    Map(
                      "id" -> Json.toJson(proposal.id),
                      "title" -> Json.toJson(proposal.title),
                      "lang" -> Json.toJson(proposal.lang),
                      "summaryAsHtml" -> Json.toJson(proposal.summaryAsHtml),
                      "summary" -> Json.toJson(proposal.summary),
                      "track" -> Json.toJson(Messages(proposal.track.label)),
                      "trackId" -> Json.toJson(proposal.track.id),
                      "talkType" -> Json.toJson(Messages(proposal.talkType.id)),
                      "speakers" -> Json.toJson(allSpeakers.map {
                        speaker =>
                          Map(
                            "link" -> Json.toJson(
                              Link(
                                routes.RestAPI.showSpeaker(eventCode, speaker.uuid),
                                routes.RestAPI.profile("speaker"),
                                speaker.cleanName
                              )
                            ),
                            "name" -> Json.toJson(speaker.cleanName)
                          )
                      })
                    )
                  updatedProposal
              }

              val fromDate = new DateTime(slot.from.getMillis).toDateTime(DateTimeZone.forID("Europe/Brussels"))
              val slotToDate = new DateTime(slot.to.getMillis).toDateTime(DateTimeZone.forID("Europe/Brussels"))

              Map(
                "slotId" -> Json.toJson(slot.id)
                , "day" -> Json.toJson(slot.day)
                , "roomId" -> Json.toJson(slot.room.id)
                , "roomName" -> Json.toJson(slot.room.name)
                , "fromTime" -> Json.toJson(fromDate.toString("HH:mm"))
                , "fromTimeMillis" -> Json.toJson(fromDate.getMillis)
                , "toTime" -> Json.toJson(slotToDate.toString("HH:mm"))
                , "toTimeMillis" -> Json.toJson(slotToDate.getMillis)
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
          Ok(jsonObject).as(JSON).withHeaders(ETAG -> newEtag, "Links" -> ("<" + routes.RestAPI.profile("schedule").absoluteURL() + ">; rel=\"profile\""))
      }
  }

  def showProposalTypes(eventCode: String) = UserAgentActionAndAllowOrigin {
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
      val eTag = allProposalTypes.hashCode().toString

      ifNoneMatch match {
        case Some(someEtag) if someEtag == eTag => NotModified
        case other =>
          val jsonObject = Json.toJson(
            Map(
              "content" -> Json.toJson("All types of proposal"),
              "proposalTypes" -> Json.toJson(allProposalTypes)
            )
          )

          Ok(jsonObject).as(JSON).withHeaders(ETAG -> eTag, "Links" -> ("<" + routes.RestAPI.profile("proposalType").absoluteURL() + ">; rel=\"profile\""))
      }
  }

  def showTracks(eventCode: String) = UserAgentActionAndAllowOrigin {
    implicit request =>

      val ifNoneMatch = request.headers.get(IF_NONE_MATCH)
      val allTracks = ConferenceDescriptor.ConferenceTracksDescription.ALL.map {
        trackDesc =>
          Json.toJson {
            Map(
              "id" -> Json.toJson(trackDesc.id)
              , "imgsrc" -> Json.toJson(trackDesc.imgSrc)
              , "title" -> Json.toJson(Messages(trackDesc.i18nTitleProp))
              , "description" -> Json.toJson(Messages(trackDesc.i18nDescProp))
            )
          }
      }
      val eTag = allTracks.hashCode().toString

      ifNoneMatch match {
        case Some(someEtag) if someEtag == eTag => NotModified
        case other =>
          val jsonObject = Json.toJson(
            Map(
              "content" -> Json.toJson("All tracks"),
              "tracks" -> Json.toJson(allTracks)
            )
          )

          Ok(jsonObject).as(JSON).withHeaders(ETAG -> eTag, "Links" -> ("<" + routes.RestAPI.profile("track").absoluteURL() + ">; rel=\"profile\""))
      }
  }

  def showRooms(eventCode: String) = UserAgentActionAndAllowOrigin {
    implicit request =>

      val ifNoneMatch = request.headers.get(IF_NONE_MATCH)
      val allRooms = ConferenceDescriptor.ConferenceRooms.allRooms.map {
        room =>
          Json.toJson {
            Map(
              "id" -> Json.toJson(room.id)
              , "name" -> Json.toJson(room.name)
              , "capacity" -> Json.toJson(room.capacity)
              , "setup" -> Json.toJson(room.setup)
            )
          }
      }
      val eTag = allRooms.hashCode().toString

      ifNoneMatch match {
        case Some(someEtag) if someEtag == eTag => NotModified
        case other =>
          val jsonObject = Json.toJson(
            Map(
              "content" -> Json.toJson("All rooms"),
              "rooms" -> Json.toJson(allRooms)
            )
          )

          Ok(jsonObject).as(JSON).withHeaders(
            ETAG -> eTag,
            "Access-Control-Allow-Origin" -> "*",
            "Links" -> ("<" + routes.RestAPI.profile("room").absoluteURL() + ">; rel=\"profile\""))
      }
  }

  def showScheduleForRoom(eventCode: String, room: String, day: String) = UserAgentActionAndAllowOrigin {
    implicit request =>

      val ifNoneMatch = request.headers.get(IF_NONE_MATCH)
      val finalListOfSlots = ScheduleConfiguration.getPublishedScheduleByDay(day)
      val newEtag = "v2-" + room.hashCode + finalListOfSlots.hashCode().toString

      ifNoneMatch match {
        case Some(someEtag) if someEtag == newEtag => NotModified
        case other =>
          val toReturn = finalListOfSlots.filter(_.room.id == room).map {
            slot =>
              val upProposal = slot.proposal.map {
                proposal =>
                  val allSpeakers = proposal.allSpeakerUUIDs.flatMap {
                    uuid => findByUUID(uuid)
                  }
                  val updatedProposal =
                    Map(
                      "id" -> Json.toJson(proposal.id),
                      "title" -> Json.toJson(proposal.title),
                      "lang" -> Json.toJson(proposal.lang),
                      "summaryAsHtml" -> Json.toJson(proposal.summaryAsHtml),
                      "summary" -> Json.toJson(proposal.summary),
                      "track" -> Json.toJson(Messages(proposal.track.label)),
                      "trackId" -> Json.toJson(proposal.track.id),
                      "talkType" -> Json.toJson(Messages(proposal.talkType.id)),
                      "speakers" -> Json.toJson(allSpeakers.map {
                        speaker =>
                          Map(
                            "link" -> Json.toJson(
                              Link(
                                routes.RestAPI.showSpeaker(eventCode, speaker.uuid),
                                routes.RestAPI.profile("speaker"),
                                speaker.cleanName
                              )
                            ),
                            "name" -> Json.toJson(speaker.cleanName)
                          )
                      })
                    )
                  updatedProposal
              }

              val fromDate = new DateTime(slot.from.getMillis).toDateTime(DateTimeZone.forID("Europe/Brussels"))
              val slotToDate = new DateTime(slot.to.getMillis).toDateTime(DateTimeZone.forID("Europe/Brussels"))

              Map(
                "slotId" -> Json.toJson(slot.id)
                , "day" -> Json.toJson(slot.day)
                , "roomId" -> Json.toJson(slot.room.id)
                , "roomName" -> Json.toJson(slot.room.name)
                , "fromTime" -> Json.toJson(fromDate.toString("HH:mm"))
                , "fromTimeMillis" -> Json.toJson(fromDate.getMillis)
                , "toTime" -> Json.toJson(slotToDate.toString("HH:mm"))
                , "toTimeMillis" -> Json.toJson(slotToDate.getMillis)
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
          Ok(jsonObject).as(JSON).withHeaders(ETAG -> newEtag, "Links" -> ("<" + routes.RestAPI.profile("schedule").absoluteURL() + ">; rel=\"profile\""))
      }
  }

  def topFavedTalks(eventCode: String, limit: Int) = UserAgentActionAndAllowOrigin {
    implicit request =>

      val ifNoneMatch = request.headers.get(IF_NONE_MATCH)
      val topFavedTalks = FavoriteTalk.all().toList.sortBy(_._2).reverse.take(limit)
      val newEtag = "t_" + topFavedTalks.hashCode().toString

      ifNoneMatch match {
        case Some(someEtag) if someEtag == newEtag => NotModified
        case other =>
          val toReturn = topFavedTalks.map {
            case (proposal, vote) =>

              val updatedProposalWithLink = {
                val allSpeakers = proposal.allSpeakerUUIDs.flatMap(uuid => findByUUID(uuid)).map {
                  speaker =>
                    Link(routes.RestAPI.showSpeaker(eventCode, speaker.uuid),
                      routes.RestAPI.profile("speaker"),
                      speaker.cleanName)
                }

                Map(
                  "id" -> Json.toJson(proposal.id),
                  "title" -> Json.toJson(proposal.title),
                  "talkType" -> Json.toJson(Messages(proposal.talkType.id)),
                  "talkTypeId" -> Json.toJson(proposal.talkType.id),
                  "links" -> Json.toJson(
                    List(
                      Link(routes.RestAPI.showTalk(eventCode, proposal.id),
                        routes.RestAPI.profile("talk"), "More details about this talk"
                      )
                    ).++(allSpeakers)
                  )
                )
              }

              val maybeSlot = {
                ScheduleConfiguration.findSlotForConfType(proposal.talkType.id, proposal.id).map {
                  slot =>
                    val fromDate = new DateTime(slot.from.getMillis).toDateTime(DateTimeZone.forID("Europe/Brussels"))
                    val slotToDate = new DateTime(slot.to.getMillis).toDateTime(DateTimeZone.forID("Europe/Brussels"))

                    Map(
                      "slotId" -> Json.toJson(slot.id)
                      , "day" -> Json.toJson(slot.day)
                      , "roomId" -> Json.toJson(slot.room.id)
                      , "roomName" -> Json.toJson(slot.room.name)
                      , "fromTime" -> Json.toJson(fromDate.toString("HH:mm"))
                      , "fromTimeMillis" -> Json.toJson(fromDate.getMillis)
                      , "toTime" -> Json.toJson(slotToDate.toString("HH:mm"))
                      , "toTimeMillis" -> Json.toJson(slotToDate.getMillis)
                      , "talk" -> Json.toJson(updatedProposalWithLink)
                      , "break" -> Json.toJson(slot.break)
                      , "roomSetup" -> Json.toJson(slot.room.setup)
                      , "roomCapacity" -> Json.toJson(slot.room.capacity)
                      , "notAllocated" -> Json.toJson(slot.notAllocated)
                    )
                }
              }

              Map(
                "vote" -> Json.toJson(vote),
                "slot" -> maybeSlot.map(Json.toJson(_)).getOrElse(JsNull)
              )
          }
          val jsonObject = Json.toJson(
            Map(
              "topTalks" -> Json.toJson(toReturn)
            )
          )
          Ok(jsonObject).as(JSON).withHeaders(ETAG -> newEtag)
      }
  }


  /**
    * Verify a user account.
    * This can also create a new user when the email does not exist!
    *
    * @return
    */
  val verifyAccountForm=Form(
    tuple(
      "email"->email,
      "networkId"->optional(text),
      "networkType"->optional(text)
    )
  )

  def verifyAccount() = UserAgentActionAndAllowOrigin {
    implicit request =>
      verifyAccountForm.bindFromRequest().fold(
        invalidForm=>{
          BadRequest(invalidForm.errorsAsJson).as(JSON)
        },
        validTuple=>{
          val email = validTuple._1
          val newNetworkType = validTuple._2
          val newNetworkId = validTuple._3
          Webuser.findByEmail(email) match {
            case Some(foundUser)=>
            // Update users social network credentials
            Webuser.update(foundUser.copy(networkType = newNetworkType, networkId = newNetworkId))
            Ok(foundUser.uuid)

            case None=>
            // User does not exist, lets create
            val devoxxian = Webuser.createDevoxxian(email, newNetworkType, newNetworkId)
            val uuid = Webuser.saveAndValidateWebuser(devoxxian)
            Webuser.addToDevoxxians(uuid)
            Created(uuid)
          }
        }
      )
  }
}

object UserAgentActionAndAllowOrigin extends ActionBuilder[Request] with play.api.http.HeaderNames {

  import ExecutionContext.Implicits.global

  override def invokeBlock[A](request: Request[A], block: (Request[A]) => Future[Result]): Future[Result] = {
    request.headers.get(USER_AGENT).collect {
      case some =>
        block(request).map { result =>
          request.headers.get("Origin") match {
            case Some(o) => result.withHeaders("Access-Control-Allow-Origin" -> o,
              "Access-Control-Expose-Headers" -> "etag,links",
              "Access-Control-Allow-Credentials" -> "true",
              "Access-Control-Allow-Headers" -> "Origin, X-Requested-With, Content-Type, Accept",
              "Access-Control-Max-Age" -> "3600")
            case None => result.withHeaders("X-No-Access" -> "no-origin")
          }
        }
    }.getOrElse {
      Future.successful(play.api.mvc.Results.Forbidden("User-Agent is required to interact with " + Messages("longName") + " API"))
    }
  }
}

case class Link(href: String, rel: String, title: String)

object Link {

  implicit val linkFormat = Json.format[Link]
  implicit def call2String(c : Call)(implicit requestHeader: RequestHeader ):String =  c.absoluteURL()
}

case class Conference(eventCode: String, label: String, locale: List[String], localisation: String, link: Link)

object Conference {

  implicit val confFormat = Json.format[Conference]

  def all(implicit req: RequestHeader) = {
    List(currentConference)
  }

  def currentConference(implicit req: RequestHeader) = Conference(
    ConferenceDescriptor.current().eventCode,
    Messages("longYearlyName") + ", " + Messages(ConferenceDescriptor.current().timing.datesI18nKey),
    ConferenceDescriptor.current().locale.map(_.toString),
    ConferenceDescriptor.current().localisation,
    Link(
      routes.RestAPI.showConference(ConferenceDescriptor.current().eventCode),
      routes.RestAPI.profile("conference"),
      "See more details about " + Messages("longYearlyName")
    ))

  // Super fast, super crade, super je m'en fiche pour l'instant
  def find(eventCode: String)(implicit req: RequestHeader): Option[Conference] = Option(currentConference)

}
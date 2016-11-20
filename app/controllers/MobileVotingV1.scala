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

import java.util.Date

import models._
import play.api.Play
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.libs.json._
import play.api.mvc.Action

/**
  * Mobile App voting REST API.
  * See also the Swagger definition
  *
  * @see https://github.com/nicmarti/cfp-devoxx/blob/dev/conf/swagger_voting.yml
  *      Backport of Jon Mort API.
  * @author created by N.Martignole, Innoteria, on 23/05/2016.
  */
object MobileVotingV1 extends SecureCFPController {
  val voteForm: Form[Rating] = Form(
    mapping(
      "talkId" -> nonEmptyText(maxLength = 50),
      "user" -> nonEmptyText(maxLength = 50),
      "rating" -> optional(number(min = 1, max = 5)),
      "details" -> seq(
        mapping(
          "aspect" -> nonEmptyText(maxLength = 50),
          "rating" -> number(min = 1, max = 5),
          "review" -> optional(text(maxLength = 200))
        )(RatingDetail.apply)(RatingDetail.unapply)
      )
    )(Rating.createNew)(Rating.unapplyRating) verifying("Failed form constraints!", fields => fields match {
      case userData =>
        userData.details.nonEmpty
    })
  )

  def acceptVoteForTalk() = UserAgentActionAndAllowOrigin {
    implicit request =>

      Play.current.configuration.getBoolean("mobile.vote.isActive").filter(_ == true).map {
        _ =>
          voteForm.bindFromRequest().fold(
            hasErrors => {
              play.Logger.of("controllers.MobileVotingV1").warn(s"Bad Request due to ${hasErrors.errorsAsJson}")
              BadRequest(hasErrors.errorsAsJson).as(JSON)
            },
            validRating => {
              Proposal.findById(validRating.talkId) match {
                case None =>
                  NotFound(Json.obj("reason" -> "Talk not found")).as(JSON)
                case Some(p) =>
                  Rating.findForUserIdAndProposalId(validRating.user, validRating.talkId) match {
                    case Some(existingRating) =>
                      val updatedRating = existingRating.copy(timestamp = new Date().getTime, details = validRating.details)
                      Rating.saveNewRating(updatedRating)
                      Accepted(Json.toJson(updatedRating)).as(JSON)
                    case None =>
                      Rating.saveNewRating(validRating)
                      Created(Json.toJson(validRating)).as(JSON)
                  }
              }
            }
          )
      }.getOrElse {
        ServiceUnavailable("Vote is closed for this talk, you cannot vote anymore.")
      }
  }

  def allVotesForTalk(talkId: String) = UserAgentActionAndAllowOrigin {
    implicit request =>
      Proposal.findById(talkId) match {
        case None => NotFound(Json.obj("reason" -> "Talk not found"))
        case Some(proposal) =>
          Rating.allRatingsForSpecificTalkId(proposal.id) match {
            case Nil => NoContent.as(JSON)
            case ratings =>

              val totalVotes: List[Int] = ratings.flatMap(r => r.allVotes)
              // TODO The old API wants String and not JSON Number
              val sum: Int = totalVotes.sum
              val count: Int = totalVotes.size
              val avg = if (count == 0) {
                0
              } else {
                sum / count
              }

              val jsonResult = Json.obj(
                "sum" -> sum.toString,
                "count" -> count.toString,
                "title" -> proposal.title,
                "summary" -> proposal.summaryAsHtml,
                "avg" -> avg.toString,
                "name" -> s"${proposal.id}",
                "type" -> s"${proposal.talkType.label}",
                "typeId" -> s"${proposal.talkType.id}",
                "track" -> s"${proposal.track.label}",
                "trackId" -> s"${proposal.track.id}",
                "speakers" -> Json.arr(
                  "Robert Munteanu"
                )
              )
              Ok(jsonResult).as(JSON)
          }
      }
  }

  // Returns the top talks for the Tweet wall.
  // floorPct is a special parameter that will filterOut the talks that don't have at least 80% of the total vote
  // This is to avoid a top ten where a talk with only one vote sets to 10 is above a talk with 100 votes and a score of 9.8
  def topTalks(day: Option[String]
               , talkTypeId: Option[String]
               , trackId: Option[String]
               , limit: Int = 10 // Stephan => if you want only 10 talks
               , floorPct: Int = 0 // This loads all talk, calculate the average number of votes and take only talks with at most 50% of votes + floor
               , hideScoreLowerThan: Int = 3 // If you want to hide the not-so-good talks, here is the param. It could be a Double so that you set 3.56 for instance
              ) = UserAgentActionAndAllowOrigin {
    implicit request =>
      // Use the limit parameter to take only 5, 10 or X results
      val allRatings = loadTopTalks(day, talkTypeId, trackId)

      val sortedRatings = sortByScoreAndKeepTopVotes(allRatings, floorPct)

      val onlyXXXResults: List[(Proposal, List[Rating])] = sortedRatings.filter(t => Rating.calculateScore(t._2) >= hideScoreLowerThan).take(limit)

      if (onlyXXXResults.isEmpty) {
        NoContent.as(JSON)
      } else {
        // JSON Serializer

        val jsonResult = onlyXXXResults.map {
          case (proposal, ratings) =>
            Json.obj(
              "proposalId" -> Json.toJson(proposal.id),
              "proposalTitle" -> Json.toJson(proposal.title),
              "proposalTalkType" -> Json.toJson(Messages(proposal.talkType.id)),
              "proposalTalkTypeId" -> Json.toJson(proposal.talkType.id),
              "ratingAverageScore" -> Json.toJson(Rating.calculateScore(ratings)),
              "ratingTotalVotes" -> Json.toJson(ratings.length),
              "proposalsSpeakers" -> Json.toJson(proposal.allSpeakers.map(_.cleanName).mkString(", "))
            )
        }

        val result = Map(
          "day" -> day.map(d => JsString(d)).getOrElse(JsNull)
          , "talkTypeId" -> talkTypeId.map(d => JsString(d)).getOrElse(JsNull)
          , "trackId" -> trackId.map(d => JsString(d)).getOrElse(JsNull)
          , "totalResults" -> JsNumber(sortedRatings.size)
          , "talks" -> JsArray(jsonResult)
        )

        val finalResult = Json.obj("result" -> Json.toJson(result))
        Ok(finalResult).as(JSON)
      }
  }

  // Code written during Devoxx BE 2016. I could have used the HTTP header Accept but I didn't want to explode the TwitterWall
  def topTalksAsHtml(day: Option[String], talkTypeId: Option[String], trackId: Option[String], limit: Int = 10, floorPct: Int = 0, hideScoreLowerThan: Int = 3) = Action {
    implicit request =>
      val allRatings: Map[Proposal, List[Rating]] = loadTopTalks(day, talkTypeId, trackId)

      val sortedRatings = sortByScoreAndKeepTopVotes(allRatings, floorPct)

      val onlyXXXResults: List[(Proposal, List[Rating])] = sortedRatings.filter(t => Rating.calculateScore(t._2) >= hideScoreLowerThan).take(limit)

      if (onlyXXXResults.isEmpty) {
        NoContent
      } else {
        Ok(views.html.CFPAdmin.topTalksAsHtml(onlyXXXResults))
      }
  }

  // Public call for a simple Web page for Devoxx Belgium 2016
  def topTalksForDevoxx(day: Option[String]) = Action {
    implicit request =>
      val allRatings: Map[Proposal, List[Rating]] = loadTopTalks(day, None, None)

      val sortedRatings = sortByScoreAndKeepTopVotes(allRatings, floorPct = 0)

      val onlyXXXResults: List[(Proposal, List[Rating])] = sortedRatings.filter(t => Rating.calculateScore(t._2) >= 3).take(100)

      if (onlyXXXResults.isEmpty) {
        NoContent
      } else {
        Ok(views.html.CFPAdmin.topTalksDevoxxBE2016(onlyXXXResults, day.getOrElse("wed")))
      }
  }

  private def loadTopTalks(day: Option[String], talkTypeId: Option[String], trackId: Option[String]): Map[Proposal, List[Rating]] = {
    // create a list of Proposals
    // Will try to filter either from the URL params (talkTypeID, trackId) or use the Rating

    val allProposalsToLoad: List[Proposal] = day match {
      case None => {
        // Load all ratings because no day was specified, thus it's faster
        val allRatings = Rating.allRatings()
        val talkIds = allRatings.map(_.talkId)
        Proposal.loadAndParseProposals(talkIds.toSet).values.toList
      }

      // If one day was specified then we needs to load the schedule.
      case Some(specifiedDay) => {
        def publishedProposalsForOneDay(slots: List[Slot], day: String): List[Proposal] = {
          val allSlots = ScheduleConfiguration.getPublishedScheduleByDay(day)
          allSlots.flatMap(slot => slot.proposal)
        }

        val proposalsForThisDay: List[Proposal] = specifiedDay match {
          case d if Set("mon", "monday").contains(d) => publishedProposalsForOneDay(models.ConferenceDescriptor.ConferenceSlots.mondaySchedule, "monday")
          case d if Set("tue", "tuesday").contains(d) => publishedProposalsForOneDay(models.ConferenceDescriptor.ConferenceSlots.tuesdaySchedule, "tuesday")
          case d if Set("wed", "wednesday").contains(d) => publishedProposalsForOneDay(models.ConferenceDescriptor.ConferenceSlots.wednesdaySchedule, "wednesday")
          case d if Set("thu", "thursday").contains(d) => publishedProposalsForOneDay(models.ConferenceDescriptor.ConferenceSlots.thursdaySchedule, "thursday")
          case d if Set("fri", "friday").contains(d) => publishedProposalsForOneDay(models.ConferenceDescriptor.ConferenceSlots.fridaySchedule, "friday")
          case other => {
            play.Logger.of("MobileVotingV1").error(s"Received an invalid day value, got $specifiedDay but expected monday/tuesday/wednesday...")
            Nil
          }
        }
        proposalsForThisDay
      }
    }

    // 2. Now the list of Proposals has to be filtered
    val proposalsToLoad = (talkTypeId, trackId) match {
      case (None, None) =>
        allProposalsToLoad
      case (Some(someTalkType), None) =>
        allProposalsToLoad.filter(_.talkType.id == someTalkType)
      case (None, Some(someTrackId)) =>
        allProposalsToLoad.filter(_.track.id == someTrackId)
      case (Some(someTalkType), Some(someTrackId)) =>
        allProposalsToLoad.filter(p => p.track.id == someTrackId && p.talkType.id == someTalkType)
    }

    // We can finally load the Ratings from the list of Proposals
    val allRatingsFiltered: Map[Proposal, List[Rating]] = Rating.allRatingsForTalks(proposalsToLoad)

    allRatingsFiltered
  }

  def sortByScoreAndKeepTopVotes(ratings: Map[Proposal, List[Rating]], floorPct: Int): List[(Proposal, List[Rating])] = {

    val groupedByNumberOfVotes = ratings.groupBy(_._2.size).toList.sortBy(_._1).reverse
    val totalTalksEvaluated = groupedByNumberOfVotes.size
    val averageNumberOfVotes = groupedByNumberOfVotes.map(_._1).sum / totalTalksEvaluated

    // println(groupedByNumberOfVotes.map(_._1).sorted.reverse)

    // println("averageNumber of votes " +averageNumberOfVotes)

    // Keep only talks with number of votes > average

    val onlyWithEnoughVotes = groupedByNumberOfVotes.sortBy(_._1).reverse.takeWhile(_._1 >= averageNumberOfVotes - floorPct)

    // println("Got "+onlyWithEnoughVotes.map(_._1))

    val onlyRatinsAndProposals = onlyWithEnoughVotes.flatMap(_._2)

    val sortedByScore = onlyRatinsAndProposals.sortBy {
      case (_, rt) =>
        Rating.calculateScore(rt)
    }.reverse

    //    sortedByScore.foreach{
    //      case(proposal,r)=>
    //        println(s"${proposal.title} ${Rating.calculateScore(r)}")
    //    }

    sortedByScore
  }


  def categories() = UserAgentActionAndAllowOrigin {
    implicit request =>
      MovedPermanently(routes.RestAPI.showTracks(Conference.currentConference.eventCode).absoluteURL())
  }
}
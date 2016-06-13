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

import models.{Proposal, Rating, RatingDetail}
import play.api.Play
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json.Json

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
    )(Rating.createNew)(Rating.unapplyRating _) verifying("Failed form constraints!", fields => fields match {
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
                    case Some(existingRating)=>
                      val updatedRating = existingRating.copy(timestamp = new Date().getTime , details = validRating.details )
                      Rating.saveNewRating(updatedRating)
                      Accepted(Json.toJson(updatedRating)).as(JSON)
                    case None=>
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

              val totalVotes: List[Int] = ratings.flatMap(r => r.details.map(_.rating))
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

  def topTalks(day: Option[String], talkType: Option[String], track: Option[String]) = UserAgentActionAndAllowOrigin {
    implicit request =>
      NotImplemented(Json.obj("reason" -> "Not yet implemented, stay tuned")).as(JSON)
  }

  def categories() = UserAgentActionAndAllowOrigin {
    implicit request =>
      MovedPermanently(routes.RestAPI.showTracks(Conference.currentConference.eventCode).absoluteURL())
  }

}

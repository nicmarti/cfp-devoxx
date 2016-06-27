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

import java.io.File

import com.fasterxml.jackson.databind.JsonNode
import models._
import org.joda.time.DateTime
import play.api.data.Form
import play.api.data.Forms._
import play.api.mvc.Action
import play.libs.Json
import play.utils.UriEncoding

/**
  * Backoffice controller used to store rating and reviews sent by the mobile
  * application, back to the CFP.
  * The same system is used also for Speakers, to review their rating.
  *
  * @author created by N.Martignole, Innoteria, on 08/05/2016.
  */

object RatingController extends SecureCFPController {

  val ratingForm = Form(mapping(
    "talkId" -> nonEmptyText(maxLength = 50),
    "user" -> nonEmptyText(maxLength = 50),
    "conference" -> nonEmptyText(maxLength = 25),
    "timestamp" -> longNumber,
    "details" -> list(
      mapping(
        "aspect" -> nonEmptyText,
        "rating" -> number(min = 0, max = 5),
        "review" -> optional(text)

      )(RatingDetail.apply)(RatingDetail.unapply _)
    )
  )(Rating.apply)(Rating.unapply _)
  )

  def homeRating() = SecuredAction(IsMemberOf("admin")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      val defaultRating = Rating(
        "",
        request.webuser.uuid,
        "DevoxxFR2016",
        DateTime.now().getMillis,
        List(
          RatingDetail(
            "default", 3, None
          )
        )
      )
      Ok(views.html.RatingController.homeRating(ratingForm.fill(defaultRating)))
  }

  def acceptVoteForTalk() = SecuredAction(IsMemberOf("admin")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>

      ratingForm.bindFromRequest().fold(hasErrors => BadRequest(views.html.RatingController.homeRating(hasErrors)),
        validRating => {
          Rating.saveNewRating(validRating)
          Ok("Top")
        }
      )
  }

  def allRatings = SecuredAction(IsMemberOf("admin")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      val ratings = Rating.allRatings()
      Ok(views.html.RatingController.allRatings(ratings))
  }

  // Special callback that accepts a JSON content from an URL, upload and set all votes for each talk
  // This is for backward compatibility with Jon Mort / Ratpack voting app and should be removed.
  // You need a Clevercloud FS Bucket, then upload the JSON export "all.json" to this bucket
  // and finally, open the /cfpadmin/postForBackup URL
  def postForBackup() = SecuredAction(IsMemberOf("admin")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>

      import scala.collection.JavaConverters._

      val env = System.getenv().asScala
      val zeFile = env.getOrElse("JSON_VOTES", "misc/all.json")

      val stream = scala.io.Source.fromFile(zeFile).getLines().mkString
      val json = Json.parse(stream)

      val rows = json.get("rows")

      val parsedRatings = rows.elements().asScala.map {
        row: JsonNode =>
          val doc = row.get("doc")
          val talkId = doc.get("talkId").asText()
          val user = doc.get("user").asText()
          val conference = doc.get("conference").asText()
          val timestamp = doc.get("timestamp").asLong()

          val details = doc.get("details")
          val ratindDetails = details.elements().asScala.map {
            d =>
              val aspect = d.get("aspect").asText()
              val rating = d.get("rating").asInt()
              val reviewTxt = d.get("review")
              val review: Option[String] = if (reviewTxt == null) {
                None
              } else {
                Option(reviewTxt.asText())
              }
              RatingDetail(aspect, rating, review)
          }

          Rating(
            talkId,
            user,
            conference,
            timestamp,
            ratindDetails.toList
          )
      }

      parsedRatings.foreach {
        rating =>
          Rating.saveNewRating(rating)
      }

      Ok(s"Loaded some json with ${parsedRatings.size}")
  }
}
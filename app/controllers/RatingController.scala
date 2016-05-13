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

import models._
import org.joda.time.DateTime
import play.api.data.Form
import play.api.data.Forms._
import library.{NotifyGoldenTicket, ZapActor}
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation.Constraints._

import scala.io.Source

/**
  * Backoffice controller used to store rating and reviews sent by the mobile
  * application, back to the CFP.
  * The same system is used also for Speakers, to review their rating.
  *
  * @author created by N.Martignole, Innoteria, on 08/05/2016.
  */

object RatingController extends SecureCFPController {

  import Rating.RatingDetailFormat
  import Rating.RatingFormat

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
      val defaultRating=Rating(
        "",
        request.webuser.uuid,
        "DevoxxFR2016",
        DateTime.now().getMillis,
        List(
          RatingDetail(
            "default",3,None
          )
        )
      )
      Ok(views.html.RatingController.homeRating(ratingForm.fill(defaultRating)))
  }

  def acceptVoteForTalk()=SecuredAction(IsMemberOf("admin")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>

     ratingForm.bindFromRequest().fold(hasErrors=>BadRequest(views.html.RatingController.homeRating(hasErrors)),
       validRating=>{
         Rating.saveNewRating(validRating)
         Ok("Top")
       }
     )
  }

}

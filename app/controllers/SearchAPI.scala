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

import play.api.data.Form
import play.api.data.Forms._
import play.api.mvc.Controller

/**
  * A controller created for advanced search for Mobile and for Guillaume Laforge experiments with Google.
  *
  * @author created by N.Martignole, Innoteria, on 28/01/2017.
  */
case class AdvancedSearchParam(topic:Option[String],
                               track:Option[String],
                               format:Option[String],
                               room:Option[String],
                               after:Option[String],
                               speaker:Option[String])

object SearchAPI extends Controller {
  val searchTalkForm = Form(
    mapping(
      "topic" -> optional(text),
      "track" -> optional(text),
      "format" -> optional(text),
      "room" -> optional(text),
      "after" -> optional(text),
      "speaker" -> optional(text)
    )(AdvancedSearchParam.apply)(AdvancedSearchParam.unapply)
  )

  def index = UserAgentActionAndAllowOrigin(implicit request => Ok(views.html.SearchAPI.indexSearch(searchTalkForm)))

  def searchTalks() = UserAgentActionAndAllowOrigin {
    implicit request =>

      Ok("todo")
  }
}
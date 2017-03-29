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

import library.search.{AdvancedSearchParam, ESSearchResult, ElasticSearch}
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.Controller

import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits._

import scala.util.{Failure, Success}

/**
  * A controller created for advanced search for Mobile and for Guillaume Laforge experiments with Google.
  *
  * @author created by N.Martignole, Innoteria, on 28/01/2017.
  */


object SearchAPI extends Controller {
  val searchTalkForm = Form(
    mapping(
      "topic" -> optional(text),
      "track" -> optional(text),
      "format" -> optional(text),
      "room" -> optional(text),
      "day" -> optional(text),
      "after" -> optional(text),
      "speaker" -> optional(text),
      "company" -> optional(text)
    )(AdvancedSearchParam.apply)(AdvancedSearchParam.unapply _)
  )

  def index = UserAgentActionAndAllowOrigin{
    implicit request =>
      Ok(views.html.SearchAPI.indexSearch(searchTalkForm))
  }

  def searchTalks() = UserAgentActionAndAllowOrigin.async {
    implicit request =>

      searchTalkForm.bindFromRequest().fold(hasErrors => {
        if (request.accepts("text/html")) {
          Future.successful(BadRequest(views.html.SearchAPI.indexSearch(hasErrors)))
        } else {
          Future.successful(BadRequest(hasErrors.errorsAsJson).as(JSON))
        }
      }, validSearch => {

        ElasticSearch.doAdvancedTalkSearch(validSearch).map {
          case Success(result) =>
            if (request.accepts("text/html")) {

              play.Logger.of("SearchAPI").warn("Raw json "+result)

              import library.search.ESSchedule._
              val searchResult = Json.parse(result).as[ESSearchResult]
              Ok(views.html.SearchAPI.showResults(searchResult))
            } else {
              Ok(result).as(JSON)
            }
          case Failure(ex) =>
            play.Logger.of("SearchAPI").error("Unable to search due to "+ex.getMessage, ex)
            if (request.accepts("text/html")) {
              InternalServerError("Unable to search. Reason : "+ex.getMessage)
            } else {
              val jsonResult: JsValue = Json.obj("error" -> ex.getMessage)
              InternalServerError(jsonResult).as(JSON)
            }
        }
      })

  }


}

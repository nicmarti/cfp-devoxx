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

import play.api.libs.json.Json
import controllers.routes

/**
 * A simple entity for Conference
 * Created by nicolas on 25/02/2014.
 */
case class Conference(eventCode:String, label:String, ref:String)

object Conference{

  implicit val confFormat = Json.format[Conference]

  val devoxxFrance2014=Conference("devoxxFR2014",
                                  "Devoxx France 2014, 16 au 18 avril 2014",
                                  routes.RestAPI.showConference("devoxxFR2014").toString)

  def all=List(devoxxFrance2014)

  // Super fast, super crade, super je m'en fiche pour l'instant
  def find(eventCode:String):Option[Conference]=Option(devoxxFrance2014)

}

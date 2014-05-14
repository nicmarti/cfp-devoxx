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

/**
 * Tracks for Devoxx Belgium
 *
 * Author: nicolas martignole
 * Created: 06/11/2013 01:41
 */
case class Track(id: String, label: String)

object Track {
  implicit val trackFormat = Json.format[Track]

  val STARTUP=Track("startup", "startup.label")
  val SSJ =Track("ssj", "ssj.label")
  val JAVA=Track("java", "java.label")
  val MOBILE =Track("mobile", "mobile.label")
  val ARCHISEC =Track("archisec", "archisec.label")
  val METHODEVOPS=Track("methodevops", "methodevops.label")
  val FUTURE=Track("future", "future.label")
  val LANG=Track("lang", "lang.label")
  val CLOUD =Track("cloud", "cloud.label")
  val WEB =Track("web", "web.label")


  val all = List(STARTUP, SSJ, JAVA, MOBILE, ARCHISEC, METHODEVOPS, FUTURE, LANG, CLOUD, WEB)

  val allAsIdsAndLabels = all.map(a=>(a.id,a.label)).toSeq.sorted

  val allIDs=all.map(_.id)

  def parse(id:String):Track={
    id match {
      case "startup"=>STARTUP
      case "ssj"=>SSJ
      case "java" => JAVA
      case "mobile"=>MOBILE
      case "archisec" =>ARCHISEC
      case "methodevops" =>METHODEVOPS
      case "future"=>FUTURE
      case "lang"=>LANG
      case "cloud" =>CLOUD
      case "web" =>WEB
      case other =>JAVA
    }
  }
}

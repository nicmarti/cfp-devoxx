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

import play.api.libs.json._
import play.api.libs.json.JsObject
import play.api.libs.json.JsNumber
import library.Redis

/**
 * Created by nicolas on 20/02/2014.
 */
case class HitView(url: String, objRef: String, objName: String, date: Long)

object HitView {

  implicit object HitViewFormat extends Format[HitView] {
    def reads(json: JsValue) = JsSuccess(
      HitView(
        (json \ "u").as[String],
        (json \ "r").as[String],
        (json \ "n").as[String],
        (json \ "d").as[Long]
      )
    )

    def writes(s: HitView): JsValue = JsObject(Seq(
      "u" -> JsString(s.url),
      "r" -> JsString(s.objRef),
      "n" -> JsString(s.objName),
      "d" -> JsNumber(s.date)
    ))
  }

  def storeLogURL(url: String, objRef: String, objValue: String) = Redis.pool.withClient {
    client =>
      val tx = client.multi
      tx.zadd("Url:Hit:"+url,System.currentTimeMillis(),createJSON(url, objRef, objValue))
      tx.exec()
  }

  private def createJSON(url: String, objRef: String, objValue: String): String = {
    val hit = new HitView(url, objRef,objValue, System.currentTimeMillis / 1000)
    play.api.libs.json.Json.toJson(hit).toString
  }
}


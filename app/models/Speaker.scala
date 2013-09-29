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

import org.apache.commons.lang3.RandomStringUtils
import reactivemongo.api.Cursor
import play.api.libs.json.Json
import scala.concurrent.{ExecutionContext, Future}
import library.MongoDB
import reactivemongo.core.commands.LastError
import ExecutionContext.Implicits.global
import reactivemongo.api.indexes.{IndexType, Index}
import reactivemongo.bson.BSONObjectID
import play.modules.reactivemongo.json.BSONFormats._


/**
 * Speaker
 *
 * Author: nicolas
 * Created: 28/09/2013 11:01
 */
case class Speaker( id: Option[BSONObjectID], email: String, bio: String, lang: Option[String], twitter: Option[String],
                    avatarUrl: Option[String], company: Option[String], blog:Option[String])

object Speaker {
  implicit val speakerFormat = Json.format[Speaker]

  def createSpeaker(email:String, bio:String,lang:Option[String], twitter:Option[String], company:Option[String], blog:Option[String]):Speaker={
    Speaker(None,email,bio,lang,twitter,None,company,blog)
  }

  def unapplyForm(s:Speaker):Option[(String,String, Option[String],Option[String],Option[String],Option[String])]={
    Some(s.email, s.bio, s.lang, s.twitter, s.company,s.blog)
  }

  def save(speaker: Speaker): Future[LastError] = MongoDB.withCollection("speaker") {
    implicit collection =>
    // Check index
      collection.indexesManager.ensure(Index(List("email" -> IndexType.Ascending), unique = true, name=Some("idx_speaker")))
      val result = collection.insert(speaker)
      result
  }

  def findByEmail(email: String): Future[Option[Speaker]] = MongoDB.withCollection("speaker") {
    implicit collection =>
      val cursor: Cursor[Speaker] = collection.find(Json.obj("email" -> email)).cursor[Speaker]
      cursor.headOption()
  }

}


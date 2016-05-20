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

import java.io._

import org.joda.time.DateTimeZone
import org.joda.time.format.{DateTimeFormat, DateTimeFormatter}
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.iteratee.Enumerator
import play.api.libs.{Codecs, MimeTypes}
import play.api.mvc._
import play.api.{Mode, Play}
import play.utils.UriEncoding

import scala.collection.JavaConverters._
import scala.util.control.NonFatal

/**
  * This is a copy of Assets controller from Play 2.3, except that the resourceNameAt function will load
  * local mounted Clever-cloud bucket file system.
  * Clever-cloud FS Bucket are mounted into your applicaiton, in /app
  * buckets.json defines the folder name, e.g /devoxx_content. Files are available at /app/devoxx_content
  *
  * This is used to load all HTML pages from previous edition, saved as HTML pages, and serve them
  * as static assets.
  * Every year, once the conference is done, I do a backup of the existing Publisher pages with wget.
  * Then I store all HTML+CSS in a Bucket (like S3).
  */
object BucketContentLoader extends AssetsBuilder {
  private val timeZoneCode = "GMT"

  //Dateformatter is immutable and threadsafe
  private val df: DateTimeFormatter =
    DateTimeFormat.forPattern("EEE, dd MMM yyyy HH:mm:ss '" + timeZoneCode + "'").withLocale(java.util.Locale.ENGLISH).withZone(DateTimeZone.forID(timeZoneCode))

  //Dateformatter is immutable and threadsafe
  private val dfp: DateTimeFormatter =
    DateTimeFormat.forPattern("EEE, dd MMM yyyy HH:mm:ss").withLocale(java.util.Locale.ENGLISH).withZone(DateTimeZone.forID(timeZoneCode))

  private val parsableTimezoneCode = " " + timeZoneCode

  private lazy val defaultCharSet = Play.configuration.getString("default.charset").getOrElse("utf-8")

  private def addCharsetIfNeeded(mimeType: String): String =
    if (MimeTypes.isText(mimeType))
      "; charset=" + defaultCharSet
    else ""

  /**
    * Get the name of the resource for a static resource. Used by `at`.
    *
    * @param path the root folder for searching the static resource files, such as `"/public"`. Not URL encoded.
    * @param file the file part extracted from the URL. May be URL encoded (note that %2F decodes to literal /).
    */
  override private[controllers] def resourceNameAt(path: String, file: String): Option[String] = {
    val decodedFile = UriEncoding.decodePath(file, "utf-8")
    val resourceName = Option(path + "/" + decodedFile).map(name => if (name.startsWith("/")) name else ("/" + name)).get
    // For security reason it has to be the bucket name defined in buckets.json
    if (new File(resourceName).isDirectory || !resourceName.startsWith("/devoxx_content")) {
      sys.error(s"Error with $resourceName")
      sys.error("Cannot read FS Bucket. For security reason, the folder in buckets.json MUST BE /devoxx_content")
      None
    } else {
      Some(resourceName)
    }
  }

  override def at(path: String, file: String) = Action {
    implicit request =>
      def parseDate(date: String): Option[java.util.Date] = try {
        //jodatime does not parse timezones, so we handle that manually
        val d = dfp.parseDateTime(date.replace(parsableTimezoneCode, "")).toDate
        Some(d)
      } catch {
        case NonFatal(_) => None
      }

      resourceNameAt(path, file).map { resourceName =>

        // Clever-cloud bucket FS system are mounted into /app
        val resourceAsHtml = new File("/app", resourceName + ".html")

        if (resourceAsHtml.exists() && resourceAsHtml.canRead) {
          MovedPermanently(routes.BucketContentLoader.at(file + ".html").url)
        }else {
          val resource = new File("/app", resourceName)
          if (resource.exists() && resource.canRead) {

            def maybeNotModified(file: File) = {
              // First check etag. Important, if there is an If-None-Match header, we MUST not check the
              // If-Modified-Since header, regardless of whether If-None-Match matches or not. This is in
              // accordance with section 14.26 of RFC2616.
              request.headers.get(IF_NONE_MATCH) match {
                case Some(eTags) => {
                  etagFor(file).filter(etag =>
                    eTags.split(",").exists(_.trim == etag)
                  ).map(_ => cacheableResult(file, NotModified))
                }
                case None => {
                  request.headers.get(IF_MODIFIED_SINCE).flatMap(parseDate).flatMap { ifModifiedSince =>
                    lastModifiedFor(file).flatMap(parseDate).filterNot(lastModified => lastModified.after(ifModifiedSince))
                  }.map(_ => NotModified.withHeaders(
                    DATE -> df.print({
                      new java.util.Date
                    }.getTime)))
                }
              }
            }

            def cacheableResult[A <: Result](file: File, r: A) = {
              // Add Etag if we are able to compute it
              val taggedResponse = etagFor(file).map(etag => r.withHeaders(ETAG -> etag)).getOrElse(r)
              val lastModifiedResponse = lastModifiedFor(file).map(lastModified => taggedResponse.withHeaders(LAST_MODIFIED -> lastModified)).getOrElse(taggedResponse)

              // Add Cache directive if configured
              val cachedResponse = lastModifiedResponse.withHeaders(CACHE_CONTROL -> {
                Play.configuration.getString("\"assets.cache." + resourceName + "\"").getOrElse(Play.mode match {
                  case Mode.Prod => Play.configuration.getString("assets.defaultCache").getOrElse("max-age=3600")
                  case _ => "no-cache"
                })
              })
              cachedResponse
            }

            Option(resource).map {

              case f if f.isDirectory => NotFound

              case f => {

                lazy val (length, resourceData) = {
                  val stream = new FileInputStream(f)
                  try {
                    (stream.available, Enumerator.fromStream(stream))
                  } catch {
                    case _: Throwable => (-1, Enumerator[Array[Byte]]())
                  }
                }

                if (length == -1) {
                  NotFound
                } else {
                  maybeNotModified(f).getOrElse {
                    // Prepare a streamed response
                    val response = SimpleResult(
                      ResponseHeader(OK, Map(
                        CONTENT_LENGTH -> length.toString,
                        CONTENT_TYPE -> MimeTypes.forFileName(file).map(m => m + addCharsetIfNeeded(m)).getOrElse(BINARY),
                        DATE -> df.print({
                          new java.util.Date
                        }.getTime))),
                      resourceData)

                    cacheableResult(f, response)
                  }
                }

              }

            }.getOrElse(NotFound)

          } else {
            NotFound("This page does not exist anymore or was moved. Please contact us, it's related to the Program.")
          }
        }
      }.getOrElse(NotFound)
  }

  // -- LastModified handling

  private val lastModifieds = (new java.util.concurrent.ConcurrentHashMap[String, String]()).asScala

  private def lastModifiedFor(file: File): Option[String] = {
    def formatLastModified(lastModified: Long): String = df.print(lastModified)

    def maybeLastModified(file: File): Option[Long] = {
      Some(new File(file.getPath).lastModified)
    }

    def cachedLastModified(file: File)(orElseAction: => Option[String]): Option[String] =
      lastModifieds.get(file.getCanonicalPath).orElse(orElseAction)

    def setAndReturnLastModified(file: File): Option[String] = {
      val mlm = maybeLastModified(file).map(formatLastModified)
      mlm.foreach(lastModifieds.put(file.getCanonicalPath, _))
      mlm
    }

    if (Play.isProd) cachedLastModified(file) {
      setAndReturnLastModified(file)
    }
    else setAndReturnLastModified(file)
  }

  private val etags = (new java.util.concurrent.ConcurrentHashMap[String, String]()).asScala

  private def etagFor(file: File): Option[String] = {
    etags.get(file.getCanonicalPath).filter(_ => Play.isProd).orElse {
      val maybeEtag = lastModifiedFor(file).map(_ + " -> " + file.getCanonicalPath).map("\"" + Codecs.sha1(_) + "\"")
      maybeEtag.foreach(etags.put(file.getCanonicalPath, _))
      maybeEtag
    }
  }
}

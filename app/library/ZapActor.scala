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

package library


import akka.actor._
import play.api.Play.current
import scala.Predef._
import models._
import org.joda.time._
import play.libs.Akka
import play.api.Play


/**
 * Akka actor that is in charge to process batch operations and long running queries
 *
 * Author: nicolas martignole
 * Created: 07/11/2013 16:20
 */

case class ReportIssue(issue:Issue)

object ZapActor {
  val actor = Akka.system.actorOf(Props[ZapActor])
}

/* The BatchEngine can be tested separately */
class ZapActor extends Actor {
  def receive = {
    case ReportIssue(issue) => publishBugReport(issue)
    case other => play.Logger.of("application.ZapActor").error("Received an invalid actor message: " + other)
  }

  def publishBugReport(issue:Issue) {
    if (play.Logger.of("application.ZapActor").isDebugEnabled) {
      play.Logger.of("application.ZapActor").debug(s"Posting a new bug report to Bitbucket")
    }

    // TODO here
    println(s"got issue ${issue}")

  }
}

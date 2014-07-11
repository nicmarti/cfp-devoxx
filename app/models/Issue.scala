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

import play.api.data._
import play.api.data.Forms._
import play.api.data.validation.Constraints._
import play.api.libs.ws.WS
import org.apache.commons.lang3.StringUtils
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import library.GitUtils
import play.api.Play


/**
 * An issue or a bug.
 * Author: nicolas martignole
 * Created: 07/11/2013 16:24
 */
case class Issue(reportedBy: String, msg: String, gitHash: String, gitBranch: String)

object Issue {
  val bugReportForm = Form(
    mapping(
      "email" -> (email verifying nonEmpty),
      "msg" -> nonEmptyText(maxLength = 2000),
      "antispam" -> number(min = 42, max = 42))
      (Issue.customApply)(Issue.customUnapply)
  )

  def customApply(email:String, msg:String, antispam:Int):Issue={
    val gitversion = GitUtils.getGitVersion
    Issue(email, msg, gitversion.version, gitversion.branch)
  }

  def customUnapply(issue:Issue)={
    Some((issue.reportedBy, issue.msg, 0))
  }

  def publish(issue: Issue) = {
    val postUrl = Play.current.configuration.getString("bitbucket.issues.url").getOrElse("Missing bitbucket issues url in config file")

    val bugReport:String = s"# AUTOMATIC Bug Report\n\n## Message posté du site ${ConferenceDescriptor.current().conferenceUrls.cfpHostname}\n## Reporté par ${issue.reportedBy}\n Git Hash ${issue.gitHash}  Git branch: ${issue.gitBranch}\n-----------------\n${issue.msg}"

    // See Bitbucket doc https://confluence.atlassian.com/display/BITBUCKET/issues+Resource#issuesResource-POSTanewissue
    val futureResult = WS.url(postUrl)
      .withAuth(
        username=Play.current.configuration.getString("bitbucket.username").getOrElse("Missing bitbucket username in config file"),
        password=Play.current.configuration.getString("bitbucket.password").getOrElse("Missing bitbucket token in config file"),

        scheme = com.ning.http.client.Realm.AuthScheme.BASIC)
      .withHeaders(
      ("Accept", "application/json"), ("User-Agent", "CFP "+ConferenceDescriptor.current().conferenceUrls.cfpHostname)
    ).post(
      Map(
        "status"-> Seq("new"),
        "title" -> Seq(StringUtils.abbreviate(issue.msg, 30)),
        "content" ->Seq(bugReport)
      )
    )


    futureResult.map {
      response =>
        response.status match {
          case 200 => play.Logger.of("models.Issue").info("Success: new issue posted")
          case 201 => play.Logger.of("models.Issue").info("Created: new issue created")
          case other => {
            play.Logger.of("models.Issue").warn("Bitbucket responded " + other)
            play.Logger.of("models.Issue").warn(response.body)
          }
        }

    }

  }
}

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
      "gitHash" -> text,
      "branch" -> text)
      (Issue.apply)(Issue.unapply)
  )

  def publish(issue: Issue) = {
    val postUrl = "https://bitbucket.org/api/1.0/repositories/nicolas_martignole/cfp-devoxx-fr/issues"

    val bugReport:String = s"# AUTOMATIC Bug Report\n\n## Message posté du site cfp.devoxx.fr\n## Reporté par ${issue.reportedBy}\n Git Hash ${issue.gitHash}  Git branch: ${issue.gitBranch}\n-----------------\n${issue.msg}"

    // See Bitbucket doc https://confluence.atlassian.com/display/BITBUCKET/issues+Resource#issuesResource-POSTanewissue
    val futureResult = WS.url(postUrl)
      .withAuth(username="nicolas_martignole", password="75laure", scheme = com.ning.http.client.Realm.AuthScheme.BASIC)
      .withHeaders(
      ("Accept", "application/json"), ("User-Agent", "Devoxx France cfp.devoxx.fr")
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
          case 200 => play.Logger.of("models.Issue").debug("Success")
          case 201 => play.Logger.of("models.Issue").debug("Created")
          case other => {
            play.Logger.of("models.Issue").warn("Bitbucket responded " + other)
            play.Logger.of("models.Issue").warn(response.body)
          }
        }

    }

  }
}

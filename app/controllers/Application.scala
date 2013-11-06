/**
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

import models._
import play.api.mvc._
import library.GitUtils
import play.api.data._
import play.api.data.Forms._
import play.api.data.validation.Constraints._


/**
 * Devoxx France Call For Paper main application.
 * @author Nicolas Martignole
 */
object Application extends Controller {

  def home = Action {
    implicit request =>
      Ok(views.html.Application.home(Authentication.loginForm))
  }

  def index = Action {
    implicit request =>
      Ok(views.html.Application.index())
  }

  def logout = Action {
    implicit request =>
      Redirect(routes.Application.index).withNewSession
  }

  def resetEnvForDev(email: String) = Action {
    implicit request =>
      Webuser.findByEmail(email).map {
        webuser =>
          Webuser.delete(webuser)
          SpeakerHelper.delete(email)
          Redirect(routes.Application.index()).flashing("success" -> "User de test effacé")
      }.getOrElse(NotFound("User does not exist"))
  }

  val bugReportForm = Form(tuple("email" -> (email verifying nonEmpty),
    "msg" -> nonEmptyText(maxLength = 2000),
    "gitHash" -> text,
    "branch" -> text))

  def bugReport = Action {
    implicit request =>
      Ok(views.html.Application.bugReport(GitUtils.getGitVersion, bugReportForm))
  }

  def submitIssue() = Action {
    implicit request =>
      bugReportForm.bindFromRequest.fold(
        invalidForm => BadRequest(views.html.Application.bugReport(GitUtils.getGitVersion, invalidForm)),
        validBugReport => {
          notifiers.Mails.sendBugReport(validBugReport)
          Redirect(routes.Application.index).flashing("success"-> "Your message has been sent to the team. Thanks!")
        })
  }


}
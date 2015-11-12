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

import library._
import models._
import play.api.i18n.Messages
import play.api.mvc._

/**
 * Call For Paper main application.
 * @author Nicolas Martignole
 */
object Application extends Controller {

  def home = Action {
    implicit request =>
      session.get("uuid") match {
        case Some(validUUID) => {
          Webuser.findByUUID(validUUID) match {
            case Some(webuser) =>
              Redirect(routes.CallForPaper.homeForSpeaker).withSession("uuid" -> validUUID)
            case None =>
              Ok(views.html.Application.home(Authentication.loginForm)).withNewSession.flashing("error" -> "Could not authenticate you automatically")
          }
        }
        case None =>
          Ok(views.html.Application.home(Authentication.loginForm)).withNewSession
      }
  }

  def homeVisitor = Action {
    implicit request =>
      Ok(views.html.Application.homeVisitor(Authentication.loginForm)).withNewSession
  }

  def index = Action {
    implicit request =>
      Ok(views.html.Application.index())
  }

  def bugReport = Action {
    implicit request =>
      Ok(views.html.Application.bugReport(Issue.bugReportForm))
  }

  def submitIssue() = Action {
    implicit request =>
      Issue.bugReportForm.bindFromRequest.fold(
        invalidForm => BadRequest(views.html.Application.bugReport(invalidForm)),
        validBugReport => {
          notifiers.Mails.sendBugReport(validBugReport)
          ZapActor.actor ! ReportIssue(validBugReport)
          Redirect(routes.Application.index).flashing("success" -> Messages("bugReport.sent"))
        })
  }

}
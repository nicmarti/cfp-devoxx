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
import scala.concurrent.{ExecutionContext, Future}
import play.api.data._
import play.api.data.Forms._
import ExecutionContext.Implicits.global
import reactivemongo.core.commands.LastError
import play.api.Logger

/**
 * Devoxx France Call For Paper main application.
 * @author Nicolas Martignole
 */
object Application extends Controller {

  val newWebuserForm: Form[Webuser] = Form(
    mapping(
      "email" -> nonEmptyText,
      "firstName" -> nonEmptyText,
      "lastName" -> nonEmptyText
    )(Webuser.createSpeaker)(Webuser.unapplyForm))


  def index = Action {
    implicit request =>
      Ok(views.html.Application.index(Authentication.loginForm))
  }

  def prepareSignup = Action {
    implicit request =>
      Ok(views.html.Application.prepareSignup())
  }

  def signup = Action {
    Ok("signup")
  }

  def newSpeaker = Action {
    Ok(views.html.Application.newUser(newWebuserForm))
  }

  def saveNewSpeaker = Action {
    implicit request =>
      newWebuserForm.bindFromRequest.fold(
        invalidForm => BadRequest(views.html.Application.newUser(invalidForm)),
        validForm => Async {
          Webuser.save(validForm).map {
            _ =>
              Created(views.html.Application.created(validForm.email))
          }.recover {
            case LastError(ok, err, code, errMsg, originalDocument, updated, updatedExisting) =>
              Logger.error("Mongo error, ok: " + ok + " err: " + err + " code: " + code + " errMsg: " + errMsg)
              if (code.get == 11000) Conflict("Email already exists") else InternalServerError("Could not create speaker.")
            case other => {
              Logger.error("Unknown Error " + other)
              InternalServerError("Unknown MongoDB Error")
            }
          }
        }
      )
  }

  def findByEmail(email: String) = Action {
    implicit request =>
      Async {
        val futureResult: Future[Option[Webuser]] = Webuser.findByEmail(email)
        futureResult.map {
          maybeWebuser:Option[Webuser] =>
            Ok(views.html.Application.showWebuser(maybeWebuser))
        }
      }
  }


}
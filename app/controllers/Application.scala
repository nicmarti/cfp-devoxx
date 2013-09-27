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
    Ok("It works!")
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
            result =>
              result match {
                case r if r.inError => InternalServerError("Could not create speaker. " + r.message)
                case other => Created(views.html.Application.created())
              }
          }
        }
      )
  }

  def findByEmail(email: String) = Action {
    implicit request =>
      Async {
        val futureUsersList: Future[List[Webuser]] = Webuser.findByEmail(email)
        futureUsersList.map {
          webuser =>
            Ok(webuser.toString())
        }
      }
  }
}
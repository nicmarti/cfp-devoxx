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
import ExecutionContext.Implicits.global
import reactivemongo.core.commands.LastError
import play.api.Logger
import javax.annotation.Resource.AuthenticationType

/**
 * Devoxx France Call For Paper main application.
 * @author Nicolas Martignole
 */
object Application extends Controller {

  def index = Action {
    implicit request =>
      Ok(views.html.Application.index(Authentication.loginForm))
  }

  def logout=Action{
    implicit request=>
      Redirect(routes.Application.index).withNewSession
  }

  def prepareSignup = Action {
    implicit request =>
      Ok(views.html.Application.prepareSignup(Authentication.newWebuserForm, Authentication.speakerForm))
  }

  def signup = Action {
    Ok("signup")
  }

  def newSpeaker = Action {
    Ok(views.html.Application.newUser(Authentication.newWebuserForm))
  }

  def findByEmail(email: String) = Action {
    implicit request =>
      Async {
        val futureResult: Future[Option[Webuser]] = Webuser.findByEmail(email)
        futureResult.map {
          maybeWebuser: Option[Webuser] =>
            Ok(views.html.Application.showWebuser(maybeWebuser))
        }
      }
  }

  def resetEnvForDev() = Action {
    implicit request =>
      Async {
        val futureResult: Future[Option[Webuser]] = Webuser.findByEmail("nicolas@martignole.net")
        futureResult.map {
          maybeWebuser =>
            maybeWebuser.map {
              webuser =>
                val err = Webuser.delete(webuser)
                Ok("Done " + err)
            }.getOrElse(NotFound("User does not exist"))
        }
      }
  }


}
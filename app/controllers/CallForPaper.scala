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

import play.api.mvc._
import models.{Speaker, Webuser}
import scala.concurrent.{ExecutionContext, Future}
import ExecutionContext.Implicits.global
import play.api.data._
import play.api.data.Forms._
import play.api.data.validation.Constraints._

/**
 * Main controller for the speakers.
 *
 * Author: nicolas
 * Created: 29/09/2013 12:24
 */
object CallForPaper extends Controller with Secured {
  def homeForSpeaker = IsAuthenticated {
    email => _ =>

      val futureSpeaker = Speaker.findByEmail(email)
      val futureWebuser = Webuser.findByEmail(email)

      Async {
        val result = futureSpeaker.flatMap(o1 => futureWebuser.map(o2 => o1.flatMap(x1 => o2.map(x2 => (x1, x2)))))
        result.map {
          tupleWebuserProfile =>
            tupleWebuserProfile.map {
              case (speaker, webuser) =>
                Ok(views.html.CallForPaper.homeForSpeaker(speaker, webuser))
            }.getOrElse {
              Redirect(routes.Application.index()).flashing("error" -> "Unable to authenticate, please contact the team, something bad happened (you can blame mongodb).")
            }
        }
      }
  }

  val editWebuserForm = Form(tuple("firstName" -> text.verifying(nonEmpty, maxLength(40)) ,
                                   "lastName" -> text.verifying(nonEmpty, maxLength(40))))

  def editCurrentWebuser = IsAuthenticated {
    email => _ =>
      val futureWebuser = Webuser.findByEmail(email)
      Async {
        futureWebuser.map {
          maybeWebuser =>
            maybeWebuser.map {
              webuser =>
                Ok(views.html.CallForPaper.editWebuser(editWebuserForm.fill(webuser.firstName, webuser.lastName)))
            }.getOrElse(Unauthorized("User not found"))
        }
      }
  }

  def saveCurrentWebuser = IsAuthenticated {
    email => implicit request =>
      editWebuserForm.bindFromRequest.fold(errorForm=>BadRequest(views.html.CallForPaper.editWebuser(errorForm)),
                                           success=>{
                                              Webuser.update(email, success._1, success._2)
                                              Redirect(routes.CallForPaper.homeForSpeaker())
                                           })
  }

  val speakerForm = Form(mapping(
    "email" -> (email verifying nonEmpty),
    "bio" -> nonEmptyText(maxLength = 200),
    "lang" -> optional(text),
    "twitter" -> optional(text),
    "avatarUrl" -> optional(text),
    "company" -> optional(text),
    "blog" -> optional(text)
  )(Speaker.createSpeaker)(Speaker.unapplyForm))

  def editProfile = IsAuthenticated {
    email => _ =>
      val futureSpeaker = Speaker.findByEmail(email)
      Async {
        futureSpeaker.map {
          maybeSpeaker =>
            maybeSpeaker.map {
              speaker =>
                Ok(views.html.CallForPaper.editProfile(speakerForm.fill(speaker)))
            }.getOrElse(Unauthorized("User not found"))
        }
      }
  }

  def saveProfile = IsAuthenticated {
    email => implicit request =>
      speakerForm.bindFromRequest.fold(
        invalidForm=>BadRequest(views.html.CallForPaper.editProfile(invalidForm)),
        validForm=>{
          if(validForm.email!=email){
            Unauthorized("You can't do that. Come-on, this is not a JSF app my friend.")
          }else{
            Speaker.update(email, validForm)
            Redirect(routes.CallForPaper.homeForSpeaker()).flashing("success"->"Profile saved")
          }
        }
      )

  }


  def findByEmail(email: String) = Action {
    implicit request =>
      Async {
        val futureResult: Future[Option[Webuser]] = Webuser.findByEmail(email)
        futureResult.map {
          maybeWebuser: Option[Webuser] =>
            Ok(views.html.CallForPaper.showWebuser(maybeWebuser))
        }
      }
  }

}

/**
 * Provide security features
 */
trait Secured {


  /**
   * Retrieve the connected user email.
   */
  private def username(request: RequestHeader) = request.session.get("email")

  /**
   * Redirect to login if the user in not authorized.
   */
  private def onUnauthorized(request: RequestHeader) = Results.Redirect(routes.Application.index).flashing("error" -> "Unauthorized : you are not authenticated or your session has expired. Please authenticate.")

  /**
   * Action for authenticated users.
   */
  def IsAuthenticated(f: => String => Request[AnyContent] => Result) = Security.Authenticated(username, onUnauthorized) {
    user =>
      Action(request => f(user)(request))
  }

  /**
   * Check if the connected user is a member of this security group.
   */
  def IsMemberOf(securityGroup: String)(f: => String => Request[AnyContent] => Result) = IsAuthenticated {
    email => request =>
      if (Webuser.isMember(securityGroup, email)) {
        f(email)(request)
      } else {
        Results.Forbidden("Sorry, you cannot access this resource")
      }
  }

  /**
   * Check if the connected user is a owner of this talk.
   */
  //  def IsOwnerOf(task: Long)(f: => String => Request[AnyContent] => Result) = IsAuthenticated { user => request =>
  //    if(Task.isOwner(task, user)) {
  //      f(user)(request)
  //    } else {
  //      Results.Forbidden("Sorrt, you are not the owner of this resource")
  //    }
  //  }

}
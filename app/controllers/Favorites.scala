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

import play.api.mvc.{Action, AnyContent, Request}

import scala.concurrent.Future


/**
 * Controller used from the program pages, to fav a talk.
 *
 * @author created by N.Martignole, Innoteria, on 26/10/15.
 */
object Favorites extends UserCFPController {
  def home() = SecuredAction{
    implicit request =>

      Ok(views.html.Favorites.homeFav())
  }

  def likeIt(proposalId: Long) = SecuredAction.async {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      Future.successful(
       Ok("like it")
      )
  }

  def unlikeIt(proposalId: Long) = SecuredAction.async {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
       Future.successful(
         Ok("unlike it")
      )
  }

  def welcomeVisitor()=SecuredAction.async {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
       Future.successful(
         Ok(views.html.Favorites.welcomeVisitor(request.webuser))
      )
  }
}

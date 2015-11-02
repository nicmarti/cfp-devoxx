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

import models.{FavoriteTalk, Proposal, ProposalState, ScheduleConfiguration}
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json.{JsObject, JsString}
import play.api.mvc.Action

import scala.concurrent.Future


/**
 * Controller used from the program pages, to fav a talk.
 *
 * @author created by N.Martignole, Innoteria, on 26/10/15.
 */
object Favorites extends UserCFPController {
  def home() = SecuredAction {
    implicit request =>

      val proposals = FavoriteTalk.allForUser(request.webuser.uuid)

      val slots = proposals.flatMap {
        talk: Proposal =>
          ScheduleConfiguration.findSlotForConfType(talk.talkType.id, talk.id)
      }.toList.sortBy(_.from.getMillis)
      val rooms = slots.groupBy(_.room).keys.toList.sortBy(_.id)
      Ok(views.html.Favorites.homeFav(slots, rooms))
  }

  val formProposal = Form("proposalId" -> nonEmptyText)

  def likeOrUnlike = SecuredAction {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      formProposal.bindFromRequest().fold(hasErrors => BadRequest("Invalid proposalId"), proposalId => {
        Proposal.findById(proposalId).filterNot(_.state == ProposalState.ARCHIVED).map {
          proposal =>
            if (FavoriteTalk.isFavByThisUser(proposal.id, request.webuser.uuid)) {
              FavoriteTalk.unfavTalk(proposal.id, request.webuser.uuid)
              Ok("{\"status\":\"unfav\"}").as(JSON)
            } else {
              FavoriteTalk.favTalk(proposal.id, request.webuser.uuid)
              Ok("{\"status\":\"fav\"}").as(JSON)
            }
        }.getOrElse {
          NotFound("Proposal not found")
        }
      })

  }


  def welcomeVisitor() = SecuredAction.async {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      Future.successful(
        Ok(views.html.Favorites.welcomeVisitor(request.webuser))
      )
  }

  def isFav(proposalId: String) = Action {
    implicit request =>
      UserCFPController.findAuthenticator.map {
        uuid =>
          val jsonResponse = JsObject(Seq("proposalId" -> JsString(proposalId)))
          Ok(jsonResponse)

      }.getOrElse {
        NoContent
      }
  }

  def showAllForAdmin()=SecuredAction(IsMemberOf("admin")){
    implicit r:SecuredRequest[play.api.mvc.AnyContent]=>
      val all=FavoriteTalk.all().toList.sortBy(_._2).reverse
      Ok(views.html.Favorites.showAllForAdmin(all))
  }
}

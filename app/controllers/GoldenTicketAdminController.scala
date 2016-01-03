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

import library.{NotifyGoldenTicket, ZapActor}
import models.GoldenTicket
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation.Constraints._

/**
  * A controller for Admin, to moderate or to add Golden Ticket.
  * See also the models.GoldenTicket entity.
  * Implemented during Devoxx Maroc 2015 :-)
  * @author created by N.Martignole, Innoteria, on 16/11/2015.
  */
object GoldenTicketAdminController extends SecureCFPController {

  val goldenTicketForm = Form(mapping(
    "ticketId" -> nonEmptyText(maxLength = 50),
    "firstName" -> nonEmptyText(maxLength = 50),
    "lastName" -> nonEmptyText(maxLength = 50),
    "email" -> (email verifying nonEmpty),
    "ticketType" -> nonEmptyText(maxLength = 50)
  )(GoldenTicket.createGoldenTicket)(GoldenTicket.unapplyForm _)
  )


  def showAll() = SecuredAction(IsMemberOf("admin")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      val tickets = GoldenTicket.allWithWebuser()
      Ok(views.html.GoldenTicketAdmin.showAll(tickets))
  }

  def newGoldenTicket() = SecuredAction(IsMemberOf("admin")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      Ok(views.html.GoldenTicketAdmin.newGoldenTicket(goldenTicketForm))
  }

  def saveGoldenTicket() = SecuredAction(IsMemberOf("admin")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>

      goldenTicketForm.bindFromRequest().fold(
        hasErrors => {
          BadRequest(views.html.GoldenTicketAdmin.newGoldenTicket(hasErrors))
        },
        validTicket => {
          if (GoldenTicket.hasTicket(webuserUUID = validTicket.webuserUUID)) {
            BadRequest(views.html.GoldenTicketAdmin.newGoldenTicket(goldenTicketForm.fill(validTicket).withError("Error", "This webuser has already a Golden Ticket. Only one ticket per user is allowed.")))
          } else {
            GoldenTicket.save(validTicket)
            ZapActor.actor ! NotifyGoldenTicket(validTicket)
            Redirect(routes.GoldenTicketAdminController.showAll()).flashing("success" -> "New ticket created for ")
          }

        }
      )
  }

  def sendEmail(goldenTicketId:String)=SecuredAction(IsMemberOf("admin")){
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      GoldenTicket.findById(goldenTicketId).map{ticket:GoldenTicket=>
        ZapActor.actor ! NotifyGoldenTicket(ticket)
        Redirect(routes.GoldenTicketAdminController.showAll()).flashing("success"->"Email sent")
      }.getOrElse(NotFound("Ticket not found"))

  }

  def unactivateGoldenTicket(id:String)=SecuredAction(IsMemberOf("admin")){
     implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      GoldenTicket.findById(id) match {
        case Some(goldenTicket)=>
          GoldenTicket.delete(id)
          Redirect(routes.GoldenTicketAdminController.showAll()).flashing("success"->"Deleted golden ticket")
        case _ => Redirect(routes.GoldenTicketAdminController.showAll()).flashing("error"->"No golden ticket with this id")
      }
  }

}

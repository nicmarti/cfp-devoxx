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

import library.{ComputeVotesAndScore, NotifyGoldenTicket, ZapActor}
import models._
import org.apache.commons.lang3.RandomStringUtils
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation.Constraints._
import play.api.i18n.Messages

/**
  * A controller for Admin, to moderate or to add Golden Ticket.
  * See also the models.GoldenTicket entity.
  * Implemented during Devoxx Maroc 2015 :-)
  *
  * @author created by N.Martignole, Innoteria, on 16/11/2015.
  */
object GoldenTicketAdminController extends SecureCFPController {

  val goldenTicketForm = Form(mapping(
    "ticketId" -> nonEmptyText(maxLength = 50),
    "firstName" -> nonEmptyText(maxLength = 50),
    "lastName" -> nonEmptyText(maxLength = 50),
    "email" -> (email verifying nonEmpty),
    "ticketType" -> nonEmptyText(maxLength = 50)
  )(GoldenTicket.createGoldenTicket)(GoldenTicket.unapplyForm)
  )

  def showAll() = SecuredAction(IsMemberOf("admin")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      val tickets = GoldenTicket.allWithWebuser()
      Ok(views.html.GoldenTicketAdmin.showAll(tickets))
  }

  def newGoldenTicket() = SecuredAction(IsMemberOf("admin")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      Ok(views.html.GoldenTicketAdmin.newGoldenTicket(goldenTicketForm.fill(GoldenTicket.createGoldenTicket(RandomStringUtils.randomNumeric(16), "", "", "", "combi"))))
  }

  def saveGoldenTicket() = SecuredAction(IsMemberOf("admin")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>

      goldenTicketForm.bindFromRequest().fold(
        hasErrors => {
          BadRequest(views.html.GoldenTicketAdmin.newGoldenTicket(hasErrors))
        },
        validTicket => {
          if (GoldenTicket.hasTicket(webuserUUID = validTicket.webuserUUID)) {
            BadRequest(views.html.GoldenTicketAdmin.newGoldenTicket(goldenTicketForm.fill(validTicket).withError("Error", s"This webuser has already a ${Messages("cfp.goldenTicket")}. Only one ticket per user is allowed.")))
          } else {
            GoldenTicket.save(validTicket)
            ZapActor.actor ! NotifyGoldenTicket(validTicket)
            Redirect(routes.GoldenTicketAdminController.showAll()).flashing("success" -> "New ticket created for ")
          }
        }
      )
  }

  def newGroupOfGoldenTicket() = SecuredAction(IsMemberOf("admin")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      Ok(views.html.GoldenTicketAdmin.newGroupOfGoldenTicket())
  }

  def importGroupOfGT() = SecuredAction(IsMemberOf("admin")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      request.body.asFormUrlEncoded.map {
        form =>
          form.get("bulk").map {
            maybeSomeTextContent =>
              maybeSomeTextContent.headOption.map {
                textareaContent: String =>
                  val goldenTickets = parseGoldenTicketBulk(textareaContent)
                  val zeForm = bulkImportGoldenTicket.fill(GoldenTicketBulkImport(goldenTickets))

                  Ok(views.html.GoldenTicketAdmin.importGroupOfGT(zeForm))
              }.getOrElse(BadRequest("Invalid bulk content"))
          }.getOrElse(BadRequest("Input bulk not found, bug in HTML Form"))
      }.getOrElse(BadRequest("Invalid form"))
  }

  val bulkImportGoldenTicket: Form[GoldenTicketBulkImport] = Form(
    mapping(
      "tickets" -> list(
        mapping(
          "ticketId" -> nonEmptyText(maxLength = 20),
          "firstName" -> text(maxLength = 30),
          "lastName" -> text(maxLength = 50),
          "email" -> email,
          "ticketType" -> text(maxLength = 30)
        )(GoldenTicketImport.apply)(GoldenTicketImport.unapply)
      )
    )(GoldenTicketBulkImport.apply)(GoldenTicketBulkImport.unapply)
  )

  def bulkImport() = SecuredAction(IsMemberOf("admin")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      bulkImportGoldenTicket.bindFromRequest().fold(
        hasErrors =>
          BadRequest(views.html.GoldenTicketAdmin.importGroupOfGT(hasErrors)),
        validForm => {
          validForm.tickets.foreach {
            ticket =>
              val gt = GoldenTicket.importTicket(ticket)
              ZapActor.actor ! NotifyGoldenTicket(gt)
          }
          Redirect(routes.Backoffice.homeBackoffice()).flashing("success" -> s"Successfully created ${validForm.tickets.length} ${Messages("cfp.goldenTickets")}")
        }
      )
  }

  def sendEmail(goldenTicketId: String) = SecuredAction(IsMemberOf("admin")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      GoldenTicket.findById(goldenTicketId).map { ticket: GoldenTicket =>
        ZapActor.actor ! NotifyGoldenTicket(ticket)
        Redirect(routes.GoldenTicketAdminController.showAll()).flashing("success" -> "Email sent")
      }.getOrElse(NotFound("Ticket not found"))

  }

  def unactivateGoldenTicket(id: String) = SecuredAction(IsMemberOf("admin")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      GoldenTicket.findById(id) match {
        case Some(goldenTicket) =>
          GoldenTicket.delete(id)
          Redirect(routes.GoldenTicketAdminController.showAll()).flashing("success" -> s"Deleted ${Messages("cfp.goldenTicket")}")
        case _ => Redirect(routes.GoldenTicketAdminController.showAll()).flashing("error" -> s"No ${Messages("cfp.goldenTicket")} with this id")
      }
  }

  def showGoldenTicketVotes() = SecuredAction(IsMemberOf("admin")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      // Trigger an update
      ZapActor.actor ! ComputeVotesAndScore()

      val allVotes: Set[(String, (models.Review.Score, models.Review.TotalVoter, models.Review.TotalAbst, models.Review.AverageNote, models.Review.StandardDev))] = ReviewByGoldenTicket.allVotes()
      val result = allVotes.toList.sortBy(_._2._1.s).reverse

      val allProposalIDs = result.map(_._1)
      // please note that a proposal with no votes will not be loaded
      val allProposalWithVotes = Proposal.loadAndParseProposals(allProposalIDs.toSet)

      val listOfProposals: List[(Proposal, (models.Review.Score, models.Review.TotalVoter, models.Review.TotalAbst, models.Review.AverageNote, models.Review.StandardDev))] = result.flatMap {
        case (proposalId, scoreAndVotes) =>
          allProposalWithVotes.get(proposalId).map {
            proposal: Proposal =>
              (proposal, scoreAndVotes)
          }
      }.filterNot {
        case (proposal, _) =>
          proposal.state == ProposalState.DRAFT || proposal.state == ProposalState.ARCHIVED || proposal.state == ProposalState.DELETED
      }.sortBy(_._2._4.n).reverse

      Ok(views.html.GoldenTicketAdmin.showGoldenTicketVotes(listOfProposals))
  }

  def repairStatsAfterGTArchivingAction() = SecuredAction(IsMemberOf("admin")) {
    def doesNotExist(reviewerUuid: String, allGoldenTicketsWithTheirReviewer: List[(GoldenTicket, Webuser)]): Boolean = {
      ! allGoldenTicketsWithTheirReviewer.exists(_._2.uuid.equals(reviewerUuid))
    }

    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      val allVotes: Set[(String, (models.Review.Score, models.Review.TotalVoter, models.Review.TotalAbst, models.Review.AverageNote, models.Review.StandardDev))] = ReviewByGoldenTicket.allVotes()
      val allVotesReversedSorted = allVotes.toList.sortBy(_._2._1.s).reverse
      val allProposalIDs = allVotesReversedSorted.map(_._1)

      allProposalIDs.foreach(
        proposalId => ReviewByGoldenTicket.deleteVoteForProposal(proposalId)
      )

      val allGoldenTicketReviewerUUIDs: Set[String] = ReviewByGoldenTicket.allGoldenTicketReviewerUUIDs();

      val allGoldenTicketsWithTheirReviewer: List[(GoldenTicket, Webuser)] = GoldenTicket.allWithWebuser()

      allGoldenTicketReviewerUUIDs
        .filter(reviewerUuid => doesNotExist(reviewerUuid, allGoldenTicketsWithTheirReviewer))
        .foreach(reviewerUuid => Webuser.removeFromGoldenTicket(reviewerUuid))

      Redirect(routes.GoldenTicketAdminController.showAll()).flashing("success" -> s"Repaired ${Messages("cfp.goldenTickets")} stats after Archiving action.")
  }

  def showStats() = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      val allVotes: Set[(String, (models.Review.Score, models.Review.TotalVoter, models.Review.TotalAbst, models.Review.AverageNote, models.Review.StandardDev))] = ReviewByGoldenTicket.allVotes()
      val result = allVotes.toList.sortBy(_._2._1.s).reverse

      val allProposalIDs = result.map(_._1)
      // please note that a proposal with no votes will not be loaded
      val allProposalWithVotes = Proposal.loadAndParseProposals(allProposalIDs.toSet)

      val listOfProposals: List[(Proposal, (models.Review.Score, models.Review.TotalVoter, models.Review.TotalAbst, models.Review.AverageNote, models.Review.StandardDev))] = result.flatMap {
        case (proposalId, scoreAndVotes) =>
          allProposalWithVotes.get(proposalId).map {
            proposal: Proposal =>
              (proposal, scoreAndVotes)
          }
      }.filterNot {
        case (proposal, _) =>
          proposal.state == ProposalState.DRAFT || proposal.state == ProposalState.ARCHIVED || proposal.state == ProposalState.DELETED
      }

      val totalGoldenTicket = GoldenTicket.size()

      Ok(views.html.GoldenTicketAdmin.showStats(listOfProposals, totalGoldenTicket))
  }

  // Very very bad code I wrote in the train before France-Germany soccer
  private def parseGoldenTicketBulk(textAreaContent: String): List[GoldenTicketImport] = {
    textAreaContent.split("\n").toList.flatMap {
      oneLine =>
        val tokens = oneLine.split(",")
        if (tokens.size == 5) {
          val maybeEmail = tokens(3)
          if (maybeEmail.contains("@")) {
            Some(GoldenTicketImport.buildFrom(tokens(0), tokens(1), tokens(2), maybeEmail, tokens(4)))
          } else {
            None
          }
        } else {
          None
        }
    }
  }
}
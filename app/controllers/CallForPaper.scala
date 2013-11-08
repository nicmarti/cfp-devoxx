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
import models._
import play.api.data._
import play.api.data.Forms._
import play.api.data.validation.Constraints._
import play.api._
import scala.concurrent._
import play.api.libs.concurrent.Execution.Implicits._
import org.apache.commons.lang3.{StringUtils, RandomStringUtils}
import play.api.libs.json.Json
import play.api.i18n.Messages

/**
 * Main controller for the speakers.
 *
 * Author: nicolas
 * Created: 29/09/2013 12:24
 */
object CallForPaper extends Controller with Secured {

  def homeForSpeaker = IsAuthenticated {
    email => implicit request =>
      val result = for (speaker <- SpeakerHelper.findByEmail(email).toRight("Speaker not found").right;
                        webuser <- Webuser.findByEmail(email).toRight("Webuser not found").right) yield (speaker, webuser)
      result.fold(errorMsg => {
        Redirect(routes.Application.index()).flashing("error" -> errorMsg)
      }, {
        case (speaker, webuser) =>
          Ok(views.html.CallForPaper.homeForSpeaker(speaker, webuser, Proposal.allMyDraftAndSubmittedProposals(email)))
      })
  }

  val editWebuserForm = play.api.data.Form(tuple("firstName" -> text.verifying(nonEmpty, maxLength(40)), "lastName" -> text.verifying(nonEmpty, maxLength(40))))

  def editCurrentWebuser = IsAuthenticated {
    email => _ =>
      Webuser.findByEmail(email).map {
        webuser =>
          Ok(views.html.CallForPaper.editWebuser(editWebuserForm.fill(webuser.firstName, webuser.lastName)))
      }.getOrElse(Unauthorized("User not found"))
  }

  def saveCurrentWebuser = IsAuthenticated {
    email => implicit request =>
      editWebuserForm.bindFromRequest.fold(errorForm => BadRequest(views.html.CallForPaper.editWebuser(errorForm)),
        success => {
          Webuser.updateNames(email, success._1, success._2)
          Redirect(routes.CallForPaper.homeForSpeaker())
        })
  }

  val speakerForm = play.api.data.Form(mapping(
    "email" -> (email verifying nonEmpty),
    "bio" -> nonEmptyText(maxLength = 750),
    "lang" -> optional(text),
    "twitter" -> optional(text),
    "avatarUrl" -> optional(text),
    "company" -> optional(text),
    "blog" -> optional(text)
  )(SpeakerHelper.createSpeaker)(SpeakerHelper.unapplyForm))

  def editProfile = IsAuthenticated {
    email => implicit request =>
      SpeakerHelper.findByEmail(email).map {
        speaker =>
          Ok(views.html.CallForPaper.editProfile(speakerForm.fill(speaker)))
      }.getOrElse(Unauthorized("User not found"))
  }

  def saveProfile = IsAuthenticated {
    email => implicit request =>
      speakerForm.bindFromRequest.fold(
        invalidForm => BadRequest(views.html.CallForPaper.editProfile(invalidForm)),
        validForm => {
          if (validForm.email != email) {
            Unauthorized("You can't do that. Come-on, this is not a JSF app my friend.")
          } else {
            SpeakerHelper.update(email, validForm)
            Redirect(routes.CallForPaper.homeForSpeaker()).flashing("success" -> "Profile saved")
          }
        }
      )
  }

  def findByEmail(email: String) = Action {
    implicit request =>
      Webuser.findByEmail(email).map {
        webuser =>
          Ok(views.html.CallForPaper.showWebuser(webuser))
      }.getOrElse(NotFound("User not found"))
  }


  // Load a new proposal form
  def newProposal() = IsAuthenticated {
    email => implicit request =>
      Ok(views.html.CallForPaper.newProposal(email, Proposal.proposalForm))

  }

  // Prerender the proposal, but do not persist
  def createNewProposal(id: Option[String]) = IsAuthenticated {
    email => implicit request =>
      Proposal.proposalForm.bindFromRequest.fold(
        hasErrors => BadRequest(views.html.CallForPaper.newProposal(email, hasErrors)).flashing("error" -> "invalid.form"),
        validProposal => {
          import com.github.rjeschke.txtmark._

          if (id.isDefined) {
            Proposal.allMyDraftProposals(email).find(_.id.get == id.get) match {
              case Some(existingProposal) => {
                val html = Processor.process(validProposal.summary) // markdown to HTML
                Ok(views.html.CallForPaper.confirmSummary(html, Proposal.proposalForm.fill(validProposal)))
              }
              case other => {
                BadRequest(views.html.CallForPaper.newProposal(email, Proposal.proposalForm.fill(validProposal))).flashing("error" -> "not.yours")
              }
            }
          } else {
            val html = Processor.process(validProposal.summary) // markdown to HTML
            Ok(views.html.CallForPaper.confirmSummary(html, Proposal.proposalForm.fill(validProposal)))
          }
        }
      )
  }

  // Revalidate to avoid CrossSite forgery and save the proposal
  def saveProposal(id: Option[String]) = IsAuthenticated {
    email => implicit request =>
      Proposal.proposalForm.bindFromRequest.fold(
        hasErrors => BadRequest(views.html.CallForPaper.newProposal(email, hasErrors)),
        validProposal => {
          if (id.isDefined) {
            // This is an edit operation
            Proposal.allMyDraftProposals(email).find(_.id.get == id.get) match {
              case Some(existingProposal) => {
                // This is an edit operation
                Proposal.saveDraft(email, validProposal.copy(id = id))
                Redirect(routes.CallForPaper.homeForSpeaker()).flashing("success" -> Messages("saved"))
              }
              case other => {
                // Check that this is really a new id and that it does not exist
                if (Proposal.isNew(id.get)) {
                  // This is a "create new" operation
                  Proposal.saveDraft(email, validProposal)
                  Redirect(routes.CallForPaper.homeForSpeaker).flashing("success" -> Messages("saved"))
                } else {
                  play.Logger.warn("ID collision " + id)
                  BadRequest(views.html.CallForPaper.newProposal(email, Proposal.proposalForm.fill(validProposal.copy(id = Proposal.generateId())))).flashing("error" -> "not.yours")
                }
              }
            }
          } else {
            // Hu... no id, something was corrupted
            BadRequest(views.html.CallForPaper.newProposal(email, Proposal.proposalForm.fill(validProposal))).flashing("error" -> "not.yours")
          }
        }
      )
  }

  def editProposal(proposalId: String) = IsAuthenticated {
    email => implicit request =>
      val maybeProposal = Proposal.allMyDraftAndSubmittedProposals(email).find(_.id.get == proposalId)
      maybeProposal match {
        case Some(proposal) => {
          Proposal.draft(email, proposalId)
          Ok(views.html.CallForPaper.newProposal(email, Proposal.proposalForm.fill(proposal)))
        }
        case None => {
          Redirect(routes.CallForPaper.homeForSpeaker).flashing("error" -> Messages("invalid.proposal"))
        }
      }
  }


  def editOtherSpeakers(proposalId: String) = IsAuthenticated {
    email => implicit request =>
      val maybeProposal = Proposal.allMyDraftAndSubmittedProposals(email).find(_.id.get == proposalId)
      maybeProposal match {
        case Some(proposal) => {
          Ok(views.html.CallForPaper.editOtherSpeaker(email, Proposal.proposalForm.fill(proposal)))
        }
        case None => {
          Redirect(routes.CallForPaper.homeForSpeaker).flashing("error" -> Messages("invalid.proposal"))
        }
      }
  }

  def saveOtherSpeakers(proposalId: String) = IsAuthenticated {
    email => implicit request =>
      val maybeProposal = Proposal.allMyDraftAndSubmittedProposals(email).find(_.id.get == proposalId)
      maybeProposal match {
        case Some(proposal) => {
          Ok("Saved")
        }
        case None => {
          Redirect(routes.CallForPaper.homeForSpeaker).flashing("error" -> Messages("invalid.proposal"))
        }
      }
  }

  def deleteProposal(proposalId: String) = IsAuthenticated {
    email => implicit request =>
      val maybeProposal = Proposal.allMyDraftAndSubmittedProposals(email).find(_.id.get == proposalId)
      maybeProposal match {
        case Some(proposal) => {
          Proposal.delete(email, proposalId)
          Redirect(routes.CallForPaper.homeForSpeaker).flashing("deleted" -> proposalId)
        }
        case None => {
          Redirect(routes.CallForPaper.homeForSpeaker).flashing("error" -> Messages("invalid.proposal"))
        }
      }
  }

  def undeleteProposal(proposalId: String) = IsAuthenticated {
    email => implicit request =>
      val maybeProposal = Proposal.allMyDeletedProposals(email).find(_.id.get == proposalId)
      maybeProposal match {
        case Some(proposal) => {
          Proposal.draft(email, proposalId)
          Redirect(routes.CallForPaper.homeForSpeaker).flashing("success" -> Messages("talk.draft"))
        }
        case None => {
          Redirect(routes.CallForPaper.homeForSpeaker).flashing("error" -> Messages("invalid.proposal"))
        }
      }

  }

  def submitProposal(proposalId: String) = IsAuthenticated {
    email => implicit request =>
      val maybeProposal = Proposal.allMyDraftProposals(email).find(_.id.get == proposalId)
      maybeProposal match {
        case Some(proposal) => {
          Proposal.submit(email, proposalId)
          Redirect(routes.CallForPaper.homeForSpeaker).flashing("success" -> Messages("talk.submitted"))
        }
        case None => {
          Redirect(routes.CallForPaper.homeForSpeaker).flashing("error" -> Messages("invalid.proposal"))
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
  def IsAuthenticated(f: => String => Request[AnyContent] => Result) = {
    Security.Authenticated(username, onUnauthorized) {
      user =>
        Action(request => f(user)(request))
    }
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
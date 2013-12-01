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
import play.api.i18n.Messages
import play.api.libs.Crypto
import library.{SendMessageToComite, SendMessageToSpeaker, ZapActor}

/**
 * Main controller for the speakers.
 *
 * Author: nicolas martignole
 * Created: 29/09/2013 12:24
 */
object CallForPaper extends Controller with Secured {

  def homeForSpeaker = IsAuthenticated {
   implicit uuid => implicit request =>
      val result = for (speaker <- SpeakerHelper.findByUUID(uuid).toRight("Speaker not found").right;
                        webuser <- Webuser.findByUUID(uuid).toRight("Webuser not found").right) yield (speaker, webuser)
      result.fold(errorMsg => {
        Redirect(routes.Application.index()).flashing("error" -> errorMsg)
      }, {
        case (speaker, webuser) =>
          Ok(views.html.CallForPaper.homeForSpeaker(speaker, webuser, Proposal.allMyDraftAndSubmittedProposals(uuid)))
      })
  }

  val editWebuserForm = play.api.data.Form(tuple("firstName" -> text.verifying(nonEmpty, maxLength(40)), "lastName" -> text.verifying(nonEmpty, maxLength(40))))

  def editCurrentWebuser = IsAuthenticated {
    implicit uuid => implicit request =>
      Webuser.findByUUID(uuid).map {
        webuser =>
          Ok(views.html.CallForPaper.editWebuser(editWebuserForm.fill(webuser.firstName, webuser.lastName)))
      }.getOrElse(Unauthorized("User not found"))
  }

  def saveCurrentWebuser = IsAuthenticated {
    implicit uuid => implicit request =>
      editWebuserForm.bindFromRequest.fold(errorForm => BadRequest(views.html.CallForPaper.editWebuser(errorForm)),
        success => {
          Webuser.updateNames(uuid, success._1, success._2)
          Redirect(routes.CallForPaper.homeForSpeaker())
        })
  }

  val speakerForm = play.api.data.Form(mapping(
    "email" -> (email verifying nonEmpty),
    "name" -> text,
    "bio" -> nonEmptyText(maxLength = 750),
    "lang" -> optional(text),
    "twitter" -> optional(text),
    "avatarUrl" -> optional(text),
    "company" -> optional(text),
    "blog" -> optional(text)
  )(SpeakerHelper.createSpeaker)(SpeakerHelper.unapplyForm))

  def editProfile = IsAuthenticated {
    implicit uuid => implicit request =>
      SpeakerHelper.findByUUID(uuid).map {
        speaker =>
          Ok(views.html.CallForPaper.editProfile(speakerForm.fill(speaker)))
      }.getOrElse(Unauthorized("User not found"))
  }

  def saveProfile = IsAuthenticated {
    implicit uuid => implicit request =>
      speakerForm.bindFromRequest.fold(
        invalidForm => BadRequest(views.html.CallForPaper.editProfile(invalidForm)).flashing("error" -> "Invalid form, please check and correct errors. "),
        validForm => {
          SpeakerHelper.update(uuid, validForm)
          Redirect(routes.CallForPaper.homeForSpeaker()).flashing("success" -> "Profile saved")
        }
      )
  }

  // Load a new proposal form
  def newProposal() = IsAuthenticated {
    implicit uuid => implicit request =>
      Ok(views.html.CallForPaper.newProposal(Proposal.proposalForm)).withSession(session + ("token" -> Crypto.sign(uuid)))

  }

  // Load a proposal, change the status to DRAFT (not sure this is a goode idea)
  def editProposal(proposalId: String) = IsAuthenticated {
    implicit uuid => implicit request =>
      val maybeProposal = Proposal.findDraftAndSubmitted(uuid, proposalId)
      maybeProposal match {
        case Some(proposal) => {
          Proposal.draft(uuid, proposalId)
          Ok(views.html.CallForPaper.newProposal(Proposal.proposalForm.fill(proposal))).withSession(session + ("token" -> Crypto.sign(proposalId)))
        }
        case None => {
          Redirect(routes.CallForPaper.homeForSpeaker).flashing("error" -> Messages("invalid.proposal"))
        }
      }
  }

  // Prerender the proposal, but do not persist
  def previewProposal() = IsAuthenticated {
    implicit uuid => implicit request =>
      Proposal.proposalForm.bindFromRequest.fold(
        hasErrors => BadRequest(views.html.CallForPaper.newProposal(hasErrors)).flashing("error" -> "invalid.form"),
        validProposal => {
          import com.github.rjeschke.txtmark._
          val html = Processor.process(validProposal.summary) // markdown to HTML
          Ok(views.html.CallForPaper.previewProposal(html, Proposal.proposalForm.fill(validProposal)))
        }
      )
  }

  // Revalidate to avoid CrossSite forgery and save the proposal
  def saveProposal() = IsAuthenticated {
    implicit uuid => implicit request =>

      Proposal.proposalForm.bindFromRequest.fold(
        hasErrors => BadRequest(views.html.CallForPaper.newProposal(hasErrors)),
        proposal => {
          // If the editor is not the owner then findDraft returns None
          Proposal.findDraft(uuid, proposal.id) match {
            case Some(existingProposal) => {
              // This is an edit operation
              Proposal.save(uuid, proposal.copy(secondarySpeaker = existingProposal.secondarySpeaker, otherSpeakers = existingProposal.otherSpeakers), ProposalState.DRAFT)
              Redirect(routes.CallForPaper.homeForSpeaker()).flashing("success" -> Messages("saved"))
            }
            case other => {
              // Check that this is really a new id and that it does not exist
              if (Proposal.isNew(proposal.id)) {
                // This is a "create new" operation
                Proposal.save(uuid, proposal, ProposalState.DRAFT)
                Redirect(routes.CallForPaper.homeForSpeaker).flashing("success" -> Messages("saved"))
              } else {
                // Maybe someone tried to edit someone's else proposal...
                Event.storeEvent(Event(proposal.id, uuid, "Tried to edit this talk but he is not the owner."))
                Redirect(routes.CallForPaper.homeForSpeaker).flashing("error" -> "You are trying to edit a proposal that is not yours. This event has been logged.")
              }
            }
          }
        }
      )
  }

  // Load a proposal by its id
  def editOtherSpeakers(proposalId: String) = IsAuthenticated {
    implicit uuid => implicit request =>
      val maybeProposal = Proposal.findDraftAndSubmitted(uuid, proposalId)
      maybeProposal match {
        case Some(proposal) => {
          Ok(views.html.CallForPaper.editOtherSpeaker(Webuser.getName(uuid), proposal, Proposal.proposalSpeakerForm.fill(proposal.secondarySpeaker, proposal.otherSpeakers)))
        }
        case None => {
          Redirect(routes.CallForPaper.homeForSpeaker).flashing("error" -> Messages("invalid.proposal"))
        }
      }
  }

  // Check that the current authenticated user is the owner
  // validate the form and then save and redirect.
  def saveOtherSpeakers(proposalId: String) = IsAuthenticated {
    implicit uuid => implicit request =>
      val maybeProposal = Proposal.findDraftAndSubmitted(uuid, proposalId)
      maybeProposal match {
        case Some(proposal) => {
          Proposal.proposalSpeakerForm.bindFromRequest.fold(
            hasErrors => BadRequest(views.html.CallForPaper.editOtherSpeaker(Webuser.getName(uuid), proposal, hasErrors)).flashing("error" -> "Errors in the proposal form, please correct errors"),
            validNewSpeakers => {
              Proposal.save(uuid, proposal.copy(secondarySpeaker = validNewSpeakers._1, otherSpeakers = validNewSpeakers._2), proposal.state)
              Redirect(routes.CallForPaper.homeForSpeaker).flashing("success" -> Messages("speakers.updated"))
            }
          )
        }
        case None => {
          Redirect(routes.CallForPaper.homeForSpeaker).flashing("error" -> Messages("invalid.proposal"))
        }
      }
  }

  def deleteProposal(proposalId: String) = IsAuthenticated {
    implicit uuid => implicit request =>
      val maybeProposal = Proposal.findDraftAndSubmitted(uuid, proposalId)
      maybeProposal match {
        case Some(proposal) => {
          Proposal.delete(uuid, proposalId)
          Redirect(routes.CallForPaper.homeForSpeaker).flashing("deleted" -> proposalId)
        }
        case None => {
          Redirect(routes.CallForPaper.homeForSpeaker).flashing("error" -> Messages("invalid.proposal"))
        }
      }
  }

  def undeleteProposal(proposalId: String) = IsAuthenticated {
    implicit uuid => implicit request =>
      val maybeProposal = Proposal.findDeleted(uuid, proposalId)
      maybeProposal match {
        case Some(proposal) => {
          Proposal.draft(uuid, proposalId)
          Redirect(routes.CallForPaper.homeForSpeaker).flashing("success" -> Messages("talk.draft"))
        }
        case None => {
          Redirect(routes.CallForPaper.homeForSpeaker).flashing("error" -> Messages("invalid.proposal"))
        }
      }

  }

  def submitProposal(proposalId: String) = IsAuthenticated {
    implicit uuid => implicit request =>
      val maybeProposal = Proposal.findDraft(uuid, proposalId)
      maybeProposal match {
        case Some(proposal) => {
          Proposal.submit(uuid, proposalId)
          Redirect(routes.CallForPaper.homeForSpeaker).flashing("success" -> Messages("talk.submitted"))
        }
        case None => {
          Redirect(routes.CallForPaper.homeForSpeaker).flashing("error" -> Messages("invalid.proposal"))
        }
      }
  }

  val speakerMsg=Form("msg"->nonEmptyText(maxLength = 2500))

  def showCommentForProposal(proposalId: String) = IsAuthenticated {
    implicit uuid => implicit request =>
      val maybeProposal = Proposal.findDraftAndSubmitted(uuid, proposalId)
      maybeProposal match {
        case Some(proposal) => {
          Ok(views.html.CallForPaper.showCommentForProposal(proposal, Comment.allSpeakerComments(proposal.id), speakerMsg))
        }
        case None => {
          Redirect(routes.CallForPaper.homeForSpeaker).flashing("error" -> Messages("invalid.proposal"))
        }
      }
  }

  def sendMessageToComite(proposalId: String) = IsAuthenticated {
    implicit uuid => implicit request =>
      val maybeProposal = Proposal.findDraftAndSubmitted(uuid, proposalId)
      maybeProposal match {
        case Some(proposal) => {
          speakerMsg.bindFromRequest.fold(
            hasErrors=>{
              BadRequest(views.html.CallForPaper.showCommentForProposal(proposal, Comment.allSpeakerComments(proposal.id), hasErrors))
            },
            validMsg=>{
              Comment.saveCommentForSpeaker(proposal.id, uuid, validMsg)
              ZapActor.actor ! SendMessageToComite(uuid, proposal, validMsg)
              Redirect(routes.CallForPaper.showCommentForProposal(proposalId)).flashing("success"->"Message was sent")
            }
          )
        }
        case None => {
          Redirect(routes.CallForPaper.homeForSpeaker).flashing("error" -> Messages("invalid.proposal"))
        }
      }
  }

}


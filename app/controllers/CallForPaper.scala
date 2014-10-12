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

import models._
import play.api.data._
import play.api.data.Forms._
import play.api.data.validation.Constraints._
import play.api.i18n.Messages
import play.api.libs.Crypto
import library.{NotifyProposalSubmitted, SendMessageToCommitte, ZapActor}
import org.apache.commons.lang3.StringUtils
import play.api.libs.json.Json
import library.search.ElasticSearch
import play.api.cache.Cache

/**
 * Main controller for the speakers.
 *
 * Author: nicolas martignole
 * Created: 29/09/2013 12:24
 */
object CallForPaper extends SecureCFPController {

  def homeForSpeaker = SecuredAction {
    implicit request =>
      val uuid = request.webuser.uuid
      val result = for (speaker <- Speaker.findByUUID(uuid).toRight("Speaker not found").right;
                        webuser <- Webuser.findByUUID(uuid).toRight("Webuser not found").right) yield (speaker, webuser)
      result.fold(errorMsg => {
        Redirect(routes.Application.index()).flashing("error" -> errorMsg)
      }, {
        case (speaker, webuser) =>
          val allProposals = Proposal.allMyProposals(uuid)
          Ok(views.html.CallForPaper.homeForSpeaker(speaker, webuser, allProposals))
      })

  }


  val speakerForm = play.api.data.Form(mapping(
    "email" -> (email verifying nonEmpty),
    "lastName" -> nonEmptyText(maxLength = 25),
    "bio" -> nonEmptyText(maxLength = 750),
    "lang" -> optional(text),
    "twitter" -> optional(text),
    "avatarUrl" -> optional(text),
    "company" -> optional(text),
    "blog" -> optional(text),
    "firstName" -> nonEmptyText(maxLength = 25),
    "qualifications" -> nonEmptyText(maxLength = 750)
  )(Speaker.createSpeaker)(Speaker.unapplyForm))

  def editProfile = SecuredAction {
    implicit request =>
      val uuid = request.webuser.uuid
      Speaker.findByUUID(uuid).map {
        speaker =>
          Ok(views.html.CallForPaper.editProfile(speakerForm.fill(speaker)))
      }.getOrElse(Unauthorized("User not found"))
  }

  def saveProfile = SecuredAction {
    implicit request =>
      val uuid = request.webuser.uuid
      speakerForm.bindFromRequest.fold(
        invalidForm => BadRequest(views.html.CallForPaper.editProfile(invalidForm)).flashing("error" -> "Invalid form, please check and correct errors. "),
        validForm => {
          Speaker.update(uuid, validForm)
          Redirect(routes.CallForPaper.homeForSpeaker()).flashing("success" -> "Profile saved")
        }
      )
  }

  // Load a new proposal form
  def newProposal() = SecuredAction {
    implicit request =>
      val uuid = request.webuser.uuid
      Ok(views.html.CallForPaper.newProposal(Proposal.proposalForm)).withSession(session + ("token" -> Crypto.sign(uuid)))
  }

  // Load a proposal, change the status to DRAFT (not sure this is a goode idea)
  def editProposal(proposalId: String) = SecuredAction {
    implicit request =>
      val uuid = request.webuser.uuid
      val maybeProposal = Proposal.findProposal(uuid, proposalId)
      maybeProposal match {
        case Some(proposal) => {
          if (proposal.mainSpeaker == uuid) {
            val proposalForm = Proposal.proposalForm.fill(proposal)
            Ok(views.html.CallForPaper.newProposal(proposalForm)).withSession(session + ("token" -> Crypto.sign(proposalId)))
          } else
          if (proposal.secondarySpeaker.isDefined && proposal.secondarySpeaker.get == uuid) {
            // Switch the mainSpeaker and the other Speakers
            val proposalForm = Proposal.proposalForm.fill(Proposal.setMainSpeaker(proposal, uuid))
            Ok(views.html.CallForPaper.newProposal(proposalForm)).withSession(session + ("token" -> Crypto.sign(proposalId)))
          } else
          if (proposal.otherSpeakers.contains(uuid)) {
            // Switch the secondary speaker and this speaker
            val proposalForm = Proposal.proposalForm.fill(Proposal.setMainSpeaker(proposal, uuid))
            Ok(views.html.CallForPaper.newProposal(proposalForm)).withSession(session + ("token" -> Crypto.sign(proposalId)))
          } else {
            Redirect(routes.CallForPaper.homeForSpeaker()).flashing("error" -> "Invalid state")
          }
        }
        case None => {
          Redirect(routes.CallForPaper.homeForSpeaker).flashing("error" -> Messages("invalid.proposal"))
        }
      }
  }

  // Prerender the proposal, but do not persist
  def previewProposal() = SecuredAction {
    implicit request =>
      Proposal.proposalForm.bindFromRequest.fold(
        hasErrors => BadRequest(views.html.CallForPaper.newProposal(hasErrors)).flashing("error" -> "invalid.form"),
        validProposal => {
          val summary = validProposal.summaryAsHtml // markdown to HTML
          val privateMessage = validProposal.privateMessageAsHtml // markdown to HTML
          Ok(views.html.CallForPaper.previewProposal(summary, privateMessage, Proposal.proposalForm.fill(validProposal), request.webuser.uuid))
        }
      )
  }

  // Revalidate to avoid CrossSite forgery and save the proposal
  def saveProposal() = SecuredAction {
    implicit request =>
      val uuid = request.webuser.uuid

      Proposal.proposalForm.bindFromRequest.fold(
        hasErrors => BadRequest(views.html.CallForPaper.newProposal(hasErrors)),
        proposal => {
          // If the editor is not the owner then findDraft returns None
          Proposal.findProposal(uuid, proposal.id) match {
            case Some(existingProposal) => {
              // This is an edit operation
              // First we try to reset the speaker's, we do not take the values from the FORM for security reason
              val updatedProposal = proposal.copy(mainSpeaker = existingProposal.mainSpeaker, secondarySpeaker = existingProposal.secondarySpeaker, otherSpeakers = existingProposal.otherSpeakers)

              // Then because the editor becomes mainSpeaker, we have to update the secondary and otherSpeaker
              if (existingProposal.state == ProposalState.DRAFT || existingProposal.state == ProposalState.SUBMITTED) {
                Proposal.save(uuid, Proposal.setMainSpeaker(updatedProposal, uuid), ProposalState.DRAFT)
                Event.storeEvent(Event(proposal.id, uuid, "Updated proposal " + proposal.id + " with title " + StringUtils.abbreviate(proposal.title, 80)))
                Redirect(routes.CallForPaper.homeForSpeaker()).flashing("success" -> Messages("saved1"))
              } else {
                Proposal.save(uuid, Proposal.setMainSpeaker(updatedProposal, uuid), existingProposal.state)
                Event.storeEvent(Event(proposal.id, uuid, "Edited proposal " + proposal.id + " with current state [" + existingProposal.state.code + "]"))
                Redirect(routes.CallForPaper.homeForSpeaker()).flashing("success" -> Messages("saved2"))
              }
            }
            case other => {
              // Check that this is really a new id and that it does not exist
              if (Proposal.isNew(proposal.id)) {
                // This is a "create new" operation
                Proposal.save(uuid, proposal, ProposalState.DRAFT)
                Event.storeEvent(Event(proposal.id, uuid, "Created a new proposal " + proposal.id + " with title " + StringUtils.abbreviate(proposal.title, 80)))
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
  def editOtherSpeakers(proposalId: String) = SecuredAction {
    implicit request =>
      val uuid = request.webuser.uuid
      val maybeProposal = Proposal.findProposal(uuid, proposalId)
      maybeProposal match {
        case Some(proposal) => {
          if (proposal.mainSpeaker == uuid) {
            val proposalForm = Proposal.proposalSpeakerForm.fill(proposal.secondarySpeaker, proposal.otherSpeakers)
            Ok(views.html.CallForPaper.editOtherSpeaker(Webuser.getName(uuid), proposal, proposalForm))
          } else
          if (proposal.secondarySpeaker.isDefined && proposal.secondarySpeaker.get == uuid) {
            // Switch the mainSpeaker and the other Speakers
            val proposalForm = Proposal.proposalSpeakerForm.fill(Option(proposal.mainSpeaker), proposal.otherSpeakers)
            Ok(views.html.CallForPaper.editOtherSpeaker(Webuser.getName(uuid), proposal, proposalForm))
          } else
          if (proposal.otherSpeakers.contains(uuid)) {
            // let this speaker as a member of the third list
            Redirect(routes.CallForPaper.homeForSpeaker()).flashing("error" -> Messages("speaker.other.error"))
          } else {
            Redirect(routes.CallForPaper.homeForSpeaker()).flashing("error" -> "Invalid state")
          }
        }
        case None => {
          Redirect(routes.CallForPaper.homeForSpeaker).flashing("error" -> Messages("invalid.proposal"))
        }
      }
  }

  // Check that the current authenticated user is the owner
  // validate the form and then save and redirect.
  def saveOtherSpeakers(proposalId: String) = SecuredAction {
    implicit request =>
      val uuid = request.webuser.uuid
      val maybeProposal = Proposal.findProposal(uuid, proposalId)
      maybeProposal match {
        case Some(proposal) => {
          println("Existing proposal " + proposal.secondarySpeaker)
          Proposal.proposalSpeakerForm.bindFromRequest.fold(
            hasErrors => BadRequest(views.html.CallForPaper.editOtherSpeaker(Webuser.getName(uuid), proposal, hasErrors)).flashing("error" -> "Errors in the proposal form, please correct errors"),
            validNewSpeakers => {
              Event.storeEvent(Event(proposal.id, uuid, "Updated speakers list " + proposal.id + " with title " + StringUtils.abbreviate(proposal.title, 80)))
              val newProposal = proposal.copy(secondarySpeaker = validNewSpeakers._1, otherSpeakers = validNewSpeakers._2)
              println(newProposal.secondarySpeaker)
              Proposal.save(uuid, newProposal, proposal.state)
              Redirect(routes.CallForPaper.homeForSpeaker).flashing("success" -> Messages("speakers.updated"))
            }
          )
        }
        case None => {
          Redirect(routes.CallForPaper.homeForSpeaker).flashing("error" -> Messages("invalid.proposal"))
        }
      }
  }

  def deleteProposal(proposalId: String) = SecuredAction {
    implicit request =>
      val uuid = request.webuser.uuid
      val maybeProposal = Proposal.findProposal(uuid, proposalId)
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

  def undeleteProposal(proposalId: String) = SecuredAction {
    implicit request =>
      val uuid = request.webuser.uuid
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

  def submitProposal(proposalId: String) = SecuredAction {
    implicit request =>
      val uuid = request.webuser.uuid
      val maybeProposal = Proposal.findDraft(uuid, proposalId)
      maybeProposal match {
        case Some(proposal) => {
          Proposal.submit(uuid, proposalId)
          ZapActor.actor ! NotifyProposalSubmitted(uuid, proposal)
          Redirect(routes.CallForPaper.homeForSpeaker).flashing("success" -> Messages("talk.submitted"))
        }
        case None => {
          Redirect(routes.CallForPaper.homeForSpeaker).flashing("error" -> Messages("invalid.proposal"))
        }
      }
  }

  val speakerMsg = Form("msg" -> nonEmptyText(maxLength = 2500))

  def showCommentForProposal(proposalId: String) = SecuredAction {
    implicit request =>
      val uuid = request.webuser.uuid
      val maybeProposal = Proposal.findProposal(uuid, proposalId)
      maybeProposal match {
        case Some(proposal) => {
          Ok(views.html.CallForPaper.showCommentForProposal(proposal, Comment.allSpeakerComments(proposal.id), speakerMsg))
        }
        case None => {
          Redirect(routes.CallForPaper.homeForSpeaker).flashing("error" -> Messages("invalid.proposal"))
        }
      }
  }

  def sendMessageToCommitte(proposalId: String) = SecuredAction {
    implicit request =>
      val uuid = request.webuser.uuid
      val maybeProposal = Proposal.findProposal(uuid, proposalId).filterNot(_.state == ProposalState.DELETED)
      maybeProposal match {
        case Some(proposal) => {
          speakerMsg.bindFromRequest.fold(
            hasErrors => {
              BadRequest(views.html.CallForPaper.showCommentForProposal(proposal, Comment.allSpeakerComments(proposal.id), hasErrors))
            },
            validMsg => {
              Comment.saveCommentForSpeaker(proposal.id, uuid, validMsg)
              ZapActor.actor ! SendMessageToCommitte(uuid, proposal, validMsg)
              Redirect(routes.CallForPaper.showCommentForProposal(proposalId)).flashing("success" -> "Message was sent")
            }
          )
        }
        case None => {
          Redirect(routes.CallForPaper.homeForSpeaker).flashing("error" -> Messages("invalid.proposal"))
        }
      }
  }

  def showQuestionsForProposal(proposalId: String) = SecuredAction {
    implicit request =>
      val uuid = request.webuser.uuid
      val maybeProposal = Proposal.findProposal(uuid, proposalId)
      maybeProposal match {
        case Some(proposal) => {
          Ok(views.html.CallForPaper.showQuestionsForProposal(proposal, Question.allQuestionsForProposal(proposal.id), speakerMsg))
        }
        case None => {
          Redirect(routes.CallForPaper.homeForSpeaker).flashing("error" -> Messages("invalid.proposal"))
        }
      }
  }

  def replyToQuestion(proposalId: String) = SecuredAction {
    implicit request =>
      val uuid = request.webuser.uuid
      val maybeProposal = Proposal.findProposal(uuid, proposalId).filterNot(_.state == ProposalState.DELETED)
      maybeProposal match {
        case Some(proposal) => {
          speakerMsg.bindFromRequest.fold(
            hasErrors => {
              BadRequest(views.html.CallForPaper.showQuestionsForProposal(proposal, Question.allQuestionsForProposal(proposal.id), hasErrors))
            },
            validMsg => {
              Question.saveQuestion(proposal.id, request.webuser.email, request.webuser.cleanName, validMsg)
              Redirect(routes.CallForPaper.showQuestionsForProposal(proposalId)).flashing("success" -> "Message was sent")
            }
          )
        }
        case None => {
          Redirect(routes.CallForPaper.homeForSpeaker).flashing("error" -> Messages("invalid.proposal"))
        }
      }
  }

  val deleteQuestionForm = Form(tuple("proposalId" -> nonEmptyText, "questionId" -> nonEmptyText))

  def deleteOneQuestion() = SecuredAction {
    implicit request =>
      val uuid = request.webuser.uuid
      deleteQuestionForm.bindFromRequest().fold(hasErrors =>
        BadRequest("Invalid form")
        , {
        case (proposalId, questionId) => {
          val maybeProposal = Proposal.findProposal(uuid, proposalId).filterNot(_.state == ProposalState.DELETED)
          maybeProposal match {
            case Some(proposal) => {
              Question.deleteQuestion(proposalId, questionId)
              Ok("Deleted")
            }
            case None => {
              BadRequest("Invalid proposal")
            }
          }
        }
      }
      )
  }

  case class TermCount(term: String, count: Int)

  def cloudTags() = SecuredAction.async {
    implicit request =>
      import play.api.libs.concurrent.Execution.Implicits.defaultContext
      import play.api.Play.current

      implicit val termCountFormat = Json.reads[TermCount]

      Cache.getOrElse("elasticSearch", 3600) {
        ElasticSearch.getTag("proposals/proposal").map {
          case r if r.isSuccess => {
            val json = Json.parse(r.get)
            val tags = (json \ "facets" \ "tags" \ "terms").as[List[TermCount]]
            Ok(views.html.CallForPaper.cloudTags(tags))
          }
          case r if r.isFailure => {
            play.Logger.error(r.get)
            InternalServerError
          }
        }
      }
  }

}


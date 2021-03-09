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

import library.search.{DoIndexSpeaker, ElasticSearch, ElasticSearchActor}
import library.sms.{SendWelcomeAndHelp, SmsActor, TwilioSender}
import library._
import models._
import org.apache.commons.lang3.StringUtils
import play.api.Play
import play.api.cache.Cache
import play.api.data.Forms._
import play.api.data._
import play.api.data.validation.Constraints._
import play.api.i18n.Messages
import play.api.libs.Crypto
import play.api.libs.json.Json
import play.api.mvc.AnyContentAsFormUrlEncoded
import views.html

import scala.concurrent.Future
import scala.util.{Failure, Success}

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

      if (request.headers.get("X-Forwarded-Proto").getOrElse("http") != "https" && Play.current.mode == play.api.Mode.Prod) {
        MovedPermanently(ConferenceDescriptor.current().conferenceUrls.cfpHostname + "/cfp/home")
      }else{
        Speaker.findByUUID(uuid).map {
          speaker: Speaker =>
            // BUG
            if (!Webuser.isSpeaker(uuid)) {
              play.Logger.error(s"****** Speaker ${speaker.cleanName} was not in the SPEAKER Webuser group")
              Webuser.addToSpeaker(uuid)
            }
            val hasApproved = Proposal.countByProposalState(uuid, ProposalState.APPROVED) > 0
            val hasAccepted = Proposal.countByProposalState(uuid, ProposalState.ACCEPTED) > 0
            val needsToAcceptTermAndCondition = Speaker.needsToAccept(uuid) && (hasAccepted || hasApproved)

            (hasApproved, hasAccepted) match {
              case (true, _) => Redirect(routes.ApproveOrRefuse.doAcceptOrRefuseTalk()).flashing("success" -> Messages("please.check.approved"))
              case _ =>
                val allProposals = Proposal.allMyProposals(uuid)
                val totalArchived = Proposal.countByProposalState(uuid, ProposalState.ARCHIVED)
                val ratings = if (hasAccepted || hasApproved) {
                  Rating.allRatingsForTalks(allProposals)
                } else {
                  Map.empty[Proposal, List[Rating]]
                }
                Ok(views.html.CallForPaper.homeForSpeaker(speaker, request.webuser, allProposals, totalArchived, ratings, needsToAcceptTermAndCondition))
            }
        }.getOrElse {
          val flashMessage = if (Webuser.hasAccessToGoldenTicket(request.webuser.uuid)) {
            Messages("callforpaper.gt.create.profile")
          } else {
            Messages("callforpaper.import.profile")
          }
          //We have a Webuser but no associated Speaker profile v
          Redirect(routes.CallForPaper.newSpeakerForExistingWebuser()).flashing("success" -> flashMessage)
        }
      }
  }

  // Specific secured action. We need a redirect from homeForSpeaker, to be able to display flash message
  def newSpeakerForExistingWebuser = SecuredAction {
    implicit request =>
      val w = request.webuser
      val defaultValues = (w.email, w.firstName, w.lastName, StringUtils.abbreviate("...", 750), None, None, None, None, "No experience")
      Ok(views.html.Authentication.confirmImport(Authentication.importSpeakerForm.fill(defaultValues)))
  }

  val speakerForm = play.api.data.Form(mapping(
    "uuid" -> ignored("xxx"),
    "email" -> (email verifying nonEmpty),
    "lastName" -> nonEmptyText(maxLength = 25),
    "bio" -> nonEmptyText(maxLength = 850), // We set a soft limit in the UX at 750 and a hard limit at 850
    "lang" -> optional(text),
    "twitter" -> optional(text),
    "avatarUrl" -> optional(text),
    "company" -> optional(text),
    "blog" -> optional(text),
    "firstName" -> nonEmptyText(maxLength = 25),
    "qualifications" -> nonEmptyText(maxLength = 850), // We set a soft limit in the UX at 750 and a hard limit at 850
    "phoneNumber" -> optional(text)
  )(Speaker.createSpeaker)(Speaker.unapplyForm))


  import play.filters.csrf._

  def editProfile = CSRFAddToken {
    SecuredAction {
      implicit request =>
        val uuid = request.webuser.uuid
        Speaker.findByUUID(uuid).map {
          speaker =>
            Ok(views.html.CallForPaper.editProfile(speakerForm.fill(speaker), uuid))
        }.getOrElse(Unauthorized("User not found"))
    }
  }

  def saveProfile =  CSRFCheck {
    SecuredAction {
      implicit request =>
        val uuid = request.webuser.uuid
        speakerForm.bindFromRequest.fold(
          invalidForm => BadRequest(views.html.CallForPaper.editProfile(invalidForm, uuid)),
          updatedSpeaker => {
            // The updatedSpeaker.uuid should be "xxx" as we do not trust the Form. Else, anyone could change the UUID.
            val firstName: String = updatedSpeaker.firstName.getOrElse(request.webuser.firstName)
            val lastName: String = updatedSpeaker.name.getOrElse(request.webuser.lastName)
            Speaker.update(uuid, updatedSpeaker)
            Webuser.updateNames(uuid, newFirstName = firstName, newLastName = lastName)
            // Index the Speaker, however reset correctly the speaker's uuid.
            ElasticSearchActor.masterActor ! DoIndexSpeaker(updatedSpeaker.copy(uuid = uuid))
            Redirect(routes.CallForPaper.homeForSpeaker()).flashing("success" -> "Profile saved")
          }
        )
    }
  }

  // Load a new proposal form
  def newProposal() = CSRFAddToken{
    SecuredAction {
      implicit request =>
        val uuid = request.webuser.uuid
        Ok(views.html.CallForPaper.newProposal(Proposal.proposalForm)).withSession(request.session + ("token" -> Crypto.sign(uuid)))
    }
  }

  // Load a proposal
  def editProposal(proposalId: String) = CSRFAddToken{
    SecuredAction {
      implicit request =>
        val uuid = request.webuser.uuid
        val maybeProposal = Proposal.findProposal(uuid, proposalId)
        maybeProposal match {
          case Some(proposal) =>
            if (proposal.mainSpeaker == uuid) {
              val proposalForm = Proposal.proposalForm.fill(proposal)
              Ok(views.html.CallForPaper.newProposal(proposalForm)).withSession(request.session + ("token" -> Crypto.sign(proposalId)))
            } else if (proposal.secondarySpeaker.isDefined && proposal.secondarySpeaker.get == uuid) {
              // Switch the mainSpeaker and the other Speakers
              val proposalForm = Proposal.proposalForm.fill(Proposal.setMainSpeaker(proposal, uuid))
              Ok(views.html.CallForPaper.newProposal(proposalForm)).withSession(request.session + ("token" -> Crypto.sign(proposalId)))
            } else if (proposal.otherSpeakers.contains(uuid)) {
              // Switch the secondary speaker and this speaker
              val proposalForm = Proposal.proposalForm.fill(Proposal.setMainSpeaker(proposal, uuid))
              Ok(views.html.CallForPaper.newProposal(proposalForm)).withSession(request.session + ("token" -> Crypto.sign(proposalId)))
            } else {
              Redirect(routes.CallForPaper.homeForSpeaker()).flashing("error" -> "Invalid state")
            }
          case None =>
            Redirect(routes.CallForPaper.homeForSpeaker()).flashing("error" -> Messages("invalid.proposal"))
        }
    }
  }


  // Prerender the proposal, but do not persist
  def previewProposal() = CSRFAddToken{
    SecuredAction {
      implicit request =>
        Proposal.proposalForm.bindFromRequest.fold(
          hasErrors => BadRequest(views.html.CallForPaper.newProposal(hasErrors)).flashing("error" -> "invalid.form"),
          validProposal => {
            val summary = validProposal.summaryAsHtml
            // markdown to HTML
            val privateMessage = validProposal.privateMessageAsHtml // markdown to HTML
            Ok(views.html.CallForPaper.previewProposal(summary, privateMessage, Proposal.proposalForm.fill(validProposal), request.webuser.uuid))
          }
        )
    }
  }

  // Revalidate to avoid CrossSite forgery and save the proposal
  def saveProposal() = CSRFCheck {
    SecuredAction {
      implicit request =>
        val uuid = request.webuser.uuid

        Proposal.proposalForm.bindFromRequest.fold(
          hasErrors => BadRequest(views.html.CallForPaper.newProposal(hasErrors)),
          proposal => {
            // If the editor is not the owner then findProposal returns None
            Proposal.findProposal(uuid, proposal.id) match {
              case Some(existingProposal) =>
                // This is an edit operation
                // First we try to reset the speaker's, we do not take the values from the FORM for security reason
                val updatedProposalWithSpeakers:Proposal = proposal.copy(mainSpeaker = existingProposal.mainSpeaker, secondarySpeaker = existingProposal.secondarySpeaker, otherSpeakers = existingProposal.otherSpeakers)

                // Then if userGroup boolean flag is set to null we must save false
                val updatedProposal2:Proposal = if(proposal.userGroup.isEmpty){
                  updatedProposalWithSpeakers.copy(userGroup=Some(false))
                }else{
                  updatedProposalWithSpeakers
                }

                 // Remove the sponsor flag if the ProposalType is not a Conference (specific to Devoxx : we want only Conference format as sponsor talk)
                val updatedProposal:Proposal = if(proposal.sponsorTalk && proposal.talkType != ConferenceDescriptor.current().conferenceSponsor.sponsorProposalType){
                  updatedProposal2.copy(sponsorTalk = false)
                }else{
                  updatedProposal2
                }

                // Then because the editor becomes mainSpeaker, we have to update the secondary and otherSpeaker
                if (existingProposal.state == ProposalState.DRAFT || existingProposal.state == ProposalState.SUBMITTED) {
                  Proposal.save(uuid, Proposal.setMainSpeaker(updatedProposal, uuid), ProposalState.DRAFT)
                  if (ConferenceDescriptor.isResetVotesForSubmitted) {
                    Review.archiveAllVotesOnProposal(proposal.id)
                    Event.storeEvent(Event(proposal.id, uuid, s"Reset all votes on ${proposal.id}"))
                  }
                  Event.storeEvent(Event(proposal.id, uuid, "Updated proposal " + proposal.id + " : '" + StringUtils.abbreviate(proposal.title, 80) + "'"))
                  Redirect(routes.CallForPaper.homeForSpeaker()).flashing("success" -> Messages("saved1"))
                } else {
                  Proposal.save(uuid, Proposal.setMainSpeaker(updatedProposal, uuid), existingProposal.state)
                  Event.storeEvent(Event(proposal.id, uuid, "Edited proposal " + proposal.id + " with current state '" + existingProposal.state.code + "'"))
                  Redirect(routes.CallForPaper.homeForSpeaker()).flashing("success" -> Messages("saved2"))
                }
              case _ =>
                // Check that this is really a new id and that it does not exist
                if (Proposal.isNew(proposal.id)) {
                  // This is a "create new" operation
                  Proposal.save(uuid, proposal, ProposalState.DRAFT)
                  Event.storeEvent(Event(proposal.id, uuid, "Created a new proposal " + proposal.id + " : '" + StringUtils.abbreviate(proposal.title, 80) + "'"))
                  Redirect(routes.CallForPaper.homeForSpeaker()).flashing("success" -> Messages("saved"))
                } else {
                  // Maybe someone tried to edit someone's else proposal...
                  Event.storeEvent(Event(proposal.id, uuid, "Tried to edit this talk but he is not the owner."))
                  Redirect(routes.CallForPaper.homeForSpeaker()).flashing("error" -> "You are trying to edit a proposal that is not yours. This event has been logged.")
                }
            }
          }
        )
    }
  }

  def autoCompleteTag(term: String) = SecuredAction {
    implicit request => {
      val tagsFound = Tag.allTags()
        .filter(tag => tag.value.toLowerCase.contains(term.toLowerCase))
        .sortBy(f => f.value)
        .map(tag => tag.value)
        .take(10)

      Ok(Json.toJson(tagsFound))
    }
  }

  // Load a proposal by its id
  def editOtherSpeakers(proposalId: String) = SecuredAction {
    implicit request =>
      val uuid = request.webuser.uuid
      val maybeProposal = Proposal.findProposal(uuid, proposalId)
      maybeProposal match {
        case Some(proposal) =>
          if (proposal.mainSpeaker == uuid) {
            val proposalForm = Proposal.proposalSpeakerForm.fill(proposal.secondarySpeaker, proposal.otherSpeakers)
            Ok(views.html.CallForPaper.editOtherSpeaker(Webuser.getName(uuid), proposal, proposalForm))
          } else if (proposal.secondarySpeaker.isDefined && proposal.secondarySpeaker.get == uuid) {
            // Switch the mainSpeaker and the other Speakers
            val proposalForm = Proposal.proposalSpeakerForm.fill(Option(proposal.mainSpeaker), proposal.otherSpeakers)
            Ok(views.html.CallForPaper.editOtherSpeaker(Webuser.getName(uuid), proposal, proposalForm))
          } else if (proposal.otherSpeakers.contains(uuid)) {
            // let this speaker as a member of the third list
            Redirect(routes.CallForPaper.homeForSpeaker()).flashing("error" -> Messages("speaker.other.error"))
          } else {
            Redirect(routes.CallForPaper.homeForSpeaker()).flashing("error" -> "Invalid state")
          }
        case None =>
          Redirect(routes.CallForPaper.homeForSpeaker()).flashing("error" -> Messages("invalid.proposal"))
      }
  }

  // Check that the current authenticated user is the owner
  // validate the form, save and redirect.
  def saveOtherSpeakers(proposalId: String) = SecuredAction {
    implicit request =>
      val uuid = request.webuser.uuid
      val maybeProposal = Proposal.findProposal(uuid, proposalId)
      maybeProposal match {
        case Some(proposal) =>
          Proposal.proposalSpeakerForm.bindFromRequest.fold(
            hasErrors => BadRequest(views.html.CallForPaper.editOtherSpeaker(Webuser.getName(uuid), proposal, hasErrors)).flashing("error" -> "Errors in the proposal form, please correct errors"),
            validNewSpeakers => {
              (proposal.secondarySpeaker, validNewSpeakers._1) match {
                case (None, Some(newSecondarySpeaker)) =>
                  val newSpeaker = Speaker.findByUUID(newSecondarySpeaker)
                  val validMsg = s"Internal notification : Added [${newSpeaker.map(_.cleanName).get}] as a secondary speaker"
                  if (proposal.state != ProposalState.DRAFT) {
                    ZapActor.actor ! SendBotMessageToCommittee(uuid, proposal, validMsg)
                  }
                  Event.storeEvent(Event(proposal.id, uuid, validMsg))
                  Proposal.updateSecondarySpeaker(uuid, proposalId, None, Some(newSecondarySpeaker))
                case (Some(oldSpeakerUUID), Some(newSecondarySpeaker)) if oldSpeakerUUID != newSecondarySpeaker =>
                  val oldSpeaker = Speaker.findByUUID(oldSpeakerUUID)
                  val newSpeaker = Speaker.findByUUID(newSecondarySpeaker)
                  val validMsg = s"Internal notification : Removed [${oldSpeaker.map(_.cleanName).get}] and added [${newSpeaker.map(_.cleanName).get}] as a secondary speaker"
                  if (proposal.state != ProposalState.DRAFT) {
                    ZapActor.actor ! SendBotMessageToCommittee(uuid, proposal, validMsg)
                  }
                  Event.storeEvent(Event(proposal.id, uuid, validMsg))
                  Proposal.updateSecondarySpeaker(uuid, proposalId, Some(oldSpeakerUUID), Some(newSecondarySpeaker))
                case (Some(oldSpeakerUUID), None) =>
                  val oldSpeaker = Speaker.findByUUID(oldSpeakerUUID)
                  val validMsg = s"Internal notification : Removed [${oldSpeaker.map(_.cleanName).get}] as a secondary speaker"
                  if (proposal.state != ProposalState.DRAFT) {
                    ZapActor.actor ! SendBotMessageToCommittee(uuid, proposal, validMsg)
                  }
                  Event.storeEvent(Event(proposal.id, uuid, validMsg))
                  Proposal.updateSecondarySpeaker(uuid, proposalId, Some(oldSpeakerUUID), None)
                case (Some(oldSpeakerUUID), Some(newSecondarySpeaker)) if oldSpeakerUUID == newSecondarySpeaker =>
                // We kept the 2nd speaker, maybe updated or added a 3rd speaker
                case (None, None) =>
                // Nothing special
              }

              Proposal.updateOtherSpeakers(uuid, proposalId, proposal.otherSpeakers, validNewSpeakers._2)
              Event.storeEvent(Event(proposal.id, uuid, "Updated speakers list for proposal " + StringUtils.abbreviate(proposal.title, 80)))

              Redirect(routes.CallForPaper.homeForSpeaker()).flashing("success" -> Messages("speakers.updated"))
            }
          )
        case None =>
          Redirect(routes.CallForPaper.homeForSpeaker()).flashing("error" -> Messages("invalid.proposal"))
      }
  }

  def deleteProposal(proposalId: String) = SecuredAction {
    implicit request =>
      val uuid = request.webuser.uuid
      val maybeProposal = Proposal.findProposal(uuid, proposalId)
      maybeProposal match {
        case Some(proposal) =>
          Proposal.delete(uuid, proposalId)
          Redirect(routes.CallForPaper.homeForSpeaker()).flashing("deleted" -> proposalId)
        case None =>
          Redirect(routes.CallForPaper.homeForSpeaker()).flashing("error" -> Messages("invalid.proposal"))
      }
  }

  def undeleteProposal(proposalId: String) = SecuredAction {
    implicit request =>
      val uuid = request.webuser.uuid
      val maybeProposal = Proposal.findDeleted(uuid, proposalId)
      maybeProposal match {
        case Some(proposal) =>
          Proposal.draft(uuid, proposalId)
          Redirect(routes.CallForPaper.homeForSpeaker()).flashing("success" -> Messages("talk.draft"))
        case None =>
          Redirect(routes.CallForPaper.homeForSpeaker()).flashing("error" -> Messages("invalid.proposal"))
      }
  }

  def submitProposal(proposalId: String) = SecuredAction {
    implicit request =>
      val uuid = request.webuser.uuid
      val maybeProposal = Proposal.findDraft(uuid, proposalId)
      val currentlySubmittedConcernedByQuota: Int = Proposal.countSubmittedAcceptedConcernedByQuota(uuid)
      val additionnalConcernedByQuota: Int = if (maybeProposal.map(p => ConferenceDescriptor.ConferenceProposalConfigurations.isConcernedByCountRestriction(p.talkType)).getOrElse(false)) 1 else 0

      maybeProposal match {
        case _ if currentlySubmittedConcernedByQuota + additionnalConcernedByQuota > ConferenceDescriptor.maxProposals() =>
          Redirect(routes.CallForPaper.homeForSpeaker()).flashing(
            "error" -> Messages("cfp.maxProposals.reached",
                ConferenceDescriptor.maxProposals(),
                ConferenceDescriptor.ConferenceProposalConfigurations.concernedByCountQuotaRestrictionAndNotHidden.map(pc => Messages(ProposalType.byProposalConfig(pc).simpleLabel)).mkString(", ")
            ))
        case Some(proposal) =>
          Proposal.submit(uuid, proposalId)
          if (ConferenceDescriptor.notifyProposalSubmitted) {
            // This generates too many emails for France and is useless
            ZapActor.actor ! NotifyProposalSubmitted(uuid, proposal)
          }
          Redirect(routes.CallForPaper.homeForSpeaker()).flashing("success" -> Messages("talk.submitted"))
        case None =>
          Redirect(routes.CallForPaper.homeForSpeaker()).flashing("error" -> Messages("invalid.proposal"))
      }
  }

  val speakerMsg = Form("msg" -> nonEmptyText(maxLength = 2500))

  def showCommentForProposal(proposalId: String) = SecuredAction {
    implicit request =>
      val uuid = request.webuser.uuid
      val maybeProposal = Proposal.findProposal(uuid, proposalId)
      maybeProposal match {
        case Some(proposal) =>
          Ok(views.html.CallForPaper.showCommentForProposal(proposal, Comment.allSpeakerComments(proposal.id), speakerMsg))
        case None =>
          Redirect(routes.CallForPaper.homeForSpeaker()).flashing("error" -> Messages("invalid.proposal"))
      }
  }

  def sendMessageToCommittee(proposalId: String) = SecuredAction {
    implicit request =>
      val uuid = request.webuser.uuid
      val maybeProposal = Proposal.findProposal(uuid, proposalId).filterNot(_.state == ProposalState.DELETED)
      maybeProposal match {
        case Some(proposal) =>
          speakerMsg.bindFromRequest.fold(
            hasErrors => {
              BadRequest(views.html.CallForPaper.showCommentForProposal(proposal, Comment.allSpeakerComments(proposal.id), hasErrors))
            },
            validMsg => {
              Comment.saveCommentForSpeaker(proposal.id, uuid, validMsg)
              ZapActor.actor ! SendMessageToCommittee(uuid, proposal, validMsg)
              Redirect(routes.CallForPaper.showCommentForProposal(proposalId)).flashing("success" -> "Message was sent")
            }
          )
        case None =>
          Redirect(routes.CallForPaper.homeForSpeaker()).flashing("error" -> Messages("invalid.proposal"))
      }
  }

  case class TermCount(term: String, count: Int)

  val phoneForm = play.api.data.Form("phoneNumber" -> nonEmptyText(maxLength = 15))
  val phoneConfirmForm = play.api.data.Form(tuple(
    "phoneNumber" -> nonEmptyText(maxLength = 15),
    "confirmation" -> nonEmptyText(maxLength = 15)
  )
  )

  def updatePhoneNumber() = SecuredAction.async {
    implicit request =>
      phoneForm.bindFromRequest().fold(
        invalidPhone => Future.successful(Redirect(routes.CallForPaper.homeForSpeaker()).flashing("error" -> Messages("invalid.phone"))),
        validPhone => {
          Future.successful {
            val code = StringUtils.left(request.webuser.uuid, 4) // Take the first 4 characters as the validation code
            if (ConferenceDescriptor.isTwilioSMSActive) {
              TwilioSender.send(validPhone, Messages("sms.confirmationTxt", code)) match {
                case Success(reason) =>
                  Ok(views.html.CallForPaper.enterConfirmCode(phoneConfirmForm.fill((validPhone, code))))
                case Failure(exception) =>
                  InternalServerError(views.html.CallForPaper.invalidPhoneNumber(exception)).as(HTML)
              }
            } else {
              val webuser = request.webuser
              Speaker.updatePhone(webuser.uuid, validPhone, request.acceptLanguages.headOption)
              Redirect(routes.CallForPaper.homeForSpeaker()).flashing("success" -> Messages("phonenumber.updated.success"))
            }

          }
        }
      )
  }

  def confirmPhone() = SecuredAction {
    implicit request =>
      phoneConfirmForm.bindFromRequest().fold(
        hasErrors => Redirect(routes.CallForPaper.homeForSpeaker()).flashing("error" -> Messages("invalid.confirmation.code")),
        success => {
          val thePhone = success._1
          val theConfCode = success._2
          val webuser = request.webuser
          val code = StringUtils.left(request.webuser.uuid, 4) // Take the first 4 characters as the validation code
          if (theConfCode == code) {
            Speaker.updatePhone(webuser.uuid, thePhone, request.acceptLanguages.headOption)
            SmsActor.actor ! SendWelcomeAndHelp(thePhone)
            Redirect(routes.CallForPaper.homeForSpeaker()).flashing("success" -> Messages("phonenumber.updated.success"))
          } else {
            Redirect(routes.CallForPaper.homeForSpeaker()).flashing("error" -> Messages("invalid.confirmation.code"))
          }

        }
      )
  }
}

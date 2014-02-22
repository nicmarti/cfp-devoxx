package controllers

import models._
import play.api.data._
import play.api.data.Forms._
import library.{Redis, ZapActor}
import library.search._
import org.joda.time.Instant
import play.api.Play
import library.search.DoIndexSpeaker
import library.search.DoIndexProposal
import library.DraftReminder

/**
 * Backoffice actions, for maintenance and validation.
 *
 * Author: nicolas martignole
 * Created: 02/12/2013 21:34
 */
object Backoffice extends SecureCFPController {

  val isCFPOpen: Boolean = {
    Play.current.configuration.getBoolean("cfp.isOpen").getOrElse(true)
  }

  def homeBackoffice() = SecuredAction(IsMemberOf("admin")) {
    implicit request =>
      Ok(views.html.Backoffice.homeBackoffice())
  }

  // Add or remove the specified user from "cfp" security group
  def switchCFPAdmin(uuidSpeaker: String) = SecuredAction(IsMemberOf("admin")) {
    implicit request =>
      if (Webuser.hasAccessToCFP(uuidSpeaker)) {
        Event.storeEvent(Event(uuidSpeaker, request.webuser.uuid, "removed user from CFP group"))
        Webuser.removeFromCFPAdmin(uuidSpeaker)
      } else {
        Webuser.addToCFPAdmin(uuidSpeaker)
        Event.storeEvent(Event(uuidSpeaker, request.webuser.uuid, "added user to CFP group"))
      }
      Redirect(routes.CFPAdmin.allWebusers())
  }

  // Authenticate on CFP on behalf of specified user.
  def authenticateAs(uuidSpeaker: String) = SecuredAction(IsMemberOf("cfp")) {
    implicit request =>
      Redirect(routes.CallForPaper.homeForSpeaker).withSession("uuid" -> uuidSpeaker)
  }

  def allProposals() = SecuredAction(IsMemberOf("admin")) {
    implicit request =>
      val proposals = Proposal.allProposals().sortBy(_.state.code)
      Ok(views.html.Backoffice.allProposals(proposals))
  }

  def changeProposalState(proposalId: String, state:String) = SecuredAction(IsMemberOf("admin")) {
    implicit request =>
      Proposal.changeProposalState(request.webuser.uuid, proposalId, ProposalState.parse(state))
      Redirect(routes.Backoffice.allProposals()).flashing("success" -> ("Changed state to "+state))
  }

  val formSecu = Form("secu" -> nonEmptyText())

  def deleteSpeaker(speakerUUIDToDelete: String) = SecuredAction(IsMemberOf("admin")) {
    implicit request =>
      val uuid = request.webuser.uuid
      if (Webuser.isMember(speakerUUIDToDelete, "cfp") || Webuser.isMember(speakerUUIDToDelete, "admin")) {
        Redirect(routes.CFPAdmin.index()).flashing("error" -> s"We cannot delete CFP admin user...")
      } else {
        formSecu.bindFromRequest.fold(invalid => {
          Redirect(routes.CFPAdmin.index()).flashing("error" -> "You did not enter DEL... are you drunk?")
        }, _ => {
          Speaker.delete(speakerUUIDToDelete)
          Webuser.findByUUID(speakerUUIDToDelete).foreach {
            w =>
              Webuser.delete(w)
              Event.storeEvent(Event(speakerUUIDToDelete, uuid, s"Deleted webuser ${w.cleanName} ${w.uuid}"))
          }
          Redirect(routes.CFPAdmin.index()).flashing("success" -> s"Speaker $speakerUUIDToDelete deleted")
        })
      }
  }

  def doIndexElasticSearch() = SecuredAction(IsMemberOf("admin")) {
    implicit request =>
      ElasticSearchActor.masterActor ! DoIndexAllSpeakers()
      ElasticSearchActor.masterActor ! DoIndexAllProposals()
      ElasticSearchActor.masterActor ! DoIndexAllHitViews()
      if(Play.current.mode==play.api.Mode.Dev){
        ElasticSearchActor.masterActor ! DoIndexAllReviews()
      }
      Redirect(routes.Backoffice.homeBackoffice).flashing("success" -> "Elastic search actor started...")
  }

  // If a user is not a member of cfp security group anymore, then we need to delete all its votes.
  def cleanUpVotesIfUserWasDeleted = SecuredAction(IsMemberOf("admin")) {
    implicit request =>
      Proposal.allProposalIDs.foreach {
        proposalID: String =>
          Review.allVotesFor(proposalID).foreach {
            case (reviewerUUID, _) => {
              if (Webuser.doesNotExist(reviewerUUID)) {
                play.Logger.of("application.Backoffice").debug(s"Deleting vote on $proposalID for user $reviewerUUID")
                Review.removeVoteForProposal(proposalID, reviewerUUID)
              }
            }
          }
      }
      Ok("Done")
  }

  def deleteVotesForPropal(proposalId: String) = SecuredAction(IsMemberOf("admin")) {
    implicit request =>
      Review.allVotesFor(proposalId).foreach {
        case (reviewerUUID, score) => {
          play.Logger.of("application.Backoffice").info(s"Deleting vote on $proposalId by $reviewerUUID of score $score")
          Review.deleteVoteForProposal(proposalId)
        }
      }
      Redirect(routes.CFPAdmin.showVotesForProposal(proposalId))
  }

  def submittedByDate() = SecuredAction(IsMemberOf("admin")) {
    implicit request =>

      Redis.pool.withClient {
        client =>
          val toReturn = client.hgetAll("Proposal:SubmittedDate").map {
            case (proposal, submitted) =>
              (proposal, new Instant(submitted.toLong).toDateTime.toDateMidnight.toString("dd-MM-yyyy"))
          }.groupBy(_._2).map {
            tuple =>
              (tuple._1, tuple._2.size)
          }.toList.sortBy(_._1).map {
            s =>
              s._1 + ", " + s._2 + "\n"
          }

          Ok("Date, total\n" + toReturn.mkString).as("text/plain")
      }
  }

}



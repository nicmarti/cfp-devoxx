package controllers

import models._
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import play.api.data.validation.Constraints._
import library.{Redis, Benchmark, DraftReminder, ZapActor}
import library.search.{DoIndexProposal, DoIndexSpeaker, ElasticSearchActor}
import library.search.DoIndexSpeaker
import library.search.DoIndexProposal
import org.joda.time.{Instant, DateTime}
import scala.collection.immutable.HashMap
import play.api.Play

/**
 * Backoffice actions, for maintenance and validation.
 *
 * Author: nicolas martignole
 * Created: 02/12/2013 21:34
 */
object Backoffice extends Controller with Secured {

   val isCFPOpen:Boolean ={
    Play.current.configuration.getBoolean("cfp.isOpen").getOrElse(true)
  }

  // Returns all speakers
  def allSpeakers = IsMemberOf("admin") {
    implicit uuid => implicit request =>
      Ok(views.html.Backoffice.allSpeakers(Webuser.allSpeakers.sortBy(_.email)))
  }

  // Add or remove the specified user from "cfp" security group
  def switchCFPAdmin(uuidSpeaker: String) = IsMemberOf("admin") {
    implicit uuid => implicit request =>
      if (Webuser.hasAccessToCFPAdmin(uuidSpeaker)) {
        Webuser.removeFromCFPAdmin(uuidSpeaker)
      } else {
        Webuser.addToCFPAdmin(uuidSpeaker)
      }
      Redirect(routes.Backoffice.allSpeakers)
  }

  // Authenticate on CFP on behalf of specified user.
  def authenticateAs(uuidSpeaker: String) = IsMemberOf("admin") {
    implicit uuid => implicit request =>
      Redirect(routes.CallForPaper.homeForSpeaker).withSession("uuid" -> uuidSpeaker)
  }

  def allDraftProposals() = IsMemberOf("admin") {
    implicit uuid => implicit request =>
      val proposals = Proposal.allDrafts()
      Ok(views.html.Backoffice.allDraftProposals(proposals))
  }

  def allSubmittedProposals() = IsMemberOf("admin") {
    implicit uuid => implicit request =>
      val proposals = Proposal.allSubmitted()
      Ok(views.html.Backoffice.allSubmittedProposals(proposals))
  }

  def moveProposalToTrash(proposalId: String) = IsMemberOf("admin") {
    implicit uuid => implicit request =>
      Proposal.delete(uuid, proposalId)
      val undoDelete = routes.Backoffice.moveProposalToDraft(proposalId).url
      Redirect(routes.Backoffice.allDraftProposals()).flashing("success" -> s"Deleted Proposal. <a href='$undoDelete'>Undo delete</a>")
  }

  def moveSubmittedProposalToTrash(proposalId: String) = IsMemberOf("admin") {
    implicit uuid => implicit request =>
      Proposal.delete(uuid, proposalId)
      val undoDelete = routes.Backoffice.moveProposalToDraft(proposalId).url
      Redirect(routes.Backoffice.allSubmittedProposals()).flashing("success" -> s"Deleted Proposal. <a href='$undoDelete'>Undo delete</a>")
  }

  def moveProposalToDraft(proposalId: String) = IsMemberOf("admin") {
    implicit uuid => implicit request =>
      Proposal.draft(uuid, proposalId)
      Redirect(routes.Backoffice.allSubmittedProposals()).flashing("success" -> s"Undeleted proposal ${proposalId}")
  }

  def moveProposalToSubmit(proposalId: String) = IsMemberOf("admin") {
    implicit uuid => implicit request =>
      Proposal.submit(uuid, proposalId)
      val undoSubmit = routes.Backoffice.moveProposalToDraft(proposalId).url
      Redirect(routes.Backoffice.allDraftProposals()).flashing("success" -> s"Proposal ${proposalId} was submitted. <a href='$undoSubmit'>Cancel this submission?</a>")
  }

  def sendReminderToSpeakersForDraft() = IsMemberOf("admin") {
    implicit uuid => implicit request =>
    // Send a message to an Actor
      ZapActor.actor ! DraftReminder()
      // Then redirect
      Redirect(routes.Backoffice.allDraftProposals()).flashing("success" -> "An email will be sent to Speakers with Draft proposal")
  }

  val formSecu = Form("secu" -> nonEmptyText())

  def deleteSpeaker(speakerUUIDToDelete: String) = IsMemberOf("admin") {
    implicit uuid => implicit request =>
      if (Webuser.isMember(speakerUUIDToDelete, "cfp") || Webuser.isMember(speakerUUIDToDelete, "admin")) {
        Redirect(routes.Backoffice.allDraftProposals()).flashing("error" -> s"We cannot delete CFP admin user...")
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

  def testElasticSearch = IsMemberOf("admin") {
    implicit uuid => implicit request =>
      ElasticSearchActor.masterActor ! DoIndexSpeaker()
      ElasticSearchActor.masterActor ! DoIndexProposal()
      Ok("Elasticsearch : index task started")
  }

  // If a user is not a member of cfp security group anymore, then we need to delete all its votes.
  def cleanUpVotesIfUserWasDeleted = IsMemberOf("admin") {
    implicit uuid => implicit request =>

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

  def updateRedis() = IsMemberOf("admin"){
    implicit uuid => implicit request=>

      Redis.pool.withClient{
        client=>
        val toReturn = client.hgetAll("Proposal:SubmittedDate").map{case(proposal,submitted)=>
          (proposal, new Instant(submitted.toLong).toDateTime.toDateMidnight.toString("dd-MM-yyyy"))
        }.groupBy(_._2).map{
          tuple=>
            (tuple._1, tuple._2.size)
        }


        Ok(toReturn.mkString("\n")).as("text/plain")
      }
  }

  def homeBackoffice()=IsMemberOf("admin"){
    implicit uuid =>
      implicit request=>
      Ok(views.html.Backoffice.homeBackoffice())
  }
}



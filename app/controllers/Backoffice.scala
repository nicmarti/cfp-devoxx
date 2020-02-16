package controllers

import library.search.{DoIndexProposal, _}
import library._
import models.ConferenceDescriptor.ConferenceProposalTypes
import models.{Tag, _}
import org.joda.time.{DateTime, Instant}
import play.api.Play
import play.api.cache.EhCachePlugin
import play.api.data.Forms._
import play.api.data._
import play.api.i18n.Messages
import play.api.libs.json.Json
import play.api.mvc.Action

import scala.concurrent.Future

/**
  * Backoffice actions, for maintenance and validation.
  *
  * Author: nicolas martignole
  * Created: 02/12/2013 21:34
  */
object Backoffice extends SecureCFPController {

  def homeBackoffice() = SecuredAction(IsMemberOf("admin")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      Ok(views.html.Backoffice.homeBackoffice())
  }

  // Add or remove the specified user from "cfp" security group
  def switchCFPAdmin(uuidSpeaker: String) = SecuredAction(IsMemberOf("admin")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      Webuser.findByUUID(uuidSpeaker).filterNot(_.uuid == "bd894205a7d579351609f8dcbde49b9ffc0fae13").map {
        webuser =>
          if (Webuser.hasAccessToCFP(uuidSpeaker)) {
            Event.storeEvent(Event(uuidSpeaker, request.webuser.uuid, s"removed ${webuser.cleanName} from CFP group"))
            Webuser.removeFromCFPAdmin(uuidSpeaker)
          } else {
            Webuser.addToCFPAdmin(uuidSpeaker)
            Event.storeEvent(Event(uuidSpeaker, request.webuser.uuid, s"added ${webuser.cleanName} to CFP group"))
          }
          Redirect(routes.CFPAdmin.allWebusers())
      }.getOrElse {
        NotFound("Webuser not found")
      }
  }

  // Authenticate on CFP on behalf of specified user.
  def authenticateAs(uuidSpeaker: String) = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      // Block redirect if the uuidSpeaker belongs to the ADMIN group and not you
      if (Webuser.isMember(uuidSpeaker, "admin") && Webuser.isNotMember(request.webuser.uuid, "admin")) {
        Unauthorized("Sorry, only admin user can become admin.")
      } else {
        Redirect(routes.CallForPaper.homeForSpeaker()).withSession("uuid" -> uuidSpeaker)
      }
  }

  def authenticateAndCreateTalk(uuidSpeaker: String) = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      if (Webuser.isMember(uuidSpeaker, "admin") && Webuser.isNotMember(request.webuser.uuid, "admin")) {
        Unauthorized("Sorry, only admin user can become admin.")
      } else {
        Redirect(routes.CallForPaper.newProposal()).withSession("uuid" -> uuidSpeaker)
      }
  }

  def allProposals(proposalId: Option[String]) = SecuredAction(IsMemberOf("admin")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>

      proposalId match {
        case Some(id) =>
          val proposal = Proposal.findById(id)
          proposal match {
            case None => NotFound("Proposal not found")
            case Some(pr) => Ok(views.html.Backoffice.allProposals(List(pr)))
          }
        case None =>
          val proposals = Proposal.allProposals().sortBy(_.state.code)
          Ok(views.html.Backoffice.allProposals(proposals))
      }


  }

  // This endpoint is deliberately *not* secured in order to transform a user into an admin
  // only if there isn't any admin in the application
  def bootstrapAdminUser(uuid: String) = Action {
    implicit request =>
      if (Webuser.noBackofficeAdmin()) {
        Webuser.addToBackofficeAdmin(uuid)
        Webuser.addToCFPAdmin(uuid)
        Redirect(routes.Application.index()).flashing("success" -> "Your UUID has been configured as super-admin")
      } else {
        Redirect(routes.Application.index()).flashing("error" -> "There is already an Admin user")
      }
  }

  def clearCaches() = SecuredAction(IsMemberOf("admin")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      Play.current.plugin[EhCachePlugin].foreach(p => p.manager.clearAll())
      Ok(views.html.Backoffice.homeBackoffice())
  }

  def changeProposalState(proposalId: String, state: String) = SecuredAction(IsMemberOf("admin")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      Proposal.changeProposalState(request.webuser.uuid, proposalId, ProposalState.parse(state))
      if (state == ProposalState.ACCEPTED.code) {
        Proposal.findById(proposalId).foreach {
          proposal =>
            ApprovedProposal.approve(proposal)
            ElasticSearchActor.masterActor ! DoIndexProposal(proposal.copy(state = ProposalState.ACCEPTED))
        }
      }
      if (state == ProposalState.DECLINED.code) {
        Proposal.findById(proposalId).foreach {
          proposal =>
            ApprovedProposal.refuse(proposal)
            ElasticSearchActor.masterActor ! DoIndexProposal(proposal.copy(state = ProposalState.DECLINED))
        }
      }
      Redirect(routes.Backoffice.allProposals()).flashing("success" -> ("Changed state to " + state))
  }

  val formSecu = Form("secu" -> nonEmptyText())

  def deleteSpeaker(speakerUUIDToDelete: String) = SecuredAction(IsMemberOf("admin")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
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
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      ElasticSearchActor.masterActor ! DoIndexAllSpeakers
      ElasticSearchActor.masterActor ! DoIndexAllProposals
      ElasticSearchActor.masterActor ! DoIndexAllAccepted
      ElasticSearchActor.masterActor ! DoIndexAllHitViews
      ElasticSearchActor.masterActor ! DoIndexSchedule
      Redirect(routes.Backoffice.homeBackoffice()).flashing("success" -> "Elastic search actor started...")
  }

  def doResetAndConfigureElasticSearch() = SecuredAction(IsMemberOf("admin")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      ElasticSearchActor.masterActor ! DoCreateConfigureIndex
      Redirect(routes.Backoffice.homeBackoffice()).flashing("success" -> "Deleted and now creating [speakers] and [proposals] indexes. Please force an indexer in one or two minutes.")
  }

  // If a user is not a member of cfp security group anymore, then we need to delete all its votes.
  def cleanUpVotesIfUserWasDeleted = SecuredAction(IsMemberOf("admin")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      Proposal.allProposalIDs.foreach {
        proposalID: String =>
          Review.allVotesFor(proposalID).foreach {
            case (reviewerUUID, _) =>
              if (Webuser.doesNotExist(reviewerUUID)) {
                play.Logger.of("application.Backoffice").debug(s"Deleting vote on $proposalID for user $reviewerUUID")
                Review.removeVoteForProposal(proposalID, reviewerUUID)
              }
          }
      }
      Ok("Done")
  }

  def deleteVotesForPropal(proposalId: String) = SecuredAction(IsMemberOf("admin")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      Review.allVotesFor(proposalId).foreach {
        case (reviewerUUID, score) =>
          play.Logger.of("application.Backoffice").info(s"Deleting vote on $proposalId by $reviewerUUID of score $score")
          Review.deleteVoteForProposal(proposalId)
          ReviewByGoldenTicket.deleteVoteForProposal(proposalId)
      }
      Redirect(routes.CFPAdmin.showVotesForProposal(proposalId))
  }

  def submittedByDate() = SecuredAction(IsMemberOf("admin")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>

      Redis.pool.withClient {
        client =>
          val toReturn = client.hgetAll("Proposal:SubmittedDate").map {
            case (proposal, submitted) =>
              (proposal, new Instant(submitted.toLong).toDateTime.toString("dd-MM-yyyy"))
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

  def sanityCheckSchedule(programScheduleId: Option[String]) = SecuredAction(IsMemberOf("admin")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      val allPublishedProposals = ScheduleConfiguration.loadAllPublishedSlots(programScheduleId).filter(_.proposal.isDefined)
      val publishedTalksExceptBOF = allPublishedProposals.filterNot(_.proposal.get.talkType == ConferenceDescriptor.ConferenceProposalTypes.BOF)

      val declined = publishedTalksExceptBOF.filter(_.proposal.get.state == ProposalState.DECLINED)
      val approved = publishedTalksExceptBOF.filter(_.proposal.get.state == ProposalState.APPROVED)
      val accepted = publishedTalksExceptBOF.filter(_.proposal.get.state == ProposalState.ACCEPTED)


      // For Terms&Conditions we focus on all talks except BOF
      val allSpeakersExceptBOF = publishedTalksExceptBOF.flatMap(_.proposal.get.allSpeakerUUIDs).toSet
      val onlySpeakersThatNeedsToAcceptTerms: Set[String] = allSpeakersExceptBOF.filter(uuid => Speaker.needsToAccept(uuid)).filter {
        speakerUUID =>
          // Keep only speakers with at least one accepted or approved talk
          approved.exists(_.proposal.get.allSpeakerUUIDs.contains(speakerUUID)) || accepted.exists(_.proposal.get.allSpeakerUUIDs.contains(speakerUUID))
      }
      val allSpeakers = Speaker.loadSpeakersFromSpeakerIDs(onlySpeakersThatNeedsToAcceptTerms)

      // Speaker declined talk AFTER it has been published
      val acceptedThenChangedToOtherState = accepted.filter {
        slot: Slot =>
          val proposal = slot.proposal.get
          !Proposal.findProposalState(proposal.id).contains(ProposalState.ACCEPTED)
      }

      // ALL Talks for Conflict search
      val approvedOrAccepted = allPublishedProposals.filter(p => p.proposal.get.state == ProposalState.ACCEPTED || p.proposal.get.state == ProposalState.APPROVED)
      val allSpeakersIDs = approvedOrAccepted.flatMap(_.proposal.get.allSpeakerUUIDs).toSet

      val specialSpeakers = allSpeakersIDs

      // Speaker that do a presentation with same TimeSlot (which is obviously not possible)
      val allWithConflicts: Set[(Speaker, Map[DateTime, Iterable[Slot]])] =
        for (speakerId <- specialSpeakers; speaker <- Speaker.findByUUID(speakerId)) yield {
          val proposalsPresentedByThisSpeaker: List[Slot] = approvedOrAccepted.filter(_.proposal.get.allSpeakerUUIDs.contains(speakerId))
          val groupedByDate = proposalsPresentedByThisSpeaker.groupBy(_.from)
          val conflict = groupedByDate.filter(_._2.size > 1)
          (speaker, conflict)
        }

      Ok(
        views.html.Backoffice.sanityCheckSchedule(
          declined,
          approved,
          acceptedThenChangedToOtherState,
          allSpeakers,
          allWithConflicts.filter(_._2.nonEmpty),
          ProgramSchedule.allProgramSchedulesForCurrentEvent(),
          programScheduleId

        )
      )

  }

  def refreshSchedules() = SecuredAction(IsMemberOf("admin")).async {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      import akka.pattern.ask
      import akka.util.Timeout

      import scala.concurrent.ExecutionContext.Implicits.global
      import scala.concurrent.duration._
      implicit val timeout = Timeout(15 seconds)

      val futureMessages: Future[Any] = ZapActor.actor ? CheckSchedules

      futureMessages.map {
        case results:List[ProposalAndRelatedError]=>
          Ok(views.html.Backoffice.refreshSchedules(results))
        case _=>
          play.Logger.error("refreshSchedules error with Akka")
          InternalServerError(s"Unable to refresh schedule, exception was raised from Akka Actor")
      }

  }

  def confirmPublicationChange(talkType: String, proposalId: String) = SecuredAction(IsMemberOf("admin")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      ZapActor.actor ! UpdateSchedule(talkType, proposalId)
      Redirect(routes.Backoffice.refreshSchedules())
  }

  def sanityCheckProposals() = SecuredAction(IsMemberOf("admin")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      Redis.pool.withClient {
        client =>
          val toReturn = client.hgetAll("Proposals").map {
            case (proposalId, json) =>
              (proposalId, Json.parse(json).asOpt[Proposal])
          }.filter(_._2.isEmpty).keys
          Ok(views.html.Backoffice.sanityCheckProposals(toReturn))
      }
  }

  def fixToAccepted(slotId: String, proposalId: String, talkType: String, programScheduleId: Option[String] = None) = SecuredAction(IsMemberOf("admin")) {
    implicit request =>
      val maybeUpdated = for (
        scheduleId <- ScheduleConfiguration.getPublishedSchedule(talkType, programScheduleId);
        scheduleConf <- ScheduleConfiguration.loadScheduledConfiguration(scheduleId);
        slot <- scheduleConf.slots.find(_.id == slotId).filter(_.proposal.isDefined).filter(_.proposal.get.id == proposalId)
      ) yield {
        val updatedProposal = slot.proposal.get.copy(state = ProposalState.ACCEPTED)
        val updatedSlot = slot.copy(proposal = Some(updatedProposal))
        val newListOfSlots = updatedSlot :: scheduleConf.slots.filterNot(_.id == slotId)
        val newID = ScheduleConfiguration.persist(talkType, newListOfSlots, request.webuser)

        // Automatically updating published program behind the scenes
        ProgramSchedule.updatePublishedScheduleConfiguration(scheduleId, newID, ConferenceProposalTypes.valueOf(talkType), None)
        newID
      }

      maybeUpdated.map {
        newID =>
          Redirect(routes.Backoffice.sanityCheckSchedule()).flashing("success" -> s"Created a new scheduleConfiguration ($newID) and published a new agenda.")
      }.getOrElse {
        NotFound("Unable to update Schedule configuration, did not find the slot, the proposal or the scheduleConfiguraiton")
      }
  }

  def sendDraftReminder = SecuredAction(IsMemberOf("admin")) {
    implicit request =>
      ZapActor.actor ! DraftReminder()
      Redirect(routes.Backoffice.homeBackoffice()).flashing("success" -> "Sent draft reminder to speakers")
  }

  def showAllDeclined() = SecuredAction(IsMemberOf("admin")) {
    implicit request =>

      val allDeclined = Proposal.allDeclinedProposals()
      //      Proposal.decline(request.webuser.uuid, proposalId)
      Ok(views.html.Backoffice.showAllDeclined(allDeclined))

  }

  def exportAgenda = Action {
    implicit request =>
      val publishedConf = ScheduleConfiguration.loadAllPublishedSlots()
      Ok(views.html.Backoffice.exportAgenda(publishedConf))
  }

  // Tag related controllers

  def showAllTags = SecuredAction(IsMemberOf("admin")) {
    implicit request =>
      val allTags = Tag.allTags().sortBy(t => t.value)
      Ok(views.html.Backoffice.showAllTags(allTags))
  }

  def newTag = SecuredAction(IsMemberOf("admin")) {
    implicit request =>
      Ok(views.html.Backoffice.newTag(Tag.tagForm))
  }

  def saveTag() = SecuredAction(IsMemberOf("admin")) {
    implicit request =>

      Tag.tagForm.bindFromRequest.fold(
        hasErrors => BadRequest(views.html.Backoffice.newTag(hasErrors)),
        tagData => {
          // Is it an update?
          if (Tag.findById(tagData.id).nonEmpty) {
            Tag.delete(tagData.id)
            Tag.save(Tag.createTag(tagData.value))
          } else {
            Tag.save(tagData)
          }
        }
      )

      Redirect(routes.Backoffice.homeBackoffice()).flashing("success" -> Messages("tag.saved"))
  }

  def editTag(uuid: String) = SecuredAction(IsMemberOf("admin")) {
    implicit request =>
      val foundTag = Tag.findById(uuid)
      foundTag match {
        case None => NotFound("Sorry, this tag does not exit")
        case Some(tag) => {
          Ok(views.html.Backoffice.newTag(Tag.tagForm.fill(tag)))
        }
      }
  }

  def importTags = SecuredAction(IsMemberOf("admin")) {
    implicit request =>
      Ok(views.html.Backoffice.importTags(Tag.tagForm))
  }

  def exportTags = SecuredAction(IsMemberOf("admin")) {
    implicit request =>
      val allTags = Tag.allTags().sortBy(t => t.value)
      Ok(views.html.Backoffice.exportTags(allTags))
  }

  def saveImportTags() = SecuredAction(IsMemberOf("admin")) {
    implicit request =>

      Tag.tagForm.bindFromRequest.fold(
        hasErrors => BadRequest(views.html.Backoffice.importTags(hasErrors)),
        tagData => {
          val tags = tagData.value.split(";")
          tags.foreach(f => Tag.save(Tag.createTag(f)))
        }
      )

      Redirect(routes.Backoffice.homeBackoffice()).flashing("success" -> Messages("tag.imported"))
  }

  def deleteTag(id: String) = SecuredAction(IsMemberOf("admin")) {
    implicit request =>
      if (Tags.isTagLinkedByProposal(id)) {
        BadRequest("Tag is used by a proposal, unlink tag first.")
      } else {
        val tagValue = Tag.findTagValueById(id)
        if (tagValue.isDefined) {
          Tag.delete(id)
          Redirect(routes.Backoffice.homeBackoffice()).flashing("success" -> Messages("tag.removed", tagValue.get))
        } else {
          BadRequest("Tag ID doesn't exist")
        }
      }
  }

  def getProposalsByTags = SecuredAction(IsMemberOf("admin")) {
    implicit request =>
      val allProposalsByTags = Tags.allProposals()

      Ok(views.html.Backoffice.showAllProposalsByTags(allProposalsByTags))
  }

  def getSelectionByTags = SecuredAction(IsMemberOf("admin")) {
    implicit request =>
      val allProposalsByTags = Tags.allProposals()

      Ok(views.html.Backoffice.showSelectionByTags(allProposalsByTags))
  }

  def getCloudTag = SecuredAction(IsMemberOf("admin")) {
    implicit request =>
      val termCounts = Tags.countProposalTags()
      if (termCounts.nonEmpty) {
        Ok(views.html.CallForPaper.cloudTags(termCounts))
      } else {
        NotFound("No proposal tags found")
      }
  }

  def showDigests = SecuredAction(IsMemberOf("admin")) {
    implicit request =>

      // TODO Can this be condensed in Scala ?  (Stephan)

      val realTimeDigests = Digest.pendingProposals(Digest.REAL_TIME)
      val dailyDigests = Digest.pendingProposals(Digest.DAILY)
      val weeklyDigests = Digest.pendingProposals(Digest.WEEKLY)

      val realTime = realTimeDigests.map {
        case (key: String, value: String) =>
          (Proposal.findById(key).get, value)
      }

      val daily = dailyDigests.map {
        case (key: String, value: String) =>
          (Proposal.findById(key).get, value)
      }

      val weekly = weeklyDigests.map {
        case (key: String, value: String) =>
          (Proposal.findById(key).get, value)
      }

      Ok(views.html.Backoffice.showDigests(realTime, daily, weekly))
  }


  def pushNotifications(message:String) = SecuredAction(IsMemberOf("admin")) {
    implicit request =>

      request.body.asJson.map {
        json =>
          ZapActor.actor ! NotifyMobileApps(message)
          Ok(message)
      }.getOrElse {
        BadRequest("{\"status\":\"expecting json data\"}").as("application/json")
      }
  }

  def deleteWebuser(uuid: String) = SecuredAction(IsMemberOf("admin")) {
    implicit request =>
      Webuser.findByUUID(uuid) match {
        case Some(w) =>
          Webuser.delete(w)
          Speaker.delete(uuid)
          FavoriteTalk.deleteAllForWebuser(uuid)
          ScheduleTalk.deleteAllForWebuser(uuid)
          Ok("Deleted Webuser and deleted speaker, favorite talks and scheduleTalk. Webuser=" + w)
        case None => NotFound("Webuser not found")
      }
  }

  def checkInvalidWebuserForAllSpeakers() = SecuredAction(IsMemberOf("admin")) {
    implicit request =>
      Speaker.allSpeakersUUID().foreach {
        uuid =>
          Webuser.findByUUID(uuid) match {
            case None =>
              val s = Speaker.findByUUID(uuid)
              play.Logger.info("Missing Webuser for Speaker " + uuid + " " + s.map(_.cleanName))
            case _ =>
          }

          Webuser.isSpeaker(uuid) match {
            case false =>
              val s = Speaker.findByUUID(uuid)
              play.Logger.info("Missing group for speaker " + uuid + " " + s.map(_.cleanName))
            case _ =>
          }
          Speaker.findByUUID(uuid).foreach {
            speaker =>
              Webuser.isEmailRegistered(speaker.email) match {
                case false =>
                  play.Logger.error(s"Speaker's email is not stored in Webuser:Email => BUG ${speaker.email}")
                  Webuser.fixMissingEmail(speaker.email, speaker.uuid)
                case _ =>
              }
          }

      }
      Ok("voir la console")
  }

  /**
    * Due to a stupid bug, some speakers are not linked to their Webuser anymore. As such, we could not reload their profile.
    * This fix will delete the "new invalid" Webuser, update the existing collections to link the speaker UUID to the old Webuser UUID
    */
  def fixForInvalidSpeakers(speakerUUID: String) = SecuredAction(IsMemberOf("admin")) {
    implicit request =>

      //Speaker.findBuggedSpeakers

      val result = Speaker.fixAndRestoreOldWebuser(speakerUUID)
      Ok("Result: " + result)
  }

  def fixRedisDataConsistency() = SecuredAction(IsMemberOf("admin")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      var summary = new StringBuilder

      Proposal.allProposals().foreach(proposal => {
        val removedStates = Proposal.ensureProposaleHasState(proposal.id, proposal.state)
        if(removedStates != 0) {
          summary ++= s"Removed ${removedStates} invalid states for proposal ${proposal.id} (valid state: ${proposal.state.code})\n"
        }
        val removedTypes = Proposal.ensureProposaleHasType(proposal.id, proposal.talkType)
        if(removedTypes != 0) {
          summary ++= s"Removed ${removedTypes} invalid types for proposal ${proposal.id} (valid type: ${proposal.talkType.id})\n"
        }
        val removeTracks = Proposal.ensureProposaleHasTrack(proposal.id, proposal.track.id)
        if(removeTracks != 0) {
          summary ++= s"Removed ${removeTracks} invalid tracks for proposal ${proposal.id} (valid track: ${proposal.track.id})\n"
        }
      })

      Ok(summary.toString)
  }

}
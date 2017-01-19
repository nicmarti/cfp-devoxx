package models

import library.{Dress, Redis}
import org.apache.commons.lang3.{RandomStringUtils, StringUtils}
import org.joda.time.Instant
import play.api.data.Forms._
import play.api.data._
import play.api.i18n.Messages
import play.api.libs.json.Json
import play.api.templates.HtmlFormat

/**
  * Proposal is the main and maybe the most important object for a CFP.
  *
  * Author: nicolas martignole
  * Created: 12/10/2013 15:19
  */
case class ProposalType(id: String, label: String) {

  // Returns the simple label name used in the I18N Messages resource bundle
  def simpleLabel: String = {
    label + ".simple"
  }
}

object ProposalType {
  implicit val proposalTypeFormat = Json.format[ProposalType]

  val UNKNOWN = ProposalType(id = "unknown", label = "unknown.label")

  val all = ConferenceDescriptor.ConferenceProposalTypes.ALL
  val allAsId = all.map(a => (a.id, a.label)).sorted

  def allForCombos = {
    val onlyThoseThatShouldBeDisplayed = all.filterNot(_ == UNKNOWN)
    val finalFormat = onlyThoseThatShouldBeDisplayed.map(a => (a.id, a.label)).sorted
    finalFormat
  }

  def allIDsOnly = allAsId.map(_._1)


  def parse(proposalType: String): ProposalType = {
    all.find(p => p.id == proposalType).getOrElse(UNKNOWN)
  }

  val audienceLevels: Seq[(String, String)] = {
    List(
      ("l1", "level1.label")
      , ("l2", "level2.label")
      , ("l3", "level3.label")
    )
  }

  val demoLevels: Seq[(String, String)] = {
    List(
      ("d1", "demoLevel1.label")
      , ("d2", "demoLevel2.label")
      , ("d3", "demoLevel3.label")
      , ("d4", "demoLevel4.label"))
  }
}

case class ProposalState(code: String)

object ProposalState {

  implicit val proposalStateFormat = Json.format[ProposalState]

  val DRAFT = ProposalState("draft")
  val SUBMITTED = ProposalState("submitted")
  val DELETED = ProposalState("deleted")
  val APPROVED = ProposalState("approved")
  val REJECTED = ProposalState("rejected")
  val ACCEPTED = ProposalState("accepted")
  val DECLINED = ProposalState("declined")
  val BACKUP = ProposalState("backup")
  val ARCHIVED = ProposalState("archived")
  val UNKNOWN = ProposalState("unknown")

  val all = List(
    DRAFT,
    SUBMITTED,
    DELETED,
    APPROVED,
    REJECTED,
    ACCEPTED,
    DECLINED,
    BACKUP,
    ARCHIVED,
    UNKNOWN
  )

  val allButDeletedAndArchived = List(
    DRAFT,
    SUBMITTED,
    APPROVED,
    REJECTED,
    ACCEPTED,
    DECLINED,
    BACKUP
  )

  val allAsCode = all.map(_.code)

  def parse(state: String): ProposalState = {
    state match {
      case "draft" => DRAFT
      case "submitted" => SUBMITTED
      case "deleted" => DELETED
      case "approved" => APPROVED
      case "rejected" => REJECTED
      case "accepted" => ACCEPTED
      case "declined" => DECLINED
      case "backup" => BACKUP
      case "ar" => ARCHIVED
      case other => UNKNOWN
    }
  }
}

import com.github.rjeschke.txtmark._

// A proposal
case class Proposal(id: String,
                    event: String,
                    lang: String,
                    title: String,
                    mainSpeaker: String,
                    secondarySpeaker: Option[String],
                    otherSpeakers: List[String],
                    talkType: ProposalType,
                    audienceLevel: String,
                    summary: String,
                    privateMessage: String,
                    state: ProposalState,
                    sponsorTalk: Boolean = false,
                    track: Track,
                    demoLevel: Option[String],
                    userGroup: Option[Boolean],
                    wishlisted: Option[Boolean] = None,
                    videoLink: Option[String] = None,
                    tags: Option[Seq[Tag]]) {

  def escapedTitle: String = title match {
    case null => ""
    case t => StringUtils.stripAccents(t.replaceAll(" ", "_").trim)
  }

  def allSpeakerUUIDs: List[String] = {
    mainSpeaker :: (secondarySpeaker.toList ++ otherSpeakers)
  }

  def allSpeakers: List[Speaker] = {
    allSpeakerUUIDs.flatMap { uuid =>
      Speaker.findByUUID(uuid)
    }
  }

  def allSpeakersGravatar: List[String] = {
    allSpeakers.flatMap(_.avatarUrl)
  }

  lazy val summaryAsHtml: String = {
    val processedMarkdownTest = Processor.process(StringUtils.trimToEmpty(summary).trim()) // Then do markdown processing
    processedMarkdownTest
  }

  lazy val privateMessageAsHtml: String = {
    val processedMarkdownTest = Processor.process(StringUtils.trimToEmpty(privateMessage).trim()) // Then do markdown processing
    processedMarkdownTest
  }
}

object Proposal {

  implicit val proposalFormat = Json.format[Proposal]

  val langs = Seq(("en", "English"), ("fr", "Français"))

  val audienceLevels = Seq(("novice", "Novice"), ("intermediate", "Intermediate"), ("expert", "Expert"))

  val ProposalIDRegExp = "([A-Z][A-Z][A-Z]-\\d\\d\\d\\d)".r

  val HttpUrl = "((([A-Za-z]{3,9}:(?:\\/\\/)?)(?:[-;:&=\\+\\$,\\w]+@)?[A-Za-z0-9.-]+|(?:www.|[-;:&=\\+\\$,\\w]+@)[A-Za-z0-9.-]+)((?:\\/[\\+~%\\/.\\w-_]*)?\\??(?:[-\\+=&;%@.\\w_]*)#?(?:[\\w]*))?)".r

  def isSpeaker(proposalId: String, uuid: String): Boolean = Redis.pool.withClient {
    implicit client =>
      client.sismember("Proposals:ByAuthor:" + uuid, proposalId)
  }

  def save(authorUUID: String, proposal: Proposal, proposalState: ProposalState): String = Redis.pool.withClient {
    client =>

      // Has to happen here, so we can verify the old proposal tags and update any "relationships"
      changeTags(proposal, proposal.tags)

      // We enforce the user id, for security reason
      val proposalWithMainSpeaker = proposal.copy(mainSpeaker = authorUUID)

      findById(proposal.id).foreach {
        oldProposal => resetVotesIfProposalTypeIsUpdated(proposal.id, proposal.talkType, oldProposal.talkType, proposalState)
      }

      val json = Json.toJson(proposalWithMainSpeaker).toString()

      val proposalId = proposalWithMainSpeaker.id
      // TX
      val tx = client.multi()
      tx.hset("Proposals", proposalId, json)
      tx.sadd("Proposals:ByAuthor:" + authorUUID, proposalId)

      // 2nd speaker
      proposalWithMainSpeaker.secondarySpeaker.map {
        secondarySpeaker =>
          tx.sadd("Proposals:ByAuthor:" + secondarySpeaker, proposalId)
      }

      // other speaker
      proposalWithMainSpeaker.otherSpeakers.map {
        otherSpeaker =>
          tx.sadd("Proposals:ByAuthor:" + otherSpeaker, proposalId)
      }

      tx.exec()

      changeTrack(authorUUID, proposal)

      changeProposalState(authorUUID, proposal.id, proposalState)

      // Reflect any changes such as talkType or speaker to the list of accepted/refused talks.
      ApprovedProposal.reflectProposalChanges(proposal)

      proposalId
  }

  val proposalForm = Form(mapping(
    "id" -> optional(text),
    "lang" -> text,
    "title" -> nonEmptyText(maxLength = 125),
    "secondarySpeaker" -> optional(text),
    "otherSpeakers" -> list(text),
    "talkType" -> nonEmptyText,
    "audienceLevel" -> text,
    // Add 20% characters for Markdown extra characters.
    "summary" -> nonEmptyText(maxLength = 200 + ConferenceDescriptor.current().maxProposalSummaryCharacters),
    "privateMessage" -> nonEmptyText(maxLength = 3500),
    "sponsorTalk" -> boolean,
    "track" -> nonEmptyText,
    "demoLevel" -> optional(text),
    "userGroup" -> optional(boolean),
    "videoLink" -> optional(text),
    "tags" -> optional(seq(
      mapping(
        "id" -> optional(text),
        "value" -> text
      )(Tag.validateNewTag)(Tag.unapplyTagForm)
    ))
  )(validateNewProposal)(unapplyProposalForm))

  def generateId(): String = Redis.pool.withClient {
    implicit client =>
      val newId = RandomStringUtils.randomAlphabetic(3).toUpperCase + "-" + RandomStringUtils.randomNumeric(4)
      if (client.hexists("Proposals", newId)) {
        play.Logger.of("Proposal").warn(s"Proposal ID collision with $newId")
        generateId()
      } else {
        newId
      }
  }

  def validateNewProposal(id: Option[String],
                          lang: String,
                          title: String,
                          secondarySpeaker: Option[String],
                          otherSpeakers: List[String],
                          talkType: String,
                          audienceLevel: String,
                          summary: String,
                          privateMessage: String,
                          sponsorTalk: Boolean,
                          track: String,
                          demoLevel: Option[String],
                          userGroup: Option[Boolean],
                          videoLink: Option[String] = None,
                          tags: Option[Seq[Tag]] = None): Proposal = {
    Proposal(
      id.getOrElse(generateId()),
      ConferenceDescriptor.current().eventCode,
      lang,
      title,
      "no_main_speaker",
      secondarySpeaker,
      otherSpeakers,
      ProposalType.parse(talkType),
      audienceLevel,
      summary,
      privateMessage,
      ProposalState.UNKNOWN,
      sponsorTalk,
      Track.parse(track),
      demoLevel,
      userGroup,
      wishlisted = None, //deprecated, kept for backward compatibility
      videoLink,
      tags
    )
  }

  def isNew(id: String): Boolean = Redis.pool.withClient {
    client =>
      // Important when we create a new proposal
      !client.hexists("Proposals", id)
  }

  def unapplyProposalForm(p: Proposal): Option[(Option[String], String, String, Option[String], List[String], String, String, String, String,
    Boolean, String, Option[String], Option[Boolean], Option[String], Option[Seq[Tag]] )] = {
    Option((
      Option(p.id),
      p.lang,
      p.title,
      p.secondarySpeaker,
      p.otherSpeakers,
      p.talkType.id,
      p.audienceLevel,
      p.summary,
      p.privateMessage,
      p.sponsorTalk,
      p.track.id,
      p.demoLevel,
      p.userGroup,
      p.videoLink,
      p.tags))
  }

  def changeTrack(uuid: String, proposal: Proposal) = Redis.pool.withClient {
    client =>
      val proposalId = proposal.id
      // If we change a proposal to a new track, we need to update all the collections
      // On Redis, this is very fast (faster than creating a mongoDB index, by an order of x100)

      val maybeExistingTrackId = client.hget("Proposals:TrackForProposal", proposalId)

      // Do the operation if and only if we changed the Track
      maybeExistingTrackId.map {
        case oldTrackId if oldTrackId != proposal.track.id =>
          // SMOVE is also a O(1) so it is faster than a SREM and SADD
          client.smove("Proposals:ByTrack:" + oldTrackId, "Proposals:ByTrack:" + proposal.track.id, proposalId)
          client.hset("Proposals:TrackForProposal", proposalId, proposal.track.id)

          // And we are able to track this event
          Event.storeEvent(Event(proposal.id, uuid, s"Changed talk's track  with id $proposalId  from $oldTrackId to ${proposal.track.id}"))
        case oldTrackId if oldTrackId == proposal.track.id =>
        // Same track
      }
      if (maybeExistingTrackId.isEmpty) {
        // SADD is O(N)
        client.sadd("Proposals:ByTrack:" + proposal.track.id, proposalId)
        client.hset("Proposals:TrackForProposal", proposalId, proposal.track.id)

        Event.storeEvent(Event(proposal.id, uuid, s"Posted a new talk ($proposalId) to ${proposal.track.id}"))
      }

  }

  def changeProposalState(uuid: String, proposalId: String, newState: ProposalState) = Redis.pool.withClient {
    client =>
      // Same kind of operation for the proposalState
      val maybeExistingState = for (state <- ProposalState.allAsCode if client.sismember("Proposals:ByState:" + state, proposalId)) yield state

      // Do the operation on the ProposalState
      maybeExistingState.filterNot(_ == newState.code).foreach {
        stateOld: String =>
          // SMOVE is also a O(1) so it is faster than a SREM and SADD
          client.smove("Proposals:ByState:" + stateOld, "Proposals:ByState:" + newState.code, proposalId)
          Event.storeEvent(Event(proposalId, uuid, s"Changed status of talk $proposalId from $stateOld to ${newState.code}"))

          if (newState == ProposalState.SUBMITTED) {
            client.hset("Proposal:SubmittedDate", proposalId, new Instant().getMillis.toString)
          }
      }
      if (maybeExistingState.isEmpty) {
        // SADD is O(N)
        client.sadd("Proposals:ByState:" + newState.code, proposalId)
        Event.storeEvent(Event(proposalId, uuid, s"Posted new talk $proposalId with status ${newState.code}"))
      }
  }

  def changeTags(proposal: Proposal, newTags: Option[Seq[Tag]]) = Redis.pool.withClient {
    implicit client =>
      if (newTags.isDefined) {

        // Existing proposal?
        val oldProposal = Proposal.findById(proposal.id)
        if (oldProposal.isDefined) {

          // Sync Tags:{tagId} Set for tags that have been removed
          val oldTags = oldProposal.get.tags

          play.Logger.of("models.Proposal").info("oldTags:" + oldTags.toList.mkString(", "))
          play.Logger.of("models.Proposal").info("newTags:" + newTags.toList.mkString(", "))

          if (oldTags.isDefined) {
            val diff = oldTags.get.diff(newTags.get)

            play.Logger.of("models.Proposal").info("diff:" + diff.toList.mkString(", "))

            if (diff.nonEmpty) {
              diff.map(oldTag => {
                client.srem("Tags:" + oldTag.id, proposal.id)
              })
            }
          }
        }

        // Add proposal id for new tags
        newTags.get.foreach( tag => {

          play.Logger.of("models.Proposal").info("tag:" + tag.value)
          if (tag.value.nonEmpty) {
            // Only allow tags that exist
            if (Tag.doesTagValueExist(tag.value)) {
              play.Logger.of("models.Proposal").info("tag " + tag.value + " exists")

              client.sadd("Tags:" + tag.id, proposal.id)
            } else {
              play.Logger.of("models.Proposal").info("tag " + tag.value + " DOES NOT exist")
            }
          } else {
            play.Logger.of("models.Proposal").info("tag is empty")
          }
        } )
      }
  }

  def getSubmissionDate(proposalId: String): Option[Long] = Redis.pool.withClient {
    implicit client =>
      client.hget("Proposal:SubmittedDate", proposalId).map {
        date: String =>
          date.toLong
      }
  }

  def delete(uuid: String, proposalId: String) {
    Event.storeEvent(Event(proposalId, uuid, s"Deleted proposal $proposalId"))
    Proposal.findById(proposalId).map {
      proposal =>
        ApprovedProposal.cancelApprove(proposal)
        ApprovedProposal.cancelRefuse(proposal)
    }

    // TODO delete votes for a Proposal if a speaker decided to cancel this talk
    changeProposalState(uuid, proposalId, ProposalState.DELETED)

    // Removed proposal from Digest Queue
    Digest.deleteProposal(proposalId)
  }

  def submit(uuid: String, proposalId: String) = {
    changeProposalState(uuid, proposalId, ProposalState.SUBMITTED)
    Digest.addProposal(proposalId)
  }

  def approve(uuid: String, proposalId: String) = {
    changeProposalState(uuid, proposalId, ProposalState.APPROVED)
  }

  def reject(uuid: String, proposalId: String) = {
    changeProposalState(uuid, proposalId, ProposalState.REJECTED)
  }

  def accept(uuid: String, proposalId: String) = {
    changeProposalState(uuid, proposalId, ProposalState.ACCEPTED)
  }

  def decline(uuid: String, proposalId: String) = {
    changeProposalState(uuid, proposalId, ProposalState.DECLINED)
  }

  def backup(uuid: String, proposalId: String) = {
    changeProposalState(uuid, proposalId, ProposalState.BACKUP)
  }

  def draft(uuid: String, proposalId: String) = {
    changeProposalState(uuid, proposalId, ProposalState.DRAFT)

    // Removed proposal if present in Digest Queue
    Digest.deleteProposal(proposalId)
  }

  def archive(uuid: String, proposalId: String) = {
    changeProposalState(uuid, proposalId, ProposalState.ARCHIVED)
  }

  private def loadProposalsByState(uuid: String, proposalState: ProposalState): List[Proposal] = Redis.pool.withClient {
    implicit client =>
      val allProposalIds: Set[String] = client.sinter(s"Proposals:ByAuthor:$uuid", s"Proposals:ByState:${proposalState.code}")
      loadProposalByIDs(allProposalIds, proposalState)
  }

  // Special function that has to be executed with an implicit client
  def loadProposalByIDs(allProposalIds: Set[String], proposalState: ProposalState)(implicit client: Dress.Wrap): List[Proposal] = {
    client.hmget("Proposals", allProposalIds).flatMap {
      proposalJson: String =>
        Json.parse(proposalJson).asOpt[Proposal].map(_.copy(state = proposalState))
    }
  }

  def allMyDraftProposals(uuid: String): List[Proposal] = {
    loadProposalsByState(uuid, ProposalState.DRAFT).sortBy(_.title)
  }

  def allMyDeletedProposals(uuid: String): List[Proposal] = {
    loadProposalsByState(uuid, ProposalState.DELETED).sortBy(_.title)
  }

  def allMySubmittedProposals(uuid: String): List[Proposal] = {
    loadProposalsByState(uuid, ProposalState.SUBMITTED).sortBy(_.title)
  }

  def allMyDraftAndSubmittedProposals(uuid: String): List[Proposal] = {
    val allDrafts = allMyDraftProposals(uuid)
    val allSubmitted = allMySubmittedProposals(uuid)
    (allDrafts ++ allSubmitted).sortBy(_.title)
  }

  def allMyProposals(uuid: String): List[Proposal] = {
    ProposalState.allButDeletedAndArchived.flatMap {
      proposalState =>
        loadProposalsByState(uuid, proposalState)
    }
  }

  def allMyArchivedProposals(uuid: String): List[Proposal] = {
    loadProposalsByState(uuid, ProposalState.ARCHIVED)
  }

  def countByProposalState(uuid: String, proposalState: ProposalState): Int = Redis.pool.withClient {
    implicit client =>
      val allProposalIds: Set[String] = client.sinter(s"Proposals:ByAuthor:$uuid", s"Proposals:ByState:${proposalState.code}")
      allProposalIds.size
  }

  def findProposal(uuid: String, proposalId: String): Option[Proposal] = {
    allMyProposals(uuid).find(_.id == proposalId)
  }

  def findDraft(uuid: String, proposalId: String): Option[Proposal] = {
    allMyDraftProposals(uuid).find(_.id == proposalId)
  }

  def findSubmitted(uuid: String, proposalId: String): Option[Proposal] = {
    allMySubmittedProposals(uuid).find(_.id == proposalId)
  }

  def findDeleted(uuid: String, proposalId: String): Option[Proposal] = {
    allMyDeletedProposals(uuid).find(_.id == proposalId)
  }

  val proposalSpeakerForm = Form(tuple(
    "secondarySpeaker" -> optional(text),
    "otherSpeakers" -> list(text)
  ))

  def findById(proposalId: String): Option[Proposal] = Redis.pool.withClient {
    client =>
      for (proposalJson <- client.hget("Proposals", proposalId);
           proposal <- Json.parse(proposalJson).asOpt[Proposal];
           realState <- findProposalState(proposal.id)) yield {
        proposal.copy(state = realState)
      }
  }

  def findProposalState(proposalId: String): Option[ProposalState] = Redis.pool.withClient {
    client =>
      // I use a for-comprehension to check each of the Set (O(1) operation)
      // when I have found what is the current state, then I stop and I return a Left that here, indicates a success
      // Note that the common behavioir for an Either is to indicate failure as a Left and Success as a Right,
      // Here I do the opposite for performance reasons. NMA.
      // This code retrieves the proposalState in less than 20-30ms.
      val thisProposalState = for (
        isNotSubmitted <- checkIsNotMember(client, ProposalState.SUBMITTED, proposalId).toRight(ProposalState.SUBMITTED).right;
        isNotDraft <- checkIsNotMember(client, ProposalState.DRAFT, proposalId).toRight(ProposalState.DRAFT).right;
        isNotApproved <- checkIsNotMember(client, ProposalState.APPROVED, proposalId).toRight(ProposalState.APPROVED).right;
        isNotAccepted <- checkIsNotMember(client, ProposalState.ACCEPTED, proposalId).toRight(ProposalState.ACCEPTED).right;
        isNotDeleted <- checkIsNotMember(client, ProposalState.DELETED, proposalId).toRight(ProposalState.DELETED).right;
        isNotDeclined <- checkIsNotMember(client, ProposalState.DECLINED, proposalId).toRight(ProposalState.DECLINED).right;
        isNotRejected <- checkIsNotMember(client, ProposalState.REJECTED, proposalId).toRight(ProposalState.REJECTED).right;
        isNotBackup <- checkIsNotMember(client, ProposalState.BACKUP, proposalId).toRight(ProposalState.BACKUP).right;
        isNotArchived <- checkIsNotMember(client, ProposalState.ARCHIVED, proposalId).toRight(ProposalState.ARCHIVED).right
      ) yield ProposalState.UNKNOWN // If we reach this code, we could not find what was the proposal state

      thisProposalState.fold(foundProposalState => Some(foundProposalState), notFound => {
        play.Logger.warn(s"Could not find proposal state for $proposalId")
        None
      })
  }

  private def checkIsNotMember(client: Dress.Wrap, state: ProposalState, proposalId: String): Option[Boolean] = {
    client.sismember("Proposals:ByState:" + state.code, proposalId) match {
      case java.lang.Boolean.FALSE => Option(true)
      case other => None
    }
  }

  def allProposalIDs: Set[String] = Redis.pool.withClient {
    implicit client =>
      client.hkeys("Proposals")
  }

  def allProposalIDsNotDeleted: Set[String] = Redis.pool.withClient {
    implicit client =>
      val allProposalIDs = client.hkeys("Proposals")
      val allProposalIDDeleted = client.smembers(s"Proposals:ByState:${ProposalState.DELETED.code}")
      val allProposalIDArchived = client.smembers(s"Proposals:ByState:${ProposalState.ARCHIVED.code}")
      val onlyValidProposalIDs = allProposalIDs.diff(allProposalIDArchived).diff(allProposalIDDeleted)
      onlyValidProposalIDs
  }

  def allProposalIDsNotArchived: Set[String] = Redis.pool.withClient {
    implicit client =>
      val allProposalIDs = client.hkeys("Proposals")
      val allProposalIDArchived = client.smembers(s"Proposals:ByState:${ProposalState.ARCHIVED.code}")
      val onlyValidProposalIDs = allProposalIDs.diff(allProposalIDArchived)
      onlyValidProposalIDs
  }

  def allProposalIDsDeleted: Set[String] = Redis.pool.withClient {
    implicit client =>
      val allProposalIDDeleted = client.smembers(s"Proposals:ByState:${ProposalState.DELETED.code}")
      allProposalIDDeleted
  }

  def allProposalIDsSubmitted: Set[String] = Redis.pool.withClient {
    implicit client =>
      val allProposalIDsSubmitted = client.smembers(s"Proposals:ByState:${ProposalState.SUBMITTED.code}")
      allProposalIDsSubmitted
  }

  def countAll() = {
    allProposalIDsNotDeleted.size
  }

  def allDrafts(): List[Proposal] = Redis.pool.withClient {
    implicit client =>
      val allProposalIds = client.smembers("Proposals:ByState:" + ProposalState.DRAFT.code)
      client.hmget("Proposals", allProposalIds).flatMap {
        proposalJson: String =>
          Json.parse(proposalJson).asOpt[Proposal].map(_.copy(state = ProposalState.DRAFT))
      }
  }

  def allProposalIDsDeletedArchivedOrDraft(): Set[String] = Redis.pool.withClient {
    implicit client =>
      val drafts = client.smembers("Proposals:ByState:" + ProposalState.DRAFT.code)
      val archived = client.smembers("Proposals:ByState:" + ProposalState.ARCHIVED.code)
      val deleted = client.smembers("Proposals:ByState:" + ProposalState.DELETED.code)
      drafts ++ archived ++ deleted
  }

  def allArchivedIDs(): Set[String] = Redis.pool.withClient {
    implicit client =>
      client.smembers("Proposals:ByState:" + ProposalState.ARCHIVED.code)
  }

  def allDeletedIDs(): Set[String] = Redis.pool.withClient {
    implicit client =>
      client.smembers("Proposals:ByState:" + ProposalState.DELETED.code)
  }


  def allSubmitted(): List[Proposal] = Redis.pool.withClient {
    implicit client =>
      val allProposalIds = client.smembers("Proposals:ByState:" + ProposalState.SUBMITTED.code)
      client.hmget("Proposals", allProposalIds).flatMap {
        proposalJson: String =>
          Json.parse(proposalJson).asOpt[Proposal].map(_.copy(state = ProposalState.SUBMITTED))
      }
  }

  def allAccepted(): List[Proposal] = Redis.pool.withClient {
    implicit client =>
      val allProposalIds = client.smembers("Proposals:ByState:" + ProposalState.ACCEPTED.code)
      client.hmget("Proposals", allProposalIds).flatMap {
        proposalJson: String =>
          Json.parse(proposalJson).asOpt[Proposal].map(_.copy(state = ProposalState.ACCEPTED))
      }
  }

  def allApproved(): List[Proposal] = Redis.pool.withClient {
    implicit client =>
      val allProposalIds = client.smembers("Proposals:ByState:" + ProposalState.APPROVED.code)

      client.hmget("Proposals", allProposalIds).flatMap {
        proposalJson: String =>
          Json.parse(proposalJson).asOpt[Proposal].map(_.copy(state = ProposalState.APPROVED))
      }
  }

  def allProposalsByAuthor(author: String): Map[String, Proposal] = Redis.pool.withClient {
    implicit client =>
      val allProposalIDs = client.smembers(s"Proposals:ByAuthor:$author")
      loadAndParseProposals(allProposalIDs)
  }

  def allApprovedProposalsByAuthor(author: String): Map[String, Proposal] = Redis.pool.withClient {
    implicit client =>
      val allProposalIDs = client.sinter(s"Proposals:ByAuthor:$author", "ApprovedById:")
      loadAndParseProposals(allProposalIDs)
  }

  def allApprovedAndAcceptedProposalsByAuthor(author: String): Map[String, Proposal] = Redis.pool.withClient {
    implicit client =>
      val allApproved = client.sinter(s"Proposals:ByAuthor:$author", "ApprovedById:")
      loadAndParseProposals(allApproved)
  }

  def allThatForgetToAccept(author: String): Map[String, Proposal] = Redis.pool.withClient {
    implicit client =>
      val allApproved = client.sinter(s"Proposals:ByAuthor:$author", "ApprovedById:")
      val onlyAcceptedNotApproved = client.sdiff("Proposals:ByState:" + ProposalState.ACCEPTED.code, "Proposals:ByState:" + ProposalState.APPROVED.code)
      val approvedAndNotAccepted = allApproved.diff(onlyAcceptedNotApproved).diff(client.smembers("Proposals:ByState:" + ProposalState.DECLINED.code))
      loadAndParseProposals(approvedAndNotAccepted)
  }

  def allDeleted(): List[Proposal] = Redis.pool.withClient {
    implicit client =>
      val allProposalIds = client.smembers("Proposals:ByState:" + ProposalState.DELETED.code)
      client.hmget("Proposals", allProposalIds).flatMap {
        proposalJson: String =>
          Json.parse(proposalJson).asOpt[Proposal].map(_.copy(state = ProposalState.DELETED))
      }
  }

  def destroy(proposal: Proposal) = Redis.pool.withClient {
    implicit client =>
      val tx = client.multi()
      tx.srem(s"Proposals:ByAuthor:${proposal.mainSpeaker}", proposal.id)
      tx.srem(s"Proposals:ByState:${proposal.state.code}", proposal.id)
      tx.srem(s"Proposals:ByTrack:${proposal.track.id}", proposal.id)
      tx.hdel("Proposals:TrackForProposal", proposal.id)
      // 2nd speaker
      proposal.secondarySpeaker.map {
        secondarySpeaker =>
          tx.srem("Proposals:ByAuthor:" + secondarySpeaker, proposal.id)
      }
      // other speaker
      proposal.otherSpeakers.map {
        otherSpeaker =>
          tx.srem("Proposals:ByAuthor:" + otherSpeaker, proposal.id)
      }
      tx.hdel("Proposal:SubmittedDate", proposal.id)
      tx.hdel("Proposals", proposal.id)
      if (proposal.id != "") {
        tx.del(s"Events:V2:${proposal.id}")
        tx.del(s"Events:LastUpdated:${proposal.id}")
      }
      tx.exec()
  }

  def findProposalTrack(proposalId: String): Option[Track] = Redis.pool.withClient {
    client =>
      client.hget("Proposals:TrackForProposal", proposalId).flatMap {
        trackId =>
          ConferenceDescriptor.ConferenceTracks.ALL.find(_.id == trackId)
      }
  }

  // How many talks submitted for Java? for Web?
  def totalSubmittedByTrack(): List[(Track, Int)] = Redis.pool.withClient {
    implicit client =>
      val toRetn = for (proposalId <- client.smembers("Proposals:ByState:" + ProposalState.SUBMITTED.code).toList;
                        track <- Proposal.findProposalTrack(proposalId)
      ) yield (track, 1)

      toRetn.groupBy(_._1).map {
        case (category, listOfCategoryAndTotal) =>
          (category, listOfCategoryAndTotal.map(_._2).sum)
      }.toList
  }

  // How many Conference, University, BOF...
  def totalSubmittedByType(): Map[ProposalType, Int] = {
    allSubmitted().groupBy(_.talkType).map {
      case (pt: ProposalType, listOfProposals: List[Proposal]) =>
        (pt, listOfProposals.size)
    }
  }

  def totalAcceptedByTrack(): List[(Track, Int)] = Redis.pool.withClient {
    implicit client =>
      val toRetn = for (proposalId <- client.smembers("Proposals:ByState:" + ProposalState.ACCEPTED.code).toList;
                        track <- Proposal.findProposalTrack(proposalId)
      ) yield (track, 1)

      toRetn.groupBy(_._1).map {
        case (category, listOfCategoryAndTotal) =>
          (category, listOfCategoryAndTotal.map(_._2).sum)
      }.toList
  }

  // How many Conference, University, BOF...
  def totalAcceptedByType(): Map[ProposalType, Int] = {
    allAccepted().groupBy(_.talkType).map {
      case (pt: ProposalType, listOfProposals: List[Proposal]) =>
        (pt, listOfProposals.size)
    }
  }

  // Move a speaker that was 2nd speaker or "otherSpeaker" to mainSpeaker
  // This is required as any edit operation will automatically set the Proposal's owner to the
  // current authenticated user
  def setMainSpeaker(proposal: Proposal, uuid: String): Proposal = {
    if (proposal.mainSpeaker != uuid) {
      proposal.secondarySpeaker match {
        case Some(u) if u == uuid => proposal.copy(mainSpeaker = uuid, secondarySpeaker = Option(proposal.mainSpeaker))
        case _ =>
          // move the main speaker to "other speaker"
          proposal.copy(mainSpeaker = uuid, otherSpeakers = proposal.mainSpeaker :: proposal.otherSpeakers.filterNot(_ == uuid))
      }
    } else {
      proposal
    }
  }

  /**
    * Returns all Proposals with sponsorTalk=true, except if talk has been deleted, declined or archived
    */
  def allSponsorsTalk(): List[Proposal] = {
    val allTalks = allProposals().filter(_.sponsorTalk)
    allTalks.map {
      proposal =>
        val proposalState = findProposalState(proposal.id)
        proposal.copy(state = proposalState.getOrElse(ProposalState.UNKNOWN))
    }.filterNot(s => s.state == ProposalState.DELETED || s.state == ProposalState.DECLINED || s.state == ProposalState.ARCHIVED)
  }

  /**
    * Load all proposals except ARCHIVED
    */
  def allProposals(): List[Proposal] = Redis.pool.withClient {
    implicit client =>

      val allProposalIDsExceptArchived = client.hkeys("Proposals").diff(client.smembers(s"Proposals:ByState:${ProposalState.ARCHIVED.code}"))

      client.hmget("Proposals", allProposalIDsExceptArchived).map {
        json =>
          val proposal = Json.parse(json).as[Proposal]
          val proposalState = findProposalState(proposal.id)
          proposal.copy(state = proposalState.getOrElse(ProposalState.UNKNOWN))
      }
  }

  def allDeclinedProposals(): List[Proposal] = Redis.pool.withClient {
    implicit client =>

      val allDeclineds = client.smembers(s"Proposals:ByState:${ProposalState.DECLINED.code}")

      client.hmget("Proposals", allDeclineds).map {
        json =>
          val proposal = Json.parse(json).as[Proposal]
          proposal.copy(state = ProposalState.DECLINED)
      }
  }


  // This code is a bit complex. It's an optimized version that loads from Redis
  // a set of Proposal. It returns only valid proposal, successfully loaded.
  def loadAndParseProposals(proposalIDs: Set[String]): Map[String, Proposal] = Redis.pool.withClient {
    implicit client =>
      val listOfProposals = proposalIDs.toList

      // Updated code to use validate so that it throw an exception if the JSON parser could not load the Proposal
      val proposals = client.hmget("Proposals", listOfProposals).map {
        json: String =>
          val p = Json.parse(json).validate[Proposal].get
          (p.id, p.copy(state = findProposalState(p.id).getOrElse(p.state)))
      }
      proposals.toMap
  }

  def loadAndParseProposals(proposalIDs: Set[String], confType: ProposalType): Map[String, Proposal] = Redis.pool.withClient {
    implicit client =>
      val listOfProposals = proposalIDs.toList

      // Updated code to use validate so that it throw an exception if the JSON parser could not load the Proposal
      val proposals = client.hmget("Proposals", listOfProposals).map {
        json: String =>
          val p = Json.parse(json).validate[Proposal].get
          (p.id, p.copy(state = findProposalState(p.id).getOrElse(p.state)))
      }.filter(_._2.talkType.id == confType.id) // TODO I should create a separate collection for ProposalType and filter the Set proposalIds with this collection.
      proposals.toMap
  }


  def removeSponsorTalkFlag(authorUUID: String, proposalId: String) = {
    Proposal.findById(proposalId).filter(_.sponsorTalk == true).map {
      proposal =>
        Event.storeEvent(Event(proposal.id, authorUUID, "Removed [sponsorTalkFlag] on proposal " + proposal.title))
        Proposal.save(proposal.mainSpeaker, proposal.copy(sponsorTalk = false), proposal.state)
    }
  }

  def hasOneProposal(uuid: String): Boolean = Redis.pool.withClient {
    implicit client =>
      client.exists(s"Proposals:ByAuthor:$uuid")
  }

  def allApprovedForSpeaker(author: String): List[Proposal] = Redis.pool.withClient {
    implicit client =>
      loadProposalsByState(author, ProposalState.APPROVED)
  }

  def allAcceptedForSpeaker(author: String): List[Proposal] = Redis.pool.withClient {
    implicit client =>
      loadProposalsByState(author, ProposalState.ACCEPTED)
  }

  def allRejectedForSpeaker(author: String): List[Proposal] = Redis.pool.withClient {
    implicit client =>
      loadProposalsByState(author, ProposalState.REJECTED)
  }

  def allBackupForSpeaker(author: String): List[Proposal] = Redis.pool.withClient {
    implicit client =>
      loadProposalsByState(author, ProposalState.BACKUP)
  }

  def allAcceptedByTalkType(talkType: String): List[Proposal] = Redis.pool.withClient {
    implicit client =>
      val allProposalIds: Set[String] = client.smembers(s"Proposals:ByState:${ProposalState.ACCEPTED.code}")
      loadProposalByIDs(allProposalIds, ProposalState.ACCEPTED).filter(_.talkType.id == talkType)
  }

  def allAcceptedByTalkType(talkTypes: List[String]): List[Proposal] = Redis.pool.withClient {
    implicit client =>
      val allProposalIds: Set[String] = client.smembers(s"Proposals:ByState:${ProposalState.ACCEPTED.code}")
      loadProposalByIDs(allProposalIds, ProposalState.ACCEPTED).filter(p => talkTypes.contains(p.talkType.id))
  }

  def hasOneAcceptedProposal(speakerUUID: String): Boolean = Redis.pool.withClient {
    implicit client =>
      client.sunion(s"Proposals:ByAuthor:$speakerUUID", s"Proposals:ByState:${ProposalState.ACCEPTED.code}").nonEmpty
  }

  def hasOneRejectedProposal(speakerUUID: String): Boolean = Redis.pool.withClient {
    implicit client =>
      client.sunion(s"Proposals:ByAuthor:$speakerUUID", s"Proposals:ByState:${ProposalState.REJECTED.code}").nonEmpty
  }

  def hasOnlyRejectedProposals(speakerUUID: String): Boolean = Redis.pool.withClient {
    implicit client =>
      val allProposalIDs = client.smembers(s"Proposals:ByAuthor:$speakerUUID")
      val proposals = loadAndParseProposals(allProposalIDs).values.toSet
      !proposals.exists(proposal => proposal.state == ProposalState.APPROVED ||
        proposal.state == ProposalState.ACCEPTED) &&
        proposals.exists(proposal => proposal.state == ProposalState.REJECTED)
  }

  def setPreferredDay(proposalId: String, day: String) = Redis.pool.withClient {
    implicit client => client.hset("PreferredDay", proposalId, day)
  }

  def resetPreferredDay(proposalId: String) = Redis.pool.withClient {
    implicit client => client.hdel("PreferredDay", proposalId)
  }

  def hasPreferredDay(proposalId: String): Boolean = Redis.pool.withClient {
    implicit client => client.hexists("PreferredDay", proposalId)
  }

  def getPreferredDay(proposalId: String): Option[String] = Redis.pool.withClient {
    implicit client => client.hget("PreferredDay", proposalId)
  }

  def updateSecondarySpeaker(author: String, proposalId: String, oldSpeakerId: Option[String], newSpeakerId: Option[String]) = Redis.pool.withClient {
    implicit client =>
      val tx = client.multi()
      oldSpeakerId.map {
        speakerId =>
          tx.srem(s"Proposals:ByAuthor:$speakerId", proposalId)
      }
      newSpeakerId.map {
        speakerId =>
          tx.sadd(s"Proposals:ByAuthor:$speakerId", proposalId)
      }
      tx.exec()

      // load and update proposal
      findById(proposalId).map {
        proposal =>
          val updated = proposal.copy(secondarySpeaker = newSpeakerId)
          save(author, updated, updated.state)
      }
  }

  def updateOtherSpeakers(updatedBy: String,
                          proposalId: String,
                          oldOtherSpeakers: List[String],
                          newOtherSpeakers: List[String]) = Redis.pool.withClient {
    implicit client =>
      val tx = client.multi()
      oldOtherSpeakers.map {
        speakerId =>
          tx.srem(s"Proposals:ByAuthor:$speakerId", proposalId)
      }
      newOtherSpeakers.map {
        speakerId =>
          tx.sadd(s"Proposals:ByAuthor:$speakerId", proposalId)
      }
      tx.exec()

      // load and update proposal
      findById(proposalId).map {
        proposal =>
          val updated = proposal.copy(otherSpeakers = newOtherSpeakers)
          save(updatedBy, updated, updated.state)
      }
  }

  private def resetVotesIfProposalTypeIsUpdated(proposalId: String, talkType: ProposalType, oldTalkType: ProposalType, state: ProposalState) {
    if (oldTalkType.id != talkType.id) {
      if (state == ProposalState.DRAFT) {
        if (!ApprovedProposal.isApproved(proposalId, talkType.id)) {
          Review.archiveAllVotesOnProposal(proposalId)
          Comment.saveInternalComment(proposalId, Webuser.Internal.uuid, s"All votes deleted for this talk, because it was changed from [${Messages(oldTalkType.id)}] to [${Messages(talkType.id)}]")
        }
      }
    }
  }
}

package models

import play.api.libs.json.Json
import library.{Benchmark, Dress, Redis}
import org.apache.commons.lang3.{StringUtils, RandomStringUtils}

import play.api.data._
import play.api.data.Forms._
import play.api.templates.HtmlFormat

/**
 * Proposal
 *
 * Author: nicolas
 * Created: 12/10/2013 15:19
 */
case class ProposalType(id: String, label: String)

object ProposalType {
  implicit val proposalTypeFormat = Json.format[ProposalType]

  val CONF = ProposalType("conf", "conf.label")
  val UNI = ProposalType("uni", "uni.label")
  val TIA = ProposalType("tia", "tia.label")
  val LAB = ProposalType("lab", "lab.label")
  val QUICK = ProposalType("quick", "quick.label")
  val BOF = ProposalType("bof", "bof.label")
  val AMD = ProposalType("amd", "amd.label")
  val KEY = ProposalType("key", "key.label")
  val OTHER = ProposalType("other", "other.label")

  val all = List(CONF, UNI, TIA, LAB, QUICK, BOF)

  val allAsId = all.map(a => (a.id, a.label)).toSeq.sorted

  def parse(proposalType: String): ProposalType = {
    proposalType match {
      case "conf" => CONF
      case "uni" => UNI
      case "tia" => TIA
      case "lab" => LAB
      case "quick" => QUICK
      case "bof" => BOF
      case "amd" => AMD
      case "key" => KEY
      case other => OTHER
    }
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
    UNKNOWN
  )

  val allAsCode = all.map(_.code)
}


import com.github.rjeschke.txtmark._

// A proposal
case class Proposal(id: String, event: String, lang: String, title: String,
                    mainSpeaker: String, secondarySpeaker: Option[String], otherSpeakers: List[String],
                    talkType: ProposalType, audienceLevel: String, summary: String,
                    privateMessage: String, state: ProposalState, sponsorTalk: Boolean = false, track: Track){

  val allSpeakerUUIDs:List[String]={
    mainSpeaker :: (secondarySpeaker.toList ++ otherSpeakers)
  }

  val summaryAsHtml:String={
    val escapedHtml = HtmlFormat.escape(summary).body // escape HTML code and JS
    val processedMarkdownTest = Processor.process(StringUtils.trimToEmpty(escapedHtml).trim()) // Then do markdown processing
    processedMarkdownTest
  }

  val privateMessageAsHtml:String={
    val escapedHtml = HtmlFormat.escape(privateMessage).body // escape HTML code and JS
    val processedMarkdownTest = Processor.process(StringUtils.trimToEmpty(escapedHtml).trim()) // Then do markdown processing
    processedMarkdownTest
  }
}

object Proposal {

  implicit val proposalFormat = Json.format[Proposal]

  val langs = Seq(("en", "English"), ("fr", "Français"))

  val audienceLevels = Seq(("novice", "Novice"), ("intermediate", "Intermediate"), ("expert", "Expert"))

  val ProposalIDRegExp="([A-Z][A-Z][A-Z]-\\d\\d\\d)".r

  val HttpUrl="((([A-Za-z]{3,9}:(?:\\/\\/)?)(?:[-;:&=\\+\\$,\\w]+@)?[A-Za-z0-9.-]+|(?:www.|[-;:&=\\+\\$,\\w]+@)[A-Za-z0-9.-]+)((?:\\/[\\+~%\\/.\\w-_]*)?\\??(?:[-\\+=&;%@.\\w_]*)#?(?:[\\w]*))?)".r

  def save(authorUUID: String, proposal: Proposal, proposalState: ProposalState) = Redis.pool.withClient {
    client =>
    // If it's a sponsor talk, we force it to be a conference
    // We also enforce the user id, for security reason
      val proposalWithMainSpeaker = if (proposal.sponsorTalk) {
        proposal.copy(talkType = ProposalType.CONF, mainSpeaker = authorUUID)
      } else {
        proposal.copy(mainSpeaker = authorUUID)
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

      Event.storeEvent(Event(proposal.id, authorUUID, "Updated or created proposal " + proposal.id + " with title " + StringUtils.abbreviate(proposal.title, 80)))

      changeTrack(authorUUID, proposal)

      changeProposalState(authorUUID, proposal.id, proposalState)
  }

  val proposalForm = Form(mapping(
    "id" -> optional(text),
    "lang" -> text,
    "title" -> nonEmptyText(maxLength = 125),
    "secondarySpeaker" -> optional(text),
    "otherSpeakers" -> list(text),
    "talkType" -> nonEmptyText,
    "audienceLevel" -> text,
    "summary" -> nonEmptyText(maxLength = 900),
    "privateMessage" -> nonEmptyText(maxLength = 3500),
    "sponsorTalk" -> boolean,
    "track" -> nonEmptyText
  )(validateNewProposal)(unapplyProposalForm))

  def generateId(): String = {
    RandomStringUtils.randomAlphabetic(3).toUpperCase + "-" + RandomStringUtils.randomNumeric(3)
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
                          track: String): Proposal = {
    Proposal(
      id.getOrElse(generateId()),
      "Devoxx France 2014",
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
      Track.parse(track)
    )

  }

  def isNew(id: String): Boolean = Redis.pool.withClient {
    client =>
    // Important when we create a new proposal
      client.hexists("Proposals", id) == false
  }

  def unapplyProposalForm(p: Proposal): Option[(Option[String], String, String, Option[String], List[String], String, String, String, String,
    Boolean, String)] = {
    Option((Option(p.id), p.lang, p.title, p.secondarySpeaker, p.otherSpeakers, p.talkType.id, p.audienceLevel, p.summary, p.privateMessage,
      p.sponsorTalk, p.track.id))
  }

  def changeTrack(uuid: String, proposal: Proposal) = Redis.pool.withClient {
    client =>
      val proposalId = proposal.id
      // If we change a proposal to a new track, we need to update all the collections
      // On Redis, this is very fast (faster than creating a mongoDB index, by an order of x100)

      val maybeExistingTrackId = client.hget("Proposals:TrackFor",proposalId)

      // Do the operation if and only if we changed the Track
      maybeExistingTrackId.map {
        oldTrackId: String =>
        // SMOVE is also a O(1) so it is faster than a SREM and SADD
          client.smove("Proposals:ByTrack:" + oldTrackId, "Proposals:ByTrack:" + proposal.track.id, proposalId)
          client.hset("Proposals:TrackForProposal", proposalId, proposal.track.id)

          // And we are able to track this event
          Event.storeEvent(Event(proposal.id, uuid, s"Changed talk's track  with id $proposalId  from $oldTrackId to ${proposal.track.id}"))
      }
      if (maybeExistingTrackId.isEmpty) {
        // SADD is O(N)
        client.sadd("Proposals:ByTrack:" + proposal.track.id, proposalId)
        client.hset("Proposals:TrackForProposal", proposalId, proposal.track.id)

        Event.storeEvent(Event(proposal.id, uuid, s"Posted a new talk ($proposalId) to ${proposal.track.id}"))
      }

  }

  private def changeProposalState(uuid: String, proposalId: String, newState: ProposalState) = Redis.pool.withClient {
    client =>
    // Same kind of operation for the proposalState
      val maybeExistingState = for (state <- ProposalState.allAsCode if client.sismember("Proposals:ByState:" + state, proposalId)) yield state

      // Do the operation on the ProposalState
      maybeExistingState.filterNot(_ == newState.code).foreach {
        stateOld: String =>
        // SMOVE is also a O(1) so it is faster than a SREM and SADD
          client.smove("Proposals:ByState:" + stateOld, "Proposals:ByState:" + newState.code, proposalId)
          Event.storeEvent(Event(proposalId, uuid, s"Changed status of talk ${proposalId} from ${stateOld} to ${newState.code}"))

      }
      if (maybeExistingState.isEmpty) {
        // SADD is O(N)
        client.sadd("Proposals:ByState:" + newState.code, proposalId)
        Event.storeEvent(Event(proposalId, uuid, s"Posted new talk ${proposalId} with status ${newState.code}"))
      }
  }

  def delete(uuid: String, proposalId: String) {
    changeProposalState(uuid, proposalId, ProposalState.DELETED)
  }

  def submit(uuid: String, proposalId: String) = {
    changeProposalState(uuid, proposalId, ProposalState.SUBMITTED)
  }

  def draft(uuid: String, proposalId: String) = {
    changeProposalState(uuid, proposalId, ProposalState.DRAFT)
  }

  private def loadProposalsByState(uuid: String, proposalState: ProposalState): List[Proposal] = Redis.pool.withClient {
    implicit client =>
      val allProposalIds: Set[String] = client.sinter(s"Proposals:ByState:${proposalState.code}", s"Proposals:ByAuthor:${uuid}")
      loadProposalByIDs(allProposalIds, proposalState)
  }

  // Special function that has to be executed with an implicit client
  def loadProposalByIDs(allProposalIds: Set[String], proposalState: ProposalState)(implicit client: Dress.Wrap): List[Proposal] = {
    client.hmget("Proposals", allProposalIds).flatMap {
      proposalJson: String =>
        Json.parse(proposalJson).asOpt[Proposal].map(_.copy(state = proposalState))
    }.sortBy(_.title)
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

  def findDraftAndSubmitted(uuid: String, proposalId: String): Option[Proposal] = {
    allMyDraftAndSubmittedProposals(uuid).find(_.id == proposalId)
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


  def givesSpeakerFreeEntrance(proposalType: ProposalType): Boolean = {
    proposalType match {
      case ProposalType.CONF => true
      case ProposalType.KEY => true
      case ProposalType.LAB => true
      case ProposalType.UNI => true
      case ProposalType.TIA => true
      case other => false
    }
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
        isNotDeleted <- checkIsNotMember(client, ProposalState.DELETED, proposalId).toRight(ProposalState.DELETED).right;
        isNotDeclined <- checkIsNotMember(client, ProposalState.DECLINED, proposalId).toRight(ProposalState.DECLINED).right;
        isNotRejected <- checkIsNotMember(client, ProposalState.REJECTED, proposalId).toRight(ProposalState.REJECTED).right;
        isNotAccepted <- checkIsNotMember(client, ProposalState.ACCEPTED, proposalId).toRight(ProposalState.ACCEPTED).right;
        isNotBackup <- checkIsNotMember(client, ProposalState.BACKUP, proposalId).toRight(ProposalState.BACKUP).right
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

  def allProposalIDs:Set[String]=Redis.pool.withClient {
    implicit client =>
      client.hkeys("Proposals")
  }

  def allProposalIDsNotDeleted:Set[String]=Redis.pool.withClient {
    implicit client =>
      val allProposalIDs = client.hkeys("Proposals")
      val allProposalIDDeleted = client.smembers(s"Proposals:ByState:${ProposalState.DELETED.code}")
      val onlyValidProposalIDs = allProposalIDs.diff(allProposalIDDeleted)
      onlyValidProposalIDs
  }

  def countAll() = {
    allProposalIDsNotDeleted.size
  }

  def allDrafts(): List[Proposal] = Redis.pool.withClient {
    implicit client =>
      val proposalIDs = client.smembers("Proposals:ByState:draft")
      loadProposalByIDs(proposalIDs, ProposalState.DRAFT)
  }

  def allSubmitted(): List[Proposal] = Redis.pool.withClient {
    implicit client =>
      val proposalIDs = client.smembers("Proposals:ByState:submitted")
      loadProposalByIDs(proposalIDs, ProposalState.SUBMITTED)
  }

  def allProposalsByAuthor(author: String): Map[String,Proposal] = Redis.pool.withClient {
    implicit client =>
      val allProposalIDs = client.smembers(s"Proposals:ByAuthor:$author")
      loadAndParseProposals(allProposalIDs)
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

      tx.hdel("Proposals", proposal.id)
      tx.exec()
  }

  def findProposalTrack(proposalId: String): Option[Track] = Redis.pool.withClient {
    client =>
      client.hget("Proposals:TrackForProposal", proposalId).flatMap{trackId=>
        Track.all.find(_.id==trackId)
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

  // What we did in 2013
  def getDevoxx2013Total(): Map[ProposalType, Int] = {
    Map(
      (ProposalType.CONF, 68) // 29 sans apres-midi decideur + 39 vendredi
      , (ProposalType.UNI, 8)
      , (ProposalType.TIA, 30)
      , (ProposalType.LAB, 12)
      , (ProposalType.QUICK, 20)
      , (ProposalType.BOF, 15)
    )
  }

  // Move a speaker that was 2nd speaker or "otherSpeaker" to mainSpeaker
  // This is required as any edit operation will automatically set the Proposal's owner to the
  // current authenticated user
  def setMainSpeaker(proposal: Proposal, uuid: String): Proposal = {
    if (proposal.mainSpeaker != uuid) {
      proposal.secondarySpeaker match {
        case Some(u) if u==uuid =>  proposal.copy(mainSpeaker = uuid, secondarySpeaker = Option(proposal.mainSpeaker))
        case _ =>
          // move the main speaker to "other speaker"
          proposal.copy(mainSpeaker = uuid, otherSpeakers = proposal.mainSpeaker :: proposal.otherSpeakers.filterNot(_ == uuid))
      }
    } else {
      proposal
    }
  }

  /**
   * Returns all Proposals with sponsorTalk=true, whatever is the current status.
   */
  def allSponsorsTalk():List[Proposal]={
    val allTalks = allProposals().filter(_.sponsorTalk)
    allTalks.map{proposal=>
      val proposalState = findProposalState(proposal.id)
      proposal.copy(state = proposalState.getOrElse(ProposalState.UNKNOWN))
    }.filterNot(_.state==ProposalState.DELETED).filterNot(_.state==ProposalState.DECLINED)
  }

  // This is a slow operation
  def allProposals(): List[Proposal] = Redis.pool.withClient {
    implicit client =>
      client.hvals("Proposals").map {
        json =>
          Json.parse(json).as[Proposal]
      }
  }

  // This code is a bit complex. It's an optimized version that loads from Redis
  // a set of Proposal. It returns only valid proposal, successfully loaded.
  def loadAndParseProposals(proposalIDs: Set[String]): Map[String, Proposal] = Redis.pool.withClient {
    implicit client =>
      val listOfProposals = proposalIDs.toList
      val proposals = client.hmget("Proposals", listOfProposals).map {
        json: String =>
          Json.parse(json).asOpt[Proposal].map(p=> p.copy(state=findProposalState(p.id).getOrElse(p.state)))
      }
      // zipAll is used to merge the list of proposals with the list of parsed/loaded Proposal
      // If a proposal was not found, the list "proposals" contains a None.
      // We then dorp the empty Proposal, so that we keep only the ones that we could load
      listOfProposals.zipAll(proposals, "?", None).filterNot(_._2.isEmpty).map(t => (t._1, t._2.get)).toMap
  }


}

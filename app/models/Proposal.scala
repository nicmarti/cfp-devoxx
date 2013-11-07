package models

import play.api.libs.json.Json
import library.Redis
import org.apache.commons.lang3.{StringUtils, RandomStringUtils}

import play.api.data._
import play.api.data.Forms._
import play.api.data.validation.Constraints._

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

  def parse(session: String): ProposalType = {
    session match {
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

  implicit val proposalTypeState = Json.format[ProposalState]

  val DRAFT = ProposalState("draft")
  val SUBMITTED = ProposalState("submitted")
  val DELETED = ProposalState("deleted")
  val APPROVED = ProposalState("approved")
  val REJECTED = ProposalState("rejected")
  val ACCEPTED = ProposalState("accepted")
  val DECLINED = ProposalState("declined")
  val BACKUP = ProposalState("backup")


  val all = List(
    DRAFT,
    SUBMITTED,
    DELETED,
    APPROVED,
    REJECTED,
    ACCEPTED,
    DECLINED,
    BACKUP
  )

  val allAsCode = all.map(_.code)
}

case class Proposal(id: Option[String], event: String, code: String, lang: String, title: String, mainSpeaker: String,
                    otherSpeakers: List[String], talkType: ProposalType, audienceLevel: String, summary: String,
                    privateMessage: String, state: ProposalState, sponsorTalk: Boolean = false, track: Track)

object Proposal {

  implicit val proposalFormat = Json.format[Proposal]

  val langs = Seq(("en", "English"), ("fr", "French"))

  val audienceLevels = Seq(("novice", "Novice"), ("intermediate", "Intermediate"), ("expert", "Expert"))

  def saveDraft(creator: String, proposal: Proposal) = Redis.pool.withClient {
    client =>
      val json = Json.toJson(proposal).toString

      // TX
      val tx = client.multi()
      tx.hset("Proposals", proposal.id.get, json)
      tx.sadd("Proposals:Draft", proposal.id.get)
      tx.sadd("Proposals:ByAuthor:" + creator, proposal.id.get)
      tx.sadd("Proposals:ByTrack:" + proposal.track.id, proposal.id.get)
      tx.zadd("Proposals:Event", new java.util.Date().getTime, creator + " posted a new proposal [" + proposal.title + "]")
      tx.exec()
  }

  def allMyDraftProposals(email: String): List[Proposal] = Redis.pool.withClient {
    client =>
      val allProposalIds: Set[String] = client.sinter("Proposals:Draft", "Proposals:ByAuthor:" + email)
      println("allProposalIds "+allProposalIds)
      client.hmget("Proposals", allProposalIds).flatMap {
        proposalJson: String =>
          println("proposalJSON "+proposalJson)
          Json.parse(proposalJson).asOpt[Proposal]
      }
  }

  val proposalForm = Form(mapping(
    "lang" -> text,
    "title" -> nonEmptyText(maxLength = 125),
    "mainSpeaker" -> nonEmptyText,
    "otherSpeakers" -> list(text),
    "talkType" -> nonEmptyText,
    "audienceLevel" -> text,
    "summary" -> nonEmptyText(maxLength = 2000),
    "privateMessage" -> optional(text(maxLength = 1000)),
    "sponsorTalk" -> boolean,
    "track" -> nonEmptyText
  )(validateNewProposal)(unapplyProposalForm))

  def validateNewProposal(lang: String, title: String, mainSpeaker: String, otherSpeakers: List[String],
                          talkType: String, audienceLevel: String, summary: String, privateMessage: Option[String],
                          sponsorTalk: Boolean, track: String): Proposal = {
    val code = RandomStringUtils.randomAlphabetic(3).toUpperCase + "-" + RandomStringUtils.randomNumeric(3)
    Proposal(Option(RandomStringUtils.randomAlphanumeric(12)),
      "Devoxx France 2014",
      code,
      lang,
      title,
      mainSpeaker,
      otherSpeakers,
      ProposalType.parse(talkType),
      audienceLevel,
      summary,
      StringUtils.trimToEmpty(privateMessage.getOrElse("")),
      ProposalState.DRAFT,
      sponsorTalk,
      Track.parse(track)
    )

  }

  def unapplyProposalForm(p: Proposal): Option[(String, String, String, List[String], String, String, String, Option[String],
    Boolean, String)] = {
    Option((p.lang, p.title, p.mainSpeaker, p.otherSpeakers, p.talkType.id, p.audienceLevel, p.summary, Option(p.privateMessage),
      p.sponsorTalk, p.track.id))
  }
}

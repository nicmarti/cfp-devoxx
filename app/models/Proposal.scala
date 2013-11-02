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

  val CONF=ProposalType("conf", "Conference")
  val UNI =ProposalType("uni", "University")
  val TIA =ProposalType("tia", "Tools-in-action")
  val LAB =ProposalType("lab", "Hands-on-labs")
  val QUICK=ProposalType("quick", "Quickie")
  val BOF=ProposalType("bof", "BOF")
  val AMD=ProposalType("amd", "AM Decideurs")
  val KEY=ProposalType("key", "Keynote")
  val OTHER=ProposalType("other", "Other")


  val all = List(CONF, UNI, TIA, LAB, QUICK, BOF, AMD, KEY, OTHER)

  val allAsId = all.map(a=>(a.id,a.label)).toSeq.sorted

  def parse(session:String):ProposalType={
    session match {
      case "conf" => CONF
      case "uni"=>UNI
      case "tia"=>TIA
      case "lab"=>LAB
      case "quick"=>QUICK
      case "bof"=>BOF
      case "amd"=>AMD
      case "key"=>KEY
      case other =>OTHER
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

  val allAsCode=all.map(_.code)
}

case class Proposal(id: Option[String], event: String, code: String, lang: String, title: String, mainSpeaker: String,
                    otherSpeakers: List[String], talkType: ProposalType, audienceLevel: String, summary: String,
                    privateMessage: String, state: ProposalState, sponsorTalk: Boolean = false)

object Proposal {

  implicit val proposalFormat = Json.format[Proposal]

  val langs=Seq(("en","English"),("fr","French"))

  val audienceLevels=Seq(("novice","Novice"),("intermediate","Intermediate"),("expert","Expert"))


  def save(proposal: Proposal) = Redis.pool.withClient {
    client =>
      val json = Json.toJson(proposal).toString
      client.hset("Proposals", "test", json)
  }

  def allMyProposals(email: String): List[Proposal] = Redis.pool.withClient {
    client =>
      Nil
  }

  val proposalForm=Form(mapping(
      "lang" -> text,
      "title" -> text(minLength = 5, maxLength = 125),
      "mainSpeaker" -> nonEmptyText,
      "otherSpeakers" -> list(text),
      "talkType" -> nonEmptyText,
      "audienceLevel"->text,
      "summary"->text(maxLength = 2000),
      "privateMessage"->optional(text),
      "sponsorTalk"->boolean
    )(validateNewProposal)(unapplyProposalForm))

  def validateNewProposal(lang:String, title:String, mainSpeaker:String, otherSpeakers:List[String],
                          talkType:String, audienceLevel:String, summary:String, privateMessage:Option[String],
                          sponsorTalk:Boolean):Proposal={
    val code=RandomStringUtils.randomAlphabetic(3).toUpperCase+"-"+RandomStringUtils.randomNumeric(3)
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
             sponsorTalk)

  }

  def unapplyProposalForm(p:Proposal):Option[(String,String,String,List[String],String,String,String,Option[String],Boolean)]={
    Option((p.lang,p.title, p.mainSpeaker, p.otherSpeakers, p.talkType.id, p.audienceLevel, p.summary, Option(p.privateMessage), p.sponsorTalk))
  }
}

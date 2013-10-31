package models

import play.api.libs.json.Json
import library.Redis

/**
 * Proposal
 *
 * Author: nicolas
 * Created: 12/10/2013 15:19
 */
case class ProposalType(id: String, label: String)

object ProposalType {
  implicit val proposalTypeFormat = Json.format[ProposalType]

  val all = List(
    ProposalType("conf", "Conference"),
    ProposalType("uni", "University"),
    ProposalType("tia", "Tools-in-action"),
    ProposalType("lab", "Hands-on-labs"),
    ProposalType("quick", "Quickie"),
    ProposalType("bof", "BOF"),
    ProposalType("amd", "AM Decideurs"),
    ProposalType("key", "Keynote")
  )
}


case class ProposalState(code: String)

object ProposalState {

  implicit val proposalTypeState = Json.format[ProposalState]

  val all = List(
    ProposalState("draft"),
    ProposalState("submitted"),
    ProposalState("Deleted"),
    ProposalState("Approved"),
    ProposalState("Rejected"),
    ProposalState("Accepted"),
    ProposalState("Declined"),
    ProposalState("Backup")
  )
}

case class Proposal(id: String, event: String, code: String, lang: String, title: String, mainSpeaker: String,
                    otherSpeakers: List[String], talkType: ProposalType, audienceLevel: String, summary: String,
                    privateMessage: String, state: ProposalState, sponsorTalk: Boolean = false)

object Proposal {

  implicit val proposalFormat = Json.format[Proposal]

  def save(proposal: Proposal) = Redis.pool.withClient {
    client =>
      val json = Json.toJson(proposal).toString
      println("Json "+json)
      client.hset("Proposals","test",json)
  }

  def allMyProposals(email: String): List[Proposal] = Redis.pool.withClient {
    client =>
      Nil
  }
}

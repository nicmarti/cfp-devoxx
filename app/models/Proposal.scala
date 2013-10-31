package models

import reactivemongo.bson.BSONObjectID
import scala.concurrent.Future
import library.MongoDB
import reactivemongo.api.Cursor
import play.api.libs.json.Json
import reactivemongo.core.commands.LastError
import reactivemongo.api.indexes.{IndexType, Index}

/**
 * Proposal
 *
 * Author: nicolas
 * Created: 12/10/2013 15:19
 */
case class ProposalType(id: String, label: String)

object ProposalType {
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


object State extends Enumeration{
  type State = Value
   val Draft = Value("draft")
   val Submitted = Value("submitted") 
   val Deleted=Value("Deleted")
   val Approved=Value("Approved")
   val Rejected=Value("Rejected")
   val Accepted=Value("Accepted")
   val Declined=Value("Declined")
   val Backup=Value("Backup")
}

case class Proposal(id: Option[BSONObjectID], event: String, code: String, lang: String, title: String, mainSpeaker: String,
                    otherSpeakers: List[String], talkType: ProposalType, audienceLevel: String, summary: String,
                    privateMessage: String, state: State, sponsorTalk: Boolean = false)

object Proposal {

  def save(proposal:Proposal):Future[LastError] = MongoDB.withCollection("proposal") {
    implicit collection=>
val result = if(proposal.id.isEmpty){
        collection.indexesManager.ensure(Index(List("mailSpeaker" -> IndexType.Ascending), name = Some("idx_mailSpeaker")))
        collection.insert(proposal.copy(id = Some(BSONObjectID.generate), state = State.Draft))

      } else {
        collection.insert(proposal
        )
      }
      result
  }

  def allMyProposals(email: String): List[Proposal] = MongoDB.withCollection("proposal") {
    implicit collection =>
      val cursor: Cursor[Proposal] = collection.find(Json.obj("mainSpeaker" -> email)).cursor[Proposal]
      cursor.toList()
  }
}

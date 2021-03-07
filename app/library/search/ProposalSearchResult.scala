package library.search

import com.sksamuel.elastic4s.{Hit, HitReader}
import models.{Proposal, Speaker}

import scala.util.{Failure, Success, Try}

case class ProposalSearchResult(
                                 id: String,
                                 trackLabel: String,
                                 talkType: String,
                                 title: String,
                                 escapedTitle: String,
                                 mainSpeaker: String,
                                 secondarySpeaker: Option[String],
                                 otherSpeakers: List[String]
                               )

object ProposalSearchResult {

  implicit object ProposalHitReader extends HitReader[ProposalSearchResult] {
    override def read(hit: Hit): Try[ProposalSearchResult] = {
      val source = hit.sourceAsMap
      val proposal: Option[ProposalSearchResult] = source.get("id").flatMap {
        id =>
          Proposal.findById(id.toString).map {
            p: Proposal =>
              val mainSpeaker = Speaker.findByUUID(p.mainSpeaker).map(_.cleanName).getOrElse("?")
              val secSpeaker = p.secondarySpeaker.flatMap(sec => Speaker.findByUUID(sec).map(_.cleanName))
              val otherSpeakers = p.otherSpeakers.flatMap(sec => Speaker.findByUUID(sec).map(_.cleanName))

              ProposalSearchResult(p.id,
                p.track.label,
                p.talkType.label,
                p.title,
                p.escapedTitle,
                mainSpeaker,
                secSpeaker,
                otherSpeakers)
          }
      }
      if (proposal.isEmpty) {
        Failure(new RuntimeException("Cannot reload proposal id " + source.get("id")))
      } else {
        Success(proposal.get)
      }
    }
  }

}
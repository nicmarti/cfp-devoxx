package models

import controllers.CallForPaper.TermCount
import library.Redis

/**
  * @author Stephan Janssen
  */
case class TagProposalEntry(tag: Tag, proposal: Proposal) {

}

object Tags {

  private val tags = "Tags:*"

  def createTagProposalEntry(tag: Tag, proposal: Proposal): TagProposalEntry = {
    TagProposalEntry(tag, proposal)
  }

  def isTagLinkedByProposal(tagId : String): Boolean = Redis.pool.withClient {
    client =>
      client.smembers("Tags:"+tagId).nonEmpty
  }

  def allProposalsByTagId(tagId : String) : Map[String, Proposal] = Redis.pool.withClient {
    client =>
      val proposalIds = client.smembers("Tags:"+tagId)
      Proposal.loadAndParseProposals(proposalIds)
  }

  def countProposalTags() : List[TermCount]  = Redis.pool.withClient {
    client =>

      val tagIDs: Set[String] = client.keys(tags).map(key => key.split(":").last)
      val termCounts = scala.collection.mutable.Set[TermCount]()

      tagIDs.map(tagId => {
        val tagValue = Tag.findTagValueById(tagId).get

        val count = client.scard("Tags:" + tagId)

        termCounts.add(TermCount(tagValue, count.toInt))

      })

      termCounts.toList
  }

  def allProposals(): List[TagProposalEntry] = Redis.pool.withClient {
    client =>
      val foundTags = scala.collection.mutable.Set[TagProposalEntry]()

      client.keys(tags).filter(t => !t.contains(":0"))
                       .foreach( tag => {

        val tagId = tag.split(":").last

        // Get proposal titles
        val proposals = client.smembers(tag)

        // Create TagProposalEntries
        proposals.foreach(proposalId => {
          foundTags.add(createTagProposalEntry(Tag.findById(tagId).get,
                                               Proposal.findById(proposalId).get))
        })
      })

      foundTags.toList.sortBy(tpe => tpe.tag.value)
  }
}

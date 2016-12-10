package models

import library.Redis

/**
  * @author Stephan Janssen
  */
case class TagProposalEntry(tag: String, proposal: String, proposalId: String) {

}

object Tags {

  private val tags = "Tags:*"

  def createTagEntry(tag: String, proposal: String, proposalId: String): TagProposalEntry = {
    new TagProposalEntry(tag, proposal, proposalId)
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
          foundTags.add(createTagEntry(Tag.findByID(tagId).get.value,
                                       Proposal.findById(proposalId).get.title,
                                       proposalId))
        })
      })

      foundTags.toList.sortBy(tpe => tpe.tag)
  }
}

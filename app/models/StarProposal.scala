package models

import library.Redis

/**
  * TODO Allow cleanup of star proposals in Atic
  *
  * @author Stephan Janssen
  */
object StarProposal {

  def assign(proposalId: String, webUserId: String) {
    if (Webuser.hasAccessToCFP(webUserId)) {
      Redis.pool.withClient {
        client =>
          client.hset(s"StarProposals", proposalId, webUserId)
      }
    }
  }

  def unassign(proposalId: String, webUserId: String) {
    Redis.pool.withClient {
      client =>
        client.hdel(s"StarProposals", proposalId)
    }
  }

  def isStarred(proposalId: String, webUserId: String): Boolean = Redis.pool.withClient {
    client =>
      client.hget(s"StarProposals", proposalId) match {
        case Some(w) if w == webUserId => true
        case _ => false
      }
  }

  def isStarred(proposalId: String): Boolean = Redis.pool.withClient {
    client =>
      client.hget(s"StarProposals", proposalId).isDefined
  }

  def all(): Map[String, String] = Redis.pool.withClient {
    client => client.hgetAll("StarProposals")
  }
}

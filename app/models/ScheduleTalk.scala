package models

import library.Redis

/**
  * @author Stephan Janssen
  */
object ScheduleTalk {

  private val redis = s"ScheduleTalk:${ConferenceDescriptor.current().eventCode}:"


  def scheduleTalk(proposalId: String, webuserId: String) = Redis.pool.withClient {
    implicit client =>
      client.sadd(redis + ":ByProp:" + proposalId, webuserId)
      client.sadd(redis + ":ByUser:" + webuserId, proposalId)
  }

  def isScheduledByThisUser(proposalId: String, webuserId: String): Boolean = Redis.pool.withClient {
    implicit client =>
      client.sismember(redis + ":ByProp:" + proposalId, webuserId)
  }

  def unscheduleTalk(proposalId: String, webuserId: String) = Redis.pool.withClient {
    implicit client =>
      client.srem(redis + ":ByProp:" + proposalId, webuserId)
      client.srem(redis + ":ByUser:" + webuserId, proposalId)
  }

  def delSchedule(proposalId: String) = Redis.pool.withClient {
    implicit client =>
      val allWebusers = client.smembers(redis + ":ByProp:" + proposalId)
      client.del(redis + ":ByProp:" + proposalId)
      allWebusers.foreach { uuid =>
        client.del(redis + ":ByUser:" + uuid, proposalId)
      }
  }

  def allForUser(webuserId: String): List[String] = Redis.pool.withClient {
    implicit client =>
      client.smembers(redis + ":ByUser:" + webuserId).toList
  }

  def countForProposal(proposalId: String): Long = Redis.pool.withClient {
    implicit client =>
      client.scard(redis + ":ByProp:" + proposalId)
  }

  def all() = Redis.pool.withClient {
    implicit client =>
      val allFav: Set[String] = client.keys(redis + ":ByProp:*")

      val allProposalIDs: Set[String] = allFav.map {
        key: String =>
          key.substring((redis + ":ByProp:").length)
      }

      allProposalIDs.map {
        proposalId =>
          val proposal = Proposal.findById(proposalId)
          val total = client.scard(redis + ":ByProp:" + proposalId)
          (proposal, total)
      }.filterNot(_._1.isEmpty)
        .map(t => (t._1.get, t._2))
  }
}
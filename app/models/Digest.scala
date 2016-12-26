package models

import java.lang.Long

import library.{NotifyProposalSubmitted, Redis, ZapActor}

/**
  *
  * @author Stephan Janssen
  */

case class Digest(value: String, intervalInDays: Integer) {
}

object Digest {
  val REAL_TIME = Digest("Realtime", 0)
  val DAILY = Digest("Daily", 1)
  val WEEKLY = Digest("Weekly", 7)
  val NEVER = Digest("Never", -1)

  val allDigests = List(REAL_TIME, DAILY, WEEKLY, NEVER)

  def update(uuid: String, digest: String): String = Redis.pool.withClient {
      client =>
        client.set("Digest:"+uuid, digest)
  }

  def retrieve(uuid: String) : String = Redis.pool.withClient {
    client =>
      if (client.exists("Digest:"+uuid)) {
        client.get("Digest:" + uuid).get
      } else {
        Digest.REAL_TIME.value
      }
  }

  /**
    * Store a proposal into the digest "queue".
    *
    * @param digest the digest type (daily, weekly, never)
    * @param uuid the CFP user to notify
    * @param proposalId the proposal Id
    * @return
    */
  def queueProposal(digest : String, uuid : String, proposalId : String): Long = Redis.pool.withClient {
    client =>
      client.hset("Digest:"+digest, uuid, proposalId)
  }

  def newProposal(speakerId : String, proposal : Proposal): Unit = Redis.pool.withClient {
    client =>

      // Walk through all the CFP members and verify digest setting
      Webuser.allCFPWebusers().foreach(user => {
        val digestValue = retrieve(user.uuid)
        digestValue match {
          case Digest.NEVER => // don't do anything for now

          // Notify user immediately
          case Digest.REAL_TIME => ZapActor.actor ! NotifyProposalSubmitted(speakerId, proposal)

          // Store notification in digest queue
          case Some(Digest) => queueProposal(digestValue, DAILY, user.uuid, proposalId)

        }
      })
  }
}
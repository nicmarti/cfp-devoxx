package models

import java.lang.Long
import java.util.Locale

import library.{Redis, ZapActor}
import notifiers.Mails
import org.joda.time.{DateTime, DateTimeConstants}
import play.Play
import play.api.i18n.Messages

/**
  * The email digest.
  *
  * Redis keys used :
  *
  * 1) Identify which digest setting a CFP web user has:
  * "Key = Digest:User:[WebUserId] / Value = Realtime, Daily, Weekly, Never"
  *
  * 2) The next redis key holds the Daily and Weekly new proposals IDs
  * "Key = Digest:Daily /  Keys = IDs of new proposals for one day  / Values = creation data
  * "Key = Digest:Weekly / Keys = IDs of new proposals for one week / Values = creation data
  *
  * @author Stephan Janssen
  */
case class Digest(value: String) {
}

object Digest {

  val REAL_TIME = Digest("Realtime")    // Real time email updates
  val DAILY = Digest("Daily")           // Daily email digest
  val WEEKLY = Digest("Weekly")         // Weekly
  val NEVER = Digest("Never")           // Never, means the CFP user will never receive proposal updates!

  // All the digest interval values
  val allDigests = List(REAL_TIME, DAILY, WEEKLY, NEVER)

  private val digestRedisKey = "Digest:"
  private val digestUserRedisKey = digestRedisKey + "User:"


  def message(uuid: String): String = {
    val app = Play.application()

    var hour = app.configuration().getString("digest.daily")
    if (hour.isEmpty) {
       hour = "00:00"
    }

    val day = app.configuration().getInt("digest.weekly")
    var dayMsg : String = ""
    if (day == null) {
      dayMsg = "Monday"
    } else {
      dayMsg = Messages("email.digest.day."+day)
    }

    retrieve(uuid) match {
      case "Daily" => Messages("email.digest.daily.description", hour)

      case "Weekly" => Messages("email.digest.weekly.description",
        app.configuration().getString("digest.daily"), dayMsg)

      case "Realtime" => Messages("email.digest.realtime.description")

      case "Never" => Messages("email.digest.never.description")
    }
  }

  /**
    * Update the email digest for the give web user id.
    *
    * @param webUserId  the web user id to update
    * @param digest the new email digest setting
    * @return
    */
  def update(webUserId: String, digest: String): String = Redis.pool.withClient {
    implicit client =>
        client.set(digestUserRedisKey+webUserId, digest)
  }

  /**
    * Retrieve the email digest setting for the web user UUID.
    *
    * @param webUserId  the web user UUID
    * @return the email digest setting
    */
  def retrieve(webUserId: String) : String = Redis.pool.withClient {
    implicit client =>
      if (client.exists(digestUserRedisKey+webUserId)) {
        client.get(digestUserRedisKey + webUserId).get
      } else {
        Digest.REAL_TIME.value
      }
  }

  /**
    * Purge (delete) the given digest.
    *
    * @param digest the digest to clean up
    * @return
    */
  def purge(digest: Digest): Long = Redis.pool.withClient {
    implicit client =>
      client.del(digestRedisKey + digest.value)
  }

  /**
    * Add the submitted proposal to the digest queue.
    *
    * @param proposalId the actual new proposal
    */
  def addProposal(proposalId : String): Unit = Redis.pool.withClient {
    implicit client =>
      val now = DateTime.now().toString("d MMM yyyy HH:mm")
      allDigests.foreach(digest => {
        client.hset(digestRedisKey + digest.value, proposalId, now)
      })
  }

  /**
    * Delete a proposal from the email digest queue.
    *
    * @param proposalId the actual new proposal
    */
  def deleteProposal(proposalId : String): Unit = Redis.pool.withClient {
    implicit client =>
      allDigests.foreach(digest => {
        if (client.hexists(digestRedisKey + digest.value, proposalId)) {
          client.hdel(digestRedisKey + digest.value, proposalId)
        }
      })
  }

  /**
    * The pending proposals for the given digest.
    *
    * @param digest the digest to query
    * @return list of proposals
    */
  def pendingProposals(digest : Digest) : Map[String, String] = Redis.pool.withClient {
    implicit client =>
      client.hgetAll(digestRedisKey + digest.value).map {
        case (key: String, value: String) =>
          (key, value)
      }
  }

  /**
    * Mail the digest.
    *
    * @param userEmails user email list to receive email digest
    * @param digest the digest interval
    * @see library.ZapActor.doEmailDigests
    */
  def mail(userEmails : List[String], digest : Digest): Unit = Redis.pool.withClient {
    implicit client =>

      val newProposalsIds = pendingProposals(digest)

      play.Logger.debug("Mail " + digest.value +
                        " digests for " + newProposalsIds.size +
                        " proposal(s) and " + userEmails.size +
                        " users.")

      if (newProposalsIds.nonEmpty) {
        val proposals = newProposalsIds.map(entry => Proposal.findById(entry._1).get).toList
        ZapActor.actor ! Mails.sendDailyDigest(userEmails, proposals)
      }
  }
}
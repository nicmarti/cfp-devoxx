package models

import java.lang.Long
import library.Redis
import org.joda.time.DateTime
import play.Play
import play.api.i18n.Messages

import java.util.function.Supplier

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
case class Digest(value: String, labelI18nMessage: Function0[String], storeProposals: Boolean) {
}

object Digest {

  val REAL_TIME = Digest("Realtime", () => Messages("email.digest.realtime.description"), storeProposals = true)    // Real time email updates
  val DAILY = Digest("Daily", () => Messages("email.digest.daily.description", getDayMoment), storeProposals = true)           // Daily email digest
  val WEEKLY = Digest("Weekly", () => Messages("email.digest.weekly.description", getDayMoment, getWeekMoment), storeProposals = true)         // Weekly
  val NEVER = Digest("Never", () => Messages("email.digest.never.description"), storeProposals = false)          // Never, means the CFP user will never receive proposal updates!

  // All the digest interval values
  val allDigests = List(REAL_TIME, DAILY, WEEKLY, NEVER)
  val selectableDigests = List(REAL_TIME, DAILY, WEEKLY)

  private val digestRedisKey = "Digest:"
  private val digestUserRedisKey = digestRedisKey + "User:"
  private val digestFilterRedisKey = digestUserRedisKey + "Filter:"

  private val app = Play.application()

  def getDayMoment: String = {
    val daily = app.configuration().getString("digest.daily")
    if (daily == null) {
      "00:00" // default value
    } else {
      daily
    }
  }

  def getWeekMoment: String = {
    val day = app.configuration().getInt("digest.weekly")
    if (day == null) {
      "Monday"  // default value
    } else {
      Messages("email.digest.day." + day)
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
      allDigests.filter(digest => digest.storeProposals)
                .foreach(digest => {
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
}

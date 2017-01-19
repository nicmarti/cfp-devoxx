package models

import java.lang.Long

import library.Redis
import org.joda.time.DateTime
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
case class Digest(value: String, storeProposals: Boolean) {
}

object Digest {

  val REAL_TIME = Digest("Realtime", storeProposals = true)    // Real time email updates
  val DAILY = Digest("Daily", storeProposals = true)           // Daily email digest
  val WEEKLY = Digest("Weekly", storeProposals = true)         // Weekly
  val NEVER = Digest("Never", storeProposals = false)          // Never, means the CFP user will never receive proposal updates!

  // All the digest interval values
  val allDigests = List(REAL_TIME, DAILY, WEEKLY, NEVER)

  private val digestRedisKey = "Digest:"
  private val digestUserRedisKey = digestRedisKey + "User:"
  private val digestFilterRedisKey = digestUserRedisKey + "Filter:"

  private val app = Play.application()

  /**
    * Construct the digest message.
    *
    * @param uuid the user id
    * @return the I18N digest message for the UI view
    */
  def message(uuid: String): String = {
    retrieve(uuid) match {
      case DAILY.value => Messages("email.digest.daily.description", getDayMoment)

      case WEEKLY.value => Messages("email.digest.weekly.description", getDayMoment, getWeekMoment)

      case REAL_TIME.value => Messages("email.digest.realtime.description")

      case NEVER.value => Messages("email.digest.never.description")
    }
  }

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
    * Update the email digest for the give web user id.
    *
    * @param webUserId  the web user id to update
    * @param digest the new email digest setting
    * @return
    */
  def update(webUserId: String, digest: String): String = Redis.pool.withClient {
    implicit client =>
        client.set(digestUserRedisKey + webUserId, digest)
  }

  /**
    * Add a track digest filter for user.
    *
    * @param webUserId the web user uuid
    * @param trackId the track ID to filter on
    */
  def addTrackFilter(webUserId: String, trackId: String): Long = Redis.pool.withClient {
    implicit client =>
      val now = DateTime.now().toString("d MMM yyyy HH:mm")
      client.hset(digestFilterRedisKey + webUserId, trackId, now)
  }

  /**
    * Remove a track digest filter for user.
    *
    * @param webUserId the web user uuid
    * @param trackId the track ID to filter on
    */
  def delTrackFilter(webUserId: String, trackId: String): Long = Redis.pool.withClient {
    implicit client =>
      client.hdel(digestFilterRedisKey + webUserId, trackId)
  }

  /**
    * Get the digest filter track identifiers.
    *
    * @param webUserId the web user uuid
    * @return the track HTML entries
    */
  def getTrackFilters(webUserId: String): List[String] = Redis.pool.withClient {
    implicit client =>
      client.hkeys(digestFilterRedisKey + webUserId).toList
  }

  /**
    * Retrieve the email digest setting for the web user UUID.
    *
    * @param webUserId  the web user UUID
    * @return the email digest setting
    */
  def retrieve(webUserId: String) : String = Redis.pool.withClient {
    implicit client =>
      if (client.exists(digestUserRedisKey + webUserId)) {
        client.get(digestUserRedisKey + webUserId).get
      } else {
        Digest.NEVER.value
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
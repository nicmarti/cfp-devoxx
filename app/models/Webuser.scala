package models

import play.api.libs.json.Json
import org.apache.commons.codec.digest.DigestUtils
import play.api.data.format.Formats._
import library.Redis
import org.apache.commons.lang3.{StringUtils, RandomStringUtils}
import play.api.libs.Crypto
import play.api.cache.Cache
import play.api.Play.current

case class Webuser(uuid: String, email: String, firstName: String, lastName: String, password: String, profile: String) {
  val cleanName = {
    firstName.capitalize + " " + lastName
  }
}

object Webuser {
  implicit val webuserFormat = Json.format[Webuser]

  def gravatarHash(email: String): String = {
    Cache.getOrElse[String]("gravatar:" + email) {
      val cleanEmail = email.trim().toLowerCase()
      DigestUtils.md5Hex(cleanEmail)
    }
  }

  def generateUUID(email:String):String={
    Crypto.sign(StringUtils.abbreviate(email.trim().toLowerCase,255))
  }

  def createSpeaker(email: String, firstName: String, lastName: String): Webuser = {
    Webuser(generateUUID(email), email, firstName, lastName, RandomStringUtils.randomAlphabetic(7), "speaker")
  }

  def unapplyForm(webuser: Webuser): Option[(String, String, String)] = {
    Some(webuser.email, webuser.firstName, webuser.lastName)
  }

  def getName(uuid: String): String = {
    findByUUID(uuid).map(_.cleanName).getOrElse("Anonymous " + uuid)
  }

  def saveNewSpeakerEmailNotValidated(webuser: Webuser) = Redis.pool.withClient {
    client =>
      val cleanWebuser = webuser.copy(email = webuser.email.toLowerCase.trim)
      val json = Json.toJson(cleanWebuser).toString
      client.hset("Webuser:New", cleanWebuser.email, json)
  }

  def findNewUserByEmail(email: String): Option[Webuser] = Redis.pool.withClient {
    client =>
      client.hget("Webuser:New", email.toLowerCase.trim).flatMap {
        json: String =>
          Json.parse(json).asOpt[Webuser]
      }
  }

  def saveAndValidateWebuser(webuser: Webuser):String = Redis.pool.withClient {
    client =>
      val cleanWebuser = webuser.copy(email = webuser.email.toLowerCase.trim)
      val json = Json.toJson(cleanWebuser).toString

      Cache.remove("web:email:" + cleanWebuser.email)
      Cache.remove("web:uuid:" + cleanWebuser.uuid)

      val tx = client.multi()
      tx.hset("Webuser", cleanWebuser.uuid, json)
      tx.set("Webuser:UUID:" + cleanWebuser.uuid, webuser.email)
      tx.set("Webuser:Email:" + cleanWebuser.email, webuser.uuid)
      tx.sadd("Webuser:" + cleanWebuser.profile, webuser.uuid)
      tx.hdel("Webuser:New", cleanWebuser.email)
      tx.exec()
      cleanWebuser.uuid
  }

  def isEmailRegistered(email:String):Boolean=Redis.pool.withClient{
    implicit client=>
      client.exists("Webuser:Email:"+email.toLowerCase.trim)
  }

  def findByEmail(email: String): Option[Webuser] = email match {
    case null => None
    case "" => None
    case validEmail => {
      val _email = validEmail.toLowerCase.trim
      Cache.getOrElse[Option[Webuser]]("web:email:" + _email, 3600) {
        Redis.pool.withClient {
          client =>
            client.get("Webuser:Email:" + _email).flatMap {
              uuid: String =>
                client.hget("Webuser", uuid).map {
                  json: String =>
                    Json.parse(json).as[Webuser]
                }
            }
        }
      }
    }
  }

  def findByUUID(uuid: String): Option[Webuser] = Redis.pool.withClient {
    client =>
      Cache.getOrElse[Option[Webuser]]("web:uuid:" + uuid, 3600) {
        client.hget("Webuser", uuid).map {
          json: String =>
            Json.parse(json).as[Webuser]
        }
      }
  }

  def checkPassword(email: String, password: String): Option[Webuser] = Redis.pool.withClient {
    client =>
      val maybeUser = findByEmail(email)
      if (maybeUser.exists(_.password == password)) {
        maybeUser
      } else {
        None
      }
  }

  def delete(webuser: Webuser) = Redis.pool.withClient {
    implicit client =>
      val cleanWebuser = webuser.copy(email = webuser.email.toLowerCase.trim)
      Proposal.allMyDraftProposals(cleanWebuser.uuid).foreach {
        proposal =>
          play.Logger.of("models.Webuser").debug(s"Deleting proposal ${proposal}")
          Proposal.destroy(proposal)
      }

      val tx = client.multi()
      tx.hdel("Webuser", cleanWebuser.uuid)
      tx.del("Webuser:UUID:" + cleanWebuser.uuid)
      tx.del("Webuser:Email:" + cleanWebuser.email)
      tx.srem("Webuser:" + cleanWebuser.profile, cleanWebuser.email)
      tx.hdel("Webuser:New", cleanWebuser.email)
      tx.srem("Webuser:speaker",cleanWebuser.uuid)
      tx.srem("Webuser:cfp",cleanWebuser.uuid)
      tx.srem("Webuser:admin",cleanWebuser.uuid)
      tx.exec()

      TrackLeader.deleteWebuser(cleanWebuser.uuid)

      Cache.remove(s"web:email:${cleanWebuser.email}")
      Cache.remove(s"Webuser:cfp:${cleanWebuser.uuid}")
      Cache.remove(s"Webuser:admin:${cleanWebuser.uuid}")
      Cache.remove(s"Webuser:speaker:${cleanWebuser.uuid}")
      Cache.remove(s"web:uuid:${cleanWebuser.uuid}")
  }

  def changePassword(webuser: Webuser): String = Redis.pool.withClient {
    client =>
      val newPassword = RandomStringUtils.randomAlphanumeric(16)
      val updatedWebuser = webuser.copy(password = newPassword)
      update(updatedWebuser)
      newPassword
  }

  def updateNames(uuid: String, newFirstName: String, newLastName: String) = Redis.pool.withClient {
    client =>
      findByUUID(uuid).map {
        webuser =>
          Cache.remove("web:uuid:" + webuser.uuid)
          Cache.remove("web:email:" + webuser.email)
          update(webuser.copy(firstName = newFirstName, lastName = newLastName))
      }
  }

  def update(webuser: Webuser) = Redis.pool.withClient {
    client =>
      val cleanWebuser = webuser.copy(email = webuser.email.toLowerCase.trim)
      val json = Json.stringify(Json.toJson(cleanWebuser))
      client.hset("Webuser", cleanWebuser.uuid, json)
      Cache.remove("web:uuid:" + cleanWebuser.uuid)
      Cache.remove("web:email:" + cleanWebuser.email)

      if (isSpeaker(cleanWebuser.uuid)) {
        Speaker.updateName(cleanWebuser.uuid, cleanWebuser.firstName, cleanWebuser.lastName)
      }
  }

  def isMember(uuid: String, securityGroup: String): Boolean = Redis.pool.withClient {
    client =>
      Cache.getOrElse[Boolean](s"Webuser:$securityGroup:$uuid", 3600) {
        client.sismember("Webuser:" + securityGroup, uuid)
      }
  }

  def hasAccessToCFP(uuid: String): Boolean = isMember(uuid, "cfp")
  def hasAccessToAdmin(uuid: String): Boolean = isMember(uuid, "admin")

  def isSpeaker(uuid: String): Boolean = isMember(uuid, "speaker")

  def addToCFPAdmin(uuid: String) = Redis.pool.withClient {
    client =>
      client.sadd("Webuser:cfp", uuid)
      Cache.remove(s"Webuser:cfp:$uuid")
      Cache.remove(s"Webuser:admin:$uuid")
  }

  def noBackofficeAdmin() = Redis.pool.withClient {
    client =>
      !client.exists("Webuser:admin")
  }

  def addToBackofficeAdmin(uuid: String) = Redis.pool.withClient {
    client =>
      client.sadd("Webuser:admin", uuid)
      // evicting cache
      Cache.remove(s"Webuser:admin:$uuid")
  }

  def removeFromCFPAdmin(uuid: String) = Redis.pool.withClient {
    client =>
      if (uuid != "b14651a3cd78ab4fd03d522ebef81cdac1d5755c") {
        client.srem("Webuser:cfp", uuid)
        Cache.remove(s"Webuser:cfp:$uuid")
      }
  }

  def allSpeakers: List[Webuser] = Redis.pool.withClient {
    client =>
      val allSpeakerUUIDs = client.smembers("Webuser:speaker").toList
      client.hmget("Webuser", allSpeakerUUIDs).flatMap {
        js: String =>
          Json.parse(js).asOpt[Webuser]
      }
  }

  def allWebusers:Map[String, Option[Webuser]]=Redis.pool.withClient {
    client =>
      client.hgetAll("Webuser").map {
        case (key:String, valueJson: String) =>
          (key, Json.parse(valueJson).asOpt[Webuser])
      }
  }

  def allSpeakersAsOption: Seq[(String, String)] = {
    allSpeakers.map {
      webuser =>
        (webuser.uuid, webuser.cleanName)
    }.sortBy(tuple => tuple._2) // sort by label
  }

  def allSecondarySpeakersAsOption: Seq[(String, String)] = {
    allSpeakersAsOption.+:(DEFAULT_LABEL) // sort by name
  }

  def allCFPWebusers(): List[Webuser] = Redis.pool.withClient {
    client =>
      val uuids = client.smembers("Webuser:cfp").toList
      client.hmget("Webuser", uuids).flatMap {
        js: String =>
          Json.parse(js).asOpt[Webuser]
      }
  }

    /* Required for helper.options */
 def allCFPAdminUsers():Seq[(String,String)]={
    val cfpUsers =  Webuser.allCFPWebusers().sortBy(_.cleanName)
      val cfpUsersAndTracks = cfpUsers.toSeq.flatMap{
        w:Webuser=>
          Seq((w.uuid,w.cleanName))
      }
    cfpUsersAndTracks
  }

  def getEmailFromUUID(uuid: String): Option[String] = Redis.pool.withClient {
    client =>
        client.get("Webuser:UUID:" + uuid)
  }

  val DEFAULT_LABEL = ("", play.api.i18n.Messages("noOther.speaker"))

  def doesNotExist(uuid: String): Boolean = Redis.pool.withClient {
    client =>
      !client.exists("Webuser:UUID:" + uuid)
  }
}

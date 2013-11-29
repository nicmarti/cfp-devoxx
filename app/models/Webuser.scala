package models

import play.api.libs.json.Json
import org.apache.commons.codec.digest.DigestUtils
import play.api.data.format.Formats._
import library.Redis
import org.apache.commons.lang3.RandomStringUtils
import redis.clients.util.RedisOutputStream
import play.api.libs.Crypto

case class Webuser(uuid: String, email: String, firstName: String, lastName: String, password: String, profile: String) {
  def cleanName = firstName.toLowerCase.capitalize + " " + lastName.toLowerCase.capitalize
}

object Webuser {
  implicit val webuserFormat = Json.format[Webuser]

  def gravatarHash(email: String): String = {
    val cleanEmail = email.trim().toLowerCase()
    DigestUtils.md5Hex(cleanEmail)
  }

  def createSpeaker(email: String, firstName: String, lastName: String): Webuser = {
    Webuser(Crypto.signToken(email), email, firstName, lastName, RandomStringUtils.randomAlphabetic(7), "speaker")
  }

  def unapplyForm(webuser: Webuser): Option[(String, String, String)] = {
    Some( webuser.email, webuser.firstName, webuser.lastName)
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

  def validateEmailForSpeaker(webuser: Webuser) = Redis.pool.withClient {
    client =>
      val cleanWebuser = webuser.copy(email = webuser.email.toLowerCase.trim)
      val json = Json.toJson(cleanWebuser).toString

      val tx = client.multi()
      tx.hset("Webuser", webuser.uuid, json)
      tx.set("Webuser:UUID:" + webuser.uuid, webuser.email)
      tx.set("Webuser:Email:" + webuser.email, webuser.uuid)
      tx.sadd("Webuser:" + webuser.profile, webuser.uuid)
      tx.hdel("Webuser:New", webuser.email)
      tx.exec()
  }

  def findByEmail(email: String): Option[Webuser] = Redis.pool.withClient {
    client =>
      client.get("Webuser:Email:" + email.toLowerCase.trim).flatMap {
        uuid: String =>
          client.hget("Webuser", uuid).map {
            json: String =>
              Json.parse(json).as[Webuser]
          }
      }
  }

  def findByUUID(uuid: String): Option[Webuser] = Redis.pool.withClient {
    client =>
          client.hget("Webuser", uuid).map {
            json: String =>
              Json.parse(json).as[Webuser]
          }
  }

  def checkPassword(email: String, password: String): Option[Webuser] = Redis.pool.withClient {
    client =>
      val maybeUser=findByEmail(email)
      if(maybeUser.exists(_.password == password)){
        maybeUser
      }else{
        None
      }
  }

  def delete(webuser: Webuser) = Redis.pool.withClient {
    client =>
      val tx = client.multi()
      tx.hdel("Webuser", webuser.uuid)
      tx.del("Webuser:UUID:" + webuser.uuid)
      tx.del("Webuser:Email:" + webuser.email)
      tx.srem("Webuser:" + webuser.profile, webuser.email)
      tx.exec()
  }

  def changePassword(webuser: Webuser): String = Redis.pool.withClient {
    client =>
      val newPassword = RandomStringUtils.randomAlphabetic(7)
      val updatedWebuser = webuser.copy(password = newPassword)
      update(updatedWebuser)
      newPassword
  }

  def updateNames(uuid: String, newFirstName: String, newLastName: String) = Redis.pool.withClient {
    client =>
      findByUUID(uuid).map {
        webuser =>
          update(webuser.copy(firstName = newFirstName.toLowerCase.capitalize, lastName = newLastName.toLowerCase.capitalize))
      }
  }

  def update(webuser: Webuser) = Redis.pool.withClient {
    client =>
      val cleanWebuser = webuser.copy(email = webuser.email.toLowerCase.trim)
      val json = Json.stringify(Json.toJson(cleanWebuser))
      client.hset("Webuser", webuser.uuid, json)

      if (isSpeaker(webuser.uuid)) {
        SpeakerHelper.updateName(webuser.email, webuser.cleanName)
      }
  }

  def isMember(uuid: String, securityGroup: String): Boolean = Redis.pool.withClient {
    client =>
      client.sismember("Webuser:" + securityGroup, uuid)
  }

  def hasAccessToCFPAdmin(uuid: String): Boolean = isMember(uuid, "cfp")

  def isSpeaker(uuid: String): Boolean = isMember(uuid, "speaker")

  def allSpeakers: List[Webuser] = Redis.pool.withClient {
    client =>
      val allSpeakerUUIDs= client.smembers("Webuser:speaker").toList
      client.hmget("Webuser", allSpeakerUUIDs).flatMap {
        js: String =>
          Json.parse(js).asOpt[Webuser]
      }
  }

  def allSpeakersAsOption: Seq[(String, String)] = {
    allSpeakers.map {
      webuser =>
        val cleanName = webuser.lastName.toLowerCase.capitalize + " " + webuser.firstName
        (webuser.uuid, cleanName)
    }.sortBy(tuple => tuple._2) // sort by label
  }

  def allSecondarySpeakersAsOption: Seq[(String, String)] = {
    allSpeakersAsOption.+:(DEFAULT_LABEL) // sort by name
  }

  def allCFPAdmin(): List[Webuser] = Redis.pool.withClient {
    client =>
      val uuids = client.smembers("Webuser:cfp").toList
      client.hmget("Webuser", uuids).flatMap {
        js: String =>
          Json.parse(js).asOpt[Webuser]
      }
  }

  def getEmailFromUUID(uuid:String):Option[String]=Redis.pool.withClient{
    client=>
      client.get("Webuser:UUID:"+uuid)
  }

  val DEFAULT_LABEL = ("", play.api.i18n.Messages("noOther.speaker"))
}


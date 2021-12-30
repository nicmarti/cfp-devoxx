/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2013 Association du Paris Java User Group.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package models

import library.Redis
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.lang3.{RandomStringUtils, StringUtils}
import org.joda.time.Instant
import play.api.i18n.Messages
import play.api.libs.Crypto
import play.api.libs.json.{Format, Json}

case class Webuser(uuid: String,
                   email: String,
                   firstName: String,
                   lastName: String,
                   password: String,
                   profile: String,
                   networkId: Option[String] = None,
                   networkType: Option[String] = None) {

  val cleanName: String = {
    firstName.capitalize + " " + lastName
  }
}

object Webuser {
  implicit val webuserFormat: Format[Webuser] = Json.format[Webuser]

  val Internal =
    Webuser("internal",
      ConferenceDescriptor.current().fromEmail,
      "CFP",
      "Program Committee",
      RandomStringUtils.random(64),
      "visitor",
      None,
      None)

  def gravatarHash(email: String): String = {
    val cleanEmail = email.trim().toLowerCase()
    DigestUtils.md5Hex(cleanEmail)
  }

  def generateUUID(email: String): String = {
    Crypto.sign(StringUtils.abbreviate(email.trim().toLowerCase, 255))
  }

  def createSpeaker(email: String,
                    firstName: String,
                    lastName: String): Webuser = {
    Webuser(generateUUID(email),
      email,
      firstName,
      lastName,
      RandomStringUtils.randomAlphabetic(7),
      "speaker",
      None,
      None)
  }

  def createVisitor(email: String,
                    firstName: String,
                    lastName: String): Webuser = {
    Webuser(generateUUID(email),
      email,
      firstName,
      lastName,
      RandomStringUtils.randomAlphabetic(7),
      "visitor",
      None,
      None)
  }

  def createDevoxxian(email: String,
                      networkType: Option[String],
                      networkId: Option[String]): Webuser = {
    val cleanEmail = StringUtils.trimToEmpty(email).toLowerCase
    Webuser(generateUUID(cleanEmail),
      cleanEmail,
      "Devoxx",
      "CFP",
      RandomStringUtils.randomAlphabetic(7),
      "devoxxian",
      networkId,
      networkType)
  }

  def unapplyForm(webuser: Webuser): Option[(String, String, String)] = {
    Some(webuser.email, webuser.firstName, webuser.lastName)
  }

  def getName(uuid: String): String = {
    findByUUID(uuid).map(_.cleanName).getOrElse("Anonymous " + uuid)
  }

  def saveNewWebuserEmailNotValidated(webuser: Webuser) = Redis.pool.withClient {
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

  def saveAndValidateWebuser(webuser: Webuser): String = Redis.pool.withClient {
    client =>
      val cleanEmail = StringUtils.trimToEmpty(webuser.email.toLowerCase)
      // This is a protection against external UUID that should not be set
      // It also means that we cannot change email for an existing Webuser.
      // A Webuser is an Entity where the ID = the clean email address
      val cleanUuid = generateUUID(cleanEmail)
      val cleanWebuser = webuser.copy(email = cleanEmail, uuid = cleanUuid)
      val json = Json.toJson(cleanWebuser).toString

      val tx = client.multi()
      tx.hset("Webuser", cleanWebuser.uuid, json)
      tx.set("Webuser:UUID:" + cleanWebuser.uuid, cleanWebuser.email)
      tx.set("Webuser:Email:" + cleanEmail, cleanWebuser.uuid)
      tx.sadd("Webuser:" + cleanWebuser.profile, cleanWebuser.uuid)
      tx.hdel("Webuser:New", cleanEmail)
      val now = new Instant().getMillis
      tx.zadd("Webuser:CreationDate", now ,cleanWebuser.uuid)
      tx.exec()
      cleanWebuser.uuid
  }

  def isEmailRegistered(email: String): Boolean = Redis.pool.withClient {
    implicit client =>
      val cleanEmail = StringUtils.trimToEmpty(email.toLowerCase)
      client.exists("Webuser:Email:" + cleanEmail)
  }

  def fixMissingEmail(email: String, uuid: String) = Redis.pool.withClient {
    implicit client =>
      val cleanEmail = StringUtils.trimToEmpty(email.toLowerCase)
      client.set("Webuser:Email:" + cleanEmail, uuid)
  }

  def findByEmail(email: String): Option[Webuser] = email match {
    case null => None
    case "" => None
    case validEmail =>
      val _email = StringUtils.trimToEmpty(validEmail.toLowerCase)
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

  def getUUIDfromEmail(email: String): Option[String] = Redis.pool.withClient {
    client =>
      val _email = StringUtils.trimToEmpty(email.toLowerCase)
      client.get("Webuser:Email:" + _email)
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
      val maybeUser = findByEmail(email)
      if (maybeUser.exists(_.password == password)) {
        maybeUser
      } else {
        None
      }
  }

  def findUser(email: String, password: String): Boolean = Redis.pool.withClient {
    client =>
      val maybeUser = findByEmail(email)
      maybeUser.exists(_.password == password)
  }

  // The My Devoxx Gluon mobile app will use the following hard coded credentials to basic authenticate.
  def gluonUser(email: String, password: String): Boolean = {
    email.equals("gluon@devoxx.com") &&
      password.equals(ConferenceDescriptor.gluonPassword())
  }

  def delete(webuser: Webuser) = Redis.pool.withClient {
    implicit client =>
      val cleanWebuser = webuser.copy(email = webuser.email.toLowerCase.trim)
      Proposal.allMyDraftProposals(cleanWebuser.uuid).foreach {
        proposal =>
          play.Logger.of("models.Webuser").debug(s"Deleting proposal $proposal")
          Proposal.destroy(proposal)
      }

      val tx = client.multi()
      tx.hdel("Webuser", cleanWebuser.uuid)
      tx.del("Webuser:UUID:" + cleanWebuser.uuid)
      tx.del("Webuser:Email:" + cleanWebuser.email)
      tx.srem("Webuser:" + cleanWebuser.profile, cleanWebuser.email)
      tx.hdel("Webuser:New", cleanWebuser.email)
      tx.srem("Webuser:speaker", cleanWebuser.uuid)
      tx.srem("Webuser:cfp", cleanWebuser.uuid)
      tx.srem("Webuser:admin", cleanWebuser.uuid)
      tx.srem("Webuser:visitor", cleanWebuser.uuid)
      tx.srem("Webuser:gticket", cleanWebuser.uuid)
      tx.srem("Webuser:devoxxian", cleanWebuser.uuid)
      tx.exec()

      TrackLeader.deleteWebuser(cleanWebuser.uuid)
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
          update(webuser.copy(firstName = newFirstName, lastName = newLastName))
      }
  }

  def update(webuser: Webuser) = Redis.pool.withClient {
    client =>
      val cleanWebuser = webuser.copy(email = webuser.email.toLowerCase.trim)
      val json = Json.stringify(Json.toJson(cleanWebuser))
      client.hset("Webuser", cleanWebuser.uuid, json)
      if (isSpeaker(cleanWebuser.uuid)) {
        Speaker.updateName(cleanWebuser.uuid, cleanWebuser.firstName, cleanWebuser.lastName)
      }
  }

  def isMember(uuid: String, securityGroup: String): Boolean = Redis.pool.withClient {
    client =>
      client.sismember("Webuser:" + securityGroup, uuid)
  }

  def isNotMember(uuid: String, securityGroup: String): Boolean = {
    !isMember(uuid, securityGroup)
  }

  def hasAccessToCFP(uuid: String): Boolean = isMember(uuid, "cfp")

  def hasAccessToAdmin(uuid: String): Boolean = isMember(uuid, "admin")

  def hasAccessToGoldenTicket(uuid: String): Boolean = isMember(uuid, "gticket")

  def isSpeaker(uuid: String): Boolean = isMember(uuid, "speaker")

  def addToCFPAdmin(uuid: String) = Redis.pool.withClient {
    client =>
      client.sadd("Webuser:cfp", uuid)
  }

  def addToSpeaker(uuid: String) = Redis.pool.withClient {
    client =>
      client.sadd("Webuser:speaker", uuid)
  }

  def addToDevoxxians(uuid: String) = Redis.pool.withClient {
    client =>
      client.sadd("Webuser:devoxxian", uuid)
  }

  def addToGoldenTicket(uuid: String) = Redis.pool.withClient {
    client =>
      client.sadd("Webuser:gticket", uuid)
  }

  def removeFromGoldenTicket(uuid: String) = Redis.pool.withClient {
    client =>
      client.srem("Webuser:gticket", uuid)

      play.Logger.info(s"${Messages("cfp.goldenTicket")} reviewer $uuid has been deleted from the ${Messages("cfp.goldenTicket")} reviewers list.")
  }

  def noBackofficeAdmin() = Redis.pool.withClient {
    client =>
      !client.exists("Webuser:admin")
  }

  def addToBackofficeAdmin(uuid: String) = Redis.pool.withClient {
    client =>
      client.sadd("Webuser:admin", uuid)
  }

  def removeFromCFPAdmin(uuid: String) = Redis.pool.withClient {
    client =>
      client.srem("Webuser:cfp", uuid)
  }

  def allSpeakers: List[Webuser] = Redis.pool.withClient {
    client =>
      val allSpeakerUUIDs = client.smembers("Webuser:speaker").toList
      client.hmget("Webuser", allSpeakerUUIDs).flatMap {
        js: String =>
          Json.parse(js).validate[Webuser].fold(
            invalid => {
              play.Logger.warn("Invalid Speaker with uuid " + js)
              None
            }, w => Option(w)
          )
      }
  }

  def allDevoxxians(): List[Webuser] = Redis.pool.withClient {
    client =>
      val allDevoxxianUUIDs = client.smembers("Webuser:devoxxian").toList
      client.hmget("Webuser", allDevoxxianUUIDs).flatMap {
        js: String =>
          Json.parse(js).asOpt[Webuser]
      }
  }

  def allWebusers: Map[String, Option[Webuser]] = Redis.pool.withClient {
    client =>
      client.hgetAll("Webuser").map {
        case (key: String, valueJson: String) =>
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

  def getEmailFromUUID(uuid: String): Option[String] = Redis.pool.withClient {
    client =>
      client.get("Webuser:UUID:" + uuid)
  }

  // Instead of doing a read then a write on a value, we do a single write redis atomic op
  def updatePublicVisibility(uuid:String) = Redis.pool.withClient{
    client =>
      client.incr(s"Webuser:Publisher:IsVisible:${uuid}")
  }

  // If the key exists and the modulo 2 returns 0 then show the user
  def isPublicVisible(uuid:String) = Redis.pool.withClient{
    client =>
      val score = client.get(s"Webuser:Publisher:IsVisible:${uuid}")
      score match {
        case None => true
        case Some(x) if x.toInt > 0 => (x.toInt % 2) == 0
        case _  => false
      }
  }

  val DEFAULT_LABEL: (String, String) = ("", play.api.i18n.Messages("noOther.speaker"))

  def doesNotExist(uuid: String): Boolean = Redis.pool.withClient {
    client =>
      !client.exists("Webuser:UUID:" + uuid)
  }
}
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
import play.api.libs.Crypto
import play.api.libs.json.Json

case class Webuser(uuid: String, email: String, firstName: String, lastName: String, password: String, profile: String) {
  val cleanName = {
    firstName.capitalize + " " + lastName
  }
}

object Webuser {
  implicit val webuserFormat = Json.format[Webuser]

  val Internal=Webuser("internal",ConferenceDescriptor.current().fromEmail,"CFP","Program Committee",RandomStringUtils.random(64),"visitor")

  def gravatarHash(email: String): String = {
    val cleanEmail = email.trim().toLowerCase()
    DigestUtils.md5Hex(cleanEmail)
  }

  def generateUUID(email: String): String = {
    Crypto.sign(StringUtils.abbreviate(email.trim().toLowerCase, 255))
  }

  def createSpeaker(email: String, firstName: String, lastName: String): Webuser = {
    Webuser(generateUUID(email), email, firstName, lastName, RandomStringUtils.randomAlphabetic(7), "speaker")
  }

  def createVisitor(email: String, firstName: String, lastName: String): Webuser = {
    Webuser(generateUUID(email), email, firstName, lastName, RandomStringUtils.randomAlphabetic(7), "visitor")
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
      val cleanWebuser = webuser.copy(email = webuser.email.toLowerCase.trim)
      val json = Json.toJson(cleanWebuser).toString

      val tx = client.multi()
      tx.hset("Webuser", cleanWebuser.uuid, json)
      tx.set("Webuser:UUID:" + cleanWebuser.uuid, webuser.email)
      tx.set("Webuser:Email:" + cleanWebuser.email, webuser.uuid)
      tx.sadd("Webuser:" + cleanWebuser.profile, webuser.uuid)
      tx.hdel("Webuser:New", cleanWebuser.email)
      tx.exec()
      cleanWebuser.uuid
  }

  def isEmailRegistered(email: String): Boolean = Redis.pool.withClient {
    implicit client =>
      client.exists("Webuser:Email:" + email.toLowerCase.trim)
  }

  def findByEmail(email: String): Option[Webuser] = email match {
    case null => None
    case "" => None
    case validEmail => {
      val _email = validEmail.toLowerCase.trim
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

  def getUUIDfromEmail(email: String): Option[String] = Redis.pool.withClient {
    client =>
      client.get("Webuser:Email:" + email.toLowerCase.trim)
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
      tx.srem("Webuser:speaker", cleanWebuser.uuid)
      tx.srem("Webuser:cfp", cleanWebuser.uuid)
      tx.srem("Webuser:admin", cleanWebuser.uuid)
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
      !isMember(uuid,securityGroup)
    }

  def hasAccessToCFP(uuid: String): Boolean = isMember(uuid, "cfp")

  def hasAccessToAdmin(uuid: String): Boolean = isMember(uuid, "admin")

  def hasAccessToGoldenTicket(uuid: String): Boolean = isMember(uuid, "gticket")

  def isSpeaker(uuid: String): Boolean = isMember(uuid, "speaker")

  def addToCFPAdmin(uuid: String) = Redis.pool.withClient {
    client =>
      client.sadd("Webuser:cfp", uuid)
  }

  def addToGoldenTicket(uuid: String) = Redis.pool.withClient {
    client =>
      client.sadd("Webuser:gticket", uuid)
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

  /* Required for helper.options */
  def allCFPAdminUsers(): Seq[(String, String)] = {
    val cfpUsers = Webuser.allCFPWebusers().sortBy(_.cleanName)
    val cfpUsersAndTracks = cfpUsers.toSeq.flatMap {
      w: Webuser =>
        Seq((w.uuid, w.cleanName))
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

package models

import play.api.libs.json.Json
import org.apache.commons.codec.digest.DigestUtils
import play.api.data.format.Formats._
import library.Redis
import org.apache.commons.lang3.RandomStringUtils

case class Webuser(email: String, firstName: String, lastName: String, password: String, profile: String) {
  def gravatarHash: String = {
    val cleanEmail = email.trim().toLowerCase()
    DigestUtils.md5Hex(cleanEmail)
  }
}

object Webuser {
  implicit val webuserFormat = Json.format[Webuser]

  def createSpeaker(email: String, firstName: String, lastName: String): Webuser = {
    Webuser(email, firstName, lastName, RandomStringUtils.randomAlphabetic(7), "speaker")
  }

  def unapplyForm(webuser: Webuser): Option[(String, String, String)] = {
    Some(webuser.email, webuser.firstName, webuser.lastName)
  }

  def createAdmin(email: String, firstName: String, lastName: String): Webuser = {
    Webuser(email, firstName, lastName, RandomStringUtils.randomAlphabetic(7), "admin")
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
      tx.hset("Webuser", webuser.email, json)
      tx.sadd("Webuser:" + webuser.profile, webuser.email)
      tx.hdel("Webuser:New", webuser.email)
      tx.exec()
  }

  def findByEmail(email: String): Option[Webuser] = Redis.pool.withClient {
    client =>
      client.hget("Webuser", email.toLowerCase.trim).map {
        json: String =>
          Json.parse(json).as[Webuser]
      }
  }

  def checkPassword(email: String, password: String): Boolean = Redis.pool.withClient {
    client =>
      findByEmail(email).exists(_.password == password)
  }

  def delete(webuser: Webuser) = Redis.pool.withClient {
    client =>
      val tx=client.multi()
      tx.hdel("Webuser", webuser.email)
      tx.srem("Webuser:"+webuser.profile, webuser.email)
      tx.exec()
  }

  def changePassword(webuser: Webuser): String = Redis.pool.withClient {
    client =>
      val newPassword = RandomStringUtils.randomAlphabetic(7)
      val updatedWebuser = webuser.copy(password = newPassword)
      update(updatedWebuser)
      newPassword
  }

  def updateNames(email: String, newFirstName: String, newLastName: String) = Redis.pool.withClient {
    client =>
      findByEmail(email).map{
        webuser=>
          update(webuser.copy(firstName=newFirstName.toLowerCase.capitalize, lastName=newLastName.toLowerCase.capitalize))
      }
  }

  def update(webuser:Webuser)=Redis.pool.withClient{
    client=>
      val cleanWebuser = webuser.copy(email = webuser.email.toLowerCase.trim)
      val json = Json.stringify(Json.toJson(cleanWebuser))
      client.hset("Webuser", webuser.email, json)
  }

  def isMember(email: String, securityGroup: String): Boolean = Redis.pool.withClient{
    client=>
      client.sismember("Webuser:"+securityGroup, email)
  }

  def allSpeakers:List[Webuser] = Redis.pool.withClient{
    client=>
      val allSpeakerEmails = client.smembers("Webuser:speaker").toList
      client.hmget("Webuser", allSpeakerEmails).flatMap{ js:String=>
          Json.parse(js).asOpt[Webuser]
      }
  }

  def allSpeakersAsOption:Seq[(String,String)]={
    allSpeakers.map{ webuser=>
      val cleanName = webuser.lastName.toLowerCase.capitalize+ " " +webuser.firstName
      (webuser.email, cleanName)
    }
  }

}


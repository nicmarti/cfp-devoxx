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

  def save(webuser: Webuser)=Redis.pool.withClient {
    client =>
     println("save webuser")
  }

  def saveAndValidate(webuser: Webuser)= Redis.pool.withClient {
    client =>
      webuser.copy(profile="speaker")
      println("Save speaker")
  }

  def findByEmail(email: String): Option[Webuser] =Redis.pool.withClient {
    client =>
    None
  }

  def checkPassword(email: String, password: String): Boolean = Redis.pool.withClient{
    client=>
      true
  }

  def delete(webuser: Webuser) = Redis.pool.withClient {
    client =>
      true
  }

  def changePassword(webuser: Webuser): String = Redis.pool.withClient {
    client =>
      val newPassword = RandomStringUtils.randomAlphabetic(7)
      newPassword
  }

  def validateEmail(webuser: Webuser): String = Redis.pool.withClient {
    client =>
      val newPassword = RandomStringUtils.randomAlphabetic(7)
      newPassword
  }

  def update(email:String, firstName:String, lastName:String)=Redis.pool.withClient{
   client=>
  }

  def isMember(email: String, securityGroup: String): Boolean = {
    true
  }

}


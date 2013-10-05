package models

import org.apache.commons.lang3.RandomStringUtils
import reactivemongo.api.Cursor
import play.api.libs.json.Json
import scala.concurrent.ExecutionContext
import library.MongoDB
import reactivemongo.core.commands.LastError
import ExecutionContext.Implicits.global
import reactivemongo.api.indexes.{IndexType, Index}
import play.modules.reactivemongo.json.BSONFormats._
import org.apache.commons.codec.digest.DigestUtils
import reactivemongo.bson._
import play.api.data.format.Formats._
import scala.concurrent._
import scala.concurrent.duration._

case class Webuser(id: Option[BSONObjectID], email: String, firstName: String, lastName: String, password: String, profile: String) {
  def gravatarHash: String = {
    val cleanEmail = email.trim().toLowerCase()
    DigestUtils.md5Hex(cleanEmail)
  }
}

object Webuser {
  implicit val webuserFormat = Json.format[Webuser]

  implicit object WebuserBSONReader extends BSONDocumentReader[Webuser] {
    def read(doc: BSONDocument): Webuser =
      Webuser(
        doc.getAs[BSONObjectID]("_id"),
        doc.getAs[String]("email").get,
        doc.getAs[String]("firstName").get,
        doc.getAs[String]("lastName").get,
        doc.getAs[String]("password").get,
        doc.getAs[String]("profile").get
      )
  }

  implicit object WebuserBSONWriter extends BSONDocumentWriter[Webuser] {
    def write(webuser: Webuser): BSONDocument =
      BSONDocument(
        "_id" -> webuser.id.getOrElse(BSONObjectID.generate),
        "email" -> webuser.email,
        "firstName" -> webuser.firstName,
        "lastName" -> webuser.lastName,
        "password" -> webuser.password,
        "profile" -> webuser.profile
      )
  }

  def createSpeaker(email: String, firstName: String, lastName: String): Webuser = {
    Webuser(None, email, firstName, lastName, RandomStringUtils.randomAlphabetic(7), "speaker")
  }

  def unapplyForm(webuser: Webuser): Option[(String, String, String)] = {
    Some(webuser.email, webuser.firstName, webuser.lastName)
  }

  def createAdmin(email: String, firstName: String, lastName: String): Webuser = {
    Webuser(None, email, firstName, lastName, RandomStringUtils.randomAlphabetic(7), "admin")
  }

  def save(webuser: Webuser): Future[LastError] = MongoDB.withCollection("webuser") {
    implicit collection =>
      val result = if (webuser.id.isEmpty) {
        // Check index
        collection.indexesManager.ensure(Index(List("email" -> IndexType.Ascending), unique = true, dropDups = true))
        collection.indexesManager.ensure(Index(List("email" -> IndexType.Ascending, "password" -> IndexType.Ascending), name = Some("idx_password")))
        collection.insert(webuser.copy(id = Some(BSONObjectID.generate), profile = "notvalidated"))

      } else {
        collection.insert(webuser)
      }
      result
  }

  def findByEmail(email: String): Future[Option[Webuser]] = MongoDB.withCollection("webuser") {
    implicit collection =>
      val cursor: Cursor[Webuser] = collection.find(Json.obj("email" -> email)).cursor[Webuser]
      cursor.headOption()
  }

  def checkPassword(email: String, password: String): Future[Boolean] = MongoDB.withCollection("webuser") {
    implicit collection =>
      val cursor: Cursor[Webuser] = collection.find(Json.obj("email" -> email, "password" -> password)).cursor[Webuser]
      cursor.headOption().map(_.isDefined)
  }

  def delete(webuser: Webuser) = MongoDB.withCollection("webuser") {
    implicit collection =>
      collection.remove[Webuser](webuser)
  }

  def isMember(email: String, securityGroup: String):Boolean = {
    val futureResult = findByEmail(email).map{
      case None=>{
        println("user not found")
        false
      }
      case Some(webuser)=>webuser.profile==securityGroup
    }
    Await.result[Boolean](futureResult, 10 seconds)
  }

  def changePassword(webuser:Webuser):String=MongoDB.withCollection("webuser"){
    implicit collection=>
      val newPassword=RandomStringUtils.randomAlphabetic(7)
      collection.update(Json.obj("email" -> webuser.email), Json.obj("$set" -> Json.obj("password" -> newPassword)), upsert = false)
      newPassword
  }

  def validateEmail(webuser:Webuser):String=MongoDB.withCollection("webuser"){
      implicit collection=>
        val newPassword=RandomStringUtils.randomAlphabetic(7)
        collection.update(Json.obj("email" -> webuser.email), Json.obj("$set" -> Json.obj("profile" -> "speaker")), upsert = false)
        newPassword
    }
}


package models

import org.apache.commons.lang3.RandomStringUtils
import reactivemongo.api.Cursor
import play.api.libs.json.Json
import scala.concurrent.{ExecutionContext, Future}
import library.MongoDB
import reactivemongo.core.commands.LastError
import ExecutionContext.Implicits.global
import reactivemongo.api.indexes.{IndexType, Index}
import reactivemongo.bson.BSONObjectID
import play.modules.reactivemongo.json.BSONFormats._
import org.apache.commons.codec.digest.DigestUtils

case class Webuser(id: Option[BSONObjectID], email: String, firstName: String, lastName: String, password: String, profile: String){
  def gravatarHash:String={
    val cleanEmail=email.trim().toLowerCase()
    DigestUtils.md5Hex(cleanEmail)
  }
}

object Webuser {
  implicit val webuserFormat = Json.format[Webuser]

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
    // Check index
      collection.indexesManager.ensure(Index(List("email" -> IndexType.Ascending), unique = true, dropDups = true))
      collection.indexesManager.ensure(Index(List("email" -> IndexType.Ascending, "password" -> IndexType.Ascending), name = Some("idx_password")))
      val result = collection.insert(webuser)
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

}


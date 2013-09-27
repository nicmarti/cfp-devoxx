package models

import org.apache.commons.lang3.RandomStringUtils
import reactivemongo.api.Cursor
import play.api.libs.json.Json
import scala.concurrent.{ExecutionContext, Future}
import library.MongoDB
import reactivemongo.core.commands.LastError
import ExecutionContext.Implicits.global
import reactivemongo.api.indexes.{IndexType, Index}
import play.api.Logger

case class Webuser(email: String, firstName: String, lastName: String, password: String, profile: String)

object Webuser {
  implicit val webuserFormat = Json.format[Webuser]

  def createSpeaker(email: String, firstName: String, lastName: String):Webuser = {
    Webuser(email, firstName, lastName, RandomStringUtils.randomAlphabetic(7), "speaker")
  }

  def unapplyForm(webuser:Webuser):Option[(String, String, String)]={
    Some(webuser.email, webuser.firstName, webuser.lastName)
  }

  def createAdmin(email: String, firstName: String, lastName: String):Webuser = {
    Webuser(email, firstName, lastName, RandomStringUtils.randomAlphabetic(7), "admin")
  }

  def save(webuser:Webuser):Future[LastError]=MongoDB.withCollection("webuser"){
    implicit collection=>
      // Check index
      collection.indexesManager.ensure(Index(List("email" -> IndexType.Ascending), unique = true))
      val result = collection.insert(webuser)
      result
  }

  def findByEmail(email: String): Future[List[Webuser]] = MongoDB.withCollection("webuser") {
    implicit collection =>
      val cursor: Cursor[Webuser] = collection.find(Json.obj("email" -> email)).sort(Json.obj("lastName" -> -1)).cursor[Webuser]
      cursor.toList
  }

  def checkPassword(email:String, password:String):Future[Boolean]=MongoDB.withCollection("webuser"){
    implicit collection=>
      val cursor: Cursor[Webuser] = collection.find(Json.obj("email" -> email, "password"->password)).cursor[Webuser]
      cursor.headOption().map(_.isDefined)

  }

}


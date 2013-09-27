package models

import org.apache.commons.lang3.RandomStringUtils
import reactivemongo.api.Cursor
import play.api.libs.json.Json
import scala.concurrent.{ExecutionContext, Future}
import library.MongoDB
import reactivemongo.core.commands.LastError
import ExecutionContext.Implicits.global

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

  def save(webuser:Webuser):Future[LastError]=MongoDB.withCollection{
    implicit collection=>
      collection.insert(webuser)
  }

  def findByEmail(email: String): Future[List[Webuser]] = MongoDB.withCollection {
    implicit collection =>
      val cursor: Cursor[Webuser] = collection.
        find(Json.obj("email" -> email)).
        sort(Json.obj("lastName" -> -1)).
        cursor[Webuser]
      cursor.toList
  }

}


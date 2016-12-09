package models

import library.Redis
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json.Json

/**
  * Proposal tags
  *
  * @author Stephan Janssen
  */
case class Tag(uuid: String, value: String) { }

object Tag {

  implicit val tagFormat = Json.format[Tag]

  private val tags = "Tags"

  val tagForm = Form(mapping(
    "id" -> optional(text),
    "value" -> text
  )(validateNewTag)(unapplyTagForm))

  def validateNewTag(
    id: Option[String],
    value: String): Tag = {
    Tag (
      id.getOrElse(generateUUID(value)),
      value
    )
  }

  def unapplyTagForm(tag: Tag): Option[(Option[String], String)] = {
    Option(
      Option(tag.uuid),
      tag.value)
  }

  def generateUUID(value:String): String = {
    value.toLowerCase.trim.hashCode.toString
  }

  def createTag(value: String): Tag = {
    Tag(generateUUID(value), value)
  }

  def save(newTag: Tag) = Redis.pool.withClient {
    client =>
      client.hset(tags, newTag.uuid, newTag.value)
  }

  def findByUUID(uuid: String): Option[Tag] = Redis.pool.withClient {
    client =>
      client.hget(tags, uuid).map {
        value => createTag(value)
      }
  }

  def findTagById(uuid : String): Option[String] = Redis.pool.withClient {
    client => {
      client.hget(tags, uuid)
    }
  }

  def delete(uuid: String) = Redis.pool.withClient {
    client =>
      client.hdel(tags, uuid)
  }

  def allTags(): List[Tag] = Redis.pool.withClient {
    client =>
      client.hvals(tags).map(
        value => createTag(value)
      )
  }

  def isNew(value: String): Boolean = Redis.pool.withClient {
    client =>
      // Important when we create a new tag
      !client.hexists(tags, generateUUID(value))
  }
}

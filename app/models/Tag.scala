package models

import java.lang.Long

import library.Redis
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json.{Format, Json}

/**
  * Proposal tags
  *
  * @author Stephan Janssen
  */
case class Tag(id: String, value: String)

object Tag {

  implicit val tagFormat: Format[Tag] = Json.format[Tag]

  private val tags = "Tags"

  val tagForm = Form(mapping(
    "id" -> optional(text),
    "value" -> text
  )(validateNewTag)(unapplyTagForm))

  def validateNewTag(
    id: Option[String],
    value: String): Tag = {
    Tag (
      id.getOrElse(generateID(value)),
      value
    )
  }

  def unapplyTagForm(tag: Tag): Option[(Option[String], String)] = {
    Option(
      Option(tag.id),
      tag.value)
  }

  def generateID(value:String): String = {
    value.toLowerCase.trim.hashCode.toString
  }

  def createTag(value: String): Tag = {
    Tag(generateID(value), value)
  }

  def save(newTag: Tag): Long = Redis.pool.withClient {
    client =>
      client.hset(tags, newTag.id, newTag.value)
  }

  def doesTagValueExist(value : String) : Boolean = Redis.pool.withClient {
    client =>
      client.hvals(tags).exists(tag => tag.toLowerCase.equals(value.toLowerCase()))
  }

  def findById(id: String): Option[Tag] = Redis.pool.withClient {
    client =>
      client.hget(tags, id).map {
        value => createTag(value)
      }
  }

  def findTagValueById(id : String): Option[String] = Redis.pool.withClient {
    client => {
      client.hget(tags, id)
    }
  }

  def delete(id: String): Long = Redis.pool.withClient {
    client =>
      client.hdel(tags, id)
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
      !client.hexists(tags, generateID(value))
  }
}

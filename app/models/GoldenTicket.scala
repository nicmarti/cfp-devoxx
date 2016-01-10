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
import org.apache.commons.lang3.RandomStringUtils
import play.api.libs.json.Json

/**
  *
  * @author created by N.Martignole, Innoteria, on 16/11/2015.
  */
case class GoldenTicket(id: String, ticketId: String, webuserUUID: String, ticketType: String)

object GoldenTicket {
  val GD_TICKET = "GoldenTicket:2016"

  implicit val goldenTicketFormat = Json.format[GoldenTicket]

  def generateId(): String = {
    RandomStringUtils.randomAlphabetic(6).toUpperCase + "-" + RandomStringUtils.randomNumeric(5)
  }

  def createGoldenTicket(ticketId: String, firstName: String, lastName: String, email: String, ticketType: String): GoldenTicket = {
    val uuid = if (Webuser.isEmailRegistered(email)) {
      // An existing speaker might have been created with another security key
      // Thus the Webuser uuid will be different and we need to search by email
      // we should call generateUUID only if the user is not in the Email collection
      Webuser.getUUIDfromEmail(email).getOrElse(Webuser.generateUUID(email))
    } else {
      val webuser = createWebuser(email, firstName, lastName)
      Webuser.saveAndValidateWebuser(webuser)
    }

    Webuser.addToGoldenTicket(uuid)
    val id = generateId()
    GoldenTicket(id, ticketId, uuid, ticketType)
  }

  def unapplyForm(gt: GoldenTicket): Option[(String, String, String, String, String)] = {
    Webuser.findByUUID(gt.webuserUUID).map {
      webuser: Webuser =>
        (gt.ticketId, webuser.firstName, webuser.lastName, webuser.email, gt.ticketType)
    }
  }

  def all(): List[GoldenTicket] = Redis.pool.withClient {
    implicit client =>
      client.hvals(GD_TICKET).map {
        json: String =>
          Json.parse(json).as[GoldenTicket]
      }
  }

  def allWithWebuser(): List[(GoldenTicket, Webuser)] = {
    all().flatMap {
      goldenTicket: GoldenTicket =>
        Webuser.findByUUID(goldenTicket.webuserUUID).map {
          webuser =>
            (goldenTicket, webuser)
        }
    }
  }

  def save(gd: GoldenTicket) = Redis.pool.withClient {
    implicit client =>
      val json: String = Json.toJson(gd).toString()
      val tx = client.multi()
      tx.hset(GD_TICKET, gd.id, json)
      tx.sadd(GD_TICKET + ":UniqueUser", gd.webuserUUID)
      tx.exec()
  }

  def findById(id: String): Option[GoldenTicket] = Redis.pool.withClient {
    implicit client =>
      client.hget(GD_TICKET, id).map {
        json: String =>
          Json.parse(json).as[GoldenTicket]
      }
  }

  def size(): Long = Redis.pool.withClient {
    implicit client =>
      client.hlen(GD_TICKET)
  }

  def delete(id: String) = Redis.pool.withClient {
    implicit client =>
      findById(id).map {
        ticket =>
          client.srem(GD_TICKET + ":UniqueUser", ticket.webuserUUID)
      }
      client.hdel(GD_TICKET, id)
  }

  def hasTicket(webuserUUID: String): Boolean = Redis.pool.withClient {
    implicit client =>
      client.sismember(GD_TICKET + ":UniqueUser", webuserUUID)
  }

  private def createWebuser(email: String, firstName: String, lastName: String): Webuser = {
    Webuser(Webuser.generateUUID(email.toLowerCase.trim), email.toLowerCase.trim, firstName, lastName, RandomStringUtils.randomAlphabetic(8), "gticket")
  }

}

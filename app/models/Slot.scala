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

import org.joda.time.DateTime
import play.api.libs.json.Json
import play.api.libs.json._
import play.api.libs.functional.syntax._

/**
 * Time slots, specific of course to Devoxx France 2014.
 * Instead of using a database, it's way simpler to define once for all everything as scala.
 * Trust me.
 * Created by nicolas on 01/02/2014.
 */

case class Room(id: String, name: String, capacity: Int, setup: String)

object Room {
  implicit val roomFormat = Json.format[Room]

  val OTHER = Room("other_room", "Other room", 100, "sans objet")

  val all = ConferenceDescriptor.current().rooms

  val allAsId = all.map(a => (a.id, a.name)).toSeq.sorted

  def parse(roomId: String): Room = {
    return all.find(r => r.id == roomId).getOrElse(OTHER)
  }

  // Small helper function that is specific to devoxxfr2014
  def idBVRent(room:Room):String={
    room.id match {
      case "seine_keynote" => "K"
      case "seine_a" => "A"
      case "seine_b" => "B"
      case "seine_c" => "C"
      case "auditorium" => "AU"
      case "el_ab" => "EL"
      case "el_ab_th" => "EL"
      case "el_ab_full" => "EL"
      case "la_ab" => "LA"
      case "la_ab_th" => "LA"
      case "la_cd" => "LA"
      case "la_cd_th" => "LA"
      case "md_a" => "MD"
      case "md_a_th" => "MD"
      case "md_b" => "MD"
      case "md_b_th" => "MD"
      case "md_c" => "MD"
      case "md_c_th" => "MD"
      case "md_full" => "MD"
      case other => "??"+other
    }
  }
}

case class SlotBreak(id: String, nameEN: String, nameFR: String, room: Room)
object SlotBreak {
  implicit val slotBreakFormat = Json.format[SlotBreak]
}

case class Slot(id: String, name: String, day: String, from: DateTime, to: DateTime, room: Room,
                proposal: Option[Proposal], break: Option[SlotBreak]) {
  override def toString: String = {
    s"Slot[" + id + "]"
  }

  def notAllocated: Boolean = {
    break.isEmpty && proposal.isEmpty
  }

  def idForBVRent:String={
    from.getDayOfMonth+"-"+ name.head.toUpper+"-" + Room.idBVRent(room)
  }

}

object SlotBuilder {

  def apply(name: String, day: String, from: DateTime, to: DateTime, room: Room): Slot = {
    val id = name + "_" + room.id + "_" + day + "_" + from.getDayOfMonth + "_" + from.getHourOfDay + "h" + from.getMinuteOfHour + "_" + to.getHourOfDay + "h" + to.getMinuteOfHour
    Slot(id, name, day, from, to, room, None, None)
  }

  def apply(name: String, day: String, from: DateTime, to: DateTime, room: Room, proposal: Option[Proposal]): Slot = {
    val id = name + "_" + room.id + "_" + day + "_" + from.getDayOfMonth + "_" + from.getHourOfDay + "h" + from.getMinuteOfHour + "_" + to.getHourOfDay + "h" + to.getMinuteOfHour
    Slot(id, name, day, from, to, room, proposal, None)
  }

  def apply(slotBreak: SlotBreak, day: String, from: DateTime, to: DateTime): Slot = {
    val id = slotBreak.id + "_" + day + "_" + from.getDayOfMonth + "_" + from.getHourOfDay + "h" + from.getMinuteOfHour + "_" + to.getHourOfDay + "h" + to.getMinuteOfHour
    Slot(id, slotBreak.nameEN, day, from, to, slotBreak.room, None, Some(slotBreak))
  }
}

// See https://groups.google.com/forum/#!topic/play-framework/ENlcpDzLZo8
object Slot {
  implicit val slotFormat = Json.format[Slot]

  val all = ConferenceDescriptor.current().slots

  def byType(proposalType: ProposalType): Seq[Slot] = {
    all.filter(s => s.name == proposalType.id)
  }
}
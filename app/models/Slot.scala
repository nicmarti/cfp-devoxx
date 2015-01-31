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
 * Time slots and Room are defined as static file.
 * Instead of using a database, it's way simpler to define once for all everything as scala.
 * Trust me.
 * Created by Nicolas Nartignole on 01/02/2014.
 * Frederic Camblor added ConferenceDescriptor 07/06/2014
 */

case class Room(id: String, name: String, capacity: Int, setup: String)

object Room {
  implicit val roomFormat = Json.format[Room]

  val OTHER = Room("other_room", "Other room", 100, "sans objet")

  val allAsId = ConferenceDescriptor.ConferenceRooms.allRooms.map(a => (a.id, a.name)).toSeq.sorted

  def parse(roomId: String): Room = {
    ConferenceDescriptor.ConferenceRooms.allRooms.find(r => r.id == roomId).getOrElse(OTHER)
  }

}

case class SlotBreak(id: String, nameEN: String, nameFR: String, room: Room)

object SlotBreak {
  implicit val slotBreakFormat = Json.format[SlotBreak]
}

case class Slot(id: String, name: String, day: String, from: DateTime, to: DateTime, room: Room,
                proposal: Option[Proposal], break: Option[SlotBreak]) {
  override def toString: String = {
    s"Slot[$id] hasProposal=${proposal.isDefined} isBreak=${break.isDefined}"
  }

  def notAllocated: Boolean = {
    break.isEmpty && proposal.isEmpty
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

  def byType(proposalType: ProposalType): Seq[Slot] = {
    ConferenceDescriptor.ConferenceSlots.all.filter(s => s.name == proposalType.id)
  }
}
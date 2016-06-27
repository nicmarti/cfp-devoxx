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

/**
 * Time slots and Room are defined as static file.
 * Instead of using a database, it's way simpler to define once for all everything as scala.
 * Trust me.
 * Created by Nicolas Nartignole on 01/02/2014.
 * Frederic Camblor added ConferenceDescriptor 07/06/2014
 */

case class Room(id: String, name: String, capacity: Int, setup: String, recorded:String) extends Ordered[Room] {

  def index: Int = {
    val regexp = "[\\D\\s]+(\\d+)".r
    id match {
      case regexp(x) => x.toInt
      case _ => 0
    }
  }

  def compare(that: Room): Int = {
    // TODO a virer apres Devoxx FR 2016
    // Hack for Devoxx France => I cannot change the Room IDs so I fix the order in an IndexedSeq here
    if(Room.fixedOrderForRoom.indexOf(this.id) < Room.fixedOrderForRoom.indexOf(that.id)){
      return -1
    }
if(Room.fixedOrderForRoom.indexOf(this.id) > Room.fixedOrderForRoom.indexOf(that.id)){
      return 1
    }
    return 0
  }
}

object Room {
  implicit val roomFormat = Json.format[Room]

  val OTHER = Room("other_room", "Other room", 100, "sans objet","")

  val allAsId = ConferenceDescriptor.ConferenceRooms.allRooms.map(a => (a.id, a.name)).toSeq.sorted

  def parse(roomId: String): Room = {
    ConferenceDescriptor.ConferenceRooms.allRooms.find(r => r.id == roomId).getOrElse(OTHER)
  }

  // TODO à virer apres Devoxx FR 2016
  val fixedOrderForRoom = IndexedSeq("a_hall",
    "b_amphi",
    "c_maillot",
    "d_par241",
    "f_neu251",
    "e_neu252",
    "par242AB",
    "par242A",
    "par242AT",
    "par242B",
    "par242BT",
    "par243",
    "neu253",
    "neu253_t",
    "par243_t",
    "par201",
    "par202_203",
    "par204",
    "par221M-222M",
    "par224M-225M",
    "neu_232_232",
    "neu_234_235",
    "neu_212_213",
    "x_hall_a"
  )

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

  def parleysId: String = {
    ConferenceDescriptor.current().eventCode + "_" + from.toString("dd") + "_" + room.id + "_" + from.toString("HHmm")
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
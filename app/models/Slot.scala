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

import models.ConferenceDescriptor.{ConferenceProposalTypes, ConferenceRooms}
import org.joda.time.DateTime
import play.api.libs.json.Json

import scala.collection.mutable.ListBuffer

/**
  * Time slots and Room are defined as static file.
  * Instead of using a database, it's way simpler to define once for all everything as scala.
  * Trust me.
  * Created by Nicolas Nartignole on 01/02/2014.
  * Frederic Camblor added ConferenceDescriptor 07/06/2014
  */

case class Room(id: String, name: String, capacity: Int, setup: String, recorded: String, scheduleMainTitle: String, scheduleSecondaryTitle: Option[String]) extends Ordered[Room] {

  def index: Int = {
    val regexp = "[\\D\\s]+(\\d+)".r
    id match {
      case regexp(x) => x.toInt
      case _ => 0
    }
  }

  def compare(that: Room): Int = {
    // Hack for Devoxx France => I cannot change the Room IDs so I fix the order in an IndexedSeq here
    if (Room.fixedOrderForRoom.indexOf(this.id) < Room.fixedOrderForRoom.indexOf(that.id)) {
      return -1
    }
    if (Room.fixedOrderForRoom.indexOf(this.id) > Room.fixedOrderForRoom.indexOf(that.id)) {
      return 1
    }
    return 0
  }

  def cssId: String = id.replace("-", "_")
}

object Room {
  implicit val roomFormat = Json.format[Room]

  val OTHER = Room("other_room", "Other room", 100, "sans objet", "", "", None)

  val allAsId = ConferenceDescriptor.ConferenceRooms.allRooms.map(a => (a.id, a.name)).toSeq.sorted

  def parse(roomId: String): Room = {
    ConferenceDescriptor.ConferenceRooms.allRooms.find(r => r.id == roomId).getOrElse(OTHER)
  }

  val fixedOrderForRoom = IndexedSeq("a_hall",
    "lobby_neuilly",
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

case class SlotBreak(id: String, nameEN: String, nameFR: String)

object SlotBreak {
  implicit val slotBreakFormat = Json.format[SlotBreak]
}

case class SlotWithRoom(id: String, name: String, day: String, from: DateTime, to: DateTime, room: Room,
                   proposal: Option[Proposal], break: Option[SlotBreak], fillerForSlotId: Option[String]) {
  def toRawSlot(): Slot = {
    Slot(id, name, day, from, to, room.id, proposal, break, fillerForSlotId)
  }
}

case class Slot(id: String, name: String, day: String, from: DateTime, to: DateTime, roomId: String,
                proposal: Option[Proposal], break: Option[SlotBreak], fillerForSlotId: Option[String]) {
  override def toString: String = {
    s"Slot[$id] hasProposal=${proposal.isDefined} isBreak=${break.isDefined} isFiller=${isFiller}"
  }

  def parleysId: String = {
    ConferenceDescriptor.current().eventCode + "_" + from.toString("dd") + "_" + roomId + "_" + from.toString("HHmm")
  }

  def notAllocated: Boolean = {
    break.isEmpty && proposal.isEmpty
  }

  def isFiller: Boolean = { fillerForSlotId.isDefined }

  def room: Room = Room.parse(roomId)

  def toSlotWithRoom: SlotWithRoom = SlotWithRoom(id, name, day, from, to, room, proposal, break, fillerForSlotId)

  def isAllocatableSlot: Boolean = !break.isDefined
}

object SlotBuilder {

  def apply(name: String, day: String, from: DateTime, to: DateTime, room: Room): Slot = {
    val id = name + "_" + room.id + "_" + day + "_" + from.getDayOfMonth + "_" + from.getHourOfDay + "h" + from.getMinuteOfHour + "_" + to.getHourOfDay + "h" + to.getMinuteOfHour
    Slot(id, name, day, from, to, room.id, None, None, None)
  }

  def apply(name: String, day: String, from: DateTime, to: DateTime, room: Room, proposal: Option[Proposal]): Slot = {
    val id = name + "_" + room.id + "_" + day + "_" + from.getDayOfMonth + "_" + from.getHourOfDay + "h" + from.getMinuteOfHour + "_" + to.getHourOfDay + "h" + to.getMinuteOfHour
    Slot(id, name, day, from, to, room.id, proposal, None, None)
  }

  def apply(slotBreak: SlotBreak, day: String, from: DateTime, to: DateTime, rooms: List[Room] = ConferenceRooms.allRooms): List[Slot] = {
    val id = slotBreak.id + "_" + day + "_" + from.getDayOfMonth + "_" + from.getHourOfDay + "h" + from.getMinuteOfHour + "_" + to.getHourOfDay + "h" + to.getMinuteOfHour
    rooms.map(room => Slot(id, slotBreak.nameEN, day, from, to, room.id, None, Some(slotBreak), None))
  }

  def apply(fillerForSlot: Slot, fillerIndex: Int, from: DateTime, to: DateTime): Slot = {
    Slot(fillerForSlot.id.replace(fillerForSlot.proposal.map(_.talkType.id).getOrElse(""), s"filler${fillerIndex}"), "filler", fillerForSlot.day, from, to, fillerForSlot.room.id, fillerForSlot.proposal, None, Option(fillerForSlot.id));
  }
}

// See https://groups.google.com/forum/#!topic/play-framework/ENlcpDzLZo8
object Slot {
  implicit def dateTimeOrdering: Ordering[DateTime] = Ordering.fromLessThan(_ isBefore _)
  implicit val slotFormat = Json.format[Slot]
  implicit val slotWithRoomFormat = Json.format[SlotWithRoom]

  def keepDeletableSlotIdsFrom(slotIds: List[String]): List[String] = {
    val programSchedules = ProgramSchedule.allProgramSchedulesForCurrentEvent()
    val programSchedulesPerConfScheduleId = ConferenceProposalTypes.slottableTypes.foldLeft(Map.empty[String, List[ProgramSchedule]].withDefaultValue(List())) { (perConfScheduleIdPrograms, proposalType) =>
      val programSchedulesPerConfScheduleIdForCurrentProposalType = programSchedules.flatMap { ps =>
        ps.scheduleConfigurations.get(proposalType) match {
          case None => None
          case Some(configScheduleId) => Some(configScheduleId, ps)
        }
      }.groupBy(_._1).mapValues(programSchedules => programSchedules.map(_._2))
      perConfScheduleIdPrograms ++ programSchedulesPerConfScheduleIdForCurrentProposalType
    }

    slotIds.filter(id => programSchedulesPerConfScheduleId.get(id).map(_.isEmpty).getOrElse(true))
  }

  def byType(proposalType: ProposalType): Seq[Slot] = {
    ConferenceDescriptor.ConferenceSlots.all.filter(s => s.name == proposalType.id)
  }

  def fillWithFillers(slots: List[Slot]): List[Slot] = {
    val fillers = new ListBuffer[Slot];

    val atomicTimeSlots = slots
      .groupBy(_.from)
      .mapValues(slots => slots.map(_.to).distinct.min(dateTimeOrdering))
      .map(fromTo => (fromTo._1, fromTo._2))
      .toArray
      .sortBy(_._1);

    // I'm pretty sure this can be achieved with some Scala API magic here, but am too noob
    // for this :-)
    for ( (startingTime: DateTime, slotsSharingSameStartingTime: List[Slot]) <- slots.groupBy(_.from)) {
      val atomicTimeSlot = atomicTimeSlots.find(atomicTimeSlot => atomicTimeSlot._1.equals(startingTime)).get
      val atomicTimeSlotIndex = atomicTimeSlots.indexOf(atomicTimeSlot)
      slotsSharingSameStartingTime.foreach(slot => {
        if(!slot.to.equals(atomicTimeSlot._2)) {
          var fillerIndex = 1
          var fillerAtomicTimeSlot = atomicTimeSlots(atomicTimeSlotIndex + fillerIndex)
          while(fillerAtomicTimeSlot._1.isBefore(slot.to)) {
            fillers += SlotBuilder(slot, fillerIndex, fillerAtomicTimeSlot._1, fillerAtomicTimeSlot._2)
            fillerIndex += 1
            fillerAtomicTimeSlot = atomicTimeSlots(atomicTimeSlotIndex + fillerIndex)
          }
        }
      })
    }
    (slots ::: fillers.toList).sortBy(_.from)
  }
}

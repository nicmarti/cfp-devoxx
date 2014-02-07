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
 * Time slot.
 * Created by nicolas on 01/02/2014.
 */

case class Room(id: String, name: String, capacity: Int)

object Room {
  implicit val roomFormat = Json.format[Room]

  val SEINE_A = Room("seine_a", "Seine A", 300)
  val SEINE_B = Room("seine_b", "Seine B", 300)
  val SEINE_C = Room("seine_c", "Seine C", 300)
  val AUDITORIUM = Room("auditorium", "Auditorium", 200)
  val ELLA_FITZGERALD_AB = Room("el_ab", "Ella Fitzgerald AB", 60)
  val ELLA_FITZGERALD = Room("el_ab_full", "Ella Fitzgerald", 190)
  val LOUIS_ARMSTRONG_AB = Room("la_ab", "Louis Armstrong AB", 60)
  val LOUIS_ARMSTRONG_CD = Room("la_cd", "Louis Armstrong CD", 60)
  val MILES_DAVIS_A = Room("md_a", "Miles Davis A", 60)
  val MILES_DAVIS_B = Room("md_b", "Miles Davis B", 60)
  val MILES_DAVIS_C = Room("md_c", "Miles Davis C", 60)
  val MILES_DAVIS = Room("md_full", "Miles Davis", 190)
  val DUKE_ELLINGTON = Room("duke", "Duke Ellignton", 20)
  val CHARLIE_PARKER = Room("charlie_parker", "Miles Davis", 190)
  val OTHER = Room("other_room", "Other room", 190)

  val all = List(SEINE_A,
    SEINE_B,
    SEINE_C,
    AUDITORIUM,
    ELLA_FITZGERALD_AB,
    ELLA_FITZGERALD,
    LOUIS_ARMSTRONG_AB,
    LOUIS_ARMSTRONG_CD,
    MILES_DAVIS_A,
    MILES_DAVIS_B,
    MILES_DAVIS_C,
    MILES_DAVIS,
    DUKE_ELLINGTON,
    CHARLIE_PARKER,
    OTHER)

  val allBigRoom = List(Room.SEINE_A, Room.SEINE_B, Room.SEINE_C, Room.AUDITORIUM)

  val allRoomsLabs = List(Room.ELLA_FITZGERALD_AB, Room.LOUIS_ARMSTRONG_AB, Room.LOUIS_ARMSTRONG_CD,
    Room.MILES_DAVIS_A, Room.MILES_DAVIS_B, Room.MILES_DAVIS_C)

  val allRoomsTIA = allBigRoom ++ allRoomsLabs

  val allRooms = allBigRoom ++ List(Room.ELLA_FITZGERALD, Room.MILES_DAVIS)

  // No Auditorium
  val allRoomsButAuditorium = List(Room.SEINE_A, Room.SEINE_B, Room.SEINE_C, Room.ELLA_FITZGERALD, Room.MILES_DAVIS)

  val allAsId = all.map(a => (a.id, a.name)).toSeq.sorted

  def parse(proposalType: String): Room = {
    proposalType match {
      case "seine_a" => SEINE_A
      case "seine_b" => SEINE_B
      case "seine_c" => SEINE_C
      case "auditorium" => AUDITORIUM
      case "el_ab" => ELLA_FITZGERALD_AB
      case "el_ab_full" => ELLA_FITZGERALD
      case "la_ab" => LOUIS_ARMSTRONG_AB
      case "la_cd" => LOUIS_ARMSTRONG_CD
      case "md_a" => MILES_DAVIS_A
      case "md_b" => MILES_DAVIS_B
      case "md_c" => MILES_DAVIS_C
      case "md_full" => MILES_DAVIS
      case "duke" => DUKE_ELLINGTON
      case "charlie_parker" => CHARLIE_PARKER
      case other => OTHER
    }
  }
}

case class Slot(id: String, name: String, day: String, from: DateTime, to: DateTime, room: Room) {
  override def toString: String = {
    s"Slot[" + id + "]"
  }
}

object Slot {

  def apply(name: String, day: String, from: DateTime, to: DateTime, room: Room): Slot = {
    val id = name + "_" + room.id + "_" + day + "_" + from.getDayOfMonth + "_" + from.getHourOfDay + "h" + from.getMinuteOfHour + "_" + to.getHourOfDay + "h" + to.getMinuteOfHour
    Slot(id, name, day, from, to, room)
  }

  val universitySlots: List[Slot] = {
    val u1 = Room.allBigRoom.map {
      r =>
        Slot(ProposalType.UNI.id, "mercredi", new DateTime("2014-04-16T09:30:00.000+02:00"), new DateTime("2014-04-16T12:30:00.000+02:00"), r)
    }
    val u2 = Room.allBigRoom.map {
      r2 =>
        Slot(ProposalType.UNI.id, "mercredi", new DateTime("2014-04-16T13:30:00.000+02:00"), new DateTime("2014-04-16T16:30:00.000+02:00"), r2)
    }
    u1 ++ u2
  }

  val toolsInActionSlots: List[Slot] = {
    val t1 = Room.allRoomsTIA.map {
      r =>
        Slot(ProposalType.TIA.id, "mercredi", new DateTime("2014-04-16T17:00:00.000+02:00"), new DateTime("2014-04-16T17:30:00.000+02:00"), r)
    }
    val t2 = Room.allRoomsTIA.map {
      r =>
        Slot(ProposalType.TIA.id, "mercredi", new DateTime("2014-04-16T17:40:00.000+02:00"), new DateTime("2014-04-16T18:10:00.000+02:00"), r)
    }
    val t3 = Room.allRoomsTIA.map {
      r =>
        Slot(ProposalType.TIA.id, "mercredi", new DateTime("2014-04-16T18:20:00.000+02:00"), new DateTime("2014-04-16T18:50:00.000+02:00"), r)
    }
    t1 ++ t2 ++ t3
  }

  val labsSlots: List[Slot] = {
    val l1 = Room.allBigRoom.map {
      r =>
        Slot(ProposalType.LAB.id, "mercredi", new DateTime("2014-04-16T09:30:00.000+02:00"), new DateTime("2014-04-16T12:30:00.000+02:00"), r)
    }
    val l2 = Room.allBigRoom.map {
      r2 =>
        Slot(ProposalType.LAB.id, "mercredi", new DateTime("2014-04-16T13:30:00.000+02:00"), new DateTime("2014-04-16T16:30:00.000+02:00"), r2)
    }
    l1 ++ l2
  }

  val quickiesSlotsThursday: List[Slot] = {
    val quickie01 = Room.allRoomsButAuditorium.map {
      r =>
        Slot(ProposalType.QUICK.id, "jeudi", new DateTime("2014-04-17T12:35:00.000+02:00"), new DateTime("2014-04-17T12:50:00.000+02:00"), r)
    }
    val quickie02 = Room.allRoomsButAuditorium.map {
      r =>
        Slot(ProposalType.QUICK.id, "jeudi", new DateTime("2014-04-17T13:00:00.000+02:00"), new DateTime("2014-04-17T13:15:00.000+02:00"), r)
    }
    quickie01 ++ quickie02
  }

  val quickiesSlotsFriday: List[Slot] = {

    val quickie03 = Room.allRoomsButAuditorium.map {
      r =>
        Slot(ProposalType.QUICK.id, "vendredi", new DateTime("2014-04-17T12:35:00.000+02:00"), new DateTime("2014-04-17T12:50:00.000+02:00"), r)
    }
    val quickie04 = Room.allRoomsButAuditorium.map {
      r =>
        Slot(ProposalType.QUICK.id, "vendredi", new DateTime("2014-04-17T13:00:00.000+02:00"), new DateTime("2014-04-17T13:15:00.000+02:00"), r)
    }
    quickie03 ++ quickie04
  }


  val conferenceSlotsThursday: List[Slot] = {
    val c1 = Room.allRooms.map {
      r =>
        Slot(ProposalType.CONF.id, "jeudi", new DateTime("2014-04-17T11:30:00.000+02:00"), new DateTime("2014-04-17T12:20:00.000+02:00"), r)
    }
    // Pas d'auditorium car apres-midi des décideurs
    val c2 = Room.allRoomsButAuditorium.map {
      r =>
        Slot(ProposalType.CONF.id, "jeudi", new DateTime("2014-04-17T13:25:00.000+02:00"), new DateTime("2014-04-17T14:15:00.000+02:00"), r)
    }
    val c3 = Room.allRoomsButAuditorium.map {
      r =>
        Slot(ProposalType.CONF.id, "jeudi", new DateTime("2014-04-17T14:30:00.000+02:00"), new DateTime("2014-04-17T15:20:00.000+02:00"), r)
    }
    val c4 = Room.allRoomsButAuditorium.map {
      r =>
        Slot(ProposalType.CONF.id, "jeudi", new DateTime("2014-04-17T15:35:00.000+02:00"), new DateTime("2014-04-17T16:25:00.000+02:00"), r)
    }
    val c5 = Room.allRoomsButAuditorium.map {
      r =>
        Slot(ProposalType.CONF.id, "jeudi", new DateTime("2014-04-17T17:00:00.000+02:00"), new DateTime("2014-04-17T17:50:00.000+02:00"), r)
    }
    // No more MilesDavis
    val c6 = Room.allRoomsButAuditorium.filterNot(_.id == Room.MILES_DAVIS.id).map {
      r =>
        Slot(ProposalType.CONF.id, "jeudi", new DateTime("2014-04-17T18:05:00.000+02:00"), new DateTime("2014-04-17T18:55:00.000+02:00"), r)
    }
    c1 ++ c2 ++ c3 ++ c4 ++ c5 ++ c6
  }

  val conferenceSlotsFriday: List[Slot] = {
    val c1 = Room.allRooms.map {
      r =>
        Slot(ProposalType.CONF.id, "vendredi", new DateTime("2014-04-18T10:40:00.000+02:00"), new DateTime("2014-04-17T11:30:00.000+02:00"), r)
    }
    val c2 = Room.allRooms.map {
      r =>
        Slot(ProposalType.CONF.id, "vendredi", new DateTime("2014-04-18T11:45:00.000+02:00"), new DateTime("2014-04-17T12:35:00.000+02:00"), r)
    }

    val c3 = Room.allRooms.map {
      r =>
        Slot(ProposalType.CONF.id, "vendredi", new DateTime("2014-04-18T13:30:00.000+02:00"), new DateTime("2014-04-17T14:20:00.000+02:00"), r)
    }
    val c4 = Room.allRooms.map {
      r =>
        Slot(ProposalType.CONF.id, "vendredi", new DateTime("2014-04-18T14:35:00.000+02:00"), new DateTime("2014-04-17T15:25:00.000+02:00"), r)
    }
    val c5 = Room.allRooms.map {
      r =>
        Slot(ProposalType.CONF.id, "vendredi", new DateTime("2014-04-18T15:40:00.000+02:00"), new DateTime("2014-04-17T16:30:00.000+02:00"), r)
    }

    val c6 = Room.allRooms.map {
      r =>
        Slot(ProposalType.CONF.id, "vendredi", new DateTime("2014-04-18T17:00:00.000+02:00"), new DateTime("2014-04-17T17:50:00.000+02:00"), r)
    }

    val c7 = List( Room.MILES_DAVIS,Room.ELLA_FITZGERALD,Room.AUDITORIUM).map {
      r =>
        Slot(ProposalType.CONF.id, "vendredi", new DateTime("2014-04-18T18:05:00.000+02:00"), new DateTime("2014-04-17T18:55:00.000+02:00"), r)
    }

    c1 ++ c2 ++ c3 ++ c4 ++ c5 ++ c6 ++ c7
  }

    val bofSlotsThursday: List[Slot] = {

    val bof01 = Room.allRoomsLabs.map {
      r =>
        Slot(ProposalType.BOF.id, "jeudi", new DateTime("2014-04-17T19:30:00.000+02:00"), new DateTime("2014-04-17T20:20:00.000+02:00"), r)
    }
    val bof02 = Room.allRoomsLabs.map {
      r =>
        Slot(ProposalType.BOF.id, "jeudi", new DateTime("2014-04-17T20:30:00.000+02:00"), new DateTime("2014-04-17T21:20:00.000+02:00"), r)
    }
    val bof03 = Room.allRoomsLabs.map {
      r =>
        Slot(ProposalType.BOF.id, "jeudi", new DateTime("2014-04-17T21:30:00.000+02:00"), new DateTime("2014-04-17T22:20:00.000+02:00"), r)
    }
    bof01 ++ bof02 ++ bof03
    }

}
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
 * Time slot.
 * Created by nicolas on 01/02/2014.
 */

case class Room(id: String, name: String, capacity: Int, setup: String)

object Room {
  implicit val roomFormat = Json.format[Room]

  val HALL_EXPO = Room("hall", "Espace d'exposition", 1500, "special")

  val KEYNOTE_SEINE = Room("seine_keynote", "Seine", 980, "keynote")
  val SEINE_A = Room("seine_a", "Seine A", 280, "theatre")
  val SEINE_B = Room("seine_b", "Seine B", 280, "theatre")
  val SEINE_C = Room("seine_c", "Seine C", 260, "theatre")
  val AUDITORIUM = Room("auditorium", "Auditorium", 160, "theatre")
  val ELLA_FITZGERALD = Room("el_ab_full", "Ella Fitzgerald", 290, "theatre")
  val MILES_DAVIS = Room("md_full", "M.Davis", 220, "theatre")

  val ELLA_FITZGERALD_AB = Room("el_ab", "Ella Fitzgerald AB", 45, "classe")
  val LOUIS_ARMSTRONG_AB = Room("la_ab", "Louis Armstrong AB", 30, "classe")
  val LOUIS_ARMSTRONG_CD = Room("la_cd", "Louis Armstrong CD", 30, "classe")
  val MILES_DAVIS_A = Room("md_a", "Miles Davis A", 24, "classe")
  val MILES_DAVIS_B = Room("md_b", "Miles Davis B", 24, "classe")
  val MILES_DAVIS_C = Room("md_c", "Miles Davis C", 48, "classe")

  val ELLA_FITZGERALD_AB_TH = Room("el_ab_th", "E.Fitzgerald AB", 80, "theatre")
  val LOUIS_ARMSTRONG_AB_TH = Room("la_ab_th", "L.Armstrong AB", 80, "theatre")
  val LOUIS_ARMSTRONG_CD_TH = Room("la_cd_th", "L.Armstrong CD", 80, "theatre")
  val MILES_DAVIS_A_TH = Room("md_a_th", "M.Davis A", 50, "theatre")
  val MILES_DAVIS_B_TH = Room("md_b_th", "M.Davis B", 50, "theatre")
  val MILES_DAVIS_C_TH = Room("md_c_th", "M.Davis C", 80, "theatre")

  val DUKE_ELLINGTON = Room("duke", "Duke Ellington-CodeStory", 15, "classe")
  val FOYER_BAS = Room("foyer_bas", "Foyer bas", 300, "classe")
  val LABO = Room("foyer_labo", "Labo", 40, "special")
  val OTHER = Room("other_room", "Other room", 100, "sans objet")

  val all = List(KEYNOTE_SEINE,
    SEINE_A,
    SEINE_B,
    SEINE_C,
    AUDITORIUM,
    ELLA_FITZGERALD,
    ELLA_FITZGERALD_AB,
    ELLA_FITZGERALD_AB_TH,
    LOUIS_ARMSTRONG_AB,
    LOUIS_ARMSTRONG_AB_TH,
    LOUIS_ARMSTRONG_CD,
    LOUIS_ARMSTRONG_CD_TH,
    MILES_DAVIS_A,
    MILES_DAVIS_A_TH,
    MILES_DAVIS_B_TH,
    MILES_DAVIS_C_TH,

    MILES_DAVIS,
    DUKE_ELLINGTON,
    FOYER_BAS,
    LABO,
    OTHER)

  val allBigRoom = List(Room.SEINE_A, Room.SEINE_B, Room.SEINE_C, Room.AUDITORIUM)

  val allRoomsLabs = List(Room.ELLA_FITZGERALD_AB, Room.LOUIS_ARMSTRONG_AB, Room.LOUIS_ARMSTRONG_CD,
    Room.MILES_DAVIS_A, Room.MILES_DAVIS_B, Room.MILES_DAVIS_C)

  val allRoomsBOFs = List(Room.ELLA_FITZGERALD_AB, Room.LOUIS_ARMSTRONG_AB, Room.LOUIS_ARMSTRONG_CD,
    Room.MILES_DAVIS_A, Room.MILES_DAVIS_B, Room.MILES_DAVIS_C, Room.LABO)

  val allRoomsTIA = List(Room.ELLA_FITZGERALD_AB_TH, Room.LOUIS_ARMSTRONG_AB_TH, Room.LOUIS_ARMSTRONG_CD_TH,
    Room.MILES_DAVIS_A_TH, Room.MILES_DAVIS_B_TH, Room.MILES_DAVIS_C_TH,
    Room.SEINE_A, Room.SEINE_B, Room.SEINE_C, Room.AUDITORIUM)

  val allRooms = allBigRoom ++ List(Room.ELLA_FITZGERALD, Room.MILES_DAVIS)

  // No E.Fitzgerald for Apres-midi des decideurs
  val allRoomsButAMD = List(Room.SEINE_A, Room.SEINE_B, Room.SEINE_C, Room.AUDITORIUM, Room.MILES_DAVIS)

  val allAsId = all.map(a => (a.id, a.name)).toSeq.sorted

  def parse(proposalType: String): Room = {
    proposalType match {
      case "seine_keynote" => KEYNOTE_SEINE
      case "seine_a" => SEINE_A
      case "seine_b" => SEINE_B
      case "seine_c" => SEINE_C
      case "auditorium" => AUDITORIUM
      case "el_ab" => ELLA_FITZGERALD_AB
      case "el_ab_th" => ELLA_FITZGERALD_AB_TH
      case "el_ab_full" => ELLA_FITZGERALD
      case "la_ab" => LOUIS_ARMSTRONG_AB
      case "la_ab_th" => LOUIS_ARMSTRONG_AB_TH
      case "la_cd" => LOUIS_ARMSTRONG_CD
      case "la_cd_th" => LOUIS_ARMSTRONG_CD_TH
      case "md_a" => MILES_DAVIS_A
      case "md_a_th" => MILES_DAVIS_A_TH
      case "md_b" => MILES_DAVIS_B
      case "md_b_th" => MILES_DAVIS_B_TH
      case "md_c" => MILES_DAVIS_C
      case "md_c_th" => MILES_DAVIS_C_TH
      case "md_full" => MILES_DAVIS
      case "duke" => DUKE_ELLINGTON
      case "hall" => HALL_EXPO
      case "foyer_bas" => FOYER_BAS
      case "foyer_labo" => LABO
      case other => OTHER
    }
  }
}

case class SlotBreak(id: String, nameEN: String, nameFR: String, room: Room)

object SlotBreak {
  implicit val slotBreakFormat = Json.format[SlotBreak]

  val petitDej = SlotBreak("dej", "Welcome and Breakfast", "Accueil et petit-déjeuner", Room.HALL_EXPO)
  val coffee = SlotBreak("coffee", "Coffee Break", "Pause café", Room.HALL_EXPO)
  val lunch = SlotBreak("lunch", "Lunch", "Pause déjeuner", Room.HALL_EXPO)
  val shortBreak = SlotBreak("chgt", "Break", "Pause courte", Room.HALL_EXPO)
}

case class Slot(id: String, name: String, day: String, from: DateTime, to: DateTime, room: Room,
                proposal: Option[Proposal], break: Option[SlotBreak]) {
  override def toString: String = {
    s"Slot[" + id + "]"
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

  val universitySlots: List[Slot] = {
    val u1 = Room.allBigRoom.map {
      r =>
        SlotBuilder(ProposalType.UNI.id, "mercredi", new DateTime("2014-04-16T09:30:00.000+02:00"), new DateTime("2014-04-16T12:30:00.000+02:00"), r)
    }
    val u2 = Room.allBigRoom.map {
      r2 =>
        SlotBuilder(ProposalType.UNI.id, "mercredi", new DateTime("2014-04-16T13:30:00.000+02:00"), new DateTime("2014-04-16T16:30:00.000+02:00"), r2)
    }
    u1 ++ u2
  }

  val toolsInActionSlots: List[Slot] = {
    val t1 = Room.allRoomsTIA.map {
      r =>
        SlotBuilder(ProposalType.TIA.id, "mercredi", new DateTime("2014-04-16T17:10:00.000+02:00"), new DateTime("2014-04-16T17:40:00.000+02:00"), r)
    }
    val t2 = Room.allRoomsTIA.map {
      r =>
        SlotBuilder(ProposalType.TIA.id, "mercredi", new DateTime("2014-04-16T17:50:00.000+02:00"), new DateTime("2014-04-16T18:20:00.000+02:00"), r)
    }
    val t3 = Room.allRoomsTIA.map {
      r =>
        SlotBuilder(ProposalType.TIA.id, "mercredi", new DateTime("2014-04-16T18:30:00.000+02:00"), new DateTime("2014-04-16T19:00:00.000+02:00"), r)
    }
    t1 ++ t2 ++ t3
  }

  val labsSlots: List[Slot] = {
    val l1 = Room.allRoomsLabs.map {
      r =>
        SlotBuilder(ProposalType.LAB.id, "mercredi", new DateTime("2014-04-16T09:30:00.000+02:00"), new DateTime("2014-04-16T12:30:00.000+02:00"), r)
    }
    val l2 = Room.allRoomsLabs.map {
      r2 =>
        SlotBuilder(ProposalType.LAB.id, "mercredi", new DateTime("2014-04-16T13:30:00.000+02:00"), new DateTime("2014-04-16T16:30:00.000+02:00"), r2)
    }
    l1 ++ l2
  }

  val quickiesSlotsThursday: List[Slot] = {
    val quickie01 = Room.allRoomsButAMD.map {
      r =>
        SlotBuilder(ProposalType.QUICK.id, "jeudi", new DateTime("2014-04-17T12:35:00.000+02:00"), new DateTime("2014-04-17T12:50:00.000+02:00"), r)
    }
    val quickie02 = Room.allRoomsButAMD.map {
      r =>
        SlotBuilder(ProposalType.QUICK.id, "jeudi", new DateTime("2014-04-17T13:00:00.000+02:00"), new DateTime("2014-04-17T13:15:00.000+02:00"), r)
    }
    quickie01 ++ quickie02
  }

  val quickiesSlotsFriday: List[Slot] = {

    val quickie03 = Room.allRooms.map {
      r =>
        SlotBuilder(ProposalType.QUICK.id, "vendredi", new DateTime("2014-04-18T12:45:00.000+02:00"), new DateTime("2014-04-18T13:00:00.000+02:00"), r)
    }
    val quickie04 = Room.allRooms.map {
      r =>
        SlotBuilder(ProposalType.QUICK.id, "vendredi", new DateTime("2014-04-18T13:10:00.000+02:00"), new DateTime("2014-04-18T13:25:00.000+02:00"), r)
    }
    quickie03 ++ quickie04
  }

  val conferenceSlotsThursday: List[Slot] = {
    val c1 = Room.allRooms.map {
      r =>
        SlotBuilder(ProposalType.CONF.id, "jeudi", new DateTime("2014-04-17T11:30:00.000+02:00"), new DateTime("2014-04-17T12:20:00.000+02:00"), r)
    }
    val c2 = Room.allRooms.map {
      r =>
        SlotBuilder(ProposalType.CONF.id, "jeudi", new DateTime("2014-04-17T13:25:00.000+02:00"), new DateTime("2014-04-17T14:15:00.000+02:00"), r)
    }
    val c3 = Room.allRooms.map {
      r =>
        SlotBuilder(ProposalType.CONF.id, "jeudi", new DateTime("2014-04-17T14:30:00.000+02:00"), new DateTime("2014-04-17T15:20:00.000+02:00"), r)
    }
    val c4 = Room.allRooms.map {
      r =>
        SlotBuilder(ProposalType.CONF.id, "jeudi", new DateTime("2014-04-17T15:35:00.000+02:00"), new DateTime("2014-04-17T16:25:00.000+02:00"), r)
    }
    val c5 = Room.allRooms.map {
      r =>
        SlotBuilder(ProposalType.CONF.id, "jeudi", new DateTime("2014-04-17T17:00:00.000+02:00"), new DateTime("2014-04-17T17:50:00.000+02:00"), r)
    }

    val c6 = Room.allRooms.map {
      r =>
        SlotBuilder(ProposalType.CONF.id, "jeudi", new DateTime("2014-04-17T18:05:00.000+02:00"), new DateTime("2014-04-17T18:55:00.000+02:00"), r)
    }
    c1 ++ c2 ++ c3 ++ c4 ++ c5 ++ c6
  }

  val amdThursday: List[Slot] = {
    val c1 = SlotBuilder(ProposalType.AMD.id, "jeudi", new DateTime("2014-04-17T13:30:00.000+02:00"), new DateTime("2014-04-17T13:50:00.000+02:00"), Room.ELLA_FITZGERALD)

    val c2 = SlotBuilder(ProposalType.AMD.id, "jeudi", new DateTime("2014-04-17T13:50:00.000+02:00"), new DateTime("2014-04-17T14:10:00.000+02:00"), Room.ELLA_FITZGERALD)

    val c3 = SlotBuilder(ProposalType.AMD.id, "jeudi", new DateTime("2014-04-17T14:10:00.000+02:00"), new DateTime("2014-04-17T14:30:00.000+02:00"), Room.ELLA_FITZGERALD)

    val c4 = SlotBuilder(ProposalType.AMD.id, "jeudi", new DateTime("2014-04-17T14:40:00.000+02:00"), new DateTime("2014-04-17T15:00:00.000+02:00"), Room.ELLA_FITZGERALD)

    val c5 = SlotBuilder(ProposalType.AMD.id, "jeudi", new DateTime("2014-04-17T15:10:00.000+02:00"), new DateTime("2014-04-17T15:30:00.000+02:00"), Room.ELLA_FITZGERALD)

    val c6 =  SlotBuilder(ProposalType.AMD.id, "jeudi", new DateTime("2014-04-17T15:40:00.000+02:00"), new DateTime("2014-04-17T16:00.000+02:00"), Room.ELLA_FITZGERALD)

    val c7 =  SlotBuilder(ProposalType.AMD.id, "jeudi", new DateTime("2014-04-17T17:30.000+02:00"), new DateTime("2014-04-17T18:30:00.000+02:00"), Room.ELLA_FITZGERALD)

    List(c1, c2, c3, c4, c5, c6, c7)
  }


  val conferenceSlotsFriday: List[Slot] = {
    val c1 = Room.allRooms.map {
      r =>
        SlotBuilder(ProposalType.CONF.id, "vendredi", new DateTime("2014-04-18T10:40:00.000+02:00"), new DateTime("2014-04-18T11:30:00.000+02:00"), r)
    }
    val c2 = Room.allRooms.map {
      r =>
        SlotBuilder(ProposalType.CONF.id, "vendredi", new DateTime("2014-04-18T11:45:00.000+02:00"), new DateTime("2014-04-18T12:35:00.000+02:00"), r)
    }

    val c3 = Room.allRooms.map {
      r =>
        SlotBuilder(ProposalType.CONF.id, "vendredi", new DateTime("2014-04-18T13:30:00.000+02:00"), new DateTime("2014-04-18T14:20:00.000+02:00"), r)
    }
    val c4 = Room.allRooms.map {
      r =>
        SlotBuilder(ProposalType.CONF.id, "vendredi", new DateTime("2014-04-18T14:35:00.000+02:00"), new DateTime("2014-04-18T15:25:00.000+02:00"), r)
    }
    val c5 = Room.allRooms.map {
      r =>
        SlotBuilder(ProposalType.CONF.id, "vendredi", new DateTime("2014-04-18T15:40:00.000+02:00"), new DateTime("2014-04-18T16:30:00.000+02:00"), r)
    }

    val c6 = Room.allRooms.map {
      r =>
        SlotBuilder(ProposalType.CONF.id, "vendredi", new DateTime("2014-04-18T17:00:00.000+02:00"), new DateTime("2014-04-18T17:50:00.000+02:00"), r)
    }

    val c7 = List(Room.MILES_DAVIS, Room.ELLA_FITZGERALD, Room.AUDITORIUM).map {
      r =>
        SlotBuilder(ProposalType.CONF.id, "vendredi", new DateTime("2014-04-18T18:05:00.000+02:00"), new DateTime("2014-04-18T18:55:00.000+02:00"), r)
    }

    c1 ++ c2 ++ c3 ++ c4 ++ c5 ++ c6 ++ c7
  }

  val bofSlotsThursday: List[Slot] = {

    val bof01 = Room.allRoomsBOFs.map {
      r =>
        SlotBuilder(ProposalType.BOF.id, "jeudi", new DateTime("2014-04-17T19:30:00.000+02:00"), new DateTime("2014-04-17T20:30:00.000+02:00"), r)
    }
    val bof02 = Room.allRoomsBOFs.map {
      r =>
        SlotBuilder(ProposalType.BOF.id, "jeudi", new DateTime("2014-04-17T20:30:00.000+02:00"), new DateTime("2014-04-17T21:30:00.000+02:00"), r)
    }
    val bof03 = Room.allRoomsBOFs.map {
      r =>
        SlotBuilder(ProposalType.BOF.id, "jeudi", new DateTime("2014-04-17T21:30:00.000+02:00"), new DateTime("2014-04-17T22:30:00.000+02:00"), r)
    }
    (bof01 ++ bof02 ++ bof03)
  }

  val wednesday: List[Slot] = {
    val wednesdayBreaks = List(
      SlotBuilder(SlotBreak.petitDej, "mercredi", new DateTime("2014-04-16T08:00:00.000+02:00"), new DateTime("2014-04-16T09:30:00.000+02:00"))
      , SlotBuilder(SlotBreak.lunch, "mercredi", new DateTime("2014-04-16T12:30:00.000+02:00"), new DateTime("2014-04-16T13:30:00.000+02:00"))
      , SlotBuilder(SlotBreak.coffee, "mercredi", new DateTime("2014-04-16T16:30:00.000+02:00"), new DateTime("2014-04-16T17:10:00.000+02:00"))
    )
    val odc = Proposal.findById("ZYE-706")
    val devoxx4Kids = Proposal.findById("USM-170")
    val hackerGarten = Proposal.findById("QIY-889")

    val specialEvents = List(
      SlotBuilder(ProposalType.OTHER.id, "mercredi", new DateTime("2014-04-16T09:30:00.000+02:00"), new DateTime("2014-04-16T18:00:00.000+02:00"), Room.OTHER, odc)
      , SlotBuilder(ProposalType.OTHER.id, "mercredi", new DateTime("2014-04-16T09:30:00.000+02:00"), new DateTime("2014-04-16T18:00:00.000+02:00"), Room.OTHER, devoxx4Kids)
      , SlotBuilder(ProposalType.HACK.id, "mercredi", new DateTime("2014-04-16T13:30:00.000+02:00"), new DateTime("2014-04-16T16:30:00.000+02:00"), Room.FOYER_BAS, hackerGarten)
    )
    wednesdayBreaks ++ specialEvents
  }

  val thursday: List[Slot] = {

    val keynoteThursday: List[Slot] = {
      val welcomeKeynote = Proposal.findById("DSD-030")
      val key01 = SlotBuilder(ProposalType.KEY.id, "jeudi", new DateTime("2014-04-17T09:00:00.000+02:00"), new DateTime("2014-04-17T09:10:00.000+02:00"), Room.KEYNOTE_SEINE, welcomeKeynote)

      val babinet=Proposal.findById("IIH-512")
      val key02 = SlotBuilder(ProposalType.KEY.id, "jeudi", new DateTime("2014-04-17T09:10:00.000+02:00"), new DateTime("2014-04-17T09:30:00.000+02:00"), Room.KEYNOTE_SEINE, babinet)

      val guyMamou=Proposal.findById("RCV-236")
      val key03 = SlotBuilder(ProposalType.KEY.id, "jeudi", new DateTime("2014-04-17T09:40:00.000+02:00"), new DateTime("2014-04-17T10:00:00.000+02:00"), Room.KEYNOTE_SEINE, guyMamou)

      val simplon=Proposal.findById("TAX-972")
      val key04 = SlotBuilder(ProposalType.KEY.id, "jeudi", new DateTime("2014-04-17T10:10:00.000+02:00"), new DateTime("2014-04-17T10:30:00.000+02:00"), Room.KEYNOTE_SEINE, simplon)

      List(key01, key02, key03, key04)
    }

    val thursdayBreaks = List(
      SlotBuilder(SlotBreak.petitDej, "jeudi", new DateTime("2014-04-17T07:30:00.000+02:00"), new DateTime("2014-04-17T09:00:00.000+02:00"))
      , SlotBuilder(SlotBreak.coffee, "jeudi", new DateTime("2014-04-17T10:45:00.000+02:00"), new DateTime("2014-04-17T11:30:00.000+02:00"))
      , SlotBuilder(SlotBreak.lunch, "jeudi", new DateTime("2014-04-17T12:20:00.000+02:00"), new DateTime("2014-04-17T13:25:00.000+02:00"))
      , SlotBuilder(SlotBreak.coffee, "jeudi", new DateTime("2014-04-17T16:25:00.000+02:00"), new DateTime("2014-04-17T17:00:00.000+02:00"))
      , SlotBuilder(SlotBreak.shortBreak, "jeudi", new DateTime("2014-04-17T18:55:00.000+02:00"), new DateTime("2014-04-17T19:30:00.000+02:00"))
    )

    val codeStory = Proposal.findById("ZDC-067")

    val specialEvents = List(
      SlotBuilder(ProposalType.CODESTORY.id, "jeudi", new DateTime("2014-04-17T11:00:00.000+02:00"), new DateTime("2014-04-17T18:00:00.000+02:00"), Room.DUKE_ELLINGTON, codeStory)
    )

    thursdayBreaks ++ specialEvents ++ keynoteThursday
  }

  val friday: List[Slot] = {

  val keynoteFriday: List[Slot] = {
      val geert=Proposal.findById("JEJ-167")
      val key05 = SlotBuilder(ProposalType.KEY.id, "vendredi", new DateTime("2014-04-18T09:00:00.000+02:00"), new DateTime("2014-04-18T09:20:00.000+02:00"), Room.KEYNOTE_SEINE, geert)

      val oracle=Proposal.findById("BHX-731")
      val key06 = SlotBuilder(ProposalType.KEY.id, "vendredi", new DateTime("2014-04-18T09:30:00.000+02:00"), new DateTime("2014-04-18T09:50:00.000+02:00"), Room.KEYNOTE_SEINE, oracle)

      val serge=Proposal.findById("KOC-474")
      val key07 = SlotBuilder(ProposalType.KEY.id, "vendredi", new DateTime("2014-04-18T10:00:00.000+02:00"), new DateTime("2014-04-18T10:20:00.000+02:00"), Room.KEYNOTE_SEINE, serge)

      List(key05, key06, key07)
    }

    val fridayBreaks = List(
      SlotBuilder(SlotBreak.petitDej, "vendredi", new DateTime("2014-04-18T08:00:00.000+02:00"), new DateTime("2014-04-18T09:00:00.000+02:00"))
      , SlotBuilder(SlotBreak.coffee, "vendredi", new DateTime("2014-04-18T10:10:00.000+02:00"), new DateTime("2014-04-18T10:40:00.000+02:00"))
      , SlotBuilder(SlotBreak.lunch, "vendredi", new DateTime("2014-04-18T12:35:00.000+02:00"), new DateTime("2014-04-18T13:30:00.000+02:00"))
      , SlotBuilder(SlotBreak.coffee, "vendredi", new DateTime("2014-04-18T16:30:00.000+02:00"), new DateTime("2014-04-18T17:00:00.000+02:00"))
    )

    val mercenaires = Proposal.findById("JCB-280")

    val specialEvents = List(
      SlotBuilder(ProposalType.OTHER.id, "vendredi", new DateTime("2014-04-18T10:40:00.000+02:00"), new DateTime("2014-04-18T16:30:00.000+02:00"), Room.DUKE_ELLINGTON, mercenaires)
    )

    fridayBreaks ++ specialEvents ++ keynoteFriday
  }
}
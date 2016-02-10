package models

import org.joda.time.{ DateTime, DateTimeZone }
import play.api.Play
import com.github.nscala_time.time.Imports._
import com.typesafe.config.ConfigValue
import scala.collection.JavaConversions._
import play.api.libs.json._

/**
 * ConferenceDescriptor.
 * This might be the first file to look at, and to customize.
 * Idea behind this file is to try to collect all configurable parameters for a conference.
 *
 * For labels, please do customize messages and messages.fr
 *
 * Note from Nicolas : the first version of the CFP was much more "static" but hardly configurable.
 *
 * @author Frederic Camblor, BDX.IO 2014
 */

case class ConferenceUrls(faq: String, registration: String, confWebsite: String, cfpHostname: String) {
  def cfpURL: String = {
    if (Play.current.configuration.getBoolean("cfp.activateHTTPS").getOrElse(false)) {
      s"https://$cfpHostname"
    } else {
      s"http://$cfpHostname"
    }
  }

}

case class ConferenceTiming(
  datesI18nKey: String,
  speakersPassDuration: Integer,
  preferredDayEnabled: Boolean,
  firstDayFr: String,
  firstDayEn: String,
  datesFr: String,
  datesEn: String,
  cfpOpenedOn: DateTime,
  cfpClosedOn: DateTime,
  scheduleAnnouncedOn: DateTime)

case class ConferenceSponsor(showSponsorProposalCheckbox: Boolean, sponsorProposalType: ProposalType = ProposalType.UNKNOWN)

case class TrackDesc(id: String, imgSrc: String, i18nTitleProp: String, i18nDescProp: String)

case class ProposalConfiguration(id: String, slotsCount: Int,
                                 givesSpeakerFreeEntrance: Boolean,
                                 freeEntranceDisplayed: Boolean,
                                 htmlClass: String,
                                 hiddenInCombo: Boolean = false,
                                 chosablePreferredDay: Boolean = false,
                                 impliedSelectedTrack: Option[Track] = None)

object ProposalConfiguration {

  val UNKNOWN = ProposalConfiguration(id = "unknown", slotsCount = 0, givesSpeakerFreeEntrance = false, freeEntranceDisplayed = false,
    htmlClass = "", hiddenInCombo = true, chosablePreferredDay = false)

  def parse(propConf: String): ProposalConfiguration = {
    ConferenceDescriptor.ConferenceProposalConfigurations.ALL.find(p => p.id == propConf).getOrElse(ProposalConfiguration.UNKNOWN)
  }

  def totalSlotsCount = ConferenceDescriptor.ConferenceProposalConfigurations.ALL.map(_.slotsCount).sum

  def isDisplayedFreeEntranceProposals(pt: ProposalType): Boolean = {
    ConferenceDescriptor.ConferenceProposalConfigurations.ALL.filter(p => p.id == pt.id).map(_.freeEntranceDisplayed).headOption.getOrElse(false)
  }

  def getProposalsImplyingATrackSelection = {
    ConferenceDescriptor.ConferenceProposalConfigurations.ALL.filter(p => p.impliedSelectedTrack.nonEmpty)
  }

  def getHTMLClassFor(pt: ProposalType): String = {
    ConferenceDescriptor.ConferenceProposalConfigurations.ALL.filter(p => p.id == pt.id).map(_.htmlClass).headOption.getOrElse("unknown")
  }

  def isChosablePreferredDaysProposals(pt: ProposalType): Boolean = {
    ConferenceDescriptor.ConferenceProposalConfigurations.ALL.filter(p => p.id == pt.id).map(_.chosablePreferredDay).headOption.getOrElse(false)
  }

  def doesProposalTypeGiveSpeakerFreeEntrance(pt: ProposalType): Boolean = {
    ConferenceDescriptor.ConferenceProposalConfigurations.ALL.filter(p => p.id == pt.id).map(_.givesSpeakerFreeEntrance).headOption.getOrElse(false)
  }
}

case class ConferenceDescriptor(eventCode: String,
                                confUrlCode: String,
                                frLangEnabled: Boolean,
                                fromEmail: String,
                                committeeEmail: String,
                                bccEmail: Option[String],
                                bugReportRecipient: String,
                                conferenceUrls: ConferenceUrls,
                                timing: ConferenceTiming,
                                hosterName: String,
                                hosterWebsite: String,
                                hashTag: String,
                                conferenceSponsor: ConferenceSponsor,
                                locale: List[String],
                                localisation: String,
                                notifyProposalSubmitted: Boolean,
                                maxProposalSummaryCharacters: Int = 1200)

object ConferenceDescriptor {

  /**
   * TODO configure here the kind of talks you will propose
   */
  object ConferenceProposalTypes {
    val CONF = ProposalType(id = "conf", label = "conf.label")

    val UNI = ProposalType(id = "uni", label = "uni.label")

    val TIA = ProposalType(id = "tia", label = "tia.label")

    val LAB = ProposalType(id = "lab", label = "lab.label")

    val QUICK = ProposalType(id = "quick", label = "quick.label")

    val BOF = ProposalType(id = "bof", label = "bof.label")

    val KEY = ProposalType(id = "key", label = "key.label")

    val IGNITE = ProposalType(id = "ignite", label = "ignite.label")

    val OTHER = ProposalType(id = "other", label = "other.label")

    val ALL = List(CONF, UNI, TIA, LAB, QUICK, BOF, KEY, IGNITE, OTHER)

    def valueOf(id: String): ProposalType = id match {
      case "conf"   => CONF
      case "uni"    => UNI
      case "tia"    => TIA
      case "lab"    => LAB
      case "quick"  => QUICK
      case "bof"    => BOF
      case "key"    => KEY
      case "ignite" => IGNITE
      case "other"  => OTHER
    }

  }

  // TODO Configure here the slot, with the number of slots available, if it gives a free ticket to the speaker, some CSS icons
  object ConferenceProposalConfigurations {
    val CONF = ProposalConfiguration(id = "conf", slotsCount = ConferenceSlots.all.count(_.name.equals(ConferenceProposalTypes.CONF.id)), givesSpeakerFreeEntrance = true, freeEntranceDisplayed = true, htmlClass = "icon-microphone",
      chosablePreferredDay = true)
    val UNI = ProposalConfiguration(id = "uni", slotsCount = ConferenceSlots.all.count(_.name.equals(ConferenceProposalTypes.UNI.id)), givesSpeakerFreeEntrance = true, freeEntranceDisplayed = true, htmlClass = "icon-laptop",
      chosablePreferredDay = true)
    val TIA = ProposalConfiguration(id = "tia", slotsCount = ConferenceSlots.all.count(_.name.equals(ConferenceProposalTypes.TIA.id)), givesSpeakerFreeEntrance = true, freeEntranceDisplayed = true, htmlClass = "icon-legal",
      chosablePreferredDay = true)
    val LAB = ProposalConfiguration(id = "lab", slotsCount = ConferenceSlots.all.count(_.name.equals(ConferenceProposalTypes.LAB.id)), givesSpeakerFreeEntrance = true, freeEntranceDisplayed = true, htmlClass = "icon-beaker",
      chosablePreferredDay = true)
    val QUICK = ProposalConfiguration(id = "quick", slotsCount = ConferenceSlots.all.count(_.name.equals(ConferenceProposalTypes.QUICK.id)), givesSpeakerFreeEntrance = false, freeEntranceDisplayed = false, htmlClass = "icon-fast-forward",
      chosablePreferredDay = true)
    val BOF = ProposalConfiguration(id = "bof", slotsCount = ConferenceSlots.all.count(_.name.equals(ConferenceProposalTypes.BOF.id)), givesSpeakerFreeEntrance = false, freeEntranceDisplayed = false, htmlClass = "icon-group",
      chosablePreferredDay = false)
    val KEY = ProposalConfiguration(id = "key", slotsCount = 7, givesSpeakerFreeEntrance = true, freeEntranceDisplayed = false, htmlClass = "icon-microphone",
      chosablePreferredDay = true)
    val IGNITE = ProposalConfiguration(id = "ignite", slotsCount = ConferenceSlots.all.count(_.name.equals(ConferenceProposalTypes.IGNITE.id)), givesSpeakerFreeEntrance = false, freeEntranceDisplayed = false, htmlClass = "icon-microphone",
      chosablePreferredDay = false)
    val OTHER = ProposalConfiguration(id = "other", slotsCount = 5, givesSpeakerFreeEntrance = false, freeEntranceDisplayed = false, htmlClass = "icon-microphone",
      hiddenInCombo = true, chosablePreferredDay = false)

    val ALL = List(CONF, UNI, TIA, LAB, QUICK, BOF, KEY, IGNITE, OTHER)

    def doesItGivesSpeakerFreeEntrance(proposalType: ProposalType): Boolean = {
      ALL.filter(_.id == proposalType.id).exists(_.givesSpeakerFreeEntrance)
    }
  }

  // TODO Configure here your Conference's tracks.

  def getConferenceTracksFromConfiguration = {
    Play.current.configuration.getConfigList("cfp.tracks").map(
      l => {
        l.map(
          t => (t.getString("id") :: t.getString("icon") :: Nil))
          .collect({
            case Some(id) :: Some(icon) :: Nil => (Track(id, id + ".label"), TrackDesc(id, icon, "track." + id + ".title", "track." + id + ".desc"))
          }).toList
      })
      .getOrElse(List())
  }

  object ConferenceTracks {

    val ALL = getConferenceTracksFromConfiguration.map(_._1)

  }

  // TODO configure the description for each Track
  object ConferenceTracksDescription {

    val ALL = getConferenceTracksFromConfiguration.map(_._2)

    def findTrackDescFor(t: Track): TrackDesc = {
      ALL.find(_.id == t.id).head
    }

    implicit val conferenceTracksDescriptionFormat = Json.format[TrackDesc]

  }

  // TODO If you want to use the Devoxx Scheduler, you can describe here the list of rooms, with capacity for seats
  object ConferenceRooms {

    // Tip : I use the ID to sort-by on the view per day... So if the exhibition floor id is "aaa" it will be
    // the first column on the HTML Table
    val HALL_EXPO = Room("a_hall", "Exhibition floor", 2300, "special", "")
    val HALL_A = Room("x_hall_a", "Open Data Camp", 100, "special", "")

    val AMPHI_BLEU = Room("b_amphi", "Amphi Bleu", 826, "theatre", "camera")
    val MAILLOT = Room("c_maillot", "Maillot", 380, "theatre", "camera")
    val NEUILLY_251 = Room("f_neu251", "Neuilly 251", 220, "theatre", "camera")
    val NEUILLY_252AB = Room("e_neu252", "Neuilly 252 AB", 380, "theatre", "camera")
    val NEUILLY_253 = Room("neu253", "Neuilly 253 Lab", 60, "classroom", "rien")
    val NEUILLY_253_T = Room("neu253_t", "Neuilly 253", 120, "theatre", "son")

    val PARIS_241 = Room("d_par241", "Paris 241", 220, "theatre", "camera")
    val PARIS_242AB_T = Room("par242AB", "Paris 242-AB", 280, "theatre", "camera")
    val PARIS_242A = Room("par242A", "Paris 242A Lab", 60, "classroom", "rien")
    val PARIS_242B = Room("par242B", "Paris 242B Lab", 60, "classroom", "rien")
    val PARIS_242A_T = Room("par242AT", "Paris 242A", 120, "theatre", "rien")
    val PARIS_242B_T = Room("par242BT", "Paris 242B", 120, "theatre", "rien")

    val PARIS_243 = Room("par243", "Paris 243", 60, "classroom", "rien")
    val PARIS_243_T = Room("par243_t", "Paris 243", 120, "theatre", "son")

    val PARIS_202_203 = Room("par202_203", "Paris 202-203 Lab", 32, "classroom", "rien")
    val PARIS_221M_222M = Room("par221M-222M", "Paris 221M-222M Lab", 32, "classroom", "rien")
    val PARIS_224M_225M = Room("par224M-225M", "Paris 224M-225M Lab", 26, "classroom", "rien")

    val PARIS_204 = Room("par204", "Paris 204", 16, "classroom", "rien")
    val PARIS_201 = Room("par201", "Paris 201", 14, "classroom", "rien")

    val ROOM_OTHER = Room("other_room", "Autres salles", 100, "classroom", "rien")

    val allRooms = List(HALL_EXPO, HALL_A, AMPHI_BLEU, MAILLOT, PARIS_241, NEUILLY_251, NEUILLY_252AB,
      PARIS_242A, PARIS_242B, PARIS_243, PARIS_243_T, NEUILLY_253, NEUILLY_253_T,
      PARIS_202_203, PARIS_221M_222M, PARIS_224M_225M, PARIS_204, PARIS_201)

    val allRoomsUni = List(AMPHI_BLEU, MAILLOT, NEUILLY_251, NEUILLY_252AB, PARIS_241)

    val allRoomsTIAWed = allRoomsUni ++ List(PARIS_242A_T, PARIS_242B_T, PARIS_243, NEUILLY_253)
    val allRoomsTIAThu = allRoomsUni ++ List(PARIS_242AB_T, PARIS_243_T, NEUILLY_253_T)

    val allRoomsLabsWednesday = List(PARIS_242A, PARIS_242B, PARIS_243, NEUILLY_253) ++ List(PARIS_202_203, PARIS_221M_222M, PARIS_224M_225M)
    val allRoomsLabThursday = List(PARIS_202_203, PARIS_221M_222M, PARIS_224M_225M)

    val allRoomsBOF = List(PARIS_242AB_T, NEUILLY_252AB, PARIS_241, NEUILLY_251, PARIS_243_T, NEUILLY_253_T, PARIS_202_203, PARIS_221M_222M, PARIS_224M_225M)

    val keynoteRoom = List(AMPHI_BLEU)

    val allRoomsConf = List(AMPHI_BLEU, MAILLOT, PARIS_242AB_T, NEUILLY_252AB, PARIS_241, NEUILLY_251, PARIS_243_T, NEUILLY_253_T)
    val allRoomsQuickiesThu = allRoomsConf.filterNot(_.id == AMPHI_BLEU.id) ++ List(PARIS_202_203)
    val allRoomsQuickiesFriday = allRoomsConf.filterNot(_.id == AMPHI_BLEU.id)
  }

  // TODO if you want to use the Scheduler, you can configure the breaks
  object ConferenceSlotBreaks {
    val registration = SlotBreak("reg", "Registration, Welcome and Breakfast", "Accueil", ConferenceRooms.HALL_EXPO)
    val petitDej = SlotBreak("dej", "Breakfast", "Accueil et petit-déjeuner", ConferenceRooms.HALL_EXPO)
    val coffee = SlotBreak("coffee", "Coffee Break", "Pause café", ConferenceRooms.HALL_EXPO)
    val lunch = SlotBreak("lunch", "Lunch", "Pause déjeuner", ConferenceRooms.HALL_EXPO)
    val shortBreak = SlotBreak("chgt", "Break", "Pause courte", ConferenceRooms.HALL_EXPO)
    val exhibition = SlotBreak("exhib", "Exhibition", "Exhibition", ConferenceRooms.HALL_EXPO)
    val meetAndGreet = SlotBreak("meet", "Meet & Greet", "Exhibition", ConferenceRooms.HALL_EXPO)
  }

  // TODO The idea here is to describe in term of Agenda, for each rooms, the slots. This is required only for the Scheduler
  object ConferenceSlots {

    // UNIVERSITY
    val universitySlotsWednesday: List[Slot] = {
      val universityWednesdayMorning = ConferenceRooms.allRoomsUni.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.UNI.id, "wednesday",
            new DateTime("2016-04-08T09:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime("2016-04-08T12:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r1)
      }
      val universityWednesdayAfternoon = ConferenceRooms.allRoomsUni.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.UNI.id, "wednesday",
            new DateTime("2016-04-08T13:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime("2016-04-08T16:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r2)
      }
      universityWednesdayMorning ++ universityWednesdayAfternoon
    }

    // TOOLS IN ACTION
    val tiaSlotsWednesday: List[Slot] = {

      val toolsWednesdayAfternoonSlot1 = ConferenceRooms.allRoomsTIAWed.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.TIA.id, "wednesday",
            new DateTime("2016-04-08T17:10:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime("2016-04-08T17:40:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r1)
      }
      val toolsWednesdayAfternoonSlot2 = ConferenceRooms.allRoomsTIAWed.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.TIA.id, "wednesday",
            new DateTime("2016-04-08T17:55:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime("2016-04-08T18:25:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r2)
      }
      val toolsWednesdayAfternoonSlot3 = ConferenceRooms.allRoomsTIAWed.map {
        r3 =>
          SlotBuilder(ConferenceProposalTypes.TIA.id, "wednesday",
            new DateTime("2016-04-08T18:40:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime("2016-04-08T19:10:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r3)
      }
      toolsWednesdayAfternoonSlot1 ++ toolsWednesdayAfternoonSlot2 ++ toolsWednesdayAfternoonSlot3
    }

    val tiaSlotsThursday: List[Slot] = {
      val toolsThursdayAfternoonSlot1 = ConferenceRooms.allRoomsTIAThu.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.TIA.id, "thursday",
            new DateTime("2016-04-09T18:45:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime("2016-04-09T19:15:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r1)
      }
      toolsThursdayAfternoonSlot1
    }

    // HANDS ON LABS
    val labsSlotsWednesday: List[Slot] = {
      val labsWednesdayMorning = ConferenceRooms.allRoomsLabsWednesday.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.LAB.id, "wednesday",
            new DateTime("2016-04-08T09:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime("2016-04-08T12:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r1)
      }
      val labsWednesdayAfternoon = ConferenceRooms.allRoomsLabsWednesday.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.LAB.id, "wednesday",
            new DateTime("2016-04-08T13:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime("2016-04-08T16:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r2)
      }
      labsWednesdayMorning ++ labsWednesdayAfternoon
    }

    val labsSlotsThursday: List[Slot] = {

      val labsThursdayMorning = ConferenceRooms.allRoomsLabThursday.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.LAB.id, "thursday",
            new DateTime("2016-04-09T13:00:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime("2016-04-09T16:00:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r1)
      }
      val labsThursdayAfternoon = ConferenceRooms.allRoomsLabThursday.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.LAB.id, "thursday",
            new DateTime("2016-04-09T16:15:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime("2016-04-09T19:15:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r2)
      }
      labsThursdayMorning ++ labsThursdayAfternoon
    }

    val labsSlotsFriday: List[Slot] = {

      val labsFridayMorning = ConferenceRooms.allRoomsLabThursday.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.LAB.id, "friday",
            new DateTime("2016-04-10T11:00:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime("2016-04-10T13:50:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r1)
      }
      val labsFridayAfternoon = ConferenceRooms.allRoomsLabThursday.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.LAB.id, "friday",
            new DateTime("2016-04-10T14:25:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime("2016-04-10T17:25:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r2)
      }
      labsFridayMorning ++ labsFridayAfternoon
    }

    // BOFS
    val bofSlotsThursday: List[Slot] = {

      val bofThursdayEveningSlot1 = ConferenceRooms.allRoomsBOF.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.BOF.id, "thursday",
            new DateTime("2016-04-09T19:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime("2016-04-09T20:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r1)
      }
      val bofThursdayEveningSlot2 = ConferenceRooms.allRoomsBOF.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.BOF.id, "thursday",
            new DateTime("2016-04-09T20:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime("2016-04-09T21:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r2)
      }
      val bofThursdayEveningSlot3 = ConferenceRooms.allRoomsBOF.map {
        r3 =>
          SlotBuilder(ConferenceProposalTypes.BOF.id, "thursday",
            new DateTime("2016-04-09T21:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime("2016-04-09T22:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r3)
      }
      bofThursdayEveningSlot1 ++ bofThursdayEveningSlot2 ++ bofThursdayEveningSlot3
    }

    // QUICKIES
    val quickiesSlotsThursday: List[Slot] = {
      val quickiesThursdayLunch1 = ConferenceRooms.allRoomsQuickiesThu.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.QUICK.id, "thursday",
            new DateTime("2016-04-09T12:10:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime("2016-04-09T12:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r1)
      }
      val quickiesThursdayLunch2 = ConferenceRooms.allRoomsQuickiesThu.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.QUICK.id, "thursday",
            new DateTime("2016-04-09T12:35:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime("2016-04-09T12:55:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r2)
      }
      quickiesThursdayLunch1 ++ quickiesThursdayLunch2
    }

    val quickiesSlotsFriday: List[Slot] = {
      val quickFriday1 = ConferenceRooms.allRoomsQuickiesFriday.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.QUICK.id, "friday",
            new DateTime("2016-04-10T12:10:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime("2016-04-10T12:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r1)
      }
      val quickFriday2 = ConferenceRooms.allRoomsQuickiesFriday.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.QUICK.id, "friday",
            new DateTime("2016-04-10T12:35:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime("2016-04-10T12:55:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r2)
      }
      quickFriday1 ++ quickFriday2
    }

    // CONFERENCE KEYNOTES
    val keynoteSlotsThursday: List[Slot] = {
      val keynoteThursdaySlot1 = ConferenceRooms.keynoteRoom.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.KEY.id, "thursday",
            new DateTime("2016-04-09T09:00:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime("2016-04-09T09:20:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r1)
      }
      val keynoteThursdaySlot2 = ConferenceRooms.keynoteRoom.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.KEY.id, "thursday",
            new DateTime("2016-04-09T09:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime("2016-04-09T09:50:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r2)
      }
      val keynoteThursdaySlot3 = ConferenceRooms.keynoteRoom.map {
        r3 =>
          SlotBuilder(ConferenceProposalTypes.KEY.id, "thursday",
            new DateTime("2016-04-09T10:00:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime("2016-04-09T10:20:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r3)
      }
      val keynoteThursdaySlot4 = ConferenceRooms.keynoteRoom.map {
        r4 =>
          SlotBuilder(ConferenceProposalTypes.KEY.id, "thursday",
            new DateTime("2016-04-09T10:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime("2016-04-09T10:50:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r4)
      }

      keynoteThursdaySlot1 ++ keynoteThursdaySlot2 ++ keynoteThursdaySlot3 ++ keynoteThursdaySlot4
    }

    val keynoteSlotsFriday: List[Slot] = {
      val keynoteFridaySlot1 = ConferenceRooms.keynoteRoom.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.KEY.id, "friday",
            new DateTime("2016-04-10T09:00:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime("2016-04-10T09:20:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r1)
      }
      val keynoteFridaySlot2 = ConferenceRooms.keynoteRoom.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.KEY.id, "friday",
            new DateTime("2016-04-10T09:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime("2016-04-10T09:50:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r2)
      }
      val keynoteFridaySlot3 = ConferenceRooms.keynoteRoom.map {
        r3 =>
          SlotBuilder(ConferenceProposalTypes.KEY.id, "friday",
            new DateTime("2016-04-10T10:00:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime("2016-04-10T10:20:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r3)
      }
      val keynoteFridaySlot4 = ConferenceRooms.keynoteRoom.map {
        r4 =>
          SlotBuilder(ConferenceProposalTypes.KEY.id, "friday",
            new DateTime("2016-04-10T10:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime("2016-04-10T10:50:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r4)
      }

      keynoteFridaySlot1 ++ keynoteFridaySlot2 ++ keynoteFridaySlot3 ++ keynoteFridaySlot4

    }

    // CONFERENCE SLOTS
    val conferenceSlotsThursday: List[Slot] = {

      val conferenceThursdaySlot1 = ConferenceRooms.allRoomsConf.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id, "thursday",
            new DateTime("2016-04-09T11:15:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime("2016-04-09T12:05:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r1)
      }
      val conferenceThursdaySlot2 = ConferenceRooms.allRoomsConf.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id, "thursday",
            new DateTime("2016-04-09T13:00:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime("2016-04-09T13:50:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r2)
      }
      val conferenceThursdaySlot3 = ConferenceRooms.allRoomsConf.map {
        r3 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id, "thursday",
            new DateTime("2016-04-09T14:05:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime("2016-04-09T14:55:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r3)
      }
      val conferenceThursdaySlot4 = ConferenceRooms.allRoomsConf.map {
        r4 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id, "thursday",
            new DateTime("2016-04-09T15:10:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime("2016-04-09T16:00:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r4)
      }
      val conferenceThursdaySlot5 = ConferenceRooms.allRoomsConf.map {
        r5 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id, "thursday",
            new DateTime("2016-04-09T16:35:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime("2016-04-09T17:25:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r5)
      }
      val conferenceThursdaySlot6 = ConferenceRooms.allRoomsConf.map {
        r6 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id, "thursday",
            new DateTime("2016-04-09T17:40:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime("2016-04-09T18:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r6)
      }
      conferenceThursdaySlot1 ++ conferenceThursdaySlot2 ++ conferenceThursdaySlot3 ++ conferenceThursdaySlot4 ++ conferenceThursdaySlot5 ++ conferenceThursdaySlot6
    }

    val conferenceSlotsFriday: List[Slot] = {

      val conferenceFridaySlot1 = ConferenceRooms.allRoomsConf.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id, "friday",
            new DateTime("2016-04-10T11:15:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime("2016-04-10T12:05:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r1)
      }
      val conferenceFridaySlot2 = ConferenceRooms.allRoomsConf.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id, "friday",
            new DateTime("2016-04-10T13:00:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime("2016-04-10T13:50:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r2)
      }
      val conferenceFridaySlot3 = ConferenceRooms.allRoomsConf.map {
        r3 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id, "friday",
            new DateTime("2016-04-10T14:05:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime("2016-04-10T14:55:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r3)
      }
      val conferenceFridaySlot4 = ConferenceRooms.allRoomsConf.map {
        r4 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id, "friday",
            new DateTime("2016-04-10T15:10:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime("2016-04-10T16:00:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r4)
      }
      val conferenceFridaySlot5 = ConferenceRooms.allRoomsConf.map {
        r5 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id, "friday",
            new DateTime("2016-04-10T16:35:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime("2016-04-10T17:25:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r5)
      }

      // Cast codeur
      val conferenceFridaySlot6 = List(ConferenceRooms.MAILLOT).map {
        rcc =>
          SlotBuilder(ConferenceProposalTypes.CONF.id, "friday",
            new DateTime("2016-04-10T17:40:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime("2016-04-10T18:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), rcc)
      }

      conferenceFridaySlot1 ++ conferenceFridaySlot2 ++ conferenceFridaySlot3 ++ conferenceFridaySlot4 ++ conferenceFridaySlot5 ++ conferenceFridaySlot6
    }

    // Registration, coffee break, lunch etc
    val wednesdayBreaks = List(
      SlotBuilder(ConferenceSlotBreaks.registration, "wednesday",
        new DateTime("2016-04-08T08:00:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
        new DateTime("2016-04-08T09:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris"))), SlotBuilder(ConferenceSlotBreaks.lunch, "wednesday",
        new DateTime("2016-04-08T12:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
        new DateTime("2016-04-08T13:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris"))), SlotBuilder(ConferenceSlotBreaks.coffee, "wednesday",
        new DateTime("2016-04-08T16:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
        new DateTime("2016-04-08T17:10:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris"))))

    val thursdayBreaks = List(
      SlotBuilder(ConferenceSlotBreaks.registration, "thursday",
        new DateTime("2016-04-09T07:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
        new DateTime("2016-04-09T09:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris"))), SlotBuilder(ConferenceSlotBreaks.shortBreak, "thursday",
        new DateTime("2016-04-09T10:50:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
        new DateTime("2016-04-09T11:15:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris"))), SlotBuilder(ConferenceSlotBreaks.lunch, "thursday",
        new DateTime("2016-04-09T12:05:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
        new DateTime("2016-04-09T13:00:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris"))), SlotBuilder(ConferenceSlotBreaks.coffee, "thursday",
        new DateTime("2016-04-09T16:00:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
        new DateTime("2016-04-09T16:35:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris"))) //      , SlotBuilder(ConferenceSlotBreaks.meetAndGreet, "thursday",
        //        new DateTime("2016-04-09T19:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
        //        new DateTime("2016-04-09T22:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")))
        )

    val fridayBreaks = List(
      SlotBuilder(ConferenceSlotBreaks.petitDej, "friday",
        new DateTime("2016-04-10T08:00:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
        new DateTime("2016-04-10T09:00:00.000+02:00")), SlotBuilder(ConferenceSlotBreaks.coffee, "friday",
        new DateTime("2016-04-10T10:45:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
        new DateTime("2016-04-10T11:15:00.000+02:00")), SlotBuilder(ConferenceSlotBreaks.lunch, "friday",
        new DateTime("2016-04-10T12:05:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
        new DateTime("2016-04-10T12:55:00.000+02:00")), SlotBuilder(ConferenceSlotBreaks.coffee, "friday",
        new DateTime("2016-04-10T16:00:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
        new DateTime("2016-04-10T16:35:00.000+02:00")))
    // DEVOXX DAYS

    val wednesday: List[Slot] = {
      wednesdayBreaks ++ universitySlotsWednesday ++ tiaSlotsWednesday ++ labsSlotsWednesday
    }

    val thursday: List[Slot] = {
      thursdayBreaks ++ keynoteSlotsThursday ++ conferenceSlotsThursday ++ quickiesSlotsThursday ++ bofSlotsThursday ++ labsSlotsThursday ++ tiaSlotsThursday
    }

    val friday: List[Slot] = {
      fridayBreaks ++ keynoteSlotsFriday ++ conferenceSlotsFriday ++ quickiesSlotsFriday ++ labsSlotsFriday
    }

    // COMPLETE DEVOXX
    def all: List[Slot] = {
      wednesday ++ thursday ++ friday
    }
  }

  // TODO You might want to start here and configure first, your various Conference Elements
  def current() = ConferenceDescriptor(
    eventCode = "DevoxxFR2016",
    // You will need to update conf/routes files with this code if modified
    confUrlCode = "devoxxfr2016",
    frLangEnabled = true,
    fromEmail = Play.current.configuration.getString("mail.from").getOrElse("program@devoxx.fr"),
    committeeEmail = Play.current.configuration.getString("mail.committee.email").getOrElse("program@devoxx.fr"),
    bccEmail = Play.current.configuration.getString("mail.bcc"),
    bugReportRecipient = Play.current.configuration.getString("mail.bugreport.recipient").getOrElse("nicolas.martignole@devoxx.fr"),
    conferenceUrls = ConferenceUrls(
      faq = "http://www.devoxx.fr/faq/",
      registration = "https://reg.devoxx.fr",
      confWebsite = "http://www.devoxx.fr/",
      cfpHostname = Play.current.configuration.getString("cfp.hostname").getOrElse("cfp.devoxx.fr")),
    timing = ConferenceTiming(
      datesI18nKey = "20 au 22 avril 2016",
      speakersPassDuration = 5,
      preferredDayEnabled = true,
      firstDayFr = "20 avril",
      firstDayEn = "april 20th",
      datesFr = "du 20 au 22 avril 2016",
      datesEn = "from 20th to 22nd of April, 2016",
      cfpOpenedOn = DateTime.parse("2015-11-11T00:00:00+02:00"),
      cfpClosedOn = DateTime.parse("2016-01-31T09:00:00+02:00"),
      scheduleAnnouncedOn = DateTime.parse("2016-02-25T00:00:00+02:00")),
    hosterName = "Clever-cloud", hosterWebsite = "http://www.clever-cloud.com/#DevoxxFR",
    hashTag = "#DevoxxFR",
    conferenceSponsor = ConferenceSponsor(showSponsorProposalCheckbox = true, sponsorProposalType = ConferenceProposalTypes.CONF), List("fr_FR"), "Palais des Congrès, Porte Maillot, Paris", notifyProposalSubmitted = false // Do not send an email for each talk submitted for France
    , 1200 // French developers tends to be a bit verbose... we need extra space :-)
    )

  def isConferenceOpen() = {
    val tim = current().timing
    val now = DateTime.now
    now.isAfter(tim.cfpOpenedOn) && now.isBefore(tim.cfpClosedOn)
  }

  // It has to be a def, not a val, else it is not re-evaluated
  def isCFPOpen: Boolean = {
    Play.current.configuration.getBoolean("cfp.isOpen").getOrElse(false)
  }

  def isGoldenTicketActive: Boolean = Play.current.configuration.getBoolean("goldenTicket.active").getOrElse(false)

}

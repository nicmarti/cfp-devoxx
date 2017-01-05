package models

import java.util.Locale

import org.joda.time.{DateTime, DateTimeZone, Period}
import play.api.Play

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
  def cfpURL(): String = {
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
                             scheduleAnnouncedOn: DateTime,
                             days: Iterator[DateTime]
                           )

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
                                locale: List[Locale],
                                localisation: String,
                                maxProposalSummaryCharacters: Int = 1200
                               )

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
      case "conf" => CONF
      case "uni" => UNI
      case "tia" => TIA
      case "lab" => LAB
      case "quick" => QUICK
      case "bof" => BOF
      case "key" => KEY
      case "ignite" => IGNITE
      case "other" => OTHER
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
  object ConferenceTracks {
    val JAVA = Track("java", "java.label")
    val MOBILE = Track("mobile", "mobile.label")
    val WEB = Track("wm", "web.label")
    val ARCHISEC = Track("archisec", "archisec.label")
    val CLOUD = Track("cldops", "cloud.label")
    val AGILE_DEVOPS = Track("agTest", "agile_devops.label")
    val BIGDATA = Track("bigd", "bigdata.label")
    val FUTURE = Track("future", "future.label")
    val LANG = Track("lang", "lang.label")
    val UNKNOWN = Track("unknown", "unknown track")
    val ALL = List(JAVA, MOBILE, WEB, ARCHISEC, AGILE_DEVOPS, CLOUD, BIGDATA, FUTURE, LANG, UNKNOWN)
  }

  // TODO configure the description for each Track
  object ConferenceTracksDescription {
    val JAVA = TrackDesc(ConferenceTracks.JAVA.id, "/assets/dvfr2015/images/icon_javase.png", "track.java.title", "track.java.desc")
    val MOBILE = TrackDesc(ConferenceTracks.MOBILE.id, "/assets/dvfr2015/images/icon_web.png", "track.mobile.title", "track.mobile.desc")
    val WEB = TrackDesc(ConferenceTracks.WEB.id, "/assets/dvfr2015/images/icon_web.png", "track.web.title", "track.web.desc")
    val ARCHISEC = TrackDesc(ConferenceTracks.ARCHISEC.id, "/assets/dvfr2015/images/icon_architecture.png", "track.archisec.title", "track.archisec.desc")
    val AGILE_DEVOPS = TrackDesc(ConferenceTracks.AGILE_DEVOPS.id, "/assets/dvfr2015/images/icon_startup.png", "track.agile_devops.title", "track.agile_devops.desc")
    val CLOUD = TrackDesc(ConferenceTracks.CLOUD.id, "/assets/dvfr2015/images/icon_cloud.png", "track.cloud.title", "track.cloud.desc")
    val BIGDATA = TrackDesc(ConferenceTracks.BIGDATA.id, "/assets/dvfr2015/images/icon_mobile.png", "track.bigdata.title", "track.bigdata.desc")
    val FUTURE = TrackDesc(ConferenceTracks.FUTURE.id, "/assets/dvfr2015/images/icon_future.png", "track.future.title", "track.future.desc")
    val LANG = TrackDesc(ConferenceTracks.LANG.id, "/assets/dvfr2015/images/icon_alternative.png", "track.lang.title", "track.lang.desc")

    val ALL = List(JAVA, MOBILE, WEB, ARCHISEC, AGILE_DEVOPS, CLOUD, BIGDATA, FUTURE, LANG)

    def findTrackDescFor(t: Track): TrackDesc = {
      ALL.find(_.id == t.id).getOrElse(JAVA)
    }
  }

  // TODO If you want to use the Devoxx Scheduler, you can describe here the list of rooms, with capacity for seats
  object ConferenceRooms {

    // Tip : I use the ID to sort-by on the view per day... So if the exhibition floor id is "aaa" it will be
    // the first column on the HTML Table

    // Do not change the ID's once the program is published
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

    val NEUILLY_212_213 = Room("neu_212_213", "Neuilly 212-213M Lab", 32, "classroom", "rien")
    val NEUILLY_231_232 = Room("neu_232_232", "Neuilly 231-232M Lab", 32, "classroom", "rien")
    val NEUILLY_234_235 = Room("neu_234_235", "Neuilly 234_234M Lab", 32, "classroom", "rien")

    val PARIS_204 = Room("par204", "Paris 204", 16, "classroom", "rien")
    val PARIS_201 = Room("par201", "Paris 201", 14, "classroom", "rien")

    val ROOM_OTHER = Room("other_room", "Autres salles", 100, "classroom", "rien")

    val allRooms = List(HALL_EXPO, HALL_A, AMPHI_BLEU, MAILLOT, PARIS_241, NEUILLY_251, NEUILLY_252AB,
      PARIS_242A, PARIS_242B, PARIS_243, PARIS_243_T, NEUILLY_253, NEUILLY_253_T,
      PARIS_202_203, PARIS_221M_222M, PARIS_224M_225M, PARIS_204, PARIS_201)

    val allRoomsUni = List(AMPHI_BLEU, MAILLOT, NEUILLY_251, PARIS_241, NEUILLY_252AB)

    val allRoomsTIAWed = allRoomsUni ++ List(PARIS_242A_T, PARIS_242B_T, PARIS_243, NEUILLY_253)
    val allRoomsTIAThu = allRoomsUni ++ List(PARIS_242AB_T, PARIS_243_T, NEUILLY_253_T)

    val allRoomsLabsWednesday = List(PARIS_242A, PARIS_242B, PARIS_243, NEUILLY_253) ++ List(PARIS_202_203, PARIS_221M_222M, PARIS_224M_225M, NEUILLY_231_232, NEUILLY_234_235)
    val allRoomsLabThursday = List(PARIS_202_203, PARIS_221M_222M, PARIS_224M_225M)
    val allRoomsLabFriday = List(PARIS_202_203, PARIS_221M_222M, PARIS_224M_225M, NEUILLY_212_213, NEUILLY_231_232, NEUILLY_234_235)

    val allRoomsBOF = List(PARIS_242AB_T, NEUILLY_252AB, PARIS_241, NEUILLY_251, PARIS_243_T, NEUILLY_253_T, PARIS_202_203) // PARIS_221M_222M, PARIS_224M_225M

    val keynoteRoom = List(AMPHI_BLEU)

    val allRoomsConf = List(AMPHI_BLEU, MAILLOT, PARIS_242AB_T, NEUILLY_252AB, PARIS_241, NEUILLY_251, PARIS_243_T, NEUILLY_253_T)

    val allRoomsQuickiesThu = allRoomsConf.filterNot(r => r.id == AMPHI_BLEU.id || r.id == MAILLOT.id) ++ List(PARIS_202_203)
    // Retire les Ignites
    val allRoomsQuickiesFriday = allRoomsConf.filterNot(r => r.id == AMPHI_BLEU.id || r.id == MAILLOT.id)
  }


  // TODO if you want to use the Scheduler, you can configure the breaks
  object ConferenceSlotBreaks {
    val registration = SlotBreak("reg", "Registration", "Accueil", ConferenceRooms.HALL_EXPO)
    val petitDej = SlotBreak("dej", "Breakfast", "Accueil et petit-déjeuner", ConferenceRooms.HALL_EXPO)
    val coffee = SlotBreak("coffee", "Coffee Break", "Pause café", ConferenceRooms.HALL_EXPO)
    val lunch = SlotBreak("lunch", "Lunch", "Pause déjeuner", ConferenceRooms.HALL_EXPO)
    val shortBreak = SlotBreak("chgt", "Break", "Pause courte", ConferenceRooms.HALL_EXPO)
    val exhibition = SlotBreak("exhib", "Exhibition", "Exhibition", ConferenceRooms.HALL_EXPO)
    val meetAndGreet = SlotBreak("meet", "Meet & Greet", "Exhibition", ConferenceRooms.HALL_EXPO)
  }

  // TODO The idea here is to describe in term of Agenda, for each rooms, the slots. This is required only for the Scheduler
  object ConferenceSlots {

    val firstDay = "2017-04-05"
    val secondDay = "2017-04-06"
    val thirdDay = "2017-04-07"

    // UNIVERSITY
    val universitySlotsWednesday: List[Slot] = {
      val universityWednesdayMorning = ConferenceRooms.allRoomsUni.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.UNI.id, "wednesday",
            new DateTime(s"${firstDay}T09:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${firstDay}T12:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r1)
      }
      val universityWednesdayAfternoon = ConferenceRooms.allRoomsUni.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.UNI.id, "wednesday",
            new DateTime(s"${firstDay}T13:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${firstDay}T16:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r2)
      }
      universityWednesdayMorning ++ universityWednesdayAfternoon
    }

    // TOOLS IN ACTION
    val tiaSlotsWednesday: List[Slot] = {

      val toolsWednesdayAfternoonSlot1 = ConferenceRooms.allRoomsTIAWed.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.TIA.id, "wednesday",
            new DateTime(s"${firstDay}T17:10:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${firstDay}T17:40:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r1)
      }
      val toolsWednesdayAfternoonSlot2 = ConferenceRooms.allRoomsTIAWed.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.TIA.id, "wednesday",
            new DateTime(s"${firstDay}T17:55:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${firstDay}T18:25:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r2)
      }
      val toolsWednesdayAfternoonSlot3 = ConferenceRooms.allRoomsTIAWed.map {
        r3 =>
          SlotBuilder(ConferenceProposalTypes.TIA.id, "wednesday",
            new DateTime(s"${firstDay}T18:40:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${firstDay}T19:10:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r3)
      }
      toolsWednesdayAfternoonSlot1 ++ toolsWednesdayAfternoonSlot2 ++ toolsWednesdayAfternoonSlot3
    }

    val tiaSlotsThursday: List[Slot] = {
      val toolsThursdayAfternoonSlot1 = ConferenceRooms.allRoomsTIAThu.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.TIA.id, "thursday",
            new DateTime(s"${secondDay}T18:10:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${secondDay}T18:40:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r1)
      }
      val toolsThursdayAfternoonSlot2 = ConferenceRooms.allRoomsTIAThu.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.TIA.id, "thursday",
            new DateTime(s"${secondDay}T18:55:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${secondDay}T19:25:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r1)
      }
      toolsThursdayAfternoonSlot1 ++ toolsThursdayAfternoonSlot2
    }

    // HANDS ON LABS
    val labsSlotsWednesday: List[Slot] = {
      val labsWednesdayMorning = ConferenceRooms.allRoomsLabsWednesday.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.LAB.id, "wednesday",
            new DateTime(s"${firstDay}T09:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${firstDay}T12:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r1)
      }
      val labsWednesdayAfternoon = ConferenceRooms.allRoomsLabsWednesday.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.LAB.id, "wednesday",
            new DateTime(s"${firstDay}T13:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${firstDay}T16:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r2)
      }
      labsWednesdayMorning ++ labsWednesdayAfternoon
    }

    val labsSlotsThursday: List[Slot] = {

      val labsThursdayMorning = ConferenceRooms.allRoomsLabThursday.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.LAB.id, "thursday",
            new DateTime(s"${secondDay}T12:55:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${secondDay}T15:55:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r1)
      }
      val labsThursdayAfternoon = ConferenceRooms.allRoomsLabThursday.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.LAB.id, "thursday",
            new DateTime(s"${secondDay}T16:10:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${secondDay}T19:10:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r2)
      }
      labsThursdayMorning ++ labsThursdayAfternoon
    }

    val labsSlotsFriday: List[Slot] = {

      val labsFridayMorning = ConferenceRooms.allRoomsLabFriday.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.LAB.id, "friday",
            new DateTime(s"${thirdDay}T11:00:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${thirdDay}T13:40:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r1)
      }
      labsFridayMorning
    }

    // BOFS
    val bofSlotsThursday: List[Slot] = {

      val bofThursdayEveningSlot1 = ConferenceRooms.allRoomsBOF.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.BOF.id, "thursday",
            new DateTime(s"${secondDay}T19:45:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${secondDay}T20:35:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r1)
      }
      val bofThursdayEveningSlot2 = ConferenceRooms.allRoomsBOF.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.BOF.id, "thursday",
            new DateTime(s"${secondDay}T20:40:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${secondDay}T21:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r2)
      }
      val bofThursdayEveningSlot3 = ConferenceRooms.allRoomsBOF.map {
        r3 =>
          SlotBuilder(ConferenceProposalTypes.BOF.id, "thursday",
            new DateTime(s"${secondDay}T21:35:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${secondDay}T22:25:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r3)
      }
      bofThursdayEveningSlot1 ++ bofThursdayEveningSlot2 ++ bofThursdayEveningSlot3
    }

    // QUICKIES
    val quickiesSlotsThursday: List[Slot] = {
      val quickiesThursdayLunch1 = ConferenceRooms.allRoomsQuickiesThu.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.QUICK.id, "thursday",
            new DateTime(s"${secondDay}T12:05:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${secondDay}T12:20:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r1)
      }
      val quickiesThursdayLunch2 = ConferenceRooms.allRoomsQuickiesThu.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.QUICK.id, "thursday",
            new DateTime(s"${secondDay}T12:25:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${secondDay}T12:40:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r2)
      }
      quickiesThursdayLunch1 ++ quickiesThursdayLunch2
    }

    val quickiesSlotsFriday: List[Slot] = {
      val quickFriday1 = ConferenceRooms.allRoomsQuickiesFriday.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.QUICK.id, "friday",
            new DateTime(s"${thirdDay}T12:05:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${thirdDay}T12:20:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r1)
      }
      val quickFriday2 = ConferenceRooms.allRoomsQuickiesFriday.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.QUICK.id, "friday",
            new DateTime(s"${thirdDay}T12:25:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${thirdDay}T12:40:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r2)
      }
      quickFriday1 ++ quickFriday2
    }

    // CONFERENCE KEYNOTES
    val keynoteSlotsThursday: List[Slot] = {
      val keynoteThursdayWelcome = ConferenceRooms.keynoteRoom.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.KEY.id, "thursday",
            new DateTime(s"${secondDay}T09:00:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${secondDay}T09:15:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r1)
      }
      val keynoteThursdaySlot1 = ConferenceRooms.keynoteRoom.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.KEY.id, "thursday",
            new DateTime(s"${secondDay}T09:20:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${secondDay}T09:40:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r1)
      }
      val keynoteThursdaySlot2 = ConferenceRooms.keynoteRoom.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.KEY.id, "thursday",
            new DateTime(s"${secondDay}T09:45:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${secondDay}T10:05:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r2)
      }
      val keynoteThursdaySlot3 = ConferenceRooms.keynoteRoom.map {
        r3 =>
          SlotBuilder(ConferenceProposalTypes.KEY.id, "thursday",
            new DateTime(s"${secondDay}T10:10:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${secondDay}T10:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r3)
      }

      keynoteThursdayWelcome ++ keynoteThursdaySlot1 ++ keynoteThursdaySlot2 ++ keynoteThursdaySlot3
    }

    val keynoteSlotsFriday: List[Slot] = {
      val keynoteFridaySlot1 = ConferenceRooms.keynoteRoom.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.KEY.id, "friday",
            new DateTime(s"${thirdDay}T09:00:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${thirdDay}T09:20:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r1)
      }
      val keynoteFridaySlot2 = ConferenceRooms.keynoteRoom.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.KEY.id, "friday",
            new DateTime(s"${thirdDay}T09:25:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${thirdDay}T09:45:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r2)
      }
      val keynoteFridaySlot3 = ConferenceRooms.keynoteRoom.map {
        r3 =>
          SlotBuilder(ConferenceProposalTypes.KEY.id, "friday",
            new DateTime(s"${thirdDay}T09:50:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${thirdDay}T10:10:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r3)
      }
      val keynoteFridaySlot4 = ConferenceRooms.keynoteRoom.map {
        r4 =>
          SlotBuilder(ConferenceProposalTypes.KEY.id, "friday",
            new DateTime(s"${thirdDay}T10:15:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${thirdDay}T10:35:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r4)
      }

      keynoteFridaySlot1 ++ keynoteFridaySlot2 ++ keynoteFridaySlot3 ++ keynoteFridaySlot4

    }

    // CONFERENCE SLOTS
    val conferenceSlotsThursday: List[Slot] = {

      val conferenceThursdaySlot1 = ConferenceRooms.allRoomsConf.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id, "thursday",
            new DateTime(s"${secondDay}T11:15:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${secondDay}T12:00:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r1)
      }
      val conferenceThursdaySlot2 = ConferenceRooms.allRoomsConf.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id, "thursday",
            new DateTime(s"${secondDay}T12:55:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${secondDay}T13:40:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r2)
      }
      val conferenceThursdaySlot3 = ConferenceRooms.allRoomsConf.map {
        r3 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id, "thursday",
            new DateTime(s"${secondDay}T13:55:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${secondDay}T14:40:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r3)
      }
      val conferenceThursdaySlot4 = ConferenceRooms.allRoomsConf.map {
        r4 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id, "thursday",
            new DateTime(s"${secondDay}T14:55:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${secondDay}T15:40:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r4)
      }

      val conferenceThursdaySlot5 = ConferenceRooms.allRoomsConf.map {
        r5 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id, "thursday",
            new DateTime(s"${secondDay}T16:10:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${secondDay}T16:55:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r5)
      }

      val conferenceThursdaySlot6 = ConferenceRooms.allRoomsConf.map {
        r6 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id, "thursday",
            new DateTime(s"${secondDay}T17:10:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${secondDay}T17:55:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r6)
      }
      conferenceThursdaySlot1 ++ conferenceThursdaySlot2 ++ conferenceThursdaySlot3 ++ conferenceThursdaySlot4 ++ conferenceThursdaySlot5 ++ conferenceThursdaySlot6
    }

    val conferenceSlotsFriday: List[Slot] = {

      val conferenceFridaySlot1 = ConferenceRooms.allRoomsConf.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id, "friday",
            new DateTime(s"${thirdDay}T11:15:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${thirdDay}T12:00:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r1)
      }
      val conferenceFridaySlot2 = ConferenceRooms.allRoomsConf.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id, "friday",
            new DateTime(s"${thirdDay}T12:55:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${thirdDay}T13:40:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r2)
      }
      val conferenceFridaySlot3 = ConferenceRooms.allRoomsConf.map {
        r3 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id, "friday",
            new DateTime(s"${thirdDay}T13:55:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${thirdDay}T14:40:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r3)
      }
      val conferenceFridaySlot4 = ConferenceRooms.allRoomsConf.map {
        r4 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id, "friday",
            new DateTime(s"${thirdDay}T14:55:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${thirdDay}T15:40:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r4)
      }
      val conferenceFridaySlot5 = ConferenceRooms.allRoomsConf.map {
        r5 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id, "friday",
            new DateTime(s"${thirdDay}T16:10:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${thirdDay}T16:55:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r5)
      }
      val conferenceFridaySlot5extra = ConferenceRooms.allRoomsConf.map {
        r5 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id, "friday",
            new DateTime(s"${thirdDay}T17:10:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${thirdDay}T17:55:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r5)
      }

      // Cast codeur
      val conferenceFridaySlot6 = List(ConferenceRooms.NEUILLY_252AB).map {
        rcc =>
          SlotBuilder(ConferenceProposalTypes.CONF.id, "friday",
            new DateTime(s"${thirdDay}T18:10:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${thirdDay}T18:55:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), rcc)
      }

      conferenceFridaySlot1 ++ conferenceFridaySlot2 ++ conferenceFridaySlot3 ++ conferenceFridaySlot4 ++ conferenceFridaySlot5 ++ conferenceFridaySlot5extra ++ conferenceFridaySlot6
    }

    // Registration, coffee break, lunch etc
    val wednesdayBreaks = List(
      SlotBuilder(ConferenceSlotBreaks.registration, "wednesday",
        new DateTime(s"${firstDay}T08:00:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
        new DateTime(s"${firstDay}T09:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")))
      , SlotBuilder(ConferenceSlotBreaks.lunch, "wednesday",
        new DateTime(s"${firstDay}T12:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
        new DateTime(s"${firstDay}T13:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")))
      , SlotBuilder(ConferenceSlotBreaks.coffee, "wednesday",
        new DateTime(s"${firstDay}T16:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
        new DateTime(s"${firstDay}T17:10:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")))
    )

    val thursdayBreaks = List(
      SlotBuilder(ConferenceSlotBreaks.registration, "thursday",
        new DateTime(s"${secondDay}T07:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
        new DateTime(s"${secondDay}T09:00:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")))
      , SlotBuilder(ConferenceSlotBreaks.shortBreak, "thursday",
        new DateTime(s"${secondDay}T10:45:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
        new DateTime(s"${secondDay}T11:15:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")))
      , SlotBuilder(ConferenceSlotBreaks.lunch, "thursday",
        new DateTime(s"${secondDay}T12:00:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
        new DateTime(s"${secondDay}T12:55:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")))
      , SlotBuilder(ConferenceSlotBreaks.coffee, "thursday",
        new DateTime(s"${secondDay}T15:40:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
        new DateTime(s"${secondDay}T16:00:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")))
    )

    val fridayBreaks = List(
      SlotBuilder(ConferenceSlotBreaks.petitDej, "friday",
        new DateTime(s"${thirdDay}T08:00:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
        new DateTime(s"${thirdDay}T09:00:00.000+02:00"))
      , SlotBuilder(ConferenceSlotBreaks.coffee, "friday",
        new DateTime(s"${thirdDay}T10:45:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
        new DateTime(s"${thirdDay}T11:15:00.000+02:00"))
      , SlotBuilder(ConferenceSlotBreaks.lunch, "friday",
        new DateTime(s"${thirdDay}T12:00:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
        new DateTime(s"${thirdDay}T12:55:00.000+02:00"))
      , SlotBuilder(ConferenceSlotBreaks.coffee, "friday",
        new DateTime(s"${thirdDay}T15:40:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
        new DateTime(s"${thirdDay}T16:10:00.000+02:00"))
    )

    val mondaySchedule: List[Slot] = List.empty[Slot]

    val tuesdaySchedule: List[Slot] = List.empty[Slot]

    val wednesdaySchedule: List[Slot] = {
      wednesdayBreaks ++ universitySlotsWednesday ++ tiaSlotsWednesday ++ labsSlotsWednesday
    }

    val thursdaySchedule: List[Slot] = {
      thursdayBreaks ++ keynoteSlotsThursday ++ conferenceSlotsThursday ++ quickiesSlotsThursday ++ bofSlotsThursday ++ labsSlotsThursday ++ tiaSlotsThursday
    }

    val fridaySchedule: List[Slot] = {
      fridayBreaks ++ keynoteSlotsFriday ++ conferenceSlotsFriday ++ quickiesSlotsFriday ++ labsSlotsFriday
    }

    def all: List[Slot] = {
      mondaySchedule ++ tuesdaySchedule ++ wednesdaySchedule ++ thursdaySchedule ++ fridaySchedule
    }
  }

  def dateRange(from: DateTime, to: DateTime, step: Period): Iterator[DateTime] = Iterator.iterate(from)(_.plus(step)).takeWhile(!_.isAfter(to))

  val fromDay = new DateTime().withYear(2017).withMonthOfYear(4).withDayOfMonth(5)
  val toDay = new DateTime().withYear(2017).withMonthOfYear(4).withDayOfMonth(7)

  // TODO You might want to start here and configure first, your various Conference Elements
  def current() = ConferenceDescriptor(
    eventCode = "DevoxxFR2017",
    // You will need to update conf/routes files with this code if modified
    confUrlCode = "devoxxfr2017",
    frLangEnabled = true,
    fromEmail = Play.current.configuration.getString("mail.from").getOrElse("program@devoxx.fr"),
    committeeEmail = Play.current.configuration.getString("mail.committee.email").getOrElse("program@devoxx.fr"),
    bccEmail = Play.current.configuration.getString("mail.bcc"),
    bugReportRecipient = Play.current.configuration.getString("mail.bugreport.recipient").getOrElse("nicolas.martignole@devoxx.fr"),
    conferenceUrls = ConferenceUrls(
      faq = "http://www.devoxx.fr/faq",
      registration = "https://reg.devoxx.fr",
      confWebsite = "http://www.devoxx.fr/",
      cfpHostname = {
        val h=Play.current.configuration.getString("cfp.hostname").getOrElse("cfp.devoxx.fr")
        if(h.endsWith("/")){
          h.substring(0,h.length - 1)
        }else{
          h
        }
      }
    ),
    timing = ConferenceTiming(
      datesI18nKey = "5 au 7 avril 2017",
      speakersPassDuration = 5,
      preferredDayEnabled = true,
      firstDayFr = "5 avril",
      firstDayEn = "april 5th",
      datesFr = "du 5 au 7 avril 2017",
      datesEn = "from 5th to 7th of April, 2017",
      cfpOpenedOn = DateTime.parse("2016-11-01T00:00:00+02:00"),
      cfpClosedOn = DateTime.parse("2017-01-08T23:59:00+02:00"),
      scheduleAnnouncedOn = DateTime.parse("2017-02-15T00:00:00+02:00"),
      days = dateRange(fromDay, toDay, new Period().withDays(1))
    ),
    hosterName = "Clever-cloud", hosterWebsite = "http://www.clever-cloud.com/#DevoxxFR",
    hashTag = "#DevoxxFR",
    conferenceSponsor = ConferenceSponsor(showSponsorProposalCheckbox = true, sponsorProposalType = ConferenceProposalTypes.CONF)
    , List(Locale.FRENCH)
    , "Palais des Congrès, Porte Maillot, Paris"
    , 1200 // French developers tends to be a bit verbose... we need extra space :-)
  )

  // It has to be a def, not a val, else it is not re-evaluated
  def isCFPOpen: Boolean = {
    Play.current.configuration.getBoolean("cfp.isOpen").getOrElse(false)
  }

  def isGoldenTicketActive: Boolean = Play.current.configuration.getBoolean("goldenTicket.active").getOrElse(false)

  def isTagSystemActive: Boolean = Play.current.configuration.getBoolean("cfp.tags.active").getOrElse(false)

  def isFavoritesSystemActive: Boolean = Play.current.configuration.getBoolean("cfp.activateFavorites").getOrElse(false)

  def isHTTPSEnabled = Play.current.configuration.getBoolean("cfp.activateHTTPS").getOrElse(false)

  // Reset all votes when a Proposal with state=SUBMITTED (or DRAFT) is updated
  // This is to reflect the fact that some speakers are eavluated, then they update the talk, and we should revote for it
  def isResetVotesForSubmitted = Play.current.configuration.getBoolean("cfp.resetVotesForSubmitted").getOrElse(false)

  // Set this to true temporarily
  // I will implement a new feature where each CFP member can decide to receive one digest email per day or a big email
  def notifyProposalSubmitted = true

}


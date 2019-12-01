package models

import java.util.Locale

import org.apache.commons.lang3.RandomStringUtils
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
                                 impliedSelectedTrack: Option[Track] = None,
                                 allowOtherSpeaker: Boolean = true)

object ProposalConfiguration {

  val UNKNOWN = ProposalConfiguration(id = "unknown", slotsCount = 0, givesSpeakerFreeEntrance = false, freeEntranceDisplayed = false,
    htmlClass = "", hiddenInCombo = true, chosablePreferredDay = false)

  def parse(propConf: String): ProposalConfiguration = {
    ConferenceDescriptor.ConferenceProposalConfigurations.ALL.find(p => p.id == propConf).getOrElse(ProposalConfiguration.UNKNOWN)
  }

  def totalSlotsCount: Int = ConferenceDescriptor.ConferenceProposalConfigurations.ALL.map(_.slotsCount).sum

  def isDisplayedFreeEntranceProposals(pt: ProposalType): Boolean = {
    ConferenceDescriptor.ConferenceProposalConfigurations.ALL.filter(p => p.id == pt.id).map(_.freeEntranceDisplayed).headOption.getOrElse(false)
  }

  def getProposalsImplyingATrackSelection: List[ProposalConfiguration] = {
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

  def doesProposalTypeAllowOtherSpeaker(pt: ProposalType): Boolean = {
    ConferenceDescriptor.ConferenceProposalConfigurations.ALL.filter(p => p.id == pt.id).map(_.allowOtherSpeaker).headOption.getOrElse(true)
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

    val OTHER = ProposalType(id = "other", label = "other.label")

    val ALL = List(CONF, UNI, TIA, LAB, QUICK, BOF, KEY, OTHER)

    def valueOf(id: String): ProposalType = id match {
      case "conf" => CONF
      case "uni" => UNI
      case "tia" => TIA
      case "lab" => LAB
      case "quick" => QUICK
      case "bof" => BOF
      case "key" => KEY
      case "other" => OTHER
    }

  }

  // TODO Configure here the slot, with the number of slots available, if it gives a free ticket to the speaker, some CSS icons
  object ConferenceProposalConfigurations {
    val CONF = ProposalConfiguration(id = "conf", slotsCount = ConferenceSlots.all.count(_.name.equals(ConferenceProposalTypes.CONF.id)), givesSpeakerFreeEntrance = true, freeEntranceDisplayed = true, htmlClass = "icon-microphone",
      chosablePreferredDay = true)
    val UNI = ProposalConfiguration(id = "uni", slotsCount = ConferenceSlots.all.count(_.name.equals(ConferenceProposalTypes.UNI.id)), givesSpeakerFreeEntrance = true, freeEntranceDisplayed = true, htmlClass = "icon-laptop",
      chosablePreferredDay = false)
    val TIA = ProposalConfiguration(id = "tia", slotsCount = ConferenceSlots.all.count(_.name.equals(ConferenceProposalTypes.TIA.id)), givesSpeakerFreeEntrance = true, freeEntranceDisplayed = true, htmlClass = "icon-legal",
      chosablePreferredDay = true)
    val LAB = ProposalConfiguration(id = "lab", slotsCount = ConferenceSlots.all.count(_.name.equals(ConferenceProposalTypes.LAB.id)), givesSpeakerFreeEntrance = true, freeEntranceDisplayed = true, htmlClass = "icon-beaker",
      chosablePreferredDay = true)
    val QUICK = ProposalConfiguration(id = "quick", slotsCount = ConferenceSlots.all.count(_.name.equals(ConferenceProposalTypes.QUICK.id)), givesSpeakerFreeEntrance = false, freeEntranceDisplayed = false, htmlClass = "icon-fast-forward",
      chosablePreferredDay = true, allowOtherSpeaker = false)
    val BOF = ProposalConfiguration(id = "bof", slotsCount = ConferenceSlots.all.count(_.name.equals(ConferenceProposalTypes.BOF.id)), givesSpeakerFreeEntrance = false, freeEntranceDisplayed = false, htmlClass = "icon-group",
      chosablePreferredDay = false)
    val KEY = ProposalConfiguration(id = "key", slotsCount = ConferenceSlots.all.count(_.name.equals(ConferenceProposalTypes.KEY.id)), givesSpeakerFreeEntrance = true, freeEntranceDisplayed = false, htmlClass = "icon-microphone",
      chosablePreferredDay = true)
    val OTHER = ProposalConfiguration(id = "other", slotsCount = 1, givesSpeakerFreeEntrance = false, freeEntranceDisplayed = false, htmlClass = "icon-microphone",
      hiddenInCombo = true, chosablePreferredDay = false)

    val ALL = List(CONF, UNI, TIA, LAB, QUICK, BOF, KEY, OTHER)

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
    val LOBBY_NEUILLY = Room("lobby_neuilly", "Lobby Neuilly", 100, "special", "")

    val AMPHI_BLEU = Room("b_amphi", "Amphi Bleu", 826, "theatre", "camera")
    val MAILLOT = Room("c_maillot", "Maillot", 380, "theatre", "camera")
    val NEUILLY_251 = Room("f_neu251", "Neuilly 251", 220, "theatre", "camera")
    val NEUILLY_252AB = Room("e_neu252", "Neuilly 252 AB", 380, "theatre", "camera")
    val NEUILLY_253 = Room("neu253", "Neuilly 253", 60, "classroom", "rien")
    val NEUILLY_253_T = Room("neu253_t", "Neuilly 253", 120, "theatre", "son")

    val PARIS_241 = Room("d_par241", "Paris 241", 220, "theatre", "camera")
    val PARIS_242AB_T = Room("par242AB", "Paris 242 AB", 280, "theatre", "camera")
    val PARIS_242A = Room("par242A", "Paris 242 A", 60, "classroom", "rien")
    val PARIS_242B = Room("par242B", "Paris 242 B", 60, "classroom", "rien")
    val PARIS_242A_T = Room("par242AT", "Paris 242 A", 120, "theatre", "rien")
    val PARIS_242B_T = Room("par242BT", "Paris 242 B", 120, "theatre", "rien")

    val PARIS_243 = Room("par243", "Paris 243", 60, "classroom", "rien")
    val PARIS_243_T = Room("par243_t", "Paris 243", 120, "theatre", "son")

    val PARIS_202_203 = Room("par202_203", "Paris 202-203", 32, "classroom", "rien")
    val PARIS_221M_222M = Room("par221M-222M", "Paris 221M-222M", 32, "classroom", "rien")
    val PARIS_224M_225M = Room("par224M-225M", "Paris 224M-225M", 26, "classroom", "rien")

    val NEUILLY_212_213 = Room("neu_212_213", "Neuilly 212-213", 32, "classroom", "rien")
    val NEUILLY_231_232 = Room("neu_232_232", "Neuilly 231M-232M", 32, "classroom", "rien")
    val NEUILLY_234_235 = Room("neu_234_235", "Neuilly 234M-235M", 24, "classroom", "rien")

    val PARIS_204 = Room("par204", "Paris 204", 16, "classroom", "rien")
    val PARIS_201 = Room("par201", "Paris 201", 14, "classroom", "rien")

    val ROOM_OTHER = Room("other_room", "Autres salles", 100, "classroom", "rien")

    val allRooms = List(HALL_EXPO, HALL_A, AMPHI_BLEU, MAILLOT, PARIS_241, NEUILLY_251, NEUILLY_252AB,
      PARIS_242A, PARIS_242B, PARIS_243, PARIS_243_T, NEUILLY_253, NEUILLY_253_T,
      PARIS_202_203, PARIS_221M_222M, PARIS_224M_225M, PARIS_204, PARIS_201)

    val allRoomsAsIdsAndLabels: Seq[(String, String)] = allRooms.map(a => (a.id, a.name)).sorted

    val allRoomsUni = List(AMPHI_BLEU, MAILLOT, NEUILLY_251, PARIS_241, NEUILLY_252AB, PARIS_242AB_T, PARIS_243_T, NEUILLY_253_T)

    val allRoomsTIAWed = List(AMPHI_BLEU, MAILLOT, NEUILLY_251, PARIS_241, NEUILLY_252AB, PARIS_242AB_T, PARIS_243_T, NEUILLY_253_T)

    val allRoomsTIAThu = List(AMPHI_BLEU, MAILLOT, NEUILLY_251, PARIS_241, NEUILLY_252AB, PARIS_242AB_T, PARIS_243_T, NEUILLY_253_T)

    val allRoomsLabsWednesday = List(PARIS_202_203, PARIS_221M_222M, PARIS_224M_225M, NEUILLY_212_213, NEUILLY_231_232, NEUILLY_234_235)

    val allRoomsLabThursday = List(PARIS_202_203, PARIS_221M_222M, PARIS_224M_225M, NEUILLY_212_213, NEUILLY_231_232, NEUILLY_234_235)

    val allRoomsLabFriday = List(PARIS_202_203, PARIS_221M_222M, PARIS_224M_225M, NEUILLY_212_213, NEUILLY_231_232, NEUILLY_234_235)

    val allRoomsBOF = List(PARIS_202_203, PARIS_221M_222M, PARIS_224M_225M, NEUILLY_212_213, NEUILLY_231_232, NEUILLY_234_235)

    val allRoomsOthersFriday = List(LOBBY_NEUILLY)

    val keynoteRoom = List(AMPHI_BLEU)

    val allRoomsConf = List(AMPHI_BLEU, MAILLOT, PARIS_242AB_T, NEUILLY_252AB, PARIS_241, NEUILLY_251, PARIS_243_T, NEUILLY_253_T)

    val allRoomsConfFridayNight = List(PARIS_242AB_T, NEUILLY_252AB, PARIS_241, NEUILLY_251, PARIS_243_T, NEUILLY_253_T)

    val allRoomsQuickiesThu: List[Room] = allRoomsConf.filterNot(r => r.id == AMPHI_BLEU.id)

    val allRoomsQuickiesFriday: List[Room] = allRoomsQuickiesThu
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

    val firstDay = "2020-04-15"
    val secondDay = "2020-04-16"
    val thirdDay = "2020-04-17"

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
            new DateTime(s"${firstDay}T17:00:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${firstDay}T17:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r1)
      }
      val toolsWednesdayAfternoonSlot2 = ConferenceRooms.allRoomsTIAWed.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.TIA.id, "wednesday",
            new DateTime(s"${firstDay}T17:45:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${firstDay}T18:15:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r2)
      }
      val toolsWednesdayAfternoonSlot3 = ConferenceRooms.allRoomsTIAWed.map {
        r3 =>
          SlotBuilder(ConferenceProposalTypes.TIA.id, "wednesday",
            new DateTime(s"${firstDay}T18:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${firstDay}T19:00:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r3)
      }
      toolsWednesdayAfternoonSlot1 ++ toolsWednesdayAfternoonSlot2 ++ toolsWednesdayAfternoonSlot3
    }

    val tiaSlotsThursday: List[Slot] = {
      val toolsThursdayAfternoonSlot1 = ConferenceRooms.allRoomsTIAThu.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.TIA.id, "thursday",
            new DateTime(s"${secondDay}T17:45:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${secondDay}T18:15:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r1)
      }
      val toolsThursdayAfternoonSlot2 = ConferenceRooms.allRoomsTIAThu.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.TIA.id, "thursday",
            new DateTime(s"${secondDay}T18:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${secondDay}T19:00:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r1)
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
            new DateTime(s"${secondDay}T10:45:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${secondDay}T14:15:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r1)
      }
      val labsThursdayAfternoon = ConferenceRooms.allRoomsLabThursday.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.LAB.id, "thursday",
            new DateTime(s"${secondDay}T15:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${secondDay}T18:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r2)
      }
      labsThursdayMorning ++ labsThursdayAfternoon
    }

    val labsSlotsFriday: List[Slot] = {
      val labsFridayMorning = ConferenceRooms.allRoomsLabFriday.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.LAB.id, "friday",
            new DateTime(s"${thirdDay}T10:45:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${thirdDay}T14:15:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r1)
      }
      labsFridayMorning
    }

    // OTHERS

    val othersSlotsFriday: List[Slot] = {
      val cafePhilo = ConferenceRooms.allRoomsOthersFriday.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.OTHER.id, "friday",
            new DateTime(s"${thirdDay}T13:00:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${thirdDay}T14:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r1)
      }
      cafePhilo
    }

    // BOFS
    val bofSlotsThursday: List[Slot] = {

      val bofThursdayEveningSlot1 = ConferenceRooms.allRoomsBOF.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.BOF.id, "thursday",
            new DateTime(s"${secondDay}T20:00:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${secondDay}T20:50:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r1)
      }
      val bofThursdayEveningSlot2 = ConferenceRooms.allRoomsBOF.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.BOF.id, "thursday",
            new DateTime(s"${secondDay}T21:00:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${secondDay}T21:50:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r2)
      }
      bofThursdayEveningSlot1 ++ bofThursdayEveningSlot2
    }

    // QUICKIES
    val quickiesSlotsThursday: List[Slot] = {
      val quickiesThursdayLunch1 = ConferenceRooms.allRoomsQuickiesThu.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.QUICK.id, "thursday",
            new DateTime(s"${secondDay}T13:00:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${secondDay}T13:15:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r1)
      }

      quickiesThursdayLunch1
    }

    val quickiesSlotsFriday: List[Slot] = {
      val quickFriday1 = ConferenceRooms.allRoomsQuickiesFriday.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.QUICK.id, "friday",
            new DateTime(s"${thirdDay}T13:00:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${thirdDay}T13:15:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r1)
      }

      quickFriday1
    }

    // CONFERENCE KEYNOTES
    val keynoteSlotsThursday: List[Slot] = {
      val keynoteThursdayWelcome = ConferenceRooms.keynoteRoom.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.KEY.id, "thursday",
            new DateTime(s"${secondDay}T09:00:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${secondDay}T09:20:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r1)
      }
      val keynoteThursdaySlot1 = ConferenceRooms.keynoteRoom.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.KEY.id, "thursday",
            new DateTime(s"${secondDay}T09:25:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${secondDay}T09:45:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r1)
      }
      val keynoteThursdaySlot2 = ConferenceRooms.keynoteRoom.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.KEY.id, "thursday",
            new DateTime(s"${secondDay}T09:50:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${secondDay}T10:10:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r2)
      }

      keynoteThursdayWelcome ++ keynoteThursdaySlot1 ++ keynoteThursdaySlot2
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

      keynoteFridaySlot1 ++ keynoteFridaySlot2 ++ keynoteFridaySlot3

    }

    // CONFERENCE SLOTS
    val conferenceSlotsThursday: List[Slot] = {

      val conferenceThursdaySlot1 = ConferenceRooms.allRoomsConf.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id, "thursday",
            new DateTime(s"${secondDay}T10:45:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${secondDay}T11:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r1)
      }
      val conferenceThursdaySlot2 = ConferenceRooms.allRoomsConf.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id, "thursday",
            new DateTime(s"${secondDay}T11:45:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${secondDay}T12:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r2)
      }
      val conferenceThursdaySlot3 = ConferenceRooms.allRoomsConf.map {
        r3 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id, "thursday",
            new DateTime(s"${secondDay}T13:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${secondDay}T14:15:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r3)
      }
      val conferenceThursdaySlot4 = ConferenceRooms.allRoomsConf.map {
        r4 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id, "thursday",
            new DateTime(s"${secondDay}T14:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${secondDay}T15:15:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r4)
      }

      val conferenceThursdaySlot5 = ConferenceRooms.allRoomsConf.map {
        r5 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id, "thursday",
            new DateTime(s"${secondDay}T15:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${secondDay}T16:15:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r5)
      }

      val conferenceThursdaySlot6 = ConferenceRooms.allRoomsConf.map {
        r6 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id, "thursday",
            new DateTime(s"${secondDay}T16:45:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${secondDay}T17:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r6)
      }
      conferenceThursdaySlot1 ++ conferenceThursdaySlot2 ++ conferenceThursdaySlot3 ++ conferenceThursdaySlot4 ++ conferenceThursdaySlot5 ++ conferenceThursdaySlot6
    }

    val conferenceSlotsFriday: List[Slot] = {

      val conferenceFridaySlot1 = ConferenceRooms.allRoomsConf.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id, "friday",
            new DateTime(s"${thirdDay}T10:45:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${thirdDay}T11:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r1)
      }
      val conferenceFridaySlot2 = ConferenceRooms.allRoomsConf.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id, "friday",
            new DateTime(s"${thirdDay}T11:45:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${thirdDay}T12:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r2)
      }
      val conferenceFridaySlot3 = ConferenceRooms.allRoomsConf.map {
        r3 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id, "friday",
            new DateTime(s"${thirdDay}T13:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${thirdDay}T14:15:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r3)
      }
      val conferenceFridaySlot4 = ConferenceRooms.allRoomsConf.map {
        r4 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id, "friday",
            new DateTime(s"${thirdDay}T14:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${thirdDay}T15:15:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r4)
      }
      val conferenceFridaySlot5 = ConferenceRooms.allRoomsConf.map {
        r5 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id, "friday",
            new DateTime(s"${thirdDay}T15:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${thirdDay}T16:15:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r5)
      }
      val conferenceFridaySlot6 = ConferenceRooms.allRoomsConf.map {
        r6 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id, "friday",
            new DateTime(s"${thirdDay}T16:45:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${thirdDay}T17:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r6)
      }
      val conferenceFridaySlot7 = ConferenceRooms.allRoomsConfFridayNight.map {
        r7 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id, "friday",
            new DateTime(s"${thirdDay}T17:45:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${thirdDay}T18:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), r7)
      }

      // Castcodeurs
      val conferenceFridaySlot8 = List(ConferenceRooms.NEUILLY_252AB).map {
        rcc =>
          SlotBuilder(ConferenceProposalTypes.CONF.id, "friday",
            new DateTime(s"${thirdDay}T18:45:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
            new DateTime(s"${thirdDay}T19:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")), rcc)
      }

      conferenceFridaySlot1 ++ conferenceFridaySlot2 ++ conferenceFridaySlot3 ++ conferenceFridaySlot4 ++ conferenceFridaySlot5 ++ conferenceFridaySlot6 ++ conferenceFridaySlot7 ++ conferenceFridaySlot8
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
        new DateTime(s"${firstDay}T17:00:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")))
    )

    val thursdayBreaks = List(
      SlotBuilder(ConferenceSlotBreaks.registration, "thursday",
        new DateTime(s"${secondDay}T07:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
        new DateTime(s"${secondDay}T09:00:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")))
      , SlotBuilder(ConferenceSlotBreaks.shortBreak, "thursday",
        new DateTime(s"${secondDay}T10:15:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
        new DateTime(s"${secondDay}T10:45:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")))
      , SlotBuilder(ConferenceSlotBreaks.lunch, "thursday",
        new DateTime(s"${secondDay}T12:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
        new DateTime(s"${secondDay}T13:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")))
      , SlotBuilder(ConferenceSlotBreaks.coffee, "thursday",
        new DateTime(s"${secondDay}T16:15:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
        new DateTime(s"${secondDay}T16:45:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")))
    )

    val fridayBreaks = List(
      SlotBuilder(ConferenceSlotBreaks.petitDej, "friday",
        new DateTime(s"${thirdDay}T08:00:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
        new DateTime(s"${thirdDay}T09:00:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")))
      , SlotBuilder(ConferenceSlotBreaks.coffee, "friday",
        new DateTime(s"${thirdDay}T10:15:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
        new DateTime(s"${thirdDay}T10:45:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")))
      , SlotBuilder(ConferenceSlotBreaks.lunch, "friday",
        new DateTime(s"${thirdDay}T12:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
        new DateTime(s"${thirdDay}T13:30:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")))
      , SlotBuilder(ConferenceSlotBreaks.coffee, "friday",
        new DateTime(s"${thirdDay}T16:15:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")),
        new DateTime(s"${thirdDay}T16:45:00.000+02:00").toDateTime(DateTimeZone.forID("Europe/Paris")))
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
      fridayBreaks ++ keynoteSlotsFriday ++ conferenceSlotsFriday ++ quickiesSlotsFriday ++ labsSlotsFriday  ++ othersSlotsFriday
    }

    def all: List[Slot] = {
      mondaySchedule ++ tuesdaySchedule ++ wednesdaySchedule ++ thursdaySchedule ++ fridaySchedule
    }
  }

  def dateRange(from: DateTime, to: DateTime, step: Period): Iterator[DateTime] = Iterator.iterate(from)(_.plus(step)).takeWhile(!_.isAfter(to))

  val fromDay = new DateTime().withYear(2020).withMonthOfYear(4).withDayOfMonth(15)
  val toDay = new DateTime().withYear(2020).withMonthOfYear(4).withDayOfMonth(17)

  // TODO You might want to start here and configure first, your various Conference Elements
  def current() = ConferenceDescriptor(
    eventCode = "DevoxxFR2020",
    // You will need to update conf/routes files with this code if modified
    confUrlCode = "devoxxfr2020",
    frLangEnabled = true,
    fromEmail = Play.current.configuration.getString("mail.from").getOrElse("program@devoxx.fr"),
    committeeEmail = Play.current.configuration.getString("mail.committee.email").getOrElse("program@devoxx.fr"),
    bccEmail = Play.current.configuration.getString("mail.bcc"),
    bugReportRecipient = Play.current.configuration.getString("mail.bugreport.recipient").getOrElse("nicolas.martignole@devoxx.fr"),
    conferenceUrls = ConferenceUrls(
      faq = "http://www.devoxx.fr/faq",
      registration = "https://reg.devoxx.fr",
      confWebsite = "https://www.devoxx.fr/",
      cfpHostname = {
        val h = Play.current.configuration.getString("cfp.hostname").getOrElse("cfp.devoxx.fr")
        if (h.endsWith("/")) {
          h.substring(0, h.length - 1)
        } else {
          h
        }
      }
    ),
    timing = ConferenceTiming(
      datesI18nKey = "15 au 17 avril 2020",
      speakersPassDuration = 3,
      preferredDayEnabled = true,
      firstDayFr = "15 avril",
      firstDayEn = "april 15th",
      datesFr = "du 15 au 17 avril 2020",
      datesEn = "from 15th to 17th of April, 2020",
      cfpOpenedOn = DateTime.parse("2019-12-01T00:00:00+02:00"),
      cfpClosedOn = DateTime.parse("2020-01-16T23:59:00+02:00"),
      scheduleAnnouncedOn = DateTime.parse("2020-02-15T00:00:00+02:00"),
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

  def isHTTPSEnabled: Boolean = Play.current.configuration.getBoolean("cfp.activateHTTPS").getOrElse(false)

  // Reset all votes when a Proposal with state=SUBMITTED (or DRAFT) is updated
  // This is to reflect the fact that some speakers are eavluated, then they update the talk, and we should revote for it
  def isResetVotesForSubmitted: Boolean = Play.current.configuration.getBoolean("cfp.resetVotesForSubmitted").getOrElse(false)

  // Set this to true temporarily
  // I will implement a new feature where each CFP member can decide to receive one digest email per day or a big email
  def notifyProposalSubmitted: Boolean = Play.current.configuration.getBoolean("cfp.notifyProposalSubmitted").getOrElse(false)

  // For practical reason we want to hide the room and the time slot until the full agenda is published
  def isShowRoomAndTimeslot: Boolean = Play.current.configuration.getBoolean("cfp.showRoomAndTimeslot").getOrElse(false)

  def isShowRoom: Boolean = Play.current.configuration.getBoolean("cfp.showRoom").getOrElse(false)

  // My Devoxx is an OAuth provider on which a user can register
  def isMyDevoxxActive: Boolean = Play.current.configuration.getBoolean("mydevoxx.active").getOrElse(false)

  def myDevoxxURL(): String = Play.current.configuration.getString("mydevoxx.url").getOrElse("https://my.devoxx.fr")

  // This is a JWT String shared secret that needs to be configured as a global environment variable
  def jwtSharedSecret(): String = Play.current.configuration.getString("mydevoxx.jwtSharedSecret").getOrElse("change me please")

  // Use Twilio (SMS service) to send notification to all speakers and to recieve also commands
  def isTwilioSMSActive: Boolean = Play.current.configuration.getBoolean("cfp.twilioSMS.active").getOrElse(false)

  def twilioAccountSid: String = Play.current.configuration.getString("cfp.twilioSMS.accountSid").getOrElse("")

  def twilioAuthToken: String = Play.current.configuration.getString("cfp.twilioSMS.authToken").getOrElse("")

  def twilioSenderNumber: String = Play.current.configuration.getString("cfp.twilioSMS.senderNumber").getOrElse("")

  def twilioMockSMS: Boolean = Play.current.configuration.getBoolean("cfp.twilioSMS.mock").getOrElse(true)

  def gluonAuthorization(): String = Play.current.configuration.getString("gluon.auth.token").getOrElse(RandomStringUtils.random(16))

  def gluonInboundAuthorization(): String = Play.current.configuration.getString("gluon.inbound.token").getOrElse(RandomStringUtils.random(16))

  def gluonUsername(): String = Play.current.configuration.getString("gluon.username").getOrElse("")

  def gluonPassword(): String = Play.current.configuration.getString("gluon.password").getOrElse(RandomStringUtils.random(16))

  def maxProposals(): Int = Play.current.configuration.getInt("cfp.max.proposals").getOrElse(5)

  def isSendProposalRefusedEmail:Boolean = Play.current.configuration.getBoolean("cfp.sendProposalRefusedEmail").getOrElse(true)
}


package models

import org.apache.commons.lang3.RandomStringUtils
import org.joda.time.{DateTime, DateTimeZone, Period}
import play.api.Play

import java.util.Locale

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
    val cleanCfpHostname = cfpHostname match {
      case null => "http://cfp.devoxx.fr"
      case "" => "http://cfp.devoxx.fr"
      case x if x.endsWith("/") => x.substring(0, x.length - 1)
      case _ => cfpHostname
    }
    if (Play.current.configuration.getBoolean("cfp.activateHTTPS").getOrElse(false)) {
      s"https://$cleanCfpHostname"
    } else {
      s"http://$cleanCfpHostname"
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
                                 accessibleTypeToGoldenTicketReviews: () => Boolean,
                                 freeEntranceDisplayed: Boolean,
                                 htmlClass: String,
                                 hiddenInCombo: Boolean = false,
                                 chosablePreferredDay: Boolean = false,
                                 impliedSelectedTrack: Option[Track] = None,
                                 concernedByCountQuotaRestriction: Boolean = true,
                                 allowOtherSpeaker: Boolean = true)

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
                                timezone: DateTimeZone,
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

    // Used to define pre-selected proposal type (to avoid searching for too much proposals on "all votes" screens)
    val DEFAULT_SEARCH_PROPOSAL_ID = UNI.id

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

    def slottableTypes: List[ProposalType] = {
      ALL.filter(t => Slot.byType(t).nonEmpty)
    }
  }

  // TODO Configure here the slot, with the number of slots available, if it gives a free ticket to the speaker, some CSS icons
  object ConferenceProposalConfigurations {
    val CONF = ProposalConfiguration(id = "conf", slotsCount = ConferenceSlots.all.count(_.name.equals(ConferenceProposalTypes.CONF.id)), givesSpeakerFreeEntrance = true, freeEntranceDisplayed = true, htmlClass = "fas fa-bullhorn",
      chosablePreferredDay = true, accessibleTypeToGoldenTicketReviews = () => true)
    val UNI = ProposalConfiguration(id = "uni", slotsCount = ConferenceSlots.all.count(_.name.equals(ConferenceProposalTypes.UNI.id)), givesSpeakerFreeEntrance = true, freeEntranceDisplayed = true, htmlClass = "fas fa-laptop",
      concernedByCountQuotaRestriction = false, chosablePreferredDay = false, accessibleTypeToGoldenTicketReviews = () => true)
    val TIA = ProposalConfiguration(id = "tia", slotsCount = ConferenceSlots.all.count(_.name.equals(ConferenceProposalTypes.TIA.id)), givesSpeakerFreeEntrance = true, freeEntranceDisplayed = true, htmlClass = "fas fa-tools",
      chosablePreferredDay = true, accessibleTypeToGoldenTicketReviews = () => true)
    val LAB = ProposalConfiguration(id = "lab", slotsCount = ConferenceSlots.all.count(_.name.equals(ConferenceProposalTypes.LAB.id)), givesSpeakerFreeEntrance = true, freeEntranceDisplayed = true, htmlClass = "fas fa-flask",
      chosablePreferredDay = true, accessibleTypeToGoldenTicketReviews = () => true)
    val QUICK = ProposalConfiguration(id = "quick", slotsCount = ConferenceSlots.all.count(_.name.equals(ConferenceProposalTypes.QUICK.id)), givesSpeakerFreeEntrance = true, freeEntranceDisplayed = true, htmlClass = "fas fa-fast-forward",
      chosablePreferredDay = true, allowOtherSpeaker = false, accessibleTypeToGoldenTicketReviews = () => true)
    val BOF = ProposalConfiguration(id = "bof", slotsCount = ConferenceSlots.all.count(_.name.equals(ConferenceProposalTypes.BOF.id)), givesSpeakerFreeEntrance = false, freeEntranceDisplayed = false, htmlClass = "fas fa-users",
      concernedByCountQuotaRestriction = false, chosablePreferredDay = false, accessibleTypeToGoldenTicketReviews = () => ConferenceDescriptor.isCFPOpen)
    val KEY = ProposalConfiguration(id = "key", slotsCount = ConferenceSlots.all.count(_.name.equals(ConferenceProposalTypes.KEY.id)), givesSpeakerFreeEntrance = true, freeEntranceDisplayed = false, htmlClass = "fas fa-microphone",
      concernedByCountQuotaRestriction = false, chosablePreferredDay = true, accessibleTypeToGoldenTicketReviews = () => false)
    val OTHER = ProposalConfiguration(id = "other", slotsCount = ConferenceSlots.all.count(_.name.equals(ConferenceProposalTypes.OTHER.id)), givesSpeakerFreeEntrance = true, freeEntranceDisplayed = false, htmlClass = "fas fa-microphone-alt",
      hiddenInCombo = true, chosablePreferredDay = false, accessibleTypeToGoldenTicketReviews = () => false)
    val UNKNOWN = ProposalConfiguration(id = "unknown", slotsCount = 0, givesSpeakerFreeEntrance = false, freeEntranceDisplayed = false,
      concernedByCountQuotaRestriction = false, htmlClass = "", hiddenInCombo = true, chosablePreferredDay = false, accessibleTypeToGoldenTicketReviews = () => false)

    // UNKNOWN is not there : it's not on purpose !
    val ALL = List(CONF, UNI, TIA, LAB, QUICK, BOF, KEY, OTHER)

    def parse(propConf: String): ProposalConfiguration = {
      ALL.find(p => p.id == propConf).getOrElse(ConferenceDescriptor.ConferenceProposalConfigurations.UNKNOWN)
    }

    def totalSlotsCount: Int = ALL.map(_.slotsCount).sum

    def getProposalsImplyingATrackSelection: List[ProposalConfiguration] = {
      ALL.filter(p => p.impliedSelectedTrack.nonEmpty)
    }

    def isDisplayedFreeEntranceProposals(pt: ProposalType): Boolean = {
      ALL.filter(p => p.id == pt.id).map(_.freeEntranceDisplayed).headOption.getOrElse(false)
    }

    def getHTMLClassFor(pt: ProposalType): String = {
      ALL.filter(p => p.id == pt.id).map(_.htmlClass).headOption.getOrElse("unknown")
    }

    def isChosablePreferredDaysProposals(pt: ProposalType): Boolean = {
      ALL.filter(p => p.id == pt.id).exists(_.chosablePreferredDay)
    }

    def doesProposalTypeAllowOtherSpeaker(pt: ProposalType): Boolean = {
      ALL.filter(p => p.id == pt.id).exists(_.allowOtherSpeaker)
    }

    def concernedByCountQuotaRestriction: List[ProposalConfiguration] = {
      ALL.filter(_.concernedByCountQuotaRestriction)
    }

    def concernedByCountQuotaRestrictionAndNotHidden: List[ProposalConfiguration] = {
      ALL.filter(p => p.concernedByCountQuotaRestriction && !p.hiddenInCombo)
    }

    def isConcernedByCountRestriction(proposalType: ProposalType): Boolean = {
      ALL.filter(_.id == proposalType.id).exists(_.concernedByCountQuotaRestriction)
    }

    def doesItGivesSpeakerFreeEntrance(proposalType: ProposalType): Boolean = {
      ALL.filter(_.id == proposalType.id).exists(_.givesSpeakerFreeEntrance)
    }

    def accessibleTypeToGoldenTicketReviews(proposalType: ProposalType): Boolean = {
      ALL.filter(_.id == proposalType.id).exists(_.accessibleTypeToGoldenTicketReviews())
    }
  }

  // TODO Configure here your Conference's tracks.
  object ConferenceTracks {
    val JAVA: Track = Track("java", "java.label")
    val WEB: Track = Track("wm", "web.label")
    val ARCHISEC: Track = Track("archisec", "archisec.label")
    val CLOUD: Track = Track("cldops", "cloud.label")
    val BIGDATA: Track = Track("bigd", "bigdata.label")
    val GEEK: Track = Track("geek", "geek.label")
    val LANG: Track = Track("lang", "lang.label")
    val UNKNOWN: Track = Track("unknown", "unknown track")
    val ALL = List(JAVA, WEB, ARCHISEC, CLOUD, BIGDATA, GEEK, LANG, UNKNOWN)
    val ALL_KNOWN = ALL.filter(_ != UNKNOWN)
  }

  // TODO configure the description for each Track
  object ConferenceTracksDescription {
    val JAVA = TrackDesc(ConferenceTracks.JAVA.id, "/assets/dvfr2015/images/track-icons/java.png", "track.java.title", "track.java.desc")
    val WEB = TrackDesc(ConferenceTracks.WEB.id, "/assets/dvfr2015/images/track-icons/front.png", "track.web.title", "track.web.desc")
    val ARCHISEC = TrackDesc(ConferenceTracks.ARCHISEC.id, "/assets/dvfr2015/images/track-icons/arch.png", "track.archisec.title", "track.archisec.desc")
    val CLOUD = TrackDesc(ConferenceTracks.CLOUD.id, "/assets/dvfr2015/images/track-icons/cloud.png", "track.cloud.title", "track.cloud.desc")
    val BIGDATA = TrackDesc(ConferenceTracks.BIGDATA.id, "/assets/dvfr2015/images/track-icons/bigd.png", "track.bigdata.title", "track.bigdata.desc")
    val GEEK = TrackDesc(ConferenceTracks.GEEK.id, "/assets/dvfr2015/images/track-icons/mind.png", "track.geek.title", "track.geek.desc")
    val LANG = TrackDesc(ConferenceTracks.LANG.id, "/assets/dvfr2015/images/track-icons/langs.png", "track.lang.title", "track.lang.desc")

    val ALL = List(JAVA, WEB, ARCHISEC, CLOUD, BIGDATA, GEEK, LANG)

    def findTrackDescFor(t: Track): TrackDesc = {
      ALL.find(_.id == t.id).getOrElse(JAVA)
    }
  }

  // TODO If you want to use the Devoxx Scheduler, you can describe here the list of rooms, with capacity for seats
  object ConferenceRooms {

    // Tip : I use the ID to sort-by on the view per day... So if the exhibition floor id is "aaa" it will be
    // the first column on the HTML Table

    // Do not change the ID's once the program is published
    val HALL_EXPO = Room("a_hall", "Exhibition floor", 2300, "special", "", "Hall", None)
    val HALL_EXPO_PARIS = Room("hall_paris", "Hall Exposition Paris", 100, "special", "", "Hall Paris", Some("Paris"))
    val LOBBY_NEUILLY = Room("lobby_neuilly", "Lobby Neuilly", 100, "special", "", "Lobby Neuilly", Some("Neuilly"))
    val LOBBY_PARIS = Room("lobby_paris", "Lobby Paris", 100, "special", "", "Lobby Paris", Some("Paris"))

    val AMPHI_BLEU = Room("b_amphi", "Amphi Bleu", 826, "theatre", "camera", "Amphi Bleu", None)
    val MAILLOT = Room("c_maillot", "Maillot", 380, "theatre", "camera", "Amphi Maillot", Some("Hall"))

    val NEUILLY_251 = Room("f_neu251", "Neuilly 251", 220, "theatre", "camera", "251", Some("Neuilly"))
    val NEUILLY_252AB = Room("e_neu252", "Neuilly 252 AB", 380, "theatre", "camera", "252AB", Some("Neuilly"))
    val NEUILLY_253 = Room("neu253", "Neuilly 253", 60, "classroom", "camera", "253", Some("Neuilly"))
    val NEUILLY_253_T = Room("neu253_t", "Neuilly 253", 120, "theatre", "camera", "253", Some("Neuilly"))

    val PARIS_241 = Room("d_par241", "Paris 241", 220, "theatre", "camera", "241", Some("Paris"))
    val PARIS_242AB_T = Room("par242AB", "Paris 242 AB", 280, "theatre", "camera", "242AB", Some("Paris"))
    val PARIS_242A = Room("par242A", "Paris 242 A", 60, "classroom", "camera", "242A", Some("Paris"))
    val PARIS_242B = Room("par242B", "Paris 242 B", 60, "classroom", "camera", "242B", Some("Paris"))
    val PARIS_242A_T = Room("par242AT", "Paris 242 A", 120, "theatre", "camera", "242A", Some("Paris"))
    val PARIS_242B_T = Room("par242BT", "Paris 242 B", 120, "theatre", "camera", "242B", Some("Paris"))
    val PARIS_243 = Room("par243", "Paris 243", 60, "classroom", "camera", "243", Some("Paris"))
    val PARIS_243_T = Room("par243_t", "Paris 243", 120, "theatre", "camera", "243", Some("Paris"))

    val PARIS_201 = Room("par201", "Paris 201", 14, "classroom", "rien", "201", Some("Hall - RdC/GF South Paris"))
    val PARIS_201_U = Room("par201_u", "Paris 201", 16, "u-shaped", "rien", "201", Some("Hall - RdC/GF South Paris"))
    val PARIS_202_203 = Room("par202_203", "Paris 202-203", 32, "classroom", "rien", "202+203", Some("Hall - RdC/GF South Paris"))
    val PARIS_204 = Room("par204", "Paris 204", 16, "classroom", "rien", "204", None)
    val PARIS_221M_222M = Room("par221M-222M", "Paris 221M-222M", 32, "classroom", "rien", "221+222M", Some("Mezzanine South Paris"))
    val PARIS_224M_225M = Room("par224M-225M", "Paris 224M-225M", 26, "classroom", "rien", "224+225M", Some("Mezzanine South Paris"))

    val NEUILLY_212_213 = Room("neu_212_213", "Neuilly 212-213", 32, "classroom", "rien", "212+213", Some("Hall - RdC/GF South Neuilly"))
    val NEUILLY_231_232 = Room("neu_232_232", "Neuilly 231M-232M", 32, "classroom", "rien", "231+232M", Some("Mezzanine South Neuilly"))
    val NEUILLY_234_235 = Room("neu_234_235", "Neuilly 234M-235M", 24, "classroom", "rien", "234+235M", Some("Mezzanine South Neuilly"))

    val ROOM_OTHER = Room("other_room", "Autres salles", 100, "classroom", "rien", "Autre salle", None)

    val allRoomsUni = List(AMPHI_BLEU, MAILLOT, NEUILLY_251, PARIS_241, NEUILLY_252AB, PARIS_242AB_T, PARIS_243_T, NEUILLY_253_T)

    val allRoomsTIAWed = List(AMPHI_BLEU, MAILLOT, NEUILLY_251, PARIS_241, NEUILLY_252AB, PARIS_242AB_T, PARIS_243_T, NEUILLY_253_T)

    val allRoomsTIAThu = List(AMPHI_BLEU, MAILLOT, NEUILLY_251, PARIS_241, NEUILLY_252AB, PARIS_242AB_T, PARIS_243_T, NEUILLY_253_T)

    val allRoomsTIAThuEOD = List(AMPHI_BLEU, NEUILLY_251, PARIS_241, NEUILLY_252AB, PARIS_242AB_T, PARIS_243_T, NEUILLY_253_T)

    val allRoomsLabsWednesday = List(PARIS_201_U, PARIS_202_203, PARIS_221M_222M, PARIS_224M_225M, NEUILLY_231_232, NEUILLY_234_235)

    val allRoomsLabThursday = List(PARIS_201_U, PARIS_202_203, PARIS_221M_222M, PARIS_224M_225M, NEUILLY_231_232, NEUILLY_234_235)

    val allRoomsLabFriday = List(PARIS_201_U, PARIS_202_203, PARIS_221M_222M, PARIS_224M_225M, NEUILLY_231_232, NEUILLY_234_235)

    val allRoomsBOF = List(PARIS_201_U, PARIS_202_203, PARIS_221M_222M, PARIS_224M_225M, NEUILLY_231_232, NEUILLY_234_235)

    val allRoomsOthersThursday = List(MAILLOT, LOBBY_NEUILLY, LOBBY_PARIS)

    val allRoomsOthersFriday = List(LOBBY_NEUILLY)

    val keynoteRoom = List(AMPHI_BLEU)

    val allRoomsConf = List(AMPHI_BLEU, MAILLOT, PARIS_242AB_T, NEUILLY_252AB, PARIS_241, NEUILLY_251, PARIS_243_T, NEUILLY_253_T)

    val allRoomsQuickiesThu: List[Room] = allRoomsConf.filterNot(r => r.id == AMPHI_BLEU.id)

    val allRoomsQuickiesFriday: List[Room] = allRoomsQuickiesThu

    val allRooms = (List(
      HALL_EXPO
    ) ++ allRoomsUni ++ allRoomsTIAWed ++ allRoomsTIAThu ++ allRoomsLabsWednesday ++
      allRoomsLabThursday ++ allRoomsLabFriday ++ allRoomsBOF ++ allRoomsOthersThursday ++ allRoomsOthersFriday ++
      keynoteRoom ++ allRoomsConf ++ allRoomsQuickiesThu ++ allRoomsQuickiesFriday).distinct

    val allRoomsAsIdsAndLabels: Seq[(String, String)] = allRooms.map(a => (a.id, a.name)).sorted
  }

  // TODO if you want to use the Scheduler, you can configure the breaks
  object ConferenceSlotBreaks {
    val registration = SlotBreak("reg", "Registration", "Accueil")
    val petitDej = SlotBreak("dej", "Breakfast", "Accueil et petit-déjeuner")
    val coffee = SlotBreak("coffee", "Coffee Break", "Pause café")
    val lunch = SlotBreak("lunch", "Lunch", "Pause déjeuner")
    val shortBreak = SlotBreak("chgt", "Break", "Pause courte")
    val exhibition = SlotBreak("exhib", "Exhibition", "Exhibition")
    val meetAndGreet = SlotBreak("meet", "Meet & Greet", "Exhibition")
    val empty = SlotBreak("empty", "", "")
  }

  // TODO The idea here is to describe in term of Agenda, for each rooms, the slots. This is required only for the Scheduler
  object ConferenceSlots {

    val firstDay = "2023-04-12"
    val secondDay = "2023-04-13"
    val thirdDay = "2023-04-14"
    lazy val confTimezone: DateTimeZone = ConferenceDescriptor.current().timezone

    // UNIVERSITY
    val universitySlotsWednesday: List[Slot] = {
      val universityWednesdayMorning = ConferenceRooms.allRoomsUni.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.UNI.id, "wednesday",
            new DateTime(s"${firstDay}T09:30:00.000+02:00").toDateTime(confTimezone),
            new DateTime(s"${firstDay}T12:30:00.000+02:00").toDateTime(confTimezone), r1)
      }
      val universityWednesdayAfternoon = ConferenceRooms.allRoomsUni.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.UNI.id, "wednesday",
            new DateTime(s"${firstDay}T13:30:00.000+02:00").toDateTime(confTimezone),
            new DateTime(s"${firstDay}T16:30:00.000+02:00").toDateTime(confTimezone), r2)
      }
      universityWednesdayMorning ++ universityWednesdayAfternoon
    }

    // TOOLS IN ACTION
    val tiaSlotsWednesday: List[Slot] = {

      val toolsWednesdayAfternoonSlot1 = ConferenceRooms.allRoomsTIAWed.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.TIA.id, "wednesday",
            new DateTime(s"${firstDay}T17:00:00.000+02:00").toDateTime(confTimezone),
            new DateTime(s"${firstDay}T17:30:00.000+02:00").toDateTime(confTimezone), r1)
      }
      val toolsWednesdayAfternoonSlot2 = ConferenceRooms.allRoomsTIAWed.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.TIA.id, "wednesday",
            new DateTime(s"${firstDay}T17:45:00.000+02:00").toDateTime(confTimezone),
            new DateTime(s"${firstDay}T18:15:00.000+02:00").toDateTime(confTimezone), r2)
      }
      val toolsWednesdayAfternoonSlot3 = ConferenceRooms.allRoomsTIAWed.map {
        r3 =>
          SlotBuilder(ConferenceProposalTypes.TIA.id, "wednesday",
            new DateTime(s"${firstDay}T18:30:00.000+02:00").toDateTime(confTimezone),
            new DateTime(s"${firstDay}T19:00:00.000+02:00").toDateTime(confTimezone), r3)
      }
      toolsWednesdayAfternoonSlot1 ++ toolsWednesdayAfternoonSlot2 ++ toolsWednesdayAfternoonSlot3
    }

    val tiaSlotsThursday: List[Slot] = {
      val toolsThursdayAfternoonSlot1 = ConferenceRooms.allRoomsTIAThu.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.TIA.id, "thursday",
            new DateTime(s"${secondDay}T17:45:00.000+02:00").toDateTime(confTimezone),
            new DateTime(s"${secondDay}T18:15:00.000+02:00").toDateTime(confTimezone), r1)
      }
      val toolsThursdayAfternoonSlot2 = ConferenceRooms.allRoomsTIAThuEOD.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.TIA.id, "thursday",
            new DateTime(s"${secondDay}T18:30:00.000+02:00").toDateTime(confTimezone),
            new DateTime(s"${secondDay}T19:00:00.000+02:00").toDateTime(confTimezone), r1)
      }
      toolsThursdayAfternoonSlot1 ++ toolsThursdayAfternoonSlot2
    }

    // HANDS ON LABS
    val labsSlotsWednesday: List[Slot] = {
      val labsWednesdayMorning = ConferenceRooms.allRoomsLabsWednesday.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.LAB.id, "wednesday",
            new DateTime(s"${firstDay}T09:30:00.000+02:00").toDateTime(confTimezone),
            new DateTime(s"${firstDay}T12:30:00.000+02:00").toDateTime(confTimezone), r1)
      }
      val labsWednesdayAfternoon = ConferenceRooms.allRoomsLabsWednesday.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.LAB.id, "wednesday",
            new DateTime(s"${firstDay}T13:30:00.000+02:00").toDateTime(confTimezone),
            new DateTime(s"${firstDay}T16:30:00.000+02:00").toDateTime(confTimezone), r2)
      }
      labsWednesdayMorning ++ labsWednesdayAfternoon
    }

    val labsSlotsThursday: List[Slot] = {

      val labsThursdayMorning = ConferenceRooms.allRoomsLabThursday.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.LAB.id, "thursday",
            new DateTime(s"${secondDay}T10:45:00.000+02:00").toDateTime(confTimezone),
            new DateTime(s"${secondDay}T14:15:00.000+02:00").toDateTime(confTimezone), r1)
      }
      val labsThursdayAfternoon = ConferenceRooms.allRoomsLabThursday.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.LAB.id, "thursday",
            new DateTime(s"${secondDay}T15:30:00.000+02:00").toDateTime(confTimezone),
            new DateTime(s"${secondDay}T18:30:00.000+02:00").toDateTime(confTimezone), r2)
      }
      labsThursdayMorning ++ labsThursdayAfternoon
    }

    val labsSlotsFriday: List[Slot] = {
      val labsFridayMorning = ConferenceRooms.allRoomsLabFriday.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.LAB.id, "friday",
            new DateTime(s"${thirdDay}T10:45:00.000+02:00").toDateTime(confTimezone),
            new DateTime(s"${thirdDay}T14:15:00.000+02:00").toDateTime(confTimezone), r1)
      }
      labsFridayMorning
    }

    // OTHERS

    val othersSlotsThursday: List[Slot] = {
      val theVoxx = SlotBuilder(ConferenceProposalTypes.OTHER.id, "thursday",
        new DateTime(s"${secondDay}T19:30:00.000+02:00").toDateTime(confTimezone),
        new DateTime(s"${secondDay}T23:00:00.000+02:00").toDateTime(confTimezone), ConferenceRooms.MAILLOT)
      val improvisationalTheatre  = SlotBuilder(ConferenceProposalTypes.OTHER.id, "thursday",
        new DateTime(s"${secondDay}T20:00:00.000+02:00").toDateTime(confTimezone),
        new DateTime(s"${secondDay}T21:30:00.000+02:00").toDateTime(confTimezone), ConferenceRooms.LOBBY_NEUILLY)
      val massage = SlotBuilder(ConferenceProposalTypes.OTHER.id, "thursday",
        new DateTime(s"${secondDay}T18:15:00.000+02:00").toDateTime(confTimezone),
        new DateTime(s"${secondDay}T22:30:00.000+02:00").toDateTime(confTimezone), ConferenceRooms.LOBBY_PARIS)
      List(theVoxx,improvisationalTheatre,massage)
    }

    val othersSlotsFriday: List[Slot] = {
      val cafePhilo = SlotBuilder(ConferenceProposalTypes.OTHER.id, "friday",
        new DateTime(s"${thirdDay}T13:00:00.000+02:00").toDateTime(confTimezone),
        new DateTime(s"${thirdDay}T14:15:00.000+02:00").toDateTime(confTimezone), ConferenceRooms.LOBBY_NEUILLY)
      List(cafePhilo)
    }

    // BOFS
    val bofSlotsThursday: List[Slot] = {

      val bofThursdayEveningSlot1 = ConferenceRooms.allRoomsBOF.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.BOF.id, "thursday",
            new DateTime(s"${secondDay}T20:00:00.000+02:00").toDateTime(confTimezone),
            new DateTime(s"${secondDay}T20:50:00.000+02:00").toDateTime(confTimezone), r1)
      }
      val bofThursdayEveningSlot2 = ConferenceRooms.allRoomsBOF.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.BOF.id, "thursday",
            new DateTime(s"${secondDay}T21:00:00.000+02:00").toDateTime(confTimezone),
            new DateTime(s"${secondDay}T21:50:00.000+02:00").toDateTime(confTimezone), r2)
      }
      bofThursdayEveningSlot1 ++ bofThursdayEveningSlot2
    }

    // QUICKIES
    val quickiesSlotsThursday: List[Slot] = {
      val quickiesThursdayLunch1 = ConferenceRooms.allRoomsQuickiesThu.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.QUICK.id, "thursday",
            new DateTime(s"${secondDay}T13:00:00.000+02:00").toDateTime(confTimezone),
            new DateTime(s"${secondDay}T13:15:00.000+02:00").toDateTime(confTimezone), r1)
      }

      quickiesThursdayLunch1
    }

    val quickiesSlotsFriday: List[Slot] = {
      val quickFriday1 = ConferenceRooms.allRoomsQuickiesFriday.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.QUICK.id, "friday",
            new DateTime(s"${thirdDay}T13:00:00.000+02:00").toDateTime(confTimezone),
            new DateTime(s"${thirdDay}T13:15:00.000+02:00").toDateTime(confTimezone), r1)
      }

      quickFriday1
    }

    // CONFERENCE KEYNOTES
    val keynoteSlotsThursday: List[Slot] = {
      val keynoteThursdayWelcome = ConferenceRooms.keynoteRoom.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.KEY.id, "thursday",
            new DateTime(s"${secondDay}T09:00:00.000+02:00").toDateTime(confTimezone),
            new DateTime(s"${secondDay}T09:20:00.000+02:00").toDateTime(confTimezone), r1)
      }
      val keynoteThursdaySlot1 = ConferenceRooms.keynoteRoom.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.KEY.id, "thursday",
            new DateTime(s"${secondDay}T09:25:00.000+02:00").toDateTime(confTimezone),
            new DateTime(s"${secondDay}T09:45:00.000+02:00").toDateTime(confTimezone), r1)
      }
      val keynoteThursdaySlot2 = ConferenceRooms.keynoteRoom.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.KEY.id, "thursday",
            new DateTime(s"${secondDay}T09:50:00.000+02:00").toDateTime(confTimezone),
            new DateTime(s"${secondDay}T10:10:00.000+02:00").toDateTime(confTimezone), r2)
      }

      keynoteThursdayWelcome ++ keynoteThursdaySlot1 ++ keynoteThursdaySlot2
    }

    val keynoteSlotsFriday: List[Slot] = {
      val keynoteFridaySlot1 = ConferenceRooms.keynoteRoom.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.KEY.id, "friday",
            new DateTime(s"${thirdDay}T09:00:00.000+02:00").toDateTime(confTimezone),
            new DateTime(s"${thirdDay}T09:20:00.000+02:00").toDateTime(confTimezone), r1)
      }
      val keynoteFridaySlot2 = ConferenceRooms.keynoteRoom.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.KEY.id, "friday",
            new DateTime(s"${thirdDay}T09:25:00.000+02:00").toDateTime(confTimezone),
            new DateTime(s"${thirdDay}T09:45:00.000+02:00").toDateTime(confTimezone), r2)
      }
      val keynoteFridaySlot3 = ConferenceRooms.keynoteRoom.map {
        r3 =>
          SlotBuilder(ConferenceProposalTypes.KEY.id, "friday",
            new DateTime(s"${thirdDay}T09:50:00.000+02:00").toDateTime(confTimezone),
            new DateTime(s"${thirdDay}T10:10:00.000+02:00").toDateTime(confTimezone), r3)
      }

      keynoteFridaySlot1 ++ keynoteFridaySlot2 ++ keynoteFridaySlot3

    }

    // CONFERENCE SLOTS
    val conferenceSlotsThursday: List[Slot] = {

      val conferenceThursdaySlot1 = ConferenceRooms.allRoomsConf.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id, "thursday",
            new DateTime(s"${secondDay}T10:45:00.000+02:00").toDateTime(confTimezone),
            new DateTime(s"${secondDay}T11:30:00.000+02:00").toDateTime(confTimezone), r1)
      }
      val conferenceThursdaySlot2 = ConferenceRooms.allRoomsConf.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id, "thursday",
            new DateTime(s"${secondDay}T11:45:00.000+02:00").toDateTime(confTimezone),
            new DateTime(s"${secondDay}T12:30:00.000+02:00").toDateTime(confTimezone), r2)
      }
      val conferenceThursdaySlot3 = ConferenceRooms.allRoomsConf.map {
        r3 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id, "thursday",
            new DateTime(s"${secondDay}T13:30:00.000+02:00").toDateTime(confTimezone),
            new DateTime(s"${secondDay}T14:15:00.000+02:00").toDateTime(confTimezone), r3)
      }
      val conferenceThursdaySlot4 = ConferenceRooms.allRoomsConf.map {
        r4 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id, "thursday",
            new DateTime(s"${secondDay}T14:30:00.000+02:00").toDateTime(confTimezone),
            new DateTime(s"${secondDay}T15:15:00.000+02:00").toDateTime(confTimezone), r4)
      }

      val conferenceThursdaySlot5 = ConferenceRooms.allRoomsConf.map {
        r5 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id, "thursday",
            new DateTime(s"${secondDay}T15:30:00.000+02:00").toDateTime(confTimezone),
            new DateTime(s"${secondDay}T16:15:00.000+02:00").toDateTime(confTimezone), r5)
      }

      val conferenceThursdaySlot6 = ConferenceRooms.allRoomsConf.map {
        r6 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id, "thursday",
            new DateTime(s"${secondDay}T16:45:00.000+02:00").toDateTime(confTimezone),
            new DateTime(s"${secondDay}T17:30:00.000+02:00").toDateTime(confTimezone), r6)
      }
      conferenceThursdaySlot1 ++ conferenceThursdaySlot2 ++ conferenceThursdaySlot3 ++ conferenceThursdaySlot4 ++ conferenceThursdaySlot5 ++ conferenceThursdaySlot6
    }

    val conferenceSlotsFriday: List[Slot] = {

      val conferenceFridaySlot1 = ConferenceRooms.allRoomsConf.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id, "friday",
            new DateTime(s"${thirdDay}T10:45:00.000+02:00").toDateTime(confTimezone),
            new DateTime(s"${thirdDay}T11:30:00.000+02:00").toDateTime(confTimezone), r1)
      }
      val conferenceFridaySlot2 = ConferenceRooms.allRoomsConf.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id, "friday",
            new DateTime(s"${thirdDay}T11:45:00.000+02:00").toDateTime(confTimezone),
            new DateTime(s"${thirdDay}T12:30:00.000+02:00").toDateTime(confTimezone), r2)
      }
      val conferenceFridaySlot3 = ConferenceRooms.allRoomsConf.map {
        r3 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id, "friday",
            new DateTime(s"${thirdDay}T13:30:00.000+02:00").toDateTime(confTimezone),
            new DateTime(s"${thirdDay}T14:15:00.000+02:00").toDateTime(confTimezone), r3)
      }
      val conferenceFridaySlot4 = ConferenceRooms.allRoomsConf.map {
        r4 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id, "friday",
            new DateTime(s"${thirdDay}T14:30:00.000+02:00").toDateTime(confTimezone),
            new DateTime(s"${thirdDay}T15:15:00.000+02:00").toDateTime(confTimezone), r4)
      }
      val conferenceFridaySlot5 = ConferenceRooms.allRoomsConf.map {
        r5 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id, "friday",
            new DateTime(s"${thirdDay}T15:30:00.000+02:00").toDateTime(confTimezone),
            new DateTime(s"${thirdDay}T16:15:00.000+02:00").toDateTime(confTimezone), r5)
      }
      val conferenceFridaySlot6 = ConferenceRooms.allRoomsConf.map {
        r6 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id, "friday",
            new DateTime(s"${thirdDay}T16:45:00.000+02:00").toDateTime(confTimezone),
            new DateTime(s"${thirdDay}T17:30:00.000+02:00").toDateTime(confTimezone), r6)
      }

      // Castcodeurs
      val conferenceFridaySlot7 = List(ConferenceRooms.NEUILLY_252AB).map {
        rcc =>
          SlotBuilder(ConferenceProposalTypes.CONF.id, "friday",
            new DateTime(s"${thirdDay}T17:45:00.000+02:00").toDateTime(confTimezone),
            new DateTime(s"${thirdDay}T18:30:00.000+02:00").toDateTime(confTimezone), rcc)
      }

      conferenceFridaySlot1 ++ conferenceFridaySlot2 ++ conferenceFridaySlot3 ++ conferenceFridaySlot4 ++ conferenceFridaySlot5 ++ conferenceFridaySlot6 ++ conferenceFridaySlot7
    }

    // Registration, coffee break, lunch etc
    // The "first room" is kind of special in the schedule algorithm, as this is the room which is used
    // to "start" break colspans
    // This is preferable to generate only a single break slot (even on time slots with break on *multiple* rooms)
    // as the devoxx mobile app doesn't like duplicated break slots (every breaks in the same time slots are displayed)
    val firstRoomForBreaks = ConferenceRooms.AMPHI_BLEU;
    val wednesdayBreaks = List(
      SlotBuilder(ConferenceSlotBreaks.registration, "wednesday",
        new DateTime(s"${firstDay}T08:00:00.000+02:00").toDateTime(confTimezone),
        new DateTime(s"${firstDay}T09:30:00.000+02:00").toDateTime(confTimezone), List(firstRoomForBreaks))
      , SlotBuilder(ConferenceSlotBreaks.lunch, "wednesday",
        new DateTime(s"${firstDay}T12:30:00.000+02:00").toDateTime(confTimezone),
        new DateTime(s"${firstDay}T13:30:00.000+02:00").toDateTime(confTimezone), List(firstRoomForBreaks))
      , SlotBuilder(ConferenceSlotBreaks.coffee, "wednesday",
        new DateTime(s"${firstDay}T16:30:00.000+02:00").toDateTime(confTimezone),
        new DateTime(s"${firstDay}T17:00:00.000+02:00").toDateTime(confTimezone), List(firstRoomForBreaks))
    ).flatten

    val thursdayBreaks = List(
      SlotBuilder(ConferenceSlotBreaks.registration, "thursday",
        new DateTime(s"${secondDay}T07:30:00.000+02:00").toDateTime(confTimezone),
        new DateTime(s"${secondDay}T09:00:00.000+02:00").toDateTime(confTimezone), List(firstRoomForBreaks))
      , SlotBuilder(ConferenceSlotBreaks.coffee, "thursday",
        new DateTime(s"${secondDay}T10:15:00.000+02:00").toDateTime(confTimezone),
        new DateTime(s"${secondDay}T10:45:00.000+02:00").toDateTime(confTimezone), List(firstRoomForBreaks))
      , SlotBuilder(ConferenceSlotBreaks.lunch, "thursday",
        new DateTime(s"${secondDay}T12:30:00.000+02:00").toDateTime(confTimezone),
        new DateTime(s"${secondDay}T13:00:00.000+02:00").toDateTime(confTimezone), List(firstRoomForBreaks,ConferenceRooms.LOBBY_NEUILLY))
      , SlotBuilder(ConferenceSlotBreaks.lunch, "thursday",
        new DateTime(s"${secondDay}T13:00:00.000+02:00").toDateTime(confTimezone),
        new DateTime(s"${secondDay}T13:15:00.000+02:00").toDateTime(confTimezone), List(firstRoomForBreaks,ConferenceRooms.LOBBY_NEUILLY))
      , SlotBuilder(ConferenceSlotBreaks.lunch, "thursday",
        new DateTime(s"${secondDay}T13:15:00.000+02:00").toDateTime(confTimezone),
        new DateTime(s"${secondDay}T13:30:00.000+02:00").toDateTime(confTimezone), List(firstRoomForBreaks,ConferenceRooms.LOBBY_NEUILLY))
      , SlotBuilder(ConferenceSlotBreaks.coffee, "thursday",
        new DateTime(s"${secondDay}T16:15:00.000+02:00").toDateTime(confTimezone),
        new DateTime(s"${secondDay}T16:45:00.000+02:00").toDateTime(confTimezone), List(firstRoomForBreaks,ConferenceRooms.LOBBY_NEUILLY))
      , SlotBuilder(ConferenceSlotBreaks.shortBreak, "thursday",
        new DateTime(s"${secondDay}T18:15:00.000+02:00").toDateTime(confTimezone),
        new DateTime(s"${secondDay}T18:30:00.000+02:00").toDateTime(confTimezone), List(firstRoomForBreaks,ConferenceRooms.LOBBY_NEUILLY))
      , SlotBuilder(ConferenceSlotBreaks.meetAndGreet, "thursday",
        new DateTime(s"${secondDay}T19:00:00.000+02:00").toDateTime(confTimezone),
        new DateTime(s"${secondDay}T19:30:00.000+02:00").toDateTime(confTimezone), List(firstRoomForBreaks))
      , SlotBuilder(ConferenceSlotBreaks.empty, "thursday",
        new DateTime(s"${secondDay}T19:30:00.000+02:00").toDateTime(confTimezone),
        new DateTime(s"${secondDay}T20:00:00.000+02:00").toDateTime(confTimezone), List(firstRoomForBreaks,ConferenceRooms.NEUILLY_251))
      , SlotBuilder(ConferenceSlotBreaks.empty, "thursday",
        new DateTime(s"${secondDay}T20:00:00.000+02:00").toDateTime(confTimezone),
        new DateTime(s"${secondDay}T20:50:00.000+02:00").toDateTime(confTimezone), List(firstRoomForBreaks,ConferenceRooms.NEUILLY_251))
      , SlotBuilder(ConferenceSlotBreaks.empty, "thursday",
        new DateTime(s"${secondDay}T21:00:00.000+02:00").toDateTime(confTimezone),
        new DateTime(s"${secondDay}T21:30:00.000+02:00").toDateTime(confTimezone), List(firstRoomForBreaks,ConferenceRooms.NEUILLY_251))
      , SlotBuilder(ConferenceSlotBreaks.empty, "thursday",
        new DateTime(s"${secondDay}T21:30:00.000+02:00").toDateTime(confTimezone),
        new DateTime(s"${secondDay}T21:50:00.000+02:00").toDateTime(confTimezone), List(firstRoomForBreaks,ConferenceRooms.NEUILLY_251,ConferenceRooms.LOBBY_NEUILLY))
      , SlotBuilder(ConferenceSlotBreaks.empty, "thursday",
        new DateTime(s"${secondDay}T21:50:00.000+02:00").toDateTime(confTimezone),
        new DateTime(s"${secondDay}T22:30:00.000+02:00").toDateTime(confTimezone), List(firstRoomForBreaks,ConferenceRooms.NEUILLY_251,ConferenceRooms.LOBBY_NEUILLY))
      , SlotBuilder(ConferenceSlotBreaks.empty, "thursday",
        new DateTime(s"${secondDay}T22:30:00.000+02:00").toDateTime(confTimezone),
        new DateTime(s"${secondDay}T23:00:00.000+02:00").toDateTime(confTimezone), List(firstRoomForBreaks,ConferenceRooms.NEUILLY_251))
    ).flatten

    val fridayBreaks = List(
      SlotBuilder(ConferenceSlotBreaks.petitDej, "friday",
        new DateTime(s"${thirdDay}T08:00:00.000+02:00").toDateTime(confTimezone),
        new DateTime(s"${thirdDay}T09:00:00.000+02:00").toDateTime(confTimezone), List(firstRoomForBreaks))
      , SlotBuilder(ConferenceSlotBreaks.coffee, "friday",
        new DateTime(s"${thirdDay}T10:15:00.000+02:00").toDateTime(confTimezone),
        new DateTime(s"${thirdDay}T10:45:00.000+02:00").toDateTime(confTimezone), List(firstRoomForBreaks))
      , SlotBuilder(ConferenceSlotBreaks.lunch, "friday",
        new DateTime(s"${thirdDay}T12:30:00.000+02:00").toDateTime(confTimezone),
        new DateTime(s"${thirdDay}T13:00:00.000+02:00").toDateTime(confTimezone), List(firstRoomForBreaks,ConferenceRooms.LOBBY_NEUILLY))
      , SlotBuilder(ConferenceSlotBreaks.lunch, "friday",
        new DateTime(s"${thirdDay}T13:00:00.000+02:00").toDateTime(confTimezone),
        new DateTime(s"${thirdDay}T13:15:00.000+02:00").toDateTime(confTimezone), List(firstRoomForBreaks))
      , SlotBuilder(ConferenceSlotBreaks.lunch, "friday",
        new DateTime(s"${thirdDay}T13:15:00.000+02:00").toDateTime(confTimezone),
        new DateTime(s"${thirdDay}T13:30:00.000+02:00").toDateTime(confTimezone), List(firstRoomForBreaks))
      , SlotBuilder(ConferenceSlotBreaks.coffee, "friday",
        new DateTime(s"${thirdDay}T16:15:00.000+02:00").toDateTime(confTimezone),
        new DateTime(s"${thirdDay}T16:45:00.000+02:00").toDateTime(confTimezone), List(firstRoomForBreaks))
    ).flatten

    val mondaySchedule: List[Slot] = List.empty[Slot]

    val tuesdaySchedule: List[Slot] = List.empty[Slot]

    val wednesdaySchedule: List[Slot] = {
      wednesdayBreaks ++ universitySlotsWednesday ++ tiaSlotsWednesday ++ labsSlotsWednesday
    }

    val thursdaySchedule: List[Slot] = {
      thursdayBreaks ++ keynoteSlotsThursday ++ conferenceSlotsThursday ++ quickiesSlotsThursday ++ bofSlotsThursday ++ labsSlotsThursday ++ tiaSlotsThursday ++ othersSlotsThursday
    }

    val fridaySchedule: List[Slot] = {
      fridayBreaks ++ keynoteSlotsFriday ++ conferenceSlotsFriday ++ quickiesSlotsFriday ++ labsSlotsFriday ++ othersSlotsFriday
    }

    def all: List[Slot] = {
      mondaySchedule ++ tuesdaySchedule ++ wednesdaySchedule ++ thursdaySchedule ++ fridaySchedule
    }
  }

  def dateRange(from: DateTime, to: DateTime, step: Period): Iterator[DateTime] = Iterator.iterate(from)(_.plus(step)).takeWhile(!_.isAfter(to))

  val fromDay = new DateTime().withYear(2023).withMonthOfYear(4).withDayOfMonth(12)
  val toDay = new DateTime().withYear(2023).withMonthOfYear(4).withDayOfMonth(14)

  // TODO You might want to start here and configure first, your various Conference Elements
  def current() = ConferenceDescriptor(
    eventCode = "DevoxxFR2023",
    // You will need to update conf/routes files with this code if modified
    confUrlCode = "devoxxfr2023",
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
      datesI18nKey = "12 avril au 14 avril 2023",
      speakersPassDuration = 3,
      preferredDayEnabled = true,
      firstDayFr = "12 avril",
      firstDayEn = "April, 12th",
      datesFr = "du 12 avril au 14 avril 2023",
      datesEn = "April, 12th - 14th 2023",
      cfpOpenedOn = DateTime.parse("2022-11-21T09:00:00+02:00"),
      cfpClosedOn = DateTime.parse("2023-01-08T23:59:59+02:00"),
      scheduleAnnouncedOn = DateTime.parse("2023-02-20T00:00:00+02:00"),
      days = dateRange(fromDay, toDay, new Period().withDays(1))
    ),
    hosterName = "Clever-cloud", hosterWebsite = "http://www.clever-cloud.com/#DevoxxFR",
    hashTag = "#DevoxxFR",
    conferenceSponsor = ConferenceSponsor(showSponsorProposalCheckbox = true, sponsorProposalType = ConferenceProposalTypes.CONF)
    , locale = List(Locale.FRENCH)
    , localisation = "Palais des Congrès, Porte Maillot, Paris"
    , timezone = DateTimeZone.forID("Europe/Paris")
    , 1200 // French developers tends to be a bit verbose... we need extra space :-)
  )

  // It has to be a def, not a val, else it is not re-evaluated
  def isCFPOpen: Boolean = {
    Play.current.configuration.getBoolean("cfp.isOpen").getOrElse(false)
  }

  def isGoldenTicketActive: Boolean = Play.current.configuration.getBoolean("goldenTicket.active").getOrElse(false)

  def isTagSystemActive: Boolean = Play.current.configuration.getBoolean("cfp.tags.active").getOrElse(false)

  def isHTTPSEnabled: Boolean = Play.current.configuration.getBoolean("cfp.activateHTTPS").getOrElse(false)

  // Reset all votes when a Proposal with state=SUBMITTED (or DRAFT) is updated
  // This is to reflect the fact that some speakers are eavluated, then they update the talk, and we should revote for it
  def isResetVotesForSubmitted: Boolean = Play.current.configuration.getBoolean("cfp.resetVotesForSubmitted").getOrElse(false)

  // My Devoxx is an OAuth provider on which a user can register
  def isMyDevoxxActive: Boolean = Play.current.configuration.getBoolean("mydevoxx.active").getOrElse(false)

  def myDevoxxURL(): String = Play.current.configuration.getString("mydevoxx.url").getOrElse("https://my.devoxx.fr")

  // This is a JWT String shared secret that needs to be configured as a global environment variable
  def jwtSharedSecret(): String = Play.current.configuration.getString("mydevoxx.jwtSharedSecret").getOrElse("change me please")

  def gluonAuthorization(): String = Play.current.configuration.getString("gluon.auth.token").getOrElse(RandomStringUtils.random(16))

  def gluonInboundAuthorization(): String = Play.current.configuration.getString("gluon.inbound.token").getOrElse(RandomStringUtils.random(16))

  def gluonUsername(): String = Play.current.configuration.getString("gluon.username").getOrElse("")

  def gluonPassword(): String = Play.current.configuration.getString("gluon.password").getOrElse(RandomStringUtils.random(16))

  def maxProposals(): Int = Play.current.configuration.getInt("cfp.max.proposals").getOrElse(5)

  def isSendProposalRefusedEmail: Boolean = Play.current.configuration.getBoolean("cfp.sendProposalRefusedEmail").getOrElse(true)
}


package models

import play.api.Play
import org.joda.time.{DateTimeZone, DateTime}

/**
 * ConferenceDescriptor.
 * This might be the first file to look at, and to customize.
 * Idea behind this file is to try to collect all configurable parameters for a conference. *
 * @author Frederic Camblor
 */

case class ConferenceUrls(
                           faq: String, registration: String,
                           confWebsite: String, cfpHostname: String
                           )

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
                             scheduleAnnouncedOn: DateTime
                             )

case class ConferenceSponsor(showSponsorProposalCheckbox: Boolean, sponsorProposalType: ProposalType = ProposalType.UNKNOWN)

case class TrackDesc(id: String, imgSrc: String, i18nTitleProp: String, i18nDescProp: String)

case class ProposalConfiguration(id: String, slotsCount: Int,
                                 givesSpeakerFreeEntrance: Boolean,
                                 freeEntranceDisplayed: Boolean,
                                 htmlClass: String, recorded: Boolean,
                                 hiddenInCombo: Boolean = false,
                                 chosablePreferredDay: Boolean = false,
                                 impliedSelectedTrack: Option[Track] = None)

object ProposalConfiguration {

  val UNKNOWN = ProposalConfiguration(id = "unknown", slotsCount = 0, givesSpeakerFreeEntrance = false, freeEntranceDisplayed = false,
    htmlClass = "", recorded = false, hiddenInCombo = true, chosablePreferredDay = false)

  def parse(propConf: String): ProposalConfiguration = {
    ConferenceDescriptor.ConferenceProposalConfigurations.ALL.find(p => p.id == propConf).getOrElse(ProposalConfiguration.UNKNOWN)
  }

  def totalSlotsCount = ConferenceDescriptor.ConferenceProposalConfigurations.ALL.map(_.slotsCount).sum

  def isRecordedProposals(pt: ProposalType): Boolean = {
    ConferenceDescriptor.ConferenceProposalConfigurations.ALL.filter(p => p.id == pt.id).map(_.recorded).headOption.getOrElse(false)
  }

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
                                showQuestion:Boolean
                                 )

object ConferenceDescriptor {

  object ConferenceProposalTypes {
    val CONF = ProposalType(id = "conf", label = "conf.label")

    val UNI = ProposalType(id = "uni", label = "uni.label")

    val TIA = ProposalType(id = "tia", label = "tia.label")

    val LAB = ProposalType(id = "lab", label = "lab.label")

    val QUICK = ProposalType(id = "quick", label = "quick.label")

    val BOF = ProposalType(id = "bof", label = "bof.label")

    val KEY = ProposalType(id = "key", label = "key.label")

    val START = ProposalType(id = "start", label = "start.label")

    val OTHER = ProposalType(id = "other", label = "other.label")

    val ALL = List(CONF, UNI, TIA, LAB, QUICK, BOF, KEY, START, OTHER)

    def valueOf(id: String): ProposalType = id match {
      case "conf" => CONF
      case "uni" => UNI
      case "tia" => TIA
      case "lab" => LAB
      case "quick" => QUICK
      case "bof" => BOF
      case "key" => KEY
      case "start" => START
      case "other" => OTHER
    }
  }

  object ConferenceProposalConfigurations {
    val CONF = ProposalConfiguration(id = "conf", slotsCount = 89, givesSpeakerFreeEntrance = true, freeEntranceDisplayed = true, htmlClass = "icon-microphone",
      recorded = true, chosablePreferredDay = true)
    val UNI = ProposalConfiguration(id = "uni", slotsCount = 16, givesSpeakerFreeEntrance = true, freeEntranceDisplayed = true, htmlClass = "icon-laptop",
      recorded = true, chosablePreferredDay = true)
    val TIA = ProposalConfiguration(id = "tia", slotsCount = 24, givesSpeakerFreeEntrance = true, freeEntranceDisplayed = true, htmlClass = "icon-legal",
      recorded = true, chosablePreferredDay = true)
    val LAB = ProposalConfiguration(id = "lab", slotsCount = 10, givesSpeakerFreeEntrance = true, freeEntranceDisplayed = true, htmlClass = "icon-beaker",
      recorded = false, chosablePreferredDay = true)
    val QUICK = ProposalConfiguration(id = "quick", slotsCount = 28, givesSpeakerFreeEntrance = false, freeEntranceDisplayed = false, htmlClass = "icon-fast-forward",
      recorded = true, chosablePreferredDay = true)
    val BOF = ProposalConfiguration(id = "bof", slotsCount = 25, givesSpeakerFreeEntrance = false, freeEntranceDisplayed = false, htmlClass = "icon-group",
      recorded = false, chosablePreferredDay = false)
    val KEY = ProposalConfiguration(id = "key", slotsCount = 1, givesSpeakerFreeEntrance = true, freeEntranceDisplayed = false, htmlClass = "icon-microphone",
      recorded = true, chosablePreferredDay = true)
    val START = ProposalConfiguration(id = "start", slotsCount = 20, givesSpeakerFreeEntrance = true, freeEntranceDisplayed = false, htmlClass = "icon-microphone",
      recorded = false, impliedSelectedTrack = Option(ConferenceTracks.STARTUP), chosablePreferredDay = false)
    val OTHER = ProposalConfiguration(id = "other", slotsCount = 1, givesSpeakerFreeEntrance = false, freeEntranceDisplayed = false, htmlClass = "icon-microphone",
      recorded = false, hiddenInCombo = true, chosablePreferredDay = false)
    val ALL = List(CONF, UNI, TIA, LAB, QUICK, BOF, KEY, START, OTHER)


    def doesItGivesSpeakerFreeEntrance(proposalType: ProposalType): Boolean = {
      ALL.filter(_.id == proposalType.id).exists(_.givesSpeakerFreeEntrance)
    }
  }

  object ConferenceTracks {
    val STARTUP = Track("startup", "startup.label")
    val SSJ = Track("ssj", "ssj.label")
    val JAVA = Track("java", "java.label")
    val MOBILE = Track("mobile", "mobile.label")
    val ARCHISEC = Track("archisec", "archisec.label")
    val METHOD_DEVOPS = Track("methodevops", "methodevops.label")
    val FUTURE = Track("future", "future.label")
    val LANG = Track("lang", "lang.label")
    val CLOUD = Track("cloud", "cloud.label")
    val WEB = Track("web", "web.label")
    val UNKNOWN = Track("unknown", "unknown track")
    val ALL = List(STARTUP, SSJ, JAVA, MOBILE, ARCHISEC, METHOD_DEVOPS, FUTURE, LANG, CLOUD, WEB, UNKNOWN)
  }

  object ConferenceTracksDescription {
    val STARTUP = TrackDesc(ConferenceTracks.STARTUP.id, "/assets/devoxxbe2014/images/icon_startup.png", "track.startup.title", "track.startup.desc")
    val SSJ = TrackDesc(ConferenceTracks.SSJ.id, "/assets/devoxxbe2014/images/icon_javaee.png", "track.ssj.title", "track.ssj.desc")
    val JAVA = TrackDesc(ConferenceTracks.JAVA.id, "/assets/devoxxbe2014/images/icon_javase.png", "track.java.title", "track.java.desc")
    val MOBILE = TrackDesc(ConferenceTracks.MOBILE.id, "/assets/devoxxbe2014/images/icon_mobile.png", "track.mobile.title", "track.mobile.desc")
    val ARCHISEC = TrackDesc(ConferenceTracks.ARCHISEC.id, "/assets/devoxxbe2014/images/icon_architecture.png", "track.archisec.title", "track.archisec.desc")
    val METHOD_DEVOPS = TrackDesc(ConferenceTracks.METHOD_DEVOPS.id, "/assets/devoxxbe2014/images/icon_methodology.png", "track.methodevops.title", "track.methodevops.desc")
    val FUTURE = TrackDesc(ConferenceTracks.FUTURE.id, "/assets/devoxxbe2014/images/icon_future.png", "track.future.title", "track.future.desc")
    val LANG = TrackDesc(ConferenceTracks.LANG.id, "/assets/devoxxbe2014/images/icon_alternative.png", "track.lang.title", "track.lang.desc")
    val CLOUD = TrackDesc(ConferenceTracks.CLOUD.id, "/assets/devoxxbe2014/images/icon_cloud.png", "track.cloud.title", "track.cloud.desc")
    val WEB = TrackDesc(ConferenceTracks.WEB.id, "/assets/devoxxbe2014/images/icon_web.png", "track.web.title", "track.web.desc")
    val ALL = List(STARTUP, SSJ, JAVA, MOBILE, ARCHISEC, METHOD_DEVOPS, FUTURE, LANG, CLOUD, WEB)

    def findTrackDescFor(t: Track): TrackDesc = {
      ALL.find(_.id == t.id).head
    }
  }

  object ConferenceRooms {

    // Tip : I use the ID to sort-by on the view per day... So if the exhibition floor id is "aaa" it will be
    // the first column on the HTML Table
    val HALL_EXPO = Room("a_hall", "Exhibition floor", 1500, recorded = false, "special")

    val ROOM3 = Room("room3", "Room 3", 345, recorded = true, "theatre")
    val ROOM4 = Room("room4", "Room 4", 364, recorded = true, "theatre")
    val ROOM5 = Room("room5", "Room 5", 684, recorded = true, "theatre")
    val ROOM6 = Room("room6", "Room 6", 407, recorded = true, "theatre")
    val ROOM7 = Room("room7", "Room 7", 407, recorded = true, "theatre")
    val ROOM8 = Room("room8", "Room 8", 745, recorded = true, "theatre")
    val ROOM9 = Room("room9", "Room 9", 425, recorded = true, "theatre")

    val BOF1 = Room("bof1", "BOF 1", 70, recorded = false, "classroom")
    val BOF2 = Room("bof2", "BOF 2", 70, recorded = false, "classroom")

    val allRoomsUni = List(ROOM4, ROOM5, ROOM8, ROOM9)

    val allRoomsTIA = List(ROOM4, ROOM5, ROOM8, ROOM9)

    val keynoteRoom = List(ROOM8,ROOM4,ROOM5,ROOM9)
    val eveningKeynoteRoom = List(ROOM8)

    val allRoomsConf = List(ROOM8, ROOM5, ROOM9, ROOM6, ROOM7, ROOM4, ROOM3)
    val fridayRoomsConf = List(ROOM4, ROOM5, ROOM8, ROOM9)

    val allRoomsLabs = List(BOF1, BOF2)
    val oneRoomLabs = List(BOF1)

    val allRoomsBOF = List(BOF1, BOF2)
    val oneRoomBOF = List(BOF1)

    val allRooms = List(ROOM8, ROOM5, ROOM9, ROOM6, ROOM7, ROOM4, ROOM3, BOF1, BOF2, HALL_EXPO)
  }

  object ConferenceSlotBreaks {
    val registration = SlotBreak("reg", "Registration, Welcome and Breakfast", "Accueil", ConferenceRooms.HALL_EXPO)
    val petitDej = SlotBreak("dej", "Breakfast", "Accueil et petit-déjeuner", ConferenceRooms.HALL_EXPO)
    val coffee = SlotBreak("coffee", "Coffee Break", "Pause café", ConferenceRooms.HALL_EXPO)
    val lunch = SlotBreak("lunch", "Lunch", "Pause déjeuner", ConferenceRooms.HALL_EXPO)
    val shortBreak = SlotBreak("chgt", "Break", "Pause courte", ConferenceRooms.HALL_EXPO)
    val exhibition = SlotBreak("exhib", "Exhibition", "Exhibition", ConferenceRooms.HALL_EXPO)
    val meetAndGreet = SlotBreak("meet", "Meet & Greet (Exhibition) - Evening Keynote 19:00-19:30", "Exhibition", ConferenceRooms.HALL_EXPO)
    val eveningKeynote = SlotBreak("evKey", "Evening Keynote", "Keynote", ConferenceRooms.ROOM8)
    val closingKeynote = SlotBreak("closeKey", "Closing Keynote", "Keynote", ConferenceRooms.ROOM8)
    val movieSpecial = SlotBreak("movie", "Closing keynote 19:00-19:30 - Movie 20:00-22:00", "Movie", ConferenceRooms.HALL_EXPO)
    val noxx = SlotBreak("noxx", "Noxx party", "Soirée au Noxx", ConferenceRooms.HALL_EXPO)
  }

  object ConferenceSlots {

    // UNIVERSITY

    val universitySlotsMonday: List[Slot] = {

      val universityMondayMorning = ConferenceRooms.allRoomsUni.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.UNI.id, "monday", new DateTime("2014-11-10T09:30:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-10T12:30:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), r1)
      }
      val universityMondayAfternoon = ConferenceRooms.allRoomsUni.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.UNI.id, "monday", new DateTime("2014-11-10T13:30:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-10T16:30:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), r2)
      }
      universityMondayMorning ++ universityMondayAfternoon
    }

    val universitySlotsTuesday: List[Slot] = {

      val universityTuesdayMorning = ConferenceRooms.allRoomsUni.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.UNI.id, "tuesday", new DateTime("2014-11-11T09:30:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-11T12:30:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), r1)
      }
      val universityTuesdayAfternoon = ConferenceRooms.allRoomsUni.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.UNI.id, "tuesday", new DateTime("2014-11-11T13:30:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-11T16:30:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), r2)
      }
      universityTuesdayMorning ++ universityTuesdayAfternoon
    }

    // TOOLS IN ACTION

    val tiaSlotsMonday: List[Slot] = {

      val toolsMondayAfternoonSlot1 = ConferenceRooms.allRoomsTIA.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.TIA.id, "monday", new DateTime("2014-11-10T16:45:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-10T17:15:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), r1)
      }
      val toolsMondayAfternoonSlot2 = ConferenceRooms.allRoomsTIA.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.TIA.id, "monday", new DateTime("2014-11-10T17:25:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-10T17:55:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), r2)
      }
      val toolsMondayAfternoonSlot3 = ConferenceRooms.allRoomsTIA.map {
        r3 =>
          SlotBuilder(ConferenceProposalTypes.TIA.id, "monday", new DateTime("2014-11-10T18:05:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-10T18:35:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), r3)
      }
      toolsMondayAfternoonSlot1 ++ toolsMondayAfternoonSlot2 ++ toolsMondayAfternoonSlot3
    }

    val tiaSlotsTuesday: List[Slot] = {

      val toolsTuesdayAfternoonSlot1 = ConferenceRooms.allRoomsTIA.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.TIA.id, "tuesday", new DateTime("2014-11-11T16:45:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-11T17:15:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), r1)
      }
      val toolsTuesdayAfternoonSlot2 = ConferenceRooms.allRoomsTIA.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.TIA.id, "tuesday", new DateTime("2014-11-11T17:25:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-11T17:55:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), r2)
      }
      val toolsTuesdayAfternoonSlot3 = ConferenceRooms.allRoomsTIA.map {
        r3 =>
          SlotBuilder(ConferenceProposalTypes.TIA.id, "tuesday", new DateTime("2014-11-11T18:05:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-11T18:35:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), r3)
      }
      toolsTuesdayAfternoonSlot1 ++ toolsTuesdayAfternoonSlot2 ++ toolsTuesdayAfternoonSlot3
    }

    // HANDS ON LABS

    val labsSlotsMonday: List[Slot] = {

      val labsMondayMorning = ConferenceRooms.allRoomsLabs.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.LAB.id, "monday", new DateTime("2014-11-10T09:30:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-10T12:30:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), r1)
      }
      val labsMondayAfternoon = ConferenceRooms.allRoomsLabs.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.LAB.id, "monday", new DateTime("2014-11-10T13:30:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-10T16:30:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), r2)
      }
      labsMondayMorning ++ labsMondayAfternoon
    }

    val labsSlotsTuesday: List[Slot] = {

      val labsTuesdayMorning = ConferenceRooms.allRoomsLabs.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.LAB.id, "tuesday", new DateTime("2014-11-11T09:30:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-11T12:30:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), r1)
      }
      val labsTuesdayAfternoon = ConferenceRooms.allRoomsLabs.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.LAB.id, "tuesday", new DateTime("2014-11-11T13:30:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-11T16:30:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), r2)
      }
      labsTuesdayMorning ++ labsTuesdayAfternoon
    }

    val labsSlotsWednesday: List[Slot] = {

      val labsWednesdayAfternoon = ConferenceRooms.oneRoomLabs.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.LAB.id, "wednesday", new DateTime("2014-11-12T14:00:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-12T17:00:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), r1)
      }
      labsWednesdayAfternoon
    }

    val labsSlotsThursday: List[Slot] = {

      val labsThursdayAfternoon = ConferenceRooms.oneRoomLabs.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.LAB.id, "thursday", new DateTime("2014-11-13T14:00:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-13T17:00:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), r1)
      }
      labsThursdayAfternoon
    }

    // BOFS

    val bofSlotsMonday: List[Slot] = {

      val bofMondayEveningSlot1 = ConferenceRooms.allRoomsBOF.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.BOF.id, "monday", new DateTime("2014-11-10T19:00:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-10T20:00:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), r1)
      }
      val bofMondayEveningSlot2 = ConferenceRooms.allRoomsBOF.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.BOF.id, "monday", new DateTime("2014-11-10T20:00:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-10T21:00:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), r2)
      }
      val bofMondayEveningSlot3 = ConferenceRooms.allRoomsBOF.map {
        r3 =>
          SlotBuilder(ConferenceProposalTypes.BOF.id, "monday", new DateTime("2014-11-10T21:00:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-10T22:00:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), r3)
      }
      bofMondayEveningSlot1 ++ bofMondayEveningSlot2 ++ bofMondayEveningSlot3
    }

    val bofSlotsTuesday: List[Slot] = {

      val bofTuesdayEveningSlot1 = ConferenceRooms.allRoomsBOF.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.BOF.id, "tuesday", new DateTime("2014-11-11T19:00:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-11T20:00:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), r1)
      }
      val bofTuesdayEveningSlot2 = ConferenceRooms.allRoomsBOF.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.BOF.id, "tuesday", new DateTime("2014-11-11T20:00:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-11T21:00:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), r2)
      }
      val bofTuesdayEveningSlot3 = ConferenceRooms.allRoomsBOF.map {
        r3 =>
          SlotBuilder(ConferenceProposalTypes.BOF.id, "tuesday", new DateTime("2014-11-11T21:00:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-11T22:00:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), r3)
      }
      bofTuesdayEveningSlot1 ++ bofTuesdayEveningSlot2 ++ bofTuesdayEveningSlot3
    }

    val bofSlotsWednesday: List[Slot] = {

      val bofWednesdayEveningSlot1 = ConferenceRooms.allRoomsBOF.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.BOF.id, "wednesday", new DateTime("2014-11-12T19:00:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-12T20:00:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), r1)
      }
      val bofWednesdayEveningSlot2 = ConferenceRooms.allRoomsBOF.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.BOF.id, "wednesday", new DateTime("2014-11-12T20:00:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-12T21:00:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), r2)
      }
      val bofWednesdayEveningSlot3 = ConferenceRooms.allRoomsBOF.map {
        r3 =>
          SlotBuilder(ConferenceProposalTypes.BOF.id, "wednesday", new DateTime("2014-11-12T21:00:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-12T22:00:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), r3)
      }
      bofWednesdayEveningSlot1 ++ bofWednesdayEveningSlot2 ++ bofWednesdayEveningSlot3
    }

    val bofSlotsThursday: List[Slot] = {

      val bofThursdayEveningSlot1 = ConferenceRooms.allRoomsBOF.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.BOF.id, "thursday", new DateTime("2014-11-13T19:00:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-13T20:00:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), r1)
      }
      val bofThursdayEveningSlot2 = ConferenceRooms.allRoomsBOF.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.BOF.id, "thursday", new DateTime("2014-11-13T20:00:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-13T21:00:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), r2)
      }
      val bofThursdayEveningSlot3 = ConferenceRooms.allRoomsBOF.map {
        r3 =>
          SlotBuilder(ConferenceProposalTypes.BOF.id, "thursday", new DateTime("2014-11-13T21:00:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-13T22:00:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), r3)
      }
      bofThursdayEveningSlot1 ++ bofThursdayEveningSlot2 ++ bofThursdayEveningSlot3
    }

    val bofSlotFriday: List[Slot] = {

      val bofFridayEveningSlot1 = ConferenceRooms.oneRoomBOF.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.BOF.id, "friday", new DateTime("2014-11-14T10:45:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-14T11:45:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), r1)
      }
      bofFridayEveningSlot1
    }

    // QUICKIES

    val quickiesSlotsWednesday: List[Slot] = {

      val quickiesWednesdayLunch1 = ConferenceRooms.allRoomsConf.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.QUICK.id, "wednesday", new DateTime("2014-11-12T13:10:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-12T13:25:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), r1)
      }
      val quickiesWednesdayLunch2 = ConferenceRooms.allRoomsConf.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.QUICK.id, "wednesday", new DateTime("2014-11-12T13:35:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-12T13:50:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), r2)
      }
      quickiesWednesdayLunch1 ++ quickiesWednesdayLunch2
    }

    val quickiesSlotsThursday: List[Slot] = {

      val quickiesThursdayLunch1 = ConferenceRooms.allRoomsConf.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.QUICK.id, "thursday", new DateTime("2014-11-13T13:10:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-13T13:25:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), r1)
      }
      val quickiesThursdayLunch2 = ConferenceRooms.allRoomsConf.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.QUICK.id, "thursday", new DateTime("2014-11-13T13:35:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-13T13:50:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), r2)
      }
      quickiesThursdayLunch1 ++ quickiesThursdayLunch2
    }

    // CONFERENCE KEYNOTES

    val keynoteSlotsWedneday: List[Slot] = {

      val keynoteWednesdaySlot1 = ConferenceRooms.keynoteRoom.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.KEY.id, "wednesday",
            new DateTime("2014-11-12T09:30:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")),
            new DateTime("2014-11-12T10:15:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), r1)
      }
      val keynoteWednesdaySlot2 = ConferenceRooms.keynoteRoom.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.KEY.id, "wednesday",
            new DateTime("2014-11-12T10:15:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")),
            new DateTime("2014-11-12T10:55:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), r2)
      }
      val keynoteWednesdaySlot3 = ConferenceRooms.keynoteRoom.map {
        r3 =>
          SlotBuilder(ConferenceProposalTypes.KEY.id, "wednesday",
            new DateTime("2014-11-12T10:55:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")),
            new DateTime("2014-11-12T11:30:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), r3)
      }
      val keynoteWednesdaySlot4 = ConferenceRooms.eveningKeynoteRoom.map {
        r4 =>
          SlotBuilder(ConferenceProposalTypes.KEY.id, "wednesday", new DateTime("2014-11-12T19:00:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-12T19:30:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), r4)
      }

      keynoteWednesdaySlot1 ++ keynoteWednesdaySlot2 ++ keynoteWednesdaySlot3 ++ keynoteWednesdaySlot4
    }

    val keynoteSlotsThursday: List[Slot] = {

      val keynoteThursdaySlot1 = ConferenceRooms.keynoteRoom.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.KEY.id, "thursday",
            new DateTime("2014-11-13T09:30:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")),
            new DateTime("2014-11-13T10:30:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), r1)
      }
      val keynoteThursdaySlot2 = ConferenceRooms.eveningKeynoteRoom.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.KEY.id, "thursday",
            new DateTime("2014-11-13T19:00:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")),
            new DateTime("2014-11-13T19:30:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), r2)
      }

      keynoteThursdaySlot1 ++ keynoteThursdaySlot2
    }

    // CONFERENCE SLOTS

    val conferenceSlotsWedneday: List[Slot] = {

      val conferenceWednesdaySlot1 = ConferenceRooms.allRoomsConf.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id, "wednesday", new DateTime("2014-11-12T12:00:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-12T13:00:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), r1)
      }
      val conferenceWednesdaySlot2 = ConferenceRooms.allRoomsConf.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id, "wednesday", new DateTime("2014-11-12T14:00:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-12T15:00:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), r2)
      }
      val conferenceWednesdaySlot3 = ConferenceRooms.allRoomsConf.map {
        r3 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id, "wednesday", new DateTime("2014-11-12T15:10:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-12T16:10:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), r3)
      }
      val conferenceWednesdaySlot4 = ConferenceRooms.allRoomsConf.map {
        r4 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id, "wednesday", new DateTime("2014-11-12T16:40:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-12T17:40:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), r4)
      }
      val conferenceWednesdaySlot5 = ConferenceRooms.allRoomsConf.map {
        r5 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id, "wednesday", new DateTime("2014-11-12T17:50:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-12T18:50:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), r5)
      }
      conferenceWednesdaySlot1 ++ conferenceWednesdaySlot2 ++ conferenceWednesdaySlot3 ++ conferenceWednesdaySlot4 ++ conferenceWednesdaySlot5
    }

    val conferenceSlotsThursday: List[Slot] = {

      val conferenceThursdaySlot1 = ConferenceRooms.allRoomsConf.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id, "thursday", new DateTime("2014-11-13T10:50:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-13T11:50:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), r1)
      }
      val conferenceThursdaySlot2 = ConferenceRooms.allRoomsConf.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id, "thursday", new DateTime("2014-11-13T12:00:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-13T13:00:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), r2)
      }
      val conferenceThursdaySlot3 = ConferenceRooms.allRoomsConf.map {
        r3 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id, "thursday", new DateTime("2014-11-13T14:00:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-13T15:00:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), r3)
      }
      val conferenceThursdaySlot4 = ConferenceRooms.allRoomsConf.map {
        r4 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id, "thursday", new DateTime("2014-11-13T15:10:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-13T16:10:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), r4)
      }


      // Second to last slot has two 30 min. slot in Room 3
      val conferenceThursdaySlot5Room8 = SlotBuilder(ConferenceProposalTypes.CONF.id, "thursday", new DateTime("2014-11-13T16:40:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-13T17:40:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), ConferenceRooms.ROOM8)

      val conferenceThursdaySlot5Room5 = SlotBuilder(ConferenceProposalTypes.CONF.id, "thursday", new DateTime("2014-11-13T16:40:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-13T17:40:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), ConferenceRooms.ROOM5)

      val conferenceThursdaySlot5Room9 = SlotBuilder(ConferenceProposalTypes.CONF.id, "thursday", new DateTime("2014-11-13T16:40:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-13T17:40:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), ConferenceRooms.ROOM9)

      val conferenceThursdaySlot5Room6 = SlotBuilder(ConferenceProposalTypes.CONF.id, "thursday", new DateTime("2014-11-13T16:40:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-13T17:40:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), ConferenceRooms.ROOM6)

      val conferenceThursdaySlot5Room7 = SlotBuilder(ConferenceProposalTypes.CONF.id, "thursday", new DateTime("2014-11-13T16:40:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-13T17:40:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), ConferenceRooms.ROOM7)

      val conferenceThursdaySlot5Room4 = SlotBuilder(ConferenceProposalTypes.CONF.id, "thursday", new DateTime("2014-11-13T16:40:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-13T17:40:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), ConferenceRooms.ROOM4)

      // Startup
      val conferenceThursdaySlot5Room3a = SlotBuilder(ConferenceProposalTypes.START.id, "thursday", new DateTime("2014-11-13T16:40:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-13T17:10:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), ConferenceRooms.ROOM3)

      val conferenceThursdaySlot5Room3b = SlotBuilder(ConferenceProposalTypes.START.id, "thursday", new DateTime("2014-11-13T17:10:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-13T17:40:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), ConferenceRooms.ROOM3)


      // Second to last slot has two 30 min. slot in Room 3
      val conferenceThursdaySlot6Room8 =
        SlotBuilder(ConferenceProposalTypes.CONF.id, "thursday", new DateTime("2014-11-13T17:50:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-13T18:50:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), ConferenceRooms.ROOM8)

      val conferenceThursdaySlot6Room5 =
        SlotBuilder(ConferenceProposalTypes.CONF.id, "thursday", new DateTime("2014-11-13T17:50:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-13T18:50:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), ConferenceRooms.ROOM5)

      val conferenceThursdaySlot6Room9 =
        SlotBuilder(ConferenceProposalTypes.CONF.id, "thursday", new DateTime("2014-11-13T17:50:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-13T18:50:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), ConferenceRooms.ROOM9)

      val conferenceThursdaySlot6Room6 =
        SlotBuilder(ConferenceProposalTypes.CONF.id, "thursday", new DateTime("2014-11-13T17:50:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-13T18:50:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), ConferenceRooms.ROOM6)

      val conferenceThursdaySlot6Room7 =
        SlotBuilder(ConferenceProposalTypes.CONF.id, "thursday", new DateTime("2014-11-13T17:50:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-13T18:50:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), ConferenceRooms.ROOM7)

      val conferenceThursdaySlot6Room4 =
        SlotBuilder(ConferenceProposalTypes.CONF.id, "thursday", new DateTime("2014-11-13T17:50:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-13T18:50:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), ConferenceRooms.ROOM4)

      val conferenceThursdaySlot6Room3a =
        SlotBuilder(ConferenceProposalTypes.START.id, "thursday", new DateTime("2014-11-13T17:50:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-13T18:20:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), ConferenceRooms.ROOM3)

      val conferenceThursdaySlot6Room3b =
        SlotBuilder(ConferenceProposalTypes.START.id, "thursday", new DateTime("2014-11-13T18:20:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-13T18:50:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), ConferenceRooms.ROOM3)

      val toReturn = conferenceThursdaySlot1 ++ conferenceThursdaySlot2 ++ conferenceThursdaySlot3 ++ conferenceThursdaySlot4 ++ List(conferenceThursdaySlot5Room8,
        conferenceThursdaySlot5Room5, conferenceThursdaySlot5Room9, conferenceThursdaySlot5Room6, conferenceThursdaySlot5Room7, conferenceThursdaySlot5Room4, conferenceThursdaySlot5Room3a,
        conferenceThursdaySlot5Room3b, conferenceThursdaySlot6Room8, conferenceThursdaySlot6Room5, conferenceThursdaySlot6Room9, conferenceThursdaySlot6Room6, conferenceThursdaySlot6Room7,
        conferenceThursdaySlot6Room4, conferenceThursdaySlot6Room3a, conferenceThursdaySlot6Room3b)

      toReturn

    }
    // ROOM4, ROOM5, ROOM8, ROOM9
    val conferenceSlotsFriday: List[Slot] = {

      val conferenceFridaySlot1Room4 =
        SlotBuilder(ConferenceProposalTypes.CONF.id, "friday", new DateTime("2014-11-14T09:30:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-14T10:30:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), ConferenceRooms.ROOM4)

      val conferenceFridaySlot1Room5 =
        SlotBuilder(ConferenceProposalTypes.CONF.id, "friday", new DateTime("2014-11-14T09:30:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-14T10:30:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), ConferenceRooms.ROOM5)

      val conferenceFridaySlot1Room8 =
        SlotBuilder(ConferenceProposalTypes.CONF.id, "friday", new DateTime("2014-11-14T09:30:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-14T10:30:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), ConferenceRooms.ROOM8)

      val conferenceFridaySlot1Room9a =
        SlotBuilder(ConferenceProposalTypes.START.id, "friday", new DateTime("2014-11-14T09:30:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-14T10:00:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), ConferenceRooms.ROOM9)

      val conferenceFridaySlot1Room9b =
        SlotBuilder(ConferenceProposalTypes.START.id, "friday", new DateTime("2014-11-14T10:00:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-14T10:30:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), ConferenceRooms.ROOM9)

      val conferenceFridaySlot2 = ConferenceRooms.fridayRoomsConf.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id, "friday", new DateTime("2014-11-14T10:45:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-14T11:45:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), r2)
      }
      val conferenceFridaySlot3 = ConferenceRooms.fridayRoomsConf.map {
        r3 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id, "friday", new DateTime("2014-11-14T11:50:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-14T12:50:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), r3)
      }
      List(conferenceFridaySlot1Room4, conferenceFridaySlot1Room5, conferenceFridaySlot1Room8, conferenceFridaySlot1Room9a, conferenceFridaySlot1Room9b) ++ conferenceFridaySlot2 ++ conferenceFridaySlot3
    }

    // Registration, coffee break, lunch etc
    val mondayBreaks=List(
        SlotBuilder(ConferenceSlotBreaks.registration, "monday", new DateTime("2014-11-10T08:00:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-10T09:30:00.000+01:00"))
      , SlotBuilder(ConferenceSlotBreaks.lunch, "monday", new DateTime("2014-11-10T12:30:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-10T13:30:00.000+01:00"))
      , SlotBuilder(ConferenceSlotBreaks.coffee, "monday", new DateTime("2014-11-10T16:30:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-10T16:45:00.000+01:00"))
      , SlotBuilder(ConferenceSlotBreaks.shortBreak, "monday", new DateTime("2014-11-10T18:35:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-10T19:00:00.000+01:00"))
    )
    val tuesdayBreaks=List(
        SlotBuilder(ConferenceSlotBreaks.registration, "tuesday", new DateTime("2014-11-11T08:00:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-11T09:30:00.000+01:00"))
      , SlotBuilder(ConferenceSlotBreaks.lunch, "tuesday", new DateTime("2014-11-11T12:30:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-11T13:30:00.000+01:00"))
      , SlotBuilder(ConferenceSlotBreaks.coffee, "tuesday", new DateTime("2014-11-11T16:30:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-11T16:45:00.000+01:00"))
      , SlotBuilder(ConferenceSlotBreaks.exhibition, "tuesday", new DateTime("2014-11-11T18:35:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-11T20:00:00.000+01:00"))
    )
    val wednesdayBreaks=List(
        SlotBuilder(ConferenceSlotBreaks.registration, "wednesday", new DateTime("2014-11-12T08:00:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-12T09:30:00.000+01:00"))
      , SlotBuilder(ConferenceSlotBreaks.coffee, "wednesday", new DateTime("2014-11-12T11:40:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-12T12:00:00.000+01:00"))
      , SlotBuilder(ConferenceSlotBreaks.lunch, "wednesday", new DateTime("2014-11-12T13:00:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-12T14:00:00.000+01:00"))
      , SlotBuilder(ConferenceSlotBreaks.coffee, "wednesday", new DateTime("2014-11-12T16:10:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-12T16:40:00.000+01:00"))
      , SlotBuilder(ConferenceSlotBreaks.meetAndGreet, "wednesday", new DateTime("2014-11-12T18:30:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-12T20:00:00.000+01:00"))
    )
    val thursdayBreaks=List(
        SlotBuilder(ConferenceSlotBreaks.petitDej, "thursday", new DateTime("2014-11-13T08:00:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-13T09:30:00.000+01:00"))
      , SlotBuilder(ConferenceSlotBreaks.coffee, "thursday", new DateTime("2014-11-13T10:30:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-13T10:50:00.000+01:00"))
      , SlotBuilder(ConferenceSlotBreaks.lunch, "thursday", new DateTime("2014-11-13T13:00:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-13T14:00:00.000+01:00"))
      , SlotBuilder(ConferenceSlotBreaks.coffee, "thursday", new DateTime("2014-11-13T16:10:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-13T16:40:00.000+01:00"))
      , SlotBuilder(ConferenceSlotBreaks.movieSpecial, "thursday", new DateTime("2014-11-13T18:50:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-13T20:00:00.000+01:00"))
    )
     val fridayBreaks=List(
        SlotBuilder(ConferenceSlotBreaks.petitDej, "friday", new DateTime("2014-11-14T08:30:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-14T09:30:00.000+01:00"))
      , SlotBuilder(ConferenceSlotBreaks.coffee, "friday", new DateTime("2014-11-14T10:30:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/Brussels")), new DateTime("2014-11-14T10:45:00.000+01:00"))
    )
    // DEVOXX DAYS

    val monday: List[Slot] = {
     mondayBreaks ++ universitySlotsMonday ++ tiaSlotsMonday ++ labsSlotsMonday ++ bofSlotsMonday
    }

    val tuesday: List[Slot] = {
      tuesdayBreaks ++ universitySlotsTuesday ++ tiaSlotsTuesday ++ labsSlotsTuesday ++ bofSlotsTuesday
    }

    val wednesday: List[Slot] = {
      wednesdayBreaks ++ keynoteSlotsWedneday ++ conferenceSlotsWedneday ++ quickiesSlotsWednesday ++ bofSlotsWednesday ++ labsSlotsWednesday
    }

    val thursday: List[Slot] = {
      thursdayBreaks ++ keynoteSlotsThursday ++ conferenceSlotsThursday ++ quickiesSlotsThursday ++ bofSlotsThursday ++ labsSlotsThursday
    }

    val friday: List[Slot] = {
      fridayBreaks ++ conferenceSlotsFriday ++ bofSlotFriday
    }

    // COMPLETE DEVOXX

    def all: List[Slot] = {
      monday ++ tuesday ++ wednesday ++ thursday ++ friday
    }
  }

  def current() = ConferenceDescriptor(
    eventCode = "DV14",
    // You will need to update conf/routes files with this code if modified
    confUrlCode = "devoxxbe2014",
    frLangEnabled = false,
    fromEmail = Play.current.configuration.getString("mail.from").getOrElse("program@devoxx.com"),
    committeeEmail = Play.current.configuration.getString("mail.committee.email").getOrElse("program@devoxx.com"),
    bccEmail = Play.current.configuration.getString("mail.bcc"),
    bugReportRecipient = Play.current.configuration.getString("mail.bugreport.recipient").getOrElse("nicolas.martignole@devoxx.fr"),
    conferenceUrls = ConferenceUrls(
      faq = "http://www.devoxx.be/faq/",
      registration = "http://regbe.devoxx.com",
      confWebsite = "http://www.devoxx.be/",
      cfpHostname = Play.current.configuration.getString("cfp.hostname").getOrElse("cfp.devoxx.be")
    ),
    timing = ConferenceTiming(
      datesI18nKey = "10th-14th November",
      speakersPassDuration = 5,
      preferredDayEnabled = true,
      firstDayFr = "10 novembre",
      firstDayEn = "november 10th",
      datesFr = "du 10 au 14 Novembre 2014",
      datesEn = "from 10th to 14th of November, 2014",
      cfpOpenedOn = DateTime.parse("2014-06-03T00:00:00+02:00"),
      cfpClosedOn = DateTime.parse("2014-07-11T23:59:59+02:00"),
      scheduleAnnouncedOn = DateTime.parse("2014-09-15T00:00:00+02:00")
    ),
    hosterName = "Clever-cloud", hosterWebsite = "http://www.clever-cloud.com/#DevoxxVE",
    hashTag = "#DevoxxBE",
    conferenceSponsor = ConferenceSponsor(showSponsorProposalCheckbox = true, sponsorProposalType = ConferenceProposalTypes.CONF)
    , List("en_EN")
    , "Metropolis Antwerp, Groenendaallaan 394, 2030 Antwerp,Belgium"
    , showQuestion=false
  )

  def isCFPOpen: Boolean = {
    false
  }

}

package models

import java.util.Locale

import org.joda.time.{DateTime, DateTimeZone, Period}
import play.api.Play
import play.api.i18n.Messages

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

case class ConferenceUrls(info: String, registration: String, sponsors: String, confWebsite: String, cfpHostname: String) {
  def cfpURL(): String = {
    if (Play.current.configuration.getBoolean("cfp.activateHTTPS").getOrElse(false)) {
      s"https://$cfpHostname"
    }else{
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
                             days:Iterator[DateTime]
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
                                notifyProposalSubmitted: Boolean,
                                maxProposalSummaryCharacters: Int = 1200)

object ConferenceDescriptor {

  /**
    * TODO configure here the kind of talks you will propose
    */
  object ConferenceProposalTypes {
    val CONF = ProposalType(id = "conf", label = "conf.label")

    val LAB = ProposalType(id = "lab", label = "lab.label")

    val QUICK = ProposalType(id = "quick", label = "quick.label")

    val BOF = ProposalType(id = "bof", label = "bof.label")

    val OPENING_KEY = ProposalType(id = "opening_key", label = "opening.key.label")

    val CLOSING_KEY = ProposalType(id = "closing_key", label = "closing.key.label")

    val IGNITE = ProposalType(id = "ignite", label = "ignite.label")

    val ALL = List(CONF, LAB, QUICK, BOF, OPENING_KEY, CLOSING_KEY, IGNITE)

    def valueOf(id: String): ProposalType = id match {
      case "conf" => CONF
      case "lab" => LAB
      case "quick" => QUICK
      case "bof" => BOF
      case "opening_key" => OPENING_KEY
      case "closing_key" => CLOSING_KEY
      case "ignite" => IGNITE
    }
  }

  object ConferenceProposalConfigurations {
    val CONF = ProposalConfiguration(id = "conf", slotsCount = ConferenceSlots.all.count(_.name.equals(ConferenceProposalTypes.CONF.id)), givesSpeakerFreeEntrance = true, freeEntranceDisplayed = true, htmlClass = "icon-microphone",
      chosablePreferredDay = true)
    val LAB = ProposalConfiguration(id = "lab", slotsCount = ConferenceSlots.all.count(_.name.equals(ConferenceProposalTypes.LAB.id)), givesSpeakerFreeEntrance = true, freeEntranceDisplayed = true, htmlClass = "icon-beaker",
      chosablePreferredDay = true)
    val QUICK = ProposalConfiguration(id = "quick", slotsCount = ConferenceSlots.all.count(_.name.equals(ConferenceProposalTypes.QUICK.id)), givesSpeakerFreeEntrance = false, freeEntranceDisplayed = false, htmlClass = "icon-fast-forward",
      chosablePreferredDay = true)
    val BOF = ProposalConfiguration(id = "bof", slotsCount = ConferenceSlots.all.count(_.name.equals(ConferenceProposalTypes.BOF.id)), givesSpeakerFreeEntrance = true, freeEntranceDisplayed = true, htmlClass = "icon-group",
      chosablePreferredDay = false)
    val OPENING_KEY = ProposalConfiguration(id = "opening_key", slotsCount = 1, givesSpeakerFreeEntrance = true, freeEntranceDisplayed = false, htmlClass = "icon-microphone",
      chosablePreferredDay = true)
    val CLOSING_KEY = ProposalConfiguration(id = "closing_key", slotsCount = 1, givesSpeakerFreeEntrance = true, freeEntranceDisplayed = false, htmlClass = "icon-microphone",
      chosablePreferredDay = true)
    val IGNITE = ProposalConfiguration(id = "ignite", slotsCount = ConferenceSlots.all.count(_.name.equals(ConferenceProposalTypes.IGNITE.id)), givesSpeakerFreeEntrance = false, freeEntranceDisplayed = false, htmlClass = "icon-microphone",
      chosablePreferredDay = false)

    val ALL = List(CONF, LAB, QUICK, BOF, OPENING_KEY, CLOSING_KEY, IGNITE)

    def doesItGivesSpeakerFreeEntrance(proposalType: ProposalType): Boolean = {
      ALL.filter(_.id == proposalType.id).exists(_.givesSpeakerFreeEntrance)
    }
  }

  object ConferenceTracks {
    val METHOD_ARCHI = Track("method_archi", "method_archi.label")
    val JAVA = Track("java", "java.label")
    val CLOUD = Track("cloud", "cloud.label")
    val SSJ = Track("ssj", "ssj.label")
    val LANG = Track("lang", "lang.label")
    val BIGDATA = Track("bigdata", "bigdata.label")
    val WEB = Track("web", "web.label")
    val GEEK = Track("geek", "geek.label")
    val IOT = Track("iot", "iot.label")

    val SECURITY = Track("security", "security.label")
    val ARCHITECTURE = Track("architecture", "architecture.label")
    val UNKNOWN = Track("unknown", "unknown track")
    val ALL = List(METHOD_ARCHI, JAVA, CLOUD, SSJ, LANG, BIGDATA, WEB, GEEK, IOT, SECURITY, ARCHITECTURE, UNKNOWN)
  }

  object ConferenceTracksDescription {
    val METHOD_ARCHI = TrackDesc(ConferenceTracks.METHOD_ARCHI.id, "/assets/devoxxuk2017/images/icon_methodology.png", ConferenceTracks.METHOD_ARCHI.label, "track.method_archi.desc")
    val JAVA = TrackDesc(ConferenceTracks.JAVA.id, "/assets/devoxxuk2017/images/icon_javase.png", ConferenceTracks.JAVA.label, "track.java.desc")
    val CLOUD = TrackDesc(ConferenceTracks.CLOUD.id, "/assets/devoxxuk2017/images/icon_cloud.png", ConferenceTracks.CLOUD.label, "track.cloud.desc")
    val SSJ = TrackDesc(ConferenceTracks.SSJ.id, "/assets/devoxxuk2017/images/icon_javaee.png", ConferenceTracks.SSJ.label, "track.ssj.desc")
    val LANG = TrackDesc(ConferenceTracks.LANG.id, "/assets/devoxxuk2017/images/icon_alternative.png", ConferenceTracks.LANG.label, "track.lang.desc")
    val BIGDATA = TrackDesc(ConferenceTracks.BIGDATA.id, "/assets/devoxxuk2017/images/icon_bigdata.png", ConferenceTracks.BIGDATA.label, "track.bigdata.desc")
    val WEB = TrackDesc(ConferenceTracks.WEB.id, "/assets/devoxxuk2017/images/icon_web.png", ConferenceTracks.WEB.label, "track.web.desc")
    val GEEK = TrackDesc(ConferenceTracks.GEEK.id, "/assets/devoxxuk2017/images/icon_geek.png", ConferenceTracks.GEEK.label, "track.geek.desc")
    val IOT = TrackDesc(ConferenceTracks.IOT.id, "/assets/devoxxuk2017/images/icon_iot.png", ConferenceTracks.IOT.label, "track.iot.desc")

    val SECURITY = TrackDesc(ConferenceTracks.SECURITY.id, "/assets/devoxxuk2017/images/icon_security.png", ConferenceTracks.SECURITY.label, "track.security.desc")
    val ARCHITECTURE = TrackDesc(ConferenceTracks.ARCHITECTURE.id, "/assets/devoxxuk2017/images/icon_architecture.png", ConferenceTracks.ARCHITECTURE.label, "track.architecture.desc")

    val UNKNOWN = TrackDesc(ConferenceTracks.UNKNOWN.id, "/assets/devoxxuk2017/images/icon_web.png", ConferenceTracks.UNKNOWN.label, "track.unknown.desc")

    val ALL = List(METHOD_ARCHI
      , JAVA
      , CLOUD
      , SSJ
      , LANG
      , BIGDATA
      , WEB
      , GEEK
      , IOT
      , SECURITY
      , ARCHITECTURE
    )

    def findTrackDescFor(t: Track): TrackDesc = {
      if (ALL.exists(_.id == t.id)) {
        ALL.find(_.id == t.id).head
      } else {
        ConferenceTracksDescription.UNKNOWN
      }
    }
  }

  // TODO If you want to use the Devoxx Scheduler, you can describe here the list of rooms, with capacity for seats
  object ConferenceRooms {

    // Tip : I use the ID to sort-by on the view per day... So if the exhibition floor id is "aaa" it will be
    // the first column on the HTML Table
    val GALLERY_HALL = Room("a_gallery_hall", "Gallery Hall", 1500, "special")

    val HALL_EXPO = Room("z_hall", "Exhibition floor", 1500, "special")

    val AUDIT = Room("aud_room", "Auditorium", 550, "theatre")
    val ROOM_A = Room("room1", "Room A", 220, "theatre")
    val ROOM_B = Room("room2", "Room B", 32, "classroom")
    val ROOM_C = Room("room3", "Room C", 32, "classroom")
    val ROOM_D = Room("room4", "Room D", 32, "classroom")

    val LAB_ROOM_A = Room("x_lab_room1", "Lab Room A", 32, "classroom")
    val LAB_ROOM_B = Room("y_lab_room2", "Lab Room B", 32, "classroom")

    val keynoteRoom = List(GALLERY_HALL)

    val conferenceRooms = List(GALLERY_HALL, AUDIT, ROOM_A, ROOM_B, ROOM_C, ROOM_D)

    val bofThu = List(AUDIT, ROOM_A, ROOM_C, LAB_ROOM_A, LAB_ROOM_B)
    val labsThu = List(LAB_ROOM_A, LAB_ROOM_B)
    val igniteThu = List(ROOM_B)
    val quickieThu = List(GALLERY_HALL, AUDIT, ROOM_A, ROOM_B, ROOM_C)

    val uniFri = List(ROOM_A)
    val quickieFri = List(GALLERY_HALL, AUDIT, ROOM_A, ROOM_B, ROOM_C)
    val labsFri = List(LAB_ROOM_A, LAB_ROOM_B)

   val allRooms = List(GALLERY_HALL, AUDIT, ROOM_A, ROOM_B, ROOM_C, ROOM_D, LAB_ROOM_A, LAB_ROOM_B, HALL_EXPO)
  }

  object ConferenceSlotBreaks {
    val registrationAndCoffee = SlotBreak("reg", "Registration & Coffee", "Accueil", ConferenceRooms.HALL_EXPO)
    val breakfast = SlotBreak("dej", "Breakfast", "Accueil et petit-déjeuner", ConferenceRooms.HALL_EXPO)
    val coffee = SlotBreak("coffee", "Coffee Break", "Pause café", ConferenceRooms.HALL_EXPO)
    val lunch = SlotBreak("lunch", "Lunch", "Pause déjeuner", ConferenceRooms.HALL_EXPO)
    val eveningReception = SlotBreak("reception", "Evening Reception", "Evening Reception", ConferenceRooms.HALL_EXPO)
    val closingKeynote = SlotBreak("closeKey", "Closing Keynote", "Keynote", ConferenceRooms.AUDIT)
  }

  object ConferenceSlots {

    // VARIABLE CONSTANTS

    private val THURSDAY: String = "thursday"
    private val FRIDAY: String = "friday"

    private val THU_DATE = "2017-05-11T"
    private val FRI_DATE = "2017-05-12T"

    private val MIN_SEC = ":00.000+01:00"

    // HANDS ON LABS
    val labsSlotThursday: List[Slot] = {

      val labsThursdayEveningSlot1 = ConferenceRooms.labsThu.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.LAB.id,
            THURSDAY,
            new DateTime(THU_DATE + "10:45" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)),
            new DateTime(THU_DATE + "12:45" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)), r1)
      }

      val labsThursdayEveningSlot2 = ConferenceRooms.labsThu.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.LAB.id,
            THURSDAY,
            new DateTime(THU_DATE + "13:40" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)),
            new DateTime(THU_DATE + "15:40" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)), r2)
      }

      val labsThursdayEveningSlot3 = ConferenceRooms.labsThu.map {
        r3 =>
          SlotBuilder(ConferenceProposalTypes.LAB.id,
            THURSDAY,
            new DateTime(THU_DATE + "16:00" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)),
            new DateTime(THU_DATE + "18:00" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)), r3)
      }

      labsThursdayEveningSlot1 ++ labsThursdayEveningSlot2 ++ labsThursdayEveningSlot3
    }

    val labsSlotFriday: List[Slot] = {

      val labsFridayEveningSlot1 = ConferenceRooms.labsFri.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.LAB.id,
            FRIDAY,
            new DateTime(FRI_DATE + "09:00" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)),
            new DateTime(FRI_DATE + "11:00" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)), r1)
      }

      val labsFridayEveningSlot2 = ConferenceRooms.labsFri.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.LAB.id,
            FRIDAY,
            new DateTime(FRI_DATE + "11:15" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)),
            new DateTime(FRI_DATE + "13:15" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)), r2)
      }

      val labsFridayEveningSlot3 = ConferenceRooms.labsFri.map {
        r3 =>
          SlotBuilder(ConferenceProposalTypes.LAB.id,
            FRIDAY,
            new DateTime(FRI_DATE + "14:15" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)),
            new DateTime(FRI_DATE + "16:15" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)), r3)
      }

      labsFridayEveningSlot1 ++ labsFridayEveningSlot2 ++ labsFridayEveningSlot3
    }

    // BOFS

    val bofSlotThursday: List[Slot] = {

      val bofThursdayEveningSlot = ConferenceRooms.bofThu.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.BOF.id,
                      THURSDAY,
                      new DateTime(THU_DATE + "18:35" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)),
                      new DateTime(THU_DATE + "19:35" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)), r1)
      }
      bofThursdayEveningSlot
    }

    // QUICKIES

    val quickiesSlotsThursday: List[Slot] = {

      val quickiesThursdayLunch1 = ConferenceRooms.quickieThu.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.QUICK.id,
                      THURSDAY,
                      new DateTime(THU_DATE + "12:55" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)),
                      new DateTime(THU_DATE + "13:10" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)), r1)
      }
      val quickiesThursdayLunch2 = ConferenceRooms.quickieThu.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.QUICK.id,
                      THURSDAY,
                      new DateTime(THU_DATE + "13:15" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)),
                      new DateTime(THU_DATE + "13:30" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)), r2)
      }
      quickiesThursdayLunch1 ++ quickiesThursdayLunch2
    }

    val quickiesSlotsFriday: List[Slot] = {

      val quickiesFridayLunch1 = ConferenceRooms.quickieFri.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.QUICK.id,
            FRIDAY,
            new DateTime(FRI_DATE + "13:25" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)),
            new DateTime(FRI_DATE + "13:40" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)), r1)
      }
      val quickiesFridayLunch2 = ConferenceRooms.quickieFri.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.QUICK.id,
            FRIDAY,
            new DateTime(FRI_DATE + "13:45" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)),
            new DateTime(FRI_DATE + "14:00" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)), r2)
      }
      quickiesFridayLunch1 ++ quickiesFridayLunch2
    }

    // CONFERENCE KEYNOTES

    val keynoteSlotThursday: List[Slot] = {

      val keynoteSlot1 = ConferenceRooms.keynoteRoom.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.OPENING_KEY.id, THURSDAY,
            new DateTime(THU_DATE + "09:00" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)),
            new DateTime(THU_DATE + "09:20" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)), r1)
      }

      val keynoteSlot2 = ConferenceRooms.keynoteRoom.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.OPENING_KEY.id, THURSDAY,
            new DateTime(THU_DATE + "09:20" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)),
            new DateTime(THU_DATE + "09:40" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)), r2)
      }

      val keynoteSlot3 = ConferenceRooms.keynoteRoom.map {
        r3 =>
          SlotBuilder(ConferenceProposalTypes.OPENING_KEY.id, THURSDAY,
            new DateTime(THU_DATE + "09:40" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)),
            new DateTime(THU_DATE + "10:00" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)), r3)
      }

      val keynoteSlot4 = ConferenceRooms.keynoteRoom.map {
        r4 =>
          SlotBuilder(ConferenceProposalTypes.OPENING_KEY.id, THURSDAY,
            new DateTime(THU_DATE + "10:00" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)),
            new DateTime(THU_DATE + "10:20" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)), r4)
      }

      keynoteSlot1 ++ keynoteSlot2 ++ keynoteSlot3 ++ keynoteSlot4
    }

    val keynoteSlotFriday: List[Slot] = {

      ConferenceRooms.keynoteRoom.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.CLOSING_KEY.id, FRIDAY,
            new DateTime(FRI_DATE + "17:40" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)),
            new DateTime(FRI_DATE + "18:30" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)), r1)
      }
    }

    // CONFERENCE SLOTS

    val conferenceSlotsThursday: List[Slot] = {

      val conferenceThursdaySlot0 = ConferenceRooms.conferenceRooms.map {
        r0 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id,
                      THURSDAY,
                      new DateTime(THU_DATE + "10:50" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)),
                      new DateTime(THU_DATE + "11:40" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)), r0)
      }
      val conferenceThursdaySlot1 = ConferenceRooms.conferenceRooms.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id,
                      THURSDAY,
                      new DateTime(THU_DATE + "11:55" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)),
                      new DateTime(THU_DATE + "12:45" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)), r1)
      }
      val conferenceThursdaySlot2 = ConferenceRooms.conferenceRooms.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id,
                      THURSDAY,
                      new DateTime(THU_DATE + "13:45" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)),
                      new DateTime(THU_DATE + "14:35" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)), r2)
      }
      val conferenceThursdaySlot3 = ConferenceRooms.conferenceRooms.map {
        r3 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id,
                      THURSDAY,
                      new DateTime(THU_DATE + "14:50" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)),
                      new DateTime(THU_DATE + "15:40" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)), r3)
      }
      val conferenceThursdaySlot4 = ConferenceRooms.conferenceRooms.map {
        r4 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id,
                      THURSDAY,
                      new DateTime(THU_DATE + "16:10" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)),
                      new DateTime(THU_DATE + "17:00" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)), r4)
      }

      val conferenceThursdaySlot5 = ConferenceRooms.conferenceRooms.map {
        r5 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id,
            THURSDAY,
            new DateTime(THU_DATE + "17:15" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)),
            new DateTime(THU_DATE + "18:05" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)), r5)
      }

      val toReturn = conferenceThursdaySlot0 ++
                     conferenceThursdaySlot1 ++
                     conferenceThursdaySlot2 ++
                     conferenceThursdaySlot3 ++
                     conferenceThursdaySlot4 ++
                     conferenceThursdaySlot5

      toReturn

    }
    // ROOM4, ROOM5, ROOM8, ROOM9, ROOM10
    val conferenceSlotsFriday: List[Slot] = {

      val conferenceFridaySlot1 = ConferenceRooms.conferenceRooms.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id,
                      FRIDAY,
                      new DateTime(FRI_DATE + "09:00" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)),
                      new DateTime(FRI_DATE + "09:50" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)), r1)
      }
      val conferenceFridaySlot2 = ConferenceRooms.conferenceRooms.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id,
                      FRIDAY,
                      new DateTime(FRI_DATE + "10:05" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)),
                      new DateTime(FRI_DATE + "10:55" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)), r2)
      }
      val conferenceFridaySlot3 = ConferenceRooms.conferenceRooms.map {
        r3 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id,
                      FRIDAY,
                      new DateTime(FRI_DATE + "11:20" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)),
                      new DateTime(FRI_DATE + "12:10" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)), r3)
      }
      val conferenceFridaySlot4 = ConferenceRooms.conferenceRooms.map {
        r4 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id,
            FRIDAY,
            new DateTime(FRI_DATE + "12:25" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)),
            new DateTime(FRI_DATE + "13:15" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)), r4)
      }
      val conferenceFridaySlot5 = ConferenceRooms.conferenceRooms.map {
        r5 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id,
            FRIDAY,
            new DateTime(FRI_DATE + "14:15" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)),
            new DateTime(FRI_DATE + "15:05" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)), r5)
      }
      val conferenceFridaySlot6 = ConferenceRooms.conferenceRooms.map {
        r6 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id,
            FRIDAY,
            new DateTime(FRI_DATE + "15:20" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)),
            new DateTime(FRI_DATE + "16:10" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)), r6)
      }
      val conferenceFridaySlot7 = ConferenceRooms.conferenceRooms.map {
        r7 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id,
            FRIDAY,
            new DateTime(FRI_DATE + "16:40" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)),
            new DateTime(FRI_DATE + "17:30" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)), r7)
      }

      conferenceFridaySlot1 ++ conferenceFridaySlot2 ++ conferenceFridaySlot3 ++ conferenceFridaySlot4 ++
      conferenceFridaySlot5 ++ conferenceFridaySlot6 ++ conferenceFridaySlot7
      }

    // Ignite slots
    val igniteSlotsThursday: List[Slot] = {
      ConferenceRooms.igniteThu.flatMap {
        room => List(
          SlotBuilder(ConferenceProposalTypes.IGNITE.id, THURSDAY,
            new DateTime(THU_DATE + "18:15" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)),
            new DateTime(THU_DATE + "18:20" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)), room),
          SlotBuilder(ConferenceProposalTypes.IGNITE.id, THURSDAY,
            new DateTime(THU_DATE + "18:20" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)),
            new DateTime(THU_DATE + "18:25" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)), room),
          SlotBuilder(ConferenceProposalTypes.IGNITE.id, THURSDAY,
            new DateTime(THU_DATE + "18:25" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)),
            new DateTime(THU_DATE + "18:30" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)), room),
          SlotBuilder(ConferenceProposalTypes.IGNITE.id, THURSDAY,
            new DateTime(THU_DATE + "18:30" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)),
            new DateTime(THU_DATE + "18:35" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)), room),
          SlotBuilder(ConferenceProposalTypes.IGNITE.id, THURSDAY,
            new DateTime(THU_DATE + "18:35" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)),
            new DateTime(THU_DATE + "18:40" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)), room),
          SlotBuilder(ConferenceProposalTypes.IGNITE.id, THURSDAY,
            new DateTime(THU_DATE + "18:40" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)),
            new DateTime(THU_DATE + "18:45" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)), room),
          SlotBuilder(ConferenceProposalTypes.IGNITE.id, THURSDAY,
            new DateTime(THU_DATE + "18:45" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)),
            new DateTime(THU_DATE + "18:50" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)), room)
        )
      }
    }

    // Registration, coffee break, lunch etc
    val thursdayBreaks = List(
      SlotBuilder(ConferenceSlotBreaks.registrationAndCoffee, THURSDAY,
        new DateTime(THU_DATE + "08:00" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)),
        new DateTime(THU_DATE + "09:00" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)))
      , SlotBuilder(ConferenceSlotBreaks.coffee, THURSDAY,
        new DateTime(THU_DATE + "10:20" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)),
        new DateTime(THU_DATE + "10:50" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)))
      , SlotBuilder(ConferenceSlotBreaks.lunch, THURSDAY,
        new DateTime(THU_DATE + "12:45" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)),
        new DateTime(THU_DATE + "13:45" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)))
      , SlotBuilder(ConferenceSlotBreaks.coffee, THURSDAY,
        new DateTime(THU_DATE + "15:40" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)),
        new DateTime(THU_DATE + "16:10" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)))
      , SlotBuilder(ConferenceSlotBreaks.eveningReception, THURSDAY,
        new DateTime(THU_DATE + "18:00" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)),
        new DateTime(THU_DATE + "18:50" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)))
    )

    val fridayBreaks = List(
      SlotBuilder(ConferenceSlotBreaks.registrationAndCoffee, FRIDAY,
        new DateTime(FRI_DATE + "08:00" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)),
        new DateTime(FRI_DATE + "09:00" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone))),
      SlotBuilder(ConferenceSlotBreaks.coffee, FRIDAY,
        new DateTime(FRI_DATE + "10:55" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)),
        new DateTime(FRI_DATE + "11:20" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone))),
      SlotBuilder(ConferenceSlotBreaks.lunch, FRIDAY,
        new DateTime(FRI_DATE + "13:15" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)),
        new DateTime(FRI_DATE + "14:15" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone))),
      SlotBuilder(ConferenceSlotBreaks.coffee, FRIDAY,
        new DateTime(FRI_DATE + "16:10" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)),
        new DateTime(FRI_DATE + "16:40" + MIN_SEC).toDateTime(DateTimeZone.forID(timeZone)))
    )

    // DEVOXX DAYS

    val thursdaySchedule: List[Slot] = {
      thursdayBreaks ++
      keynoteSlotThursday ++
      conferenceSlotsThursday ++
      quickiesSlotsThursday ++
      bofSlotThursday ++
      labsSlotThursday ++
      igniteSlotsThursday
    }

    val fridaySchedule: List[Slot] = {
      fridayBreaks ++
      keynoteSlotFriday ++
      conferenceSlotsFriday ++
      labsSlotFriday ++
      quickiesSlotsFriday
    }

    // COMPLETE DEVOXX
    def all: List[Slot] = {
      thursdaySchedule ++
      fridaySchedule
    }
  }

  def dateRange(from: DateTime, to: DateTime, step: Period): Iterator[DateTime] = Iterator.iterate(from)(_.plus(step)).takeWhile(!_.isAfter(to))

  val fromDay = new DateTime().withYear(2017).withMonthOfYear(5).withDayOfMonth(11)
  val toDay = new DateTime().withYear(2017).withMonthOfYear(5).withDayOfMonth(12)
  
  val MAXIMUM_SUMMARY_CHARACTERS = 1200

  def current(): ConferenceDescriptor = new ConferenceDescriptor(
    eventCode = "DV17",
    // You will need to update conf/routes files with this code if modified
    confUrlCode = "devoxxuk2017",
    frLangEnabled = false,
    fromEmail = Play.current.configuration.getString("mail.from").getOrElse("info@devoxx.co.uk"),
    committeeEmail = Play.current.configuration.getString("mail.committee.email").getOrElse("program@devoxx.co.uk"),
    bccEmail = Play.current.configuration.getString("mail.bcc"),
    bugReportRecipient = Play.current.configuration.getString("mail.bugreport.recipient").getOrElse("nicolas.martignole@devoxx.fr"),
    conferenceUrls = ConferenceUrls(
      info = "http://www.devoxx.co.uk/#info",
      sponsors = "http://www.devoxx.co.uk/#sponsors",
      registration = "https://www.eventbrite.co.uk/e/devoxx-uk-2017-tickets-28649696012",
      confWebsite = "http://www.devoxx.co.uk/",
      cfpHostname = {
        val h=Play.current.configuration.getString("cfp.hostname").getOrElse("cfp.devoxx.co.uk")
        if(h.endsWith("/")){
          h.substring(0,h.length - 1)
        }else{
          h
        }
      }
    ),
    timing = ConferenceTiming(
      datesI18nKey = Messages("conference.dates"),
      speakersPassDuration = 2,
      preferredDayEnabled = true,
      firstDayFr = "11 mai",
      firstDayEn = "May 11th",
      datesFr = "do 11 au 12 Mai 2017",
      datesEn = "from 11th to 12th of May, 2017",
      cfpOpenedOn = DateTime.parse("2016-11-01T00:00:00+01:00"),
      cfpClosedOn = DateTime.parse("2017-01-16T23:59:59+01:00"),
      scheduleAnnouncedOn = DateTime.parse("2017-01-30T00:00:00+01:00"),
      days=dateRange(fromDay, toDay,new Period().withDays(1))
    ),
    hosterName = "Clever-cloud", hosterWebsite = "http://www.clever-cloud.com/#DevoxxUK",
    hashTag = "#DevoxxUK",
    conferenceSponsor = ConferenceSponsor(showSponsorProposalCheckbox = true, sponsorProposalType = ConferenceProposalTypes.CONF)
    , List(Locale.ENGLISH)
    , "Business Design Centre, 52 Upper St, London N1 0QH, United Kingdom"
    , notifyProposalSubmitted = true
    , MAXIMUM_SUMMARY_CHARACTERS // French developers tends to be a bit verbose... we need extra space :-)
  )

  def conference2017() = ConferenceDescriptor(
    eventCode = "DV17",
    // You will need to update conf/routes files with this code if modified
    confUrlCode = "devoxxuk2017",
    frLangEnabled = false,
    fromEmail = Play.current.configuration.getString("mail.from").getOrElse("info@devoxx.co.uk"),
    committeeEmail = Play.current.configuration.getString("mail.committee.email").getOrElse("program@devoxx.co.uk"),
    bccEmail = Play.current.configuration.getString("mail.bcc"),
    bugReportRecipient = Play.current.configuration.getString("mail.bugreport.recipient").getOrElse("nicolas.martignole@devoxx.fr"),
    conferenceUrls = ConferenceUrls(
      info = "http://www.devoxx.co.uk/#info",
      sponsors = "http://www.devoxx.co.uk/#sponsors",
      registration = "https://www.eventbrite.co.uk/e/devoxx-uk-2017-tickets-28649696012",
      confWebsite = "https://devoxx.co.uk/",
      cfpHostname = Play.current.configuration.getString("cfp.hostname").getOrElse("cfp.devoxx.co.uk")
    ),
    timing = ConferenceTiming(
      datesI18nKey = Messages("conference.dates"),
      speakersPassDuration = 2,
      preferredDayEnabled = true,
      firstDayFr = "11 mai",
      firstDayEn = "may 11th",
      datesFr = "me 11 au 12 Mai 2017",
      datesEn = "from 11th to 12th of May, 2017",
      cfpOpenedOn = DateTime.parse("2016-11-01T00:00:00+01:00"),
      cfpClosedOn = DateTime.parse("2017-01-16T23:59:59+01:00"),
      scheduleAnnouncedOn = DateTime.parse("2017-01-30T00:00:00+01:00"),
      days=dateRange(fromDay,toDay,new Period().withDays(1))
    ),
    hosterName = "Clever-cloud", hosterWebsite = "http://www.clever-cloud.com/#DevoxxUK",
    hashTag = "#DevoxxUK",
    conferenceSponsor = ConferenceSponsor(showSponsorProposalCheckbox = true, sponsorProposalType = ConferenceProposalTypes.CONF)
    , List(Locale.ENGLISH)
    , "Business Design Centre, 52 Upper St, London N1 0QH, United Kingdom"
    , notifyProposalSubmitted = true
    , MAXIMUM_SUMMARY_CHARACTERS // French developers tends to be a bit verbose... we need extra space :-)
  )

  def isCFPOpen: Boolean = {
    Play.current.configuration.getBoolean("cfp.isOpen").getOrElse(false)
  }

  // All timezone sensitive methods are using this constant variable.
  // Defaults to "Europe/London" if not set in the Clever Cloud env. variables page.
  def timeZone: String = Play.current.configuration.getString("conference.timezone").getOrElse("Europe/London")

  def isGoldenTicketActive: Boolean = Play.current.configuration.getBoolean("goldenTicket.active").getOrElse(false)

  def isFavoritesSystemActive: Boolean = Play.current.configuration.getBoolean("cfp.activateFavorites").getOrElse(false)

  def isHTTPSEnabled: Boolean = Play.current.configuration.getBoolean("cfp.activateHTTPS").getOrElse(false)

  // Reset all votes when a Proposal with state=SUBMITTED (or DRAFT) is updated
  // This is to reflect the fact that some speakers are eavluated, then they update the talk, and we should revote for it
  def isResetVotesForSubmitted = Play.current.configuration.getBoolean("cfp.resetVotesForSubmitted").getOrElse(false)

  // Set this to true temporarily
  // I will implement a new feature where each CFP member can decide to receive one digest email per day or a big email
  def notifyProposalSubmitted = true

  def gluonUsername(): String = Play.current.configuration.getString("gluon.username").getOrElse("")
  def gluonPassword(): String = Play.current.configuration.getString("gluon.password").getOrElse("")

  def capgeminiUsername(): String = Play.current.configuration.getString("capgemini.username").getOrElse("")
  def capgeminiPassword(): String = Play.current.configuration.getString("capgemini.password").getOrElse("")

}

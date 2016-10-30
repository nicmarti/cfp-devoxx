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

case class ConferenceUrls(info: String, registration: String, confWebsite: String, cfpHostname: String){
    def cfpURL:String={
    if(Play.current.configuration.getBoolean("cfp.activateHTTPS").getOrElse(false)){
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
                                notifyProposalSubmitted:Boolean,
                                maxProposalSummaryCharacters:Int=1200
                               )

object ConferenceDescriptor {

  /**
    * TODO configure here the kind of talks you will propose
    */
  object ConferenceProposalTypes {
    val CONF = ProposalType(id = "conf", label = "conf.label")

    val LAB = ProposalType(id = "lab", label = "lab.label")

    val QUICK = ProposalType(id = "quick", label = "quick.label")

    val BOF = ProposalType(id = "bof", label = "bof.label")

    val HACK = ProposalType(id = "hack", label = "hack.label")

    val KEY = ProposalType(id = "key", label = "key.label")

    val IGNITE = ProposalType(id = "ignite", label = "ignite.label")

    val ALL = List(CONF, LAB, QUICK, BOF, KEY, IGNITE)

    def valueOf(id: String): ProposalType = id match {
      case "conf" => CONF
      case "lab" => LAB
      case "quick" => QUICK
      case "bof" => BOF
      case "hack" => HACK
      case "key" => KEY
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
    val HACK = ProposalConfiguration(id = "hack", slotsCount = ConferenceSlots.all.count(_.name.equals(ConferenceProposalTypes.BOF.id)), givesSpeakerFreeEntrance = true, freeEntranceDisplayed = true, htmlClass = "icon-group",
      chosablePreferredDay = false)
    val KEY = ProposalConfiguration(id = "key", slotsCount = 1, givesSpeakerFreeEntrance = true, freeEntranceDisplayed = false, htmlClass = "icon-microphone",
      chosablePreferredDay = true)
    val IGNITE = ProposalConfiguration(id = "ignite", slotsCount = ConferenceSlots.all.count(_.name.equals(ConferenceProposalTypes.IGNITE.id)), givesSpeakerFreeEntrance = false, freeEntranceDisplayed = false, htmlClass = "icon-microphone",
      chosablePreferredDay = false)

    val ALL = List(CONF, LAB, QUICK, BOF, HACK, KEY, IGNITE)

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
    val METHOD_ARCHI = TrackDesc(ConferenceTracks.METHOD_ARCHI.id, "/assets/devoxxbe2016/images/icon_methodology.png", ConferenceTracks.METHOD_ARCHI.label, "track.method_archi.desc")
    val JAVA = TrackDesc(ConferenceTracks.JAVA.id, "/assets/devoxxbe2016/images/icon_javase.png", ConferenceTracks.JAVA.label, "track.java.desc")
    val CLOUD = TrackDesc(ConferenceTracks.CLOUD.id, "/assets/devoxxbe2016/images/icon_cloud.png", ConferenceTracks.CLOUD.label, "track.cloud.desc")
    val SSJ = TrackDesc(ConferenceTracks.SSJ.id, "/assets/devoxxbe2016/images/icon_javaee.png", ConferenceTracks.SSJ.label, "track.ssj.desc")
    val LANG = TrackDesc(ConferenceTracks.LANG.id, "/assets/devoxxbe2016/images/icon_alternative.png", ConferenceTracks.LANG.label, "track.lang.desc")
    val BIGDATA = TrackDesc(ConferenceTracks.BIGDATA.id, "/assets/devoxxbe2016/images/icon_architecture.png", ConferenceTracks.BIGDATA.label, "track.bigdata.desc")
    val WEB = TrackDesc(ConferenceTracks.WEB.id, "/assets/devoxxbe2016/images/icon_web.png", ConferenceTracks.WEB.label, "track.web.desc")
    val GEEK = TrackDesc(ConferenceTracks.GEEK.id, "/assets/devoxxbe2016/images/icon_geek.png", ConferenceTracks.GEEK.label, "track.geek.desc")
    val IOT = TrackDesc(ConferenceTracks.IOT.id, "/assets/devoxxbe2016/images/icon_iot.png", ConferenceTracks.IOT.label, "track.iot.desc")

    val SECURITY = TrackDesc(ConferenceTracks.SECURITY.id, "/assets/devoxxbe2016/images/icon_security.png", ConferenceTracks.SECURITY.label, "track.security.desc")
    val ARCHITECTURE = TrackDesc(ConferenceTracks.ARCHITECTURE.id, "/assets/devoxxbe2016/images/icon_architecture.png", ConferenceTracks.ARCHITECTURE.label, "track.architecture.desc")

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
      ALL.find(_.id == t.id).head
    }
  }

  // TODO If you want to use the Devoxx Scheduler, you can describe here the list of rooms, with capacity for seats
  object ConferenceRooms {

    // Tip : I use the ID to sort-by on the view per day... So if the exhibition floor id is "aaa" it will be
    // the first column on the HTML Table
    val HALL_EXPO = Room("a_hall", "Exhibition floor", 1500, "special")

    val AUDIT = Room("aud_room", "Auditorium", 550, "theatre")
    val ROOM_A = Room("room1", "Room A", 220, "theatre")
    val ROOM_B = Room("room2", "Room B", 32, "classroom")
    val ROOM_C = Room("room3", "Room C", 32, "classroom")
    val ROOM_D = Room("room4", "Room D", 32, "classroom")
    val ROOM_E = Room("room5", "Room E", 32, "classroom")

    val keynoteRoom = List(AUDIT)

    val conferenceRooms = List(AUDIT, ROOM_A, ROOM_B, ROOM_C, ROOM_D)

    val bofThu = List(AUDIT)
    val hackThu = List(ROOM_D)
    val igniteThu = List(ROOM_B)
    val quickieThu = List(AUDIT, ROOM_A, ROOM_B, ROOM_C, ROOM_D)

    val uniFri = List(ROOM_A)
    val quickieFri = List(ROOM_A, ROOM_B, ROOM_C, ROOM_D)
    val labsFri = List(ROOM_B, ROOM_C, ROOM_D, ROOM_E)

    val allRooms = List(HALL_EXPO, ROOM_A, ROOM_B, ROOM_C, ROOM_D, AUDIT)
  }

  object ConferenceSlotBreaks {
    val registrationAndCoffee = SlotBreak("reg", "Registration & Coffee", "Accueil", ConferenceRooms.HALL_EXPO)
    val breakfast = SlotBreak("dej", "Breakfast", "Accueil et petit-déjeuner", ConferenceRooms.HALL_EXPO)
    val coffee = SlotBreak("coffee", "Coffee Break", "Pause café", ConferenceRooms.HALL_EXPO)
    val lunch = SlotBreak("lunch", "Lunch", "Pause déjeuner", ConferenceRooms.HALL_EXPO)
    val eveningReception = SlotBreak("reception", "Evening Reception", "Evening Reception", ConferenceRooms.HALL_EXPO)
    val closingKeynote = SlotBreak("closeKey", "Closing Keynote (Room 5)", "Keynote", ConferenceRooms.AUDIT)
  }

  object ConferenceSlots {

    // VARIABLE CONSTANTS

    private val europeLondon: String = "Europe/London"
    private val THURSDAY: String = "thursday"
    private val FRIDAY: String = "friday"

    private val THU_DATE = "2017-05-11T"
    private val FRI_DATE = "2017-05-12T"

    private val MIN_SEC = ":00.000+01:00"

    // HANDS ON LABS

    // BOFS

    val bofSlotThursday: List[Slot] = {

      val bofThursdayEveningSlot = ConferenceRooms.bofThu.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.BOF.id,
                      THURSDAY,
                      new DateTime(THU_DATE + "18:35" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
                      new DateTime(THU_DATE + "19:35" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r1)
      }
      bofThursdayEveningSlot
    }

    // HACK

    val hackSlotsThursday: List[Slot] = {

      val hackThursdayEveningSlot = ConferenceRooms.hackThu.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.HACK.id,
            THURSDAY,
            new DateTime(THU_DATE + "18:35" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
            new DateTime(THU_DATE + "19:35" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r1)
      }
      hackThursdayEveningSlot
    }

    // QUICKIES

    val quickiesSlotsThursday: List[Slot] = {

      val quickiesThursdayLunch1 = ConferenceRooms.quickieThu.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.QUICK.id,
                      THURSDAY,
                      new DateTime(THU_DATE + "12:55" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
                      new DateTime(THU_DATE + "13:10" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r1)
      }
      val quickiesThursdayLunch2 = ConferenceRooms.quickieThu.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.QUICK.id,
                      THURSDAY,
                      new DateTime(THU_DATE + "13:15" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
                      new DateTime(THU_DATE + "13:30" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r2)
      }
      quickiesThursdayLunch1 ++ quickiesThursdayLunch2
    }

    val quickiesSlotsFriday: List[Slot] = {

      val quickiesFridayLunch1 = ConferenceRooms.quickieFri.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.QUICK.id,
            FRIDAY,
            new DateTime(FRI_DATE + "13:25" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
            new DateTime(FRI_DATE + "13:40" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r1)
      }
      val quickiesFridayLunch2 = ConferenceRooms.quickieFri.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.QUICK.id,
            FRIDAY,
            new DateTime(FRI_DATE + "13:45" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
            new DateTime(FRI_DATE + "14:00" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r2)
      }
      quickiesFridayLunch1 ++ quickiesFridayLunch2
    }

    // CONFERENCE KEYNOTES

    val keynoteSlotThursday: List[Slot] = {

      val keynoteSlot = ConferenceRooms.keynoteRoom.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.KEY.id, THURSDAY,
            new DateTime(THU_DATE + "09:00" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
            new DateTime(THU_DATE + "10:20" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r1)
      }
      keynoteSlot
    }

    val keynoteSlotFriday: List[Slot] = {

      ConferenceRooms.keynoteRoom.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.KEY.id, FRIDAY,
            new DateTime(FRI_DATE + "17:40" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
            new DateTime(FRI_DATE + "18:40" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r1)
      }
    }

    // CONFERENCE SLOTS

    val conferenceSlotsThursday: List[Slot] = {

      val conferenceThursdaySlot0 = ConferenceRooms.conferenceRooms.map {
        r0 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id,
                      THURSDAY,
                      new DateTime(THU_DATE + "10:50" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
                      new DateTime(THU_DATE + "11:40" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r0)
      }
      val conferenceThursdaySlot1 = ConferenceRooms.conferenceRooms.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id,
                      THURSDAY,
                      new DateTime(THU_DATE + "11:55" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
                      new DateTime(THU_DATE + "12:45" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r1)
      }
      val conferenceThursdaySlot2 = ConferenceRooms.conferenceRooms.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id,
                      THURSDAY,
                      new DateTime(THU_DATE + "13:45" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
                      new DateTime(THU_DATE + "14:35" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r2)
      }
      val conferenceThursdaySlot3 = ConferenceRooms.conferenceRooms.map {
        r3 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id,
                      THURSDAY,
                      new DateTime(THU_DATE + "14:50" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
                      new DateTime(THU_DATE + "15:40" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r3)
      }
      val conferenceThursdaySlot4 = ConferenceRooms.conferenceRooms.map {
        r4 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id,
                      THURSDAY,
                      new DateTime(THU_DATE + "16:10" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
                      new DateTime(THU_DATE + "17:00" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r4)
      }

      val conferenceThursdaySlot5 = ConferenceRooms.conferenceRooms.map {
        r5 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id,
            THURSDAY,
            new DateTime(THU_DATE + "17:15" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
            new DateTime(THU_DATE + "18:05" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r5)
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
                      new DateTime(FRI_DATE + "09:00" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
                      new DateTime(FRI_DATE + "09:50" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r1)
      }
      val conferenceFridaySlot2 = ConferenceRooms.conferenceRooms.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id,
                      FRIDAY,
                      new DateTime(FRI_DATE + "10:05" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
                      new DateTime(FRI_DATE + "10:55" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r2)
      }
      val conferenceFridaySlot3 = ConferenceRooms.conferenceRooms.map {
        r3 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id,
                      FRIDAY,
                      new DateTime(FRI_DATE + "11:20" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
                      new DateTime(FRI_DATE + "12:10" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r3)
      }
      val conferenceFridaySlot4 = ConferenceRooms.conferenceRooms.map {
        r4 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id,
            FRIDAY,
            new DateTime(FRI_DATE + "12:25" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
            new DateTime(FRI_DATE + "13:15" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r4)
      }
      val conferenceFridaySlot5 = ConferenceRooms.conferenceRooms.map {
        r5 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id,
            FRIDAY,
            new DateTime(FRI_DATE + "14:15" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
            new DateTime(FRI_DATE + "15:05" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r5)
      }
      val conferenceFridaySlot6 = ConferenceRooms.conferenceRooms.map {
        r6 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id,
            FRIDAY,
            new DateTime(FRI_DATE + "15:20" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
            new DateTime(FRI_DATE + "16:10" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r6)
      }
      val conferenceFridaySlot7 = ConferenceRooms.conferenceRooms.map {
        r7 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id,
            FRIDAY,
            new DateTime(FRI_DATE + "16:40" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
            new DateTime(FRI_DATE + "17:30" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r7)
      }

      conferenceFridaySlot1 ++ conferenceFridaySlot2 ++ conferenceFridaySlot3 ++ conferenceFridaySlot4 ++
      conferenceFridaySlot5 ++ conferenceFridaySlot6 ++ conferenceFridaySlot7
      }

    // Ignite slots
    val igniteSlotsThursday: List[Slot] = {
      ConferenceRooms.igniteThu.flatMap {
        room => List(
          SlotBuilder(ConferenceProposalTypes.IGNITE.id, THURSDAY,
            new DateTime(THU_DATE + "18:15" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
            new DateTime(THU_DATE + "18:20" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), room),
          SlotBuilder(ConferenceProposalTypes.IGNITE.id, THURSDAY,
            new DateTime(THU_DATE + "18:20" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
            new DateTime(THU_DATE + "18:25" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), room),
          SlotBuilder(ConferenceProposalTypes.IGNITE.id, THURSDAY,
            new DateTime(THU_DATE + "18:25" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
            new DateTime(THU_DATE + "18:30" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), room),
          SlotBuilder(ConferenceProposalTypes.IGNITE.id, THURSDAY,
            new DateTime(THU_DATE + "18:30" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
            new DateTime(THU_DATE + "18:35" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), room),
          SlotBuilder(ConferenceProposalTypes.IGNITE.id, THURSDAY,
            new DateTime(THU_DATE + "18:35" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
            new DateTime(THU_DATE + "18:40" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), room),
          SlotBuilder(ConferenceProposalTypes.IGNITE.id, THURSDAY,
            new DateTime(THU_DATE + "18:40" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
            new DateTime(THU_DATE + "18:45" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), room),
          SlotBuilder(ConferenceProposalTypes.IGNITE.id, THURSDAY,
            new DateTime(THU_DATE + "18:45" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
            new DateTime(THU_DATE + "18:50" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), room),
          SlotBuilder(ConferenceProposalTypes.IGNITE.id, THURSDAY,
            new DateTime(THU_DATE + "18:50" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
            new DateTime(THU_DATE + "18:55" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), room)
        )
      }
    }

    // Registration, coffee break, lunch etc
    val thursdayBreaks = List(
      SlotBuilder(ConferenceSlotBreaks.registrationAndCoffee, THURSDAY,
        new DateTime(THU_DATE + "08:00" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
        new DateTime(THU_DATE + "09:00" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)))
      , SlotBuilder(ConferenceSlotBreaks.coffee, THURSDAY,
        new DateTime(THU_DATE + "10:20" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
        new DateTime(THU_DATE + "10:50" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)))
      , SlotBuilder(ConferenceSlotBreaks.lunch, THURSDAY,
        new DateTime(THU_DATE + "12:45" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
        new DateTime(THU_DATE + "13:45" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)))
      , SlotBuilder(ConferenceSlotBreaks.coffee, THURSDAY,
        new DateTime(THU_DATE + "15:40" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
        new DateTime(THU_DATE + "16:10" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)))
      , SlotBuilder(ConferenceSlotBreaks.eveningReception, THURSDAY,
        new DateTime(THU_DATE + "18:00" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
        new DateTime(THU_DATE + "18:50" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)))
    )

    val fridayBreaks = List(
      SlotBuilder(ConferenceSlotBreaks.coffee, FRIDAY,
        new DateTime(FRI_DATE + "10:55" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
        new DateTime(FRI_DATE + "11:20" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon))),
      SlotBuilder(ConferenceSlotBreaks.lunch, THURSDAY,
        new DateTime(THU_DATE + "13:15" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
        new DateTime(THU_DATE + "14:15" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon))),
      SlotBuilder(ConferenceSlotBreaks.coffee, FRIDAY,
        new DateTime(FRI_DATE + "16:10" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
        new DateTime(FRI_DATE + "16:40" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)))
    )

    // DEVOXX DAYS

    val thursdaySchedule: List[Slot] = {
      thursdayBreaks ++
      keynoteSlotFriday ++
      conferenceSlotsThursday ++
      quickiesSlotsThursday ++
      bofSlotThursday ++
      igniteSlotsThursday
    }

    val fridaySchedule: List[Slot] = {
      fridayBreaks ++
        conferenceSlotsFriday ++
        quickiesSlotsFriday
    }

    // COMPLETE DEVOXX
    def all: List[Slot] = {
      thursdaySchedule ++
      fridaySchedule
    }
  }

  def dateRange(from: DateTime, to: DateTime, step: Period): Iterator[DateTime] = Iterator.iterate(from)(_.plus(step)).takeWhile(!_.isAfter(to))

  val fromDay = new DateTime().withYear(2017).withMonthOfYear(05).withDayOfMonth(11)
  val toDay = new DateTime().withYear(2017).withMonthOfYear(05).withDayOfMonth(12)

  def current() = ConferenceDescriptor(
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
      registration = "https://www.eventbrite.co.uk/e/devoxx-uk-2017-tickets-28649696012",
      confWebsite = "https://devoxx.co.uk/",
      cfpHostname = Play.current.configuration.getString("cfp.hostname").getOrElse("cfp.devoxx.co.uk")
    ),
    timing = ConferenceTiming(
      datesI18nKey = "11th-12th May",
      speakersPassDuration = 2,
      preferredDayEnabled = true,
      firstDayFr = "11 mai",
      firstDayEn = "may 11th",
      datesFr = "do 11 au 12 Mai 2017",
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
    , notifyProposalSubmitted = false // Do not send an email for each talk submitted for France
    , 1200 // French developers tends to be a bit verbose... we need extra space :-)
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
      registration = "https://www.eventbrite.co.uk/e/devoxx-uk-2017-tickets-28649696012",
      confWebsite = "https://devoxx.co.uk/",
      cfpHostname = Play.current.configuration.getString("cfp.hostname").getOrElse("cfp.devoxx.co.uk")
    ),
    timing = ConferenceTiming(
      datesI18nKey = "11th-12th May",
      speakersPassDuration = 2,
      preferredDayEnabled = true,
      firstDayFr = "11 mai",
      firstDayEn = "may 11th",
      datesFr = "me 11 au 12 Mai 2017",
      datesEn = "from 11th to 12th of May, 2017",
      cfpOpenedOn = DateTime.parse("2016-10-25T00:00:00+01:00"),
      cfpClosedOn = DateTime.parse("2017-01-09T23:59:59+01:00"),
      scheduleAnnouncedOn = DateTime.parse("2017-01-23T00:00:00+01:00"),
      days=dateRange(fromDay,toDay,new Period().withDays(1))
    ),
    hosterName = "Clever-cloud", hosterWebsite = "http://www.clever-cloud.com/#DevoxxUK",
    hashTag = "#DevoxxUK",
    conferenceSponsor = ConferenceSponsor(showSponsorProposalCheckbox = true, sponsorProposalType = ConferenceProposalTypes.CONF)
    , List(Locale.ENGLISH)
    , "Business Design Centre, 52 Upper St, London N1 0QH, United Kingdom"
    , notifyProposalSubmitted = false // Do not send an email for each talk submitted for France
    , 1200 // French developers tends to be a bit verbose... we need extra space :-)
  )

  def isCFPOpen: Boolean = {
    Play.current.configuration.getBoolean("cfp.isOpen").getOrElse(false)
  }

  def isGoldenTicketActive:Boolean = Play.current.configuration.getBoolean("goldenTicket.active").getOrElse(false)

  def isFavoritesSystemActive:Boolean = Play.current.configuration.getBoolean("cfp.activateFavorites").getOrElse(false)

  def isHTTPSEnabled = Play.current.configuration.getBoolean("cfp.activateHTTPS").getOrElse(false)

}


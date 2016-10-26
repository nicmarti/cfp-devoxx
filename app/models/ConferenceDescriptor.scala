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

case class ConferenceUrls(faq: String, registration: String,confWebsite: String, cfpHostname: String){
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

    val UNI = ProposalType(id = "uni", label = "uni.label")

    val TIA = ProposalType(id = "tia", label = "tia.label")

    val LAB = ProposalType(id = "lab", label = "lab.label")

    val QUICK = ProposalType(id = "quick", label = "quick.label")

    val BOF = ProposalType(id = "bof", label = "bof.label")

    val KEY = ProposalType(id = "key", label = "key.label")

    val START = ProposalType(id = "start", label = "start.label")

    val IGNITE = ProposalType(id = "ignite", label = "ignite.label")

    val OTHER = ProposalType(id = "other", label = "other.label")

    val ALL = List(CONF, UNI, TIA, LAB, QUICK, BOF, KEY, START, IGNITE, OTHER)

    def valueOf(id: String): ProposalType = id match {
      case "conf" => CONF
      case "uni" => UNI
      case "tia" => TIA
      case "lab" => LAB
      case "quick" => QUICK
      case "bof" => BOF
      case "key" => KEY
      case "start" => START
      case "ignite" => IGNITE
      case "other" => OTHER
      case other => OTHER
    }

  }

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
    val BOF = ProposalConfiguration(id = "bof", slotsCount = ConferenceSlots.all.count(_.name.equals(ConferenceProposalTypes.BOF.id)), givesSpeakerFreeEntrance = true, freeEntranceDisplayed = true, htmlClass = "icon-group",
      chosablePreferredDay = false)
    val KEY = ProposalConfiguration(id = "key", slotsCount = 1, givesSpeakerFreeEntrance = true, freeEntranceDisplayed = false, htmlClass = "icon-microphone",
      chosablePreferredDay = true)
    val IGNITE = ProposalConfiguration(id = "ignite", slotsCount = ConferenceSlots.all.count(_.name.equals(ConferenceProposalTypes.IGNITE.id)), givesSpeakerFreeEntrance = false, freeEntranceDisplayed = false, htmlClass = "icon-microphone",
      chosablePreferredDay = false)
    val OTHER = ProposalConfiguration(id = "other", slotsCount = 1, givesSpeakerFreeEntrance = false, freeEntranceDisplayed = false, htmlClass = "icon-microphone",
      hiddenInCombo = true, chosablePreferredDay = false)

    val ALL = List(CONF, UNI, TIA, LAB, QUICK, BOF, KEY, IGNITE, OTHER)

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
    val FUTURE = Track("future", "future.label")
    val MOBILE = Track("mobile", "mobile.label")

    val UNKNOWN = Track("unknown", "unknown track")
    val ALL = List(METHOD_ARCHI, JAVA, CLOUD, SSJ, LANG, BIGDATA, WEB, FUTURE, MOBILE, UNKNOWN)
  }

  object ConferenceTracksDescription {
    val METHOD_ARCHI = TrackDesc(ConferenceTracks.METHOD_ARCHI.id, "/assets/devoxxbe2016/images/icon_methodology.png", ConferenceTracks.METHOD_ARCHI.label, "track.method_archi.desc")
    val JAVA = TrackDesc(ConferenceTracks.JAVA.id, "/assets/devoxxbe2016/images/icon_javase.png", ConferenceTracks.JAVA.label, "track.java.desc")
    val CLOUD = TrackDesc(ConferenceTracks.CLOUD.id, "/assets/devoxxbe2016/images/icon_cloud.png", ConferenceTracks.CLOUD.label, "track.cloud.desc")
    val SSJ = TrackDesc(ConferenceTracks.SSJ.id, "/assets/devoxxbe2016/images/icon_javaee.png", ConferenceTracks.SSJ.label, "track.ssj.desc")
    val LANG = TrackDesc(ConferenceTracks.LANG.id, "/assets/devoxxbe2016/images/icon_alternative.png", ConferenceTracks.LANG.label, "track.lang.desc")
    val BIGDATA = TrackDesc(ConferenceTracks.BIGDATA.id, "/assets/devoxxbe2016/images/icon_architecture.png", ConferenceTracks.BIGDATA.label, "track.bigdata.desc")
    val WEB = TrackDesc(ConferenceTracks.WEB.id, "/assets/devoxxbe2016/images/icon_web.png", ConferenceTracks.WEB.label, "track.web.desc")
    val FUTURE = TrackDesc(ConferenceTracks.FUTURE.id, "/assets/devoxxbe2016/images/icon_future.png", ConferenceTracks.FUTURE.label, "track.future.desc")
    val MOBILE = TrackDesc(ConferenceTracks.MOBILE.id, "/assets/devoxxbe2016/images/icon_mobile.png", ConferenceTracks.MOBILE.label, "track.mobile.desc")

    val ALL = List(METHOD_ARCHI
      , JAVA
      , CLOUD
      , SSJ
      , LANG
      , BIGDATA
      , WEB
      , FUTURE
      , MOBILE
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

    val ROOM3 = Room("room3", "Room 3", 300, "theatre")
    val ROOM4 = Room("room4", "Room 4", 347, "theatre")
    val ROOM5 = Room("room5", "Room 5", 641, "theatre")
    val ROOM6 = Room("room6", "Room 6", 372, "theatre")
    val ROOM7 = Room("room7", "Room 7", 370, "theatre")
    val ROOM8 = Room("room8", "Room 8", 696, "theatre")
    val ROOM9 = Room("room9", "Room 9", 393, "theatre")
    val ROOM10 = Room("room10", "Room 10", 286, "theatre")

    val BOF1 = Room("bof1", "BOF 1", 70, "classroom")
    val BOF2 = Room("bof2", "BOF 2", 70, "classroom")

    val allRoomsUni = List(ROOM4, ROOM5, ROOM8, ROOM9)

    val allRoomsTIA = List(ROOM4, ROOM5, ROOM8, ROOM9)

    val keynoteRoom = List(ROOM8, ROOM4, ROOM5, ROOM9)

    val eveningKeynoteRoom = List(ROOM5)

    val allRoomsConf = List(ROOM8, ROOM5, ROOM9, ROOM6, ROOM7, ROOM4, ROOM3, ROOM10)
    val fridayRoomsConf = List(ROOM4, ROOM5, ROOM8, ROOM9)

    val allRoomsQuick = List(ROOM8, ROOM5, ROOM9, ROOM6, ROOM7, ROOM4, ROOM3)

    val allRoomsLabs = List(BOF1, BOF2)
    val oneRoomLabs = List(BOF1)

    val allRoomsBOF = List(BOF1, BOF2)
    val oneRoomBOF = List(BOF1)

    val igniteRoom = List(BOF1)

    val allRooms = List(ROOM8, ROOM5, ROOM9, ROOM6, ROOM7, ROOM4, ROOM3, ROOM10, BOF1, BOF2, HALL_EXPO)
  }

  object ConferenceSlotBreaks {
    val registration = SlotBreak("reg", "Registration", "Accueil", ConferenceRooms.HALL_EXPO)
    val petitDej = SlotBreak("dej", "Breakfast", "Accueil et petit-déjeuner", ConferenceRooms.HALL_EXPO)
    val coffee = SlotBreak("coffee", "Coffee Break", "Pause café", ConferenceRooms.HALL_EXPO)
    val lunch = SlotBreak("lunch", "Lunch", "Pause déjeuner", ConferenceRooms.HALL_EXPO)
    val shortBreak = SlotBreak("chgt", "Break", "Pause courte", ConferenceRooms.HALL_EXPO)
    val exhibition = SlotBreak("exhib", "Exhibition", "Exhibition", ConferenceRooms.HALL_EXPO)
    val meetAndGreet = SlotBreak("meet", "Meet & Greet (Exhibition)", "Exhibition", ConferenceRooms.HALL_EXPO)
    val eveningKeynote = SlotBreak("evKey", "Evening Keynote", "Keynote", ConferenceRooms.ROOM5)
    val closingKeynote = SlotBreak("closeKey", "Closing Keynote (Room 5)", "Keynote", ConferenceRooms.ROOM3)
    val movieSpecial = SlotBreak("movie", "Movie 20:00-22:00 (Room 8)", "Movie", ConferenceRooms.ROOM3)
  }

  object ConferenceSlots {

    // VARIABLE CONSTANTS

    private val europeLondon: String = "Europe/London"
    private val MONDAY: String = "monday"
    private val TUESDAY: String = "tuesday"
    private val WEDNESDAY: String = "wednesday"
    private val THURSDAY: String = "thursday"
    private val FRIDAY: String = "friday"

    private val MON_DATE = "2017-05-08T"
    private val TUE_DATE = "2017-05-09T"
    private val WED_DATE = "2017-05-10T"
    private val THU_DATE = "2017-05-11T"
    private val FRI_DATE = "2017-05-12T"

    private val MIN_SEC = ":00.000+01:00"

    // UNIVERSITY

    val universitySlotsMonday: List[Slot] = {

      val universityMondayMorning = ConferenceRooms.allRoomsUni.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.UNI.id,
                      MONDAY,
                      new DateTime(MON_DATE + "09:30" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
                      new DateTime(MON_DATE + "12:30" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r1)
      }
      val universityMondayAfternoon = ConferenceRooms.allRoomsUni.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.UNI.id,
                      MONDAY,
                      new DateTime(MON_DATE + "13:30" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
                      new DateTime(MON_DATE + "16:30" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r2)
      }
      universityMondayMorning ++
      universityMondayAfternoon
    }

    val universitySlotsTuesday: List[Slot] = {

      val universityTuesdayMorning = ConferenceRooms.allRoomsUni.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.UNI.id,
                      TUESDAY,
                      new DateTime(TUE_DATE + "09:30" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
                      new DateTime(TUE_DATE + "12:30" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r1)
      }
      val universityTuesdayAfternoon = ConferenceRooms.allRoomsUni.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.UNI.id,
                      TUESDAY,
                      new DateTime(TUE_DATE + "13:30" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
                      new DateTime(TUE_DATE + "16:30" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r2)
      }
      universityTuesdayMorning ++
      universityTuesdayAfternoon
    }

    // TOOLS IN ACTION

    val tiaSlotsMonday: List[Slot] = {

      val toolsMondayAfternoonSlot1 = ConferenceRooms.allRoomsTIA.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.TIA.id,
                      MONDAY,
                      new DateTime(MON_DATE + "16:45" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
                      new DateTime(MON_DATE + "17:15" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r1)
      }
      val toolsMondayAfternoonSlot2 = ConferenceRooms.allRoomsTIA.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.TIA.id,
                      MONDAY,
                      new DateTime(MON_DATE + "17:25" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
                      new DateTime(MON_DATE + "17:55" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r2)
      }
      val toolsMondayAfternoonSlot3 = ConferenceRooms.allRoomsTIA.map {
        r3 =>
          SlotBuilder(ConferenceProposalTypes.TIA.id,
                      MONDAY,
                      new DateTime(MON_DATE + "18:15" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
                      new DateTime(MON_DATE + "18:45" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r3)
      }
      val toolsMondayAfternoonSlot4 = ConferenceRooms.allRoomsTIA.map {
        r4 =>
          SlotBuilder(ConferenceProposalTypes.TIA.id,
                      MONDAY,
                      new DateTime(MON_DATE + "18:55" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
                      new DateTime(MON_DATE + "19:25" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r4)
      }
      toolsMondayAfternoonSlot1 ++
      toolsMondayAfternoonSlot2 ++
      toolsMondayAfternoonSlot3 ++
      toolsMondayAfternoonSlot4
    }

    val tiaSlotsTuesday: List[Slot] = {

      val toolsTuesdayAfternoonSlot1 = ConferenceRooms.allRoomsTIA.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.TIA.id,
                      TUESDAY,
                      new DateTime(TUE_DATE + "16:45" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
                      new DateTime(TUE_DATE + "17:15" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r1)
      }
      val toolsTuesdayAfternoonSlot2 = ConferenceRooms.allRoomsTIA.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.TIA.id,
                      TUESDAY,
                      new DateTime(TUE_DATE + "17:25" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
                      new DateTime(TUE_DATE + "17:55" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r2)
      }
      val toolsTuesdayAfternoonSlot3 = ConferenceRooms.allRoomsTIA.map {
        r3 =>
          SlotBuilder(ConferenceProposalTypes.TIA.id,
                      TUESDAY,
                      new DateTime(TUE_DATE + "18:15" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
                      new DateTime(TUE_DATE + "18:45" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r3)
      }
      val toolsTuesdayAfternoonSlot4 = ConferenceRooms.allRoomsTIA.map {
        r4 =>
          SlotBuilder(ConferenceProposalTypes.TIA.id,
                      TUESDAY,
                      new DateTime(TUE_DATE + "18:55" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
                      new DateTime(TUE_DATE + "19:25" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r4)
      }
      toolsTuesdayAfternoonSlot1 ++
      toolsTuesdayAfternoonSlot2 ++
      toolsTuesdayAfternoonSlot3 ++
      toolsTuesdayAfternoonSlot4
    }

    // HANDS ON LABS

    val labsSlotsMonday: List[Slot] = {

      val labsMondayMorning = ConferenceRooms.allRoomsLabs.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.LAB.id,
                      MONDAY,
                      new DateTime(MON_DATE + "09:30" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
                      new DateTime(MON_DATE + "12:30" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r1)
      }
      val labsMondayAfternoon = ConferenceRooms.allRoomsLabs.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.LAB.id,
                      MONDAY,
                      new DateTime(MON_DATE + "13:30" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
                      new DateTime(MON_DATE + "16:30" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r2)
      }
      labsMondayMorning ++
      labsMondayAfternoon
    }

    val labsSlotsTuesday: List[Slot] = {

      val labsTuesdayMorning = ConferenceRooms.allRoomsLabs.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.LAB.id,
                      TUESDAY,
                      new DateTime(TUE_DATE + "09:30" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
                      new DateTime(TUE_DATE + "12:30" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r1)
      }
      val labsTuesdayAfternoon = ConferenceRooms.allRoomsLabs.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.LAB.id,
                      TUESDAY,
                      new DateTime(TUE_DATE + "13:30" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
                      new DateTime(TUE_DATE + "16:30" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r2)
      }
      labsTuesdayMorning ++
      labsTuesdayAfternoon
    }

    // BOFS

    val bofSlotsMonday: List[Slot] = {

      val bofMondayEveningSlot1 = ConferenceRooms.allRoomsBOF.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.BOF.id,
                      MONDAY,
                      new DateTime(MON_DATE + "19:30" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
                      new DateTime(MON_DATE + "20:30" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r1)
      }
      val bofMondayEveningSlot2 = ConferenceRooms.allRoomsBOF.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.BOF.id,
                      MONDAY,
                      new DateTime(MON_DATE + "20:30" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
                      new DateTime(MON_DATE + "21:30" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r2)
      }
      bofMondayEveningSlot1 ++
      bofMondayEveningSlot2
    }

    val bofSlotsTuesday: List[Slot] = {

      val bofTuesdayEveningSlot1 = ConferenceRooms.allRoomsBOF.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.BOF.id,
                      TUESDAY,
                      new DateTime(TUE_DATE + "19:30" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
                      new DateTime(TUE_DATE + "20:30" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r1)
      }
      val bofTuesdayEveningSlot2 = ConferenceRooms.allRoomsBOF.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.BOF.id,
                      TUESDAY,
                      new DateTime(TUE_DATE + "20:30" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
                      new DateTime(TUE_DATE + "21:30" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r2)
      }
      bofTuesdayEveningSlot1 ++
      bofTuesdayEveningSlot2
    }

    val bofSlotsWednesday: List[Slot] = {

      val bofWednesdayEveningSlot1 = ConferenceRooms.allRoomsBOF.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.BOF.id,
                      WEDNESDAY,
                      new DateTime(WED_DATE + "19:00" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
                      new DateTime(WED_DATE + "20:00" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r1)
      }
      val bofWednesdayEveningSlot2 = ConferenceRooms.allRoomsBOF.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.BOF.id,
                      WEDNESDAY,
                      new DateTime(WED_DATE + "20:00" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
                      new DateTime(WED_DATE + "21:00" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r2)
      }
      val bofWednesdayEveningSlot3 = ConferenceRooms.allRoomsBOF.map {
        r3 =>
          SlotBuilder(ConferenceProposalTypes.BOF.id,
                      WEDNESDAY,
                      new DateTime(WED_DATE + "21:00" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
                      new DateTime(WED_DATE + "22:00" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r3)
      }
      bofWednesdayEveningSlot1 ++ bofWednesdayEveningSlot2 ++ bofWednesdayEveningSlot3
    }

    val bofSlotsThursday: List[Slot] = {

      val bofThursdayEveningSlot1 = ConferenceRooms.allRoomsBOF.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.BOF.id,
                      THURSDAY,
                      new DateTime(THU_DATE + "19:00" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
                      new DateTime(THU_DATE + "20:00" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r1)
      }
      val bofThursdayEveningSlot2 = ConferenceRooms.allRoomsBOF.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.BOF.id,
                      THURSDAY,
                      new DateTime(THU_DATE + "20:00" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
                      new DateTime(THU_DATE + "21:00" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r2)
      }
      val bofThursdayEveningSlot3 = ConferenceRooms.allRoomsBOF.map {
        r3 =>
          SlotBuilder(ConferenceProposalTypes.BOF.id,
                      THURSDAY,
                      new DateTime(THU_DATE + "21:00" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
                      new DateTime(THU_DATE + "22:00" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r3)
      }
      bofThursdayEveningSlot1 ++ bofThursdayEveningSlot2 ++ bofThursdayEveningSlot3
    }

    // QUICKIES

    val quickiesSlotsWednesday: List[Slot] = {

      val quickiesWednesdayLunch1 = ConferenceRooms.allRoomsQuick.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.QUICK.id,
                      WEDNESDAY,
                      new DateTime(WED_DATE + "13:10" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
                      new DateTime(WED_DATE + "13:25" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r1)
      }
      val quickiesWednesdayLunch2 = ConferenceRooms.allRoomsQuick.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.QUICK.id,
                      WEDNESDAY,
                      new DateTime(WED_DATE + "13:35" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
                      new DateTime(WED_DATE + "13:50" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r2)
      }
      quickiesWednesdayLunch1 ++ quickiesWednesdayLunch2
    }

    val quickiesSlotsThursday: List[Slot] = {

      val quickiesThursdayLunch1 = ConferenceRooms.allRoomsQuick.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.QUICK.id,
                      THURSDAY,
                      new DateTime(THU_DATE + "13:10" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
                      new DateTime(THU_DATE + "13:25" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r1)
      }
      val quickiesThursdayLunch2 = ConferenceRooms.allRoomsQuick.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.QUICK.id,
                      THURSDAY,
                      new DateTime(THU_DATE + "13:35" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
                      new DateTime(THU_DATE + "13:50" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r2)
      }
      quickiesThursdayLunch1 ++ quickiesThursdayLunch2
    }

    // CONFERENCE KEYNOTES

    val keynoteSlotsWednesday: List[Slot] = {

      val keynoteSlot1 = ConferenceRooms.keynoteRoom.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.KEY.id, WEDNESDAY,
            new DateTime(WED_DATE + "09:30" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
            new DateTime(WED_DATE + "10:30" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r1)
      }
      val keynoteSlot2 = ConferenceRooms.keynoteRoom.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.KEY.id, WEDNESDAY,
            new DateTime(WED_DATE + "10:30" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
            new DateTime(WED_DATE + "11:00" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r2)
      }
      val keynoteSlot3 = ConferenceRooms.keynoteRoom.map {
        r3 =>
          SlotBuilder(ConferenceProposalTypes.KEY.id, WEDNESDAY,
            new DateTime(WED_DATE + "11:00" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
            new DateTime(WED_DATE + "11:30" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r3)
      }
      keynoteSlot1 ++
      keynoteSlot2 ++
      keynoteSlot3
    }

    val keynoteSlotsThursday: List[Slot] = {

      ConferenceRooms.keynoteRoom.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.KEY.id, THURSDAY,
            new DateTime(THU_DATE + "19:00" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
            new DateTime(THU_DATE + "19:45" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r1)
      }
    }

    // CONFERENCE SLOTS

    val conferenceSlotsWednesday: List[Slot] = {

      val conferenceWednesdaySlot1 = ConferenceRooms.allRoomsConf.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id,
                      WEDNESDAY,
                      new DateTime(WED_DATE + "12:00" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
                      new DateTime(WED_DATE + "13:00" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r1)
      }
      val conferenceWednesdaySlot2 = ConferenceRooms.allRoomsConf.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id,
                      WEDNESDAY,
                      new DateTime(WED_DATE + "14:00" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
                      new DateTime(WED_DATE + "15:00" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r2)
      }
      val conferenceWednesdaySlot3 = ConferenceRooms.allRoomsConf.map {
        r3 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id,
                      WEDNESDAY,
                      new DateTime(WED_DATE + "15:10" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
                      new DateTime(WED_DATE + "16:10" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r3)
      }
      val conferenceWednesdaySlot4 = ConferenceRooms.allRoomsConf.map {
        r4 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id,
                      WEDNESDAY,
                      new DateTime(WED_DATE + "16:40" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
                      new DateTime(WED_DATE + "17:40" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r4)
      }
      val conferenceWednesdaySlot5 = ConferenceRooms.allRoomsConf.map {
        r5 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id,
                      WEDNESDAY,
                      new DateTime(WED_DATE + "17:50" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
                      new DateTime(WED_DATE + "18:50" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r5)
      }
      conferenceWednesdaySlot1 ++
      conferenceWednesdaySlot2 ++
      conferenceWednesdaySlot3 ++
      conferenceWednesdaySlot4 ++
      conferenceWednesdaySlot5
    }

    val conferenceSlotsThursday: List[Slot] = {

      val conferenceThursdaySlot0 = ConferenceRooms.allRoomsConf.map {
        r0 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id,
                      THURSDAY,
                      new DateTime(THU_DATE + "09:30" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
                      new DateTime(THU_DATE + "10:30" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r0)
      }
      val conferenceThursdaySlot1 = ConferenceRooms.allRoomsConf.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id,
                      THURSDAY,
                      new DateTime(THU_DATE + "10:50" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
                      new DateTime(THU_DATE + "11:50" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r1)
      }
      val conferenceThursdaySlot2 = ConferenceRooms.allRoomsConf.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id,
                      THURSDAY,
                      new DateTime(THU_DATE + "12:00" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
                      new DateTime(THU_DATE + "13:00" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r2)
      }
      val conferenceThursdaySlot3 = ConferenceRooms.allRoomsConf.map {
        r3 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id,
                      THURSDAY,
                      new DateTime(THU_DATE + "14:00" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
                      new DateTime(THU_DATE + "15:00" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r3)
      }
      val conferenceThursdaySlot4 = ConferenceRooms.allRoomsConf.map {
        r4 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id,
                      THURSDAY,
                      new DateTime(THU_DATE + "15:10" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
                      new DateTime(THU_DATE + "16:10" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r4)
      }

      val conferenceThursdaySlot5 = ConferenceRooms.allRoomsConf.map {
        r5 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id,
            THURSDAY,
            new DateTime(THU_DATE + "16:40" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
            new DateTime(THU_DATE + "17:40" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r5)
      }

      val conferenceThursdaySlot6 = ConferenceRooms.allRoomsConf.map {
        r6 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id,
                      THURSDAY,
                      new DateTime(THU_DATE + "17:50" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
                      new DateTime(THU_DATE + "18:50" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r6)
      }

      // Closing sessions on Thursday
      val closingSessions = List(SlotBuilder(ConferenceSlotBreaks.closingKeynote,
        THURSDAY,
        new DateTime(THU_DATE + "19:00" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
        new DateTime(THU_DATE + "19:45" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon))),

        SlotBuilder(ConferenceSlotBreaks.movieSpecial,
          THURSDAY,
          new DateTime(THU_DATE + "20:00" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
          new DateTime(THU_DATE + "22:00" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon))))

      val toReturn = conferenceThursdaySlot0 ++
                     conferenceThursdaySlot1 ++
                     conferenceThursdaySlot2 ++
                     conferenceThursdaySlot3 ++
                     conferenceThursdaySlot4 ++
                     conferenceThursdaySlot5 ++
                     conferenceThursdaySlot6 ++
                     closingSessions

      toReturn

    }
    // ROOM4, ROOM5, ROOM8, ROOM9, ROOM10
    val conferenceSlotsFriday: List[Slot] = {

      val conferenceFridaySlot1 = ConferenceRooms.fridayRoomsConf.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id,
                      FRIDAY,
                      new DateTime(FRI_DATE + "09:30" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
                      new DateTime(FRI_DATE + "10:30" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r1)
      }
      val conferenceFridaySlot2 = ConferenceRooms.fridayRoomsConf.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id,
                      FRIDAY,
                      new DateTime(FRI_DATE + "10:45" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
                      new DateTime(FRI_DATE + "11:45" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r2)
      }
      val conferenceFridaySlot3 = ConferenceRooms.fridayRoomsConf.map {
        r3 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id,
                      FRIDAY,
                      new DateTime(FRI_DATE + "11:50" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
                      new DateTime(FRI_DATE + "12:50" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), r3)
      }
      conferenceFridaySlot1 ++ conferenceFridaySlot2 ++ conferenceFridaySlot3
      }

    // Ignite slots
    val igniteSlotsThursday: List[Slot] = {
      ConferenceRooms.igniteRoom.flatMap {
        room => List(
          SlotBuilder(ConferenceProposalTypes.IGNITE.id, THURSDAY,
            new DateTime(THU_DATE + "13:00" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
            new DateTime(THU_DATE + "13:05" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), room),
          SlotBuilder(ConferenceProposalTypes.IGNITE.id, THURSDAY,
            new DateTime(THU_DATE + "13:05" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
            new DateTime(THU_DATE + "13:10" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), room),
          SlotBuilder(ConferenceProposalTypes.IGNITE.id, THURSDAY,
            new DateTime(THU_DATE + "13:10" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
            new DateTime(THU_DATE + "13:15" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), room),
          SlotBuilder(ConferenceProposalTypes.IGNITE.id, THURSDAY,
            new DateTime(THU_DATE + "13:15" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
            new DateTime(THU_DATE + "13:20" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), room),
          SlotBuilder(ConferenceProposalTypes.IGNITE.id, THURSDAY,
            new DateTime(THU_DATE + "13:20" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
            new DateTime(THU_DATE + "13:25" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), room),
          SlotBuilder(ConferenceProposalTypes.IGNITE.id, THURSDAY,
            new DateTime(THU_DATE + "13:25" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
            new DateTime(THU_DATE + "13:30" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), room),
          SlotBuilder(ConferenceProposalTypes.IGNITE.id, THURSDAY,
            new DateTime(THU_DATE + "13:30" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
            new DateTime(THU_DATE + "13:35" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), room),
          SlotBuilder(ConferenceProposalTypes.IGNITE.id, THURSDAY,
            new DateTime(THU_DATE + "13:35" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
            new DateTime(THU_DATE + "13:40" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), room),
          SlotBuilder(ConferenceProposalTypes.IGNITE.id, THURSDAY,
            new DateTime(THU_DATE + "13:40" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
            new DateTime(THU_DATE + "13:45" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), room),
          SlotBuilder(ConferenceProposalTypes.IGNITE.id, THURSDAY,
            new DateTime(THU_DATE + "13:45" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
            new DateTime(THU_DATE + "13:50" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), room),
          SlotBuilder(ConferenceProposalTypes.IGNITE.id, THURSDAY,
            new DateTime(THU_DATE + "13:50" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
            new DateTime(THU_DATE + "13:55" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), room),
          SlotBuilder(ConferenceProposalTypes.IGNITE.id, THURSDAY,
            new DateTime(THU_DATE + "13:55" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
            new DateTime(THU_DATE + "14:00" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)), room)
        )
      }
    }

    // Registration, coffee break, lunch etc
    val mondayBreaks = List(
      SlotBuilder(ConferenceSlotBreaks.registration, MONDAY,
        new DateTime(MON_DATE + "08:00" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
        new DateTime(MON_DATE + "09:30" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)))
      , SlotBuilder(ConferenceSlotBreaks.lunch, MONDAY,
        new DateTime(MON_DATE + "12:30" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
        new DateTime(MON_DATE + "13:30" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)))
      , SlotBuilder(ConferenceSlotBreaks.coffee, MONDAY,
        new DateTime(MON_DATE + "16:30" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
        new DateTime(MON_DATE + "16:45" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)))
      , SlotBuilder(ConferenceSlotBreaks.shortBreak, MONDAY,
        new DateTime(MON_DATE + "17:55" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
        new DateTime(MON_DATE + "18:15" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)))
    )

    val tuesdayBreaks = List(
      SlotBuilder(ConferenceSlotBreaks.registration, TUESDAY,
        new DateTime(TUE_DATE + "08:00" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
        new DateTime(TUE_DATE + "09:30" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)))
      , SlotBuilder(ConferenceSlotBreaks.lunch, TUESDAY,
        new DateTime(TUE_DATE + "12:30" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
        new DateTime(TUE_DATE + "13:30" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)))
      , SlotBuilder(ConferenceSlotBreaks.coffee, TUESDAY,
        new DateTime(TUE_DATE + "16:30" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
        new DateTime(TUE_DATE + "16:45" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)))
      , SlotBuilder(ConferenceSlotBreaks.exhibition, TUESDAY,
        new DateTime(TUE_DATE + "17:55" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
        new DateTime(TUE_DATE + "18:15" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)))
    )

    val wednesdayBreaks = List(
      SlotBuilder(ConferenceSlotBreaks.registration, WEDNESDAY,
        new DateTime(WED_DATE + "08:00" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
        new DateTime(WED_DATE + "09:30" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)))
      , SlotBuilder(ConferenceSlotBreaks.coffee, WEDNESDAY,
        new DateTime(WED_DATE + "11:40" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
        new DateTime(WED_DATE + "12:00" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)))
      , SlotBuilder(ConferenceSlotBreaks.lunch, WEDNESDAY,
        new DateTime(WED_DATE + "13:00" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
        new DateTime(WED_DATE + "14:00" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)))
      , SlotBuilder(ConferenceSlotBreaks.coffee, WEDNESDAY,
        new DateTime(WED_DATE + "16:10" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
        new DateTime(WED_DATE + "16:40" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)))
      , SlotBuilder(ConferenceSlotBreaks.meetAndGreet, WEDNESDAY,
        new DateTime(WED_DATE + "18:30" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
        new DateTime(WED_DATE + "20:00" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)))
    )

    val thursdayBreaks = List(
      SlotBuilder(ConferenceSlotBreaks.petitDej, THURSDAY,
        new DateTime(THU_DATE + "08:00" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
        new DateTime(THU_DATE + "09:30" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)))
      , SlotBuilder(ConferenceSlotBreaks.coffee, THURSDAY,
        new DateTime(THU_DATE + "10:30" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
        new DateTime(THU_DATE + "10:50" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)))
      , SlotBuilder(ConferenceSlotBreaks.lunch, THURSDAY,
        new DateTime(THU_DATE + "13:00" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
        new DateTime(THU_DATE + "14:00" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)))
      , SlotBuilder(ConferenceSlotBreaks.coffee, THURSDAY,
        new DateTime(THU_DATE + "16:10" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
        new DateTime(THU_DATE + "16:40" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)))
    )

    val fridayBreaks = List(
      SlotBuilder(ConferenceSlotBreaks.petitDej, FRIDAY,
        new DateTime(FRI_DATE + "08:30" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
        new DateTime(FRI_DATE + "09:30" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon))),
      SlotBuilder(ConferenceSlotBreaks.coffee, FRIDAY,
        new DateTime(FRI_DATE + "10:30" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)),
        new DateTime(FRI_DATE + "10:45" + MIN_SEC).toDateTime(DateTimeZone.forID(europeLondon)))
    )

    // DEVOXX DAYS

    val mondaySchedule: List[Slot] = {
      mondayBreaks ++
      universitySlotsMonday ++
      tiaSlotsMonday ++
      labsSlotsMonday ++
      bofSlotsMonday
    }

    val tuesdaySchedule: List[Slot] = {
      tuesdayBreaks ++
      universitySlotsTuesday ++
      tiaSlotsTuesday ++
      labsSlotsTuesday ++
      bofSlotsTuesday
    }

    val wednesdaySchedule: List[Slot] = {
      wednesdayBreaks ++
      keynoteSlotsWednesday ++
      conferenceSlotsWednesday ++
      quickiesSlotsWednesday ++
      bofSlotsWednesday
    }

    val thursdaySchedule: List[Slot] = {
      thursdayBreaks ++
      keynoteSlotsThursday ++
      conferenceSlotsThursday ++
      quickiesSlotsThursday ++
      bofSlotsThursday ++
      igniteSlotsThursday
    }

    val fridaySchedule: List[Slot] = {
      fridayBreaks ++
      conferenceSlotsFriday
    }

    // COMPLETE DEVOXX
    def all: List[Slot] = {
      mondaySchedule ++
      tuesdaySchedule ++
      wednesdaySchedule ++
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
      faq = "https://devoxx.co.uk/faq/",
      registration = "http://reg.devoxx.co.uk",
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
      faq = "https://devoxx.co.uk/faq/",
      registration = "http://reg.devoxx.co.uk",
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


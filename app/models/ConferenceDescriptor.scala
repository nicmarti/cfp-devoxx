package models

import org.joda.time.{DateTime, DateTimeZone}
import play.api.Play

/**
 * ConferenceDescriptor.
 * This might be the first file to look at, and to customize.
 * Idea behind this file is to try to collect all configurable parameters for a conference.
 *
 * For labels, please do customize messages and messages.fr
 *
 * @author Frederic Camblor
 */

case class ConferenceUrls(faq: String, registration: String,
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
                                showQuestion: Boolean
                                 )

object ConferenceDescriptor {

  object ConferenceProposalTypes {
    val CONF = ProposalType(id = "conf", label = "conf.label")

    val UNI = ProposalType(id = "uni", label = "uni.label")

    val LAB = ProposalType(id = "lab", label = "lab.label")

    val QUICK = ProposalType(id = "quick", label = "quick.label")

    val BOF = ProposalType(id = "bof", label = "bof.label")

    val KEY = ProposalType(id = "key", label = "key.label")

    val CODE = ProposalType(id = "cstory", label = "code.label")

    val HACK = ProposalType(id = "hack", label = "hack.label")

    val OTHER = ProposalType(id = "other", label = "other.label")

    val ALL = List(CONF, UNI, LAB, QUICK, BOF, KEY, OTHER)

    def valueOf(id: String): ProposalType = id match {
      case "conf" => CONF
      case "uni" => UNI
      case "lab" => LAB
      case "quick" => QUICK
      case "bof" => BOF
      case "key" => KEY
      case "cstory" => CODE
      case "hack" => HACK
      case "other" => OTHER
    }

  }

  object ConferenceProposalConfigurations {
    val CONF = ProposalConfiguration(id = "conf", slotsCount = 65, givesSpeakerFreeEntrance = true, freeEntranceDisplayed = true, htmlClass = "icon-microphone", chosablePreferredDay = true)

    val UNI = ProposalConfiguration(id = "uni", slotsCount = 4, givesSpeakerFreeEntrance = true, freeEntranceDisplayed = true, htmlClass = "icon-laptop",
      chosablePreferredDay = true)
    val LAB = ProposalConfiguration(id = "lab", slotsCount = 8, givesSpeakerFreeEntrance = true, freeEntranceDisplayed = true, htmlClass = "icon-beaker",
      chosablePreferredDay = true)
    val QUICK = ProposalConfiguration(id = "quick", slotsCount = 20, givesSpeakerFreeEntrance = false, freeEntranceDisplayed = false, htmlClass = "icon-fast-forward",
      chosablePreferredDay = true)
    val BOF = ProposalConfiguration(id = "bof", slotsCount = 7, givesSpeakerFreeEntrance = false, freeEntranceDisplayed = false, htmlClass = "icon-group",
      chosablePreferredDay = false)
    val KEY = ProposalConfiguration(id = "key", slotsCount = 2, givesSpeakerFreeEntrance = true, freeEntranceDisplayed = false, htmlClass = "icon-microphone",
      chosablePreferredDay = true)
    val CODE = ProposalConfiguration(id = "cstory", slotsCount = 0, givesSpeakerFreeEntrance = false, freeEntranceDisplayed = false, htmlClass = "icon-microphone",
      chosablePreferredDay = false)
    val OTHER = ProposalConfiguration(id = "other", slotsCount = 0, givesSpeakerFreeEntrance = false, freeEntranceDisplayed = false, htmlClass = "icon-microphone",
      hiddenInCombo = true, chosablePreferredDay = false)
    val HACK = ProposalConfiguration(id = "hack", slotsCount = 2, givesSpeakerFreeEntrance = false, freeEntranceDisplayed = false, htmlClass = "icon-microphone",
      hiddenInCombo = false, chosablePreferredDay = false)
    val ALL = List(CONF, UNI, LAB, QUICK, BOF, KEY, CODE, HACK, OTHER)

    def doesItGivesSpeakerFreeEntrance(proposalType: ProposalType): Boolean = {
      ALL.filter(_.id == proposalType.id).exists(_.givesSpeakerFreeEntrance)
    }
  }

  object ConferenceTracks {
    val SERVERSIDE = Track("ssj", "serverside.label")
    val JAVASE = Track("jse", "javase.label")
    val MOBILE = Track("m", "mobile.label")
    val ARCHISEC = Track("archisec", "archisec.label")
    val AGILITY_TESTS = Track("agTest", "agilityTest.label")
    val FUTURE = Track("future", "future.label")
    val JAVA = Track("java", "java.label")
    val CLOUDBIGDATA = Track("cldbd", "cloudBigData.label")
    val WEBHTML5 = Track("webHtml5", "webHtml5.label")
    val UNKNOWN = Track("unknown", "unknown track")
    val ALL = List(SERVERSIDE, JAVASE, MOBILE, ARCHISEC, AGILITY_TESTS, FUTURE, JAVA, CLOUDBIGDATA, WEBHTML5, UNKNOWN)
  }

  object ConferenceTracksDescription {
    val SERVERSIDE = TrackDesc(ConferenceTracks.SERVERSIDE.id, "/assets/devoxxbe2014/images/icon_alternative.png", "track.serverside.title", "track.serverside.desc")
    val JAVASE = TrackDesc(ConferenceTracks.JAVASE.id, "/assets/devoxxbe2014/images/icon_javase.png", "track.javase.title", "track.javase.desc")
    val MOBILE = TrackDesc(ConferenceTracks.MOBILE.id, "/assets/devoxxbe2014/images/icon_web.png", "track.mobile.title", "track.mobile.desc")
    val ARCHISEC = TrackDesc(ConferenceTracks.ARCHISEC.id, "/assets/devoxxbe2014/images/icon_architecture.png", "track.archisec.title", "track.archisec.desc")
    val AGILITY_TESTS = TrackDesc(ConferenceTracks.AGILITY_TESTS.id, "/assets/devoxxbe2014/images/icon_startup.png", "track.agilityTest.title", "track.agilityTest.desc")
    val FUTURE = TrackDesc(ConferenceTracks.FUTURE.id, "/assets/devoxxbe2014/images/icon_future.png", "track.future.title", "track.future.desc")
    val JAVA = TrackDesc(ConferenceTracks.JAVA.id, "/assets/devoxxbe2014/images/icon_javase.png", "track.java.title", "track.java.desc")
    val CLOUDBIGDATA = TrackDesc(ConferenceTracks.CLOUDBIGDATA.id, "/assets/devoxxbe2014/images/icon_cloud.png", "track.cloudBigData.title", "track.cloudBigData.desc")
    val WEBHTML5 = TrackDesc(ConferenceTracks.WEBHTML5.id, "/assets/devoxxbe2014/images/icon_web.png", "track.webHtml5.title", "track.webHtml5.desc")
    val ALL = List(SERVERSIDE, JAVASE, MOBILE, ARCHISEC, AGILITY_TESTS, FUTURE, JAVA, CLOUDBIGDATA, WEBHTML5)

    def findTrackDescFor(t: Track): TrackDesc = {
      ALL.find(_.id == t.id).head
    }
  }

  object ConferenceRooms {

    // Tip : I use the ID to sort-by on the view per day... So if the exhibition floor id is "aaa" it will be
    // the first column on the HTML Table
    val HALL_EXPO = Room("a_hall", "Exhibition floor", 1500, "special")

    val ROOM_A = Room("rooma", "Room A", 345, "theatre")
    val ROOM_B = Room("roomb", "Room B", 364, "theatre")
    val ROOM_C = Room("roomc", "Room C", 684, "theatre")
    val ROOM_D = Room("roomd", "Room D", 407, "theatre")
    val ROOM_EF = Room("roomef", "Room EF", 407, "theatre")
    val ROOM_BC = Room("roombc", "Room BC", 407, "theatre")
    val ROOM_DEF = Room("roomdef", "Room DEF", 407, "theatre")
    val AUDIT = Room("roomaud", "Auditorium", 407, "theatre")
    val ROOM_EXEC = Room("rexec", "Exec Centre", 100, "theatre")

    val keynoteRoom = List(AUDIT)

    val uniWed = List(ROOM_A, ROOM_EF)
    val labsWed = List(ROOM_B, ROOM_C,ROOM_D,ROOM_EXEC)
    val bofWed = List(ROOM_B,ROOM_C,ROOM_EXEC)
    val hackWed = List(ROOM_A)

    val conferenceRooms= List(AUDIT,ROOM_A,ROOM_BC,ROOM_DEF,ROOM_EXEC)
    val quickieRooms= List(AUDIT,ROOM_A,ROOM_BC,ROOM_DEF,ROOM_EXEC)

    val bofThu=List(AUDIT,ROOM_BC,ROOM_DEF,ROOM_EXEC)
    val hackThu=List(ROOM_A)

    val allRooms = List(HALL_EXPO,ROOM_A,ROOM_B,ROOM_C,ROOM_D,ROOM_DEF,ROOM_EF,ROOM_BC,AUDIT,ROOM_EXEC)
  }

  object ConferenceSlotBreaks {
    val registration = SlotBreak("reg", "Registration, Welcome and Breakfast", "Accueil", ConferenceRooms.HALL_EXPO)
    val petitDej = SlotBreak("dej", "Breakfast", "Accueil et petit-déjeuner", ConferenceRooms.HALL_EXPO)
    val coffee = SlotBreak("coffee", "Coffee Break", "Pause café", ConferenceRooms.HALL_EXPO)
    val lunch = SlotBreak("lunch", "Lunch", "Pause déjeuner", ConferenceRooms.HALL_EXPO)
    val shortBreak = SlotBreak("chgt", "Break", "Pause courte", ConferenceRooms.HALL_EXPO)
    val exhibition = SlotBreak("exhib", "Exhibition", "Exhibition", ConferenceRooms.HALL_EXPO)
    val reception = SlotBreak("reception", "Evening Reception", "Exhibition", ConferenceRooms.HALL_EXPO)
    val closingKeynote = SlotBreak("closeKey", "Closing Keynote", "Keynote", ConferenceRooms.AUDIT)
  }

  object ConferenceSlots {

    // UNIVERSITY - Wednesday
    val uniSlotWednesday: List[Slot] = {
      val uniSlot1 = ConferenceRooms.uniWed.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.UNI.id, "wednesday",
            new DateTime("2015-06-17T10:00:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/London")),
            new DateTime("2015-06-17T13:30:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/London")), r1)
      }
      val uniSlot2 = ConferenceRooms.uniWed.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.UNI.id, "wednesday",
            new DateTime("2015-06-17T14:30:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/London")),
            new DateTime("2015-06-17T18:00:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/London")), r1)
      }
      uniSlot1 ++ uniSlot2
    }

    // HANDS ON LABS - Wednesday
    val holSlotWednesday: List[Slot] = {
      val slot1 = ConferenceRooms.labsWed.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.LAB.id, "wednesday",
            new DateTime("2015-06-17T10:00:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/London")),
            new DateTime("2015-06-17T13:30:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/London")), r1)
      }
      val slot2 = ConferenceRooms.labsWed.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.LAB.id, "wednesday",
            new DateTime("2015-06-17T14:30:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/London")),
            new DateTime("2015-06-17T18:00:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/London")), r1)
      }
      slot1 ++ slot2
    }

    // BOFS - Wednesday
    val bofSlotWednesday: List[Slot] = {
      val bofWednesdayEveningSlot1 = ConferenceRooms.bofWed.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.BOF.id, "wednesday",
            new DateTime("2015-06-17T18:45:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/London")),
            new DateTime("2015-06-17T19:45:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/London")), r1)
      }
      bofWednesdayEveningSlot1
    }

    // Hackaton - Wednesday
    val hackSlotWednesday: List[Slot] = {
      val slot1 = ConferenceRooms.hackWed.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.BOF.id, "wednesday",
            new DateTime("2015-06-17T18:45:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/London")),
            new DateTime("2015-06-17T19:45:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/London")), r1)
      }
      slot1
    }

     // Registration, coffee break, lunch etc - Wednesday
    val wednesdayBreaks = List(
      SlotBuilder(ConferenceSlotBreaks.registration, "wednesday",
        new DateTime("2015-06-17T08:00:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/London")),
        new DateTime("2015-06-17T09:30:00.000+01:00"))
      , SlotBuilder(ConferenceSlotBreaks.coffee, "wednesday",
         new DateTime("2015-06-17T11:30:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/London")),
         new DateTime("2015-06-17T12:00:00.000+01:00"))
      , SlotBuilder(ConferenceSlotBreaks.lunch, "wednesday",
         new DateTime("2015-06-17T13:00:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/London")),
         new DateTime("2015-06-17T14:30:00.000+01:00"))
      , SlotBuilder(ConferenceSlotBreaks.coffee, "wednesday",
         new DateTime("2015-06-17T16:00:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/London")),
         new DateTime("2015-06-17T16:30:00.000+01:00"))
    )

    // What is exactly a Wednesday
    val wednesday: List[Slot] = {
      uniSlotWednesday ++ holSlotWednesday ++ bofSlotWednesday ++ hackSlotWednesday  ++ wednesdayBreaks
    }

    // QUICKIES - Thursday
    val quickiesSlotsThursday: List[Slot] = {
      val quickiesThursdayLunch1 = ConferenceRooms.quickieRooms.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.QUICK.id, "thursday",
            new DateTime("2015-06-18T12:50:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/London")),
            new DateTime("2015-06-18T13:05:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/London")), r1)
      }
      val quickiesThursdayLunch2 = ConferenceRooms.quickieRooms.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.QUICK.id, "thursday",
            new DateTime("2015-06-18T13:15:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/London")),
            new DateTime("2015-06-18T13:50:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/London")), r2)
      }
      quickiesThursdayLunch1 ++ quickiesThursdayLunch2
    }

    // CONFERENCE KEYNOTES
    val keynoteSlotsThursday: List[Slot] = {
      val openingKeynoteSlot = ConferenceRooms.keynoteRoom.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.KEY.id, "thursday",
            new DateTime("2015-06-18T09:00:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/London")),
            new DateTime("2015-06-18T10:20:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/London")), r2)
      }
      openingKeynoteSlot
    }

    // CONFERENCE SLOTS
    val conferenceSlotsThursday: List[Slot] = {

      val slot1 = ConferenceRooms.conferenceRooms.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id, "thursday",
            new DateTime("2015-06-18T10:50:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/London")),
            new DateTime("2015-06-18T11:40:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/London")), r1)
      }
      val slot2 = ConferenceRooms.conferenceRooms.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id, "thursday",
            new DateTime("2015-06-18T11:50:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/London")),
            new DateTime("2015-06-18T12:40:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/London")), r1)
      }
      val slot3 = ConferenceRooms.conferenceRooms.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id, "thursday",
            new DateTime("2015-06-18T13:40:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/London")),
            new DateTime("2015-06-18T14:30:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/London")), r1)
      }
      val slot4 = ConferenceRooms.conferenceRooms.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id, "thursday",
            new DateTime("2015-06-18T14:40:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/London")),
            new DateTime("2015-06-18T15:30:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/London")), r1)
      }
      val slot5 = ConferenceRooms.conferenceRooms.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id, "thursday",
            new DateTime("2015-06-18T16:00:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/London")),
            new DateTime("2015-06-18T16:50:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/London")), r1)
      }
      val slot6 = ConferenceRooms.conferenceRooms.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.CONF.id, "thursday",
            new DateTime("2015-06-18T17:00:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/London")),
            new DateTime("2015-06-18T17:50:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/London")), r1)
      }
      slot1++slot2++slot3++slot4++slot5++slot6
    }

    // BOF - Thursday
    val bofThursday = ConferenceRooms.bofThu.map{
      r1 =>
      SlotBuilder(ConferenceProposalTypes.BOF.id, "thursday",
            new DateTime("2015-06-18T18:45:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/London")),
            new DateTime("2015-06-18T19:45:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/London")), r1)
    }

    // Hackaton - Thursday
    val hackSlotThursday: List[Slot] = {
      val slot1 = ConferenceRooms.hackThu.map {
        r1 =>
          SlotBuilder(ConferenceProposalTypes.BOF.id, "thursday",
            new DateTime("2015-06-18T18:45:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/London")),
            new DateTime("2015-06-18T19:45:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/London")), r1)
      }
      slot1
    }

    val thursdayBreaks = List(
       SlotBuilder(ConferenceSlotBreaks.coffee, "thursday",
        new DateTime("2015-06-18T10:20:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/London")),
        new DateTime("2015-06-18T10:50:00.000+01:00"))
      , SlotBuilder(ConferenceSlotBreaks.lunch, "thursday",
        new DateTime("2015-06-18T12:40:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/London")),
        new DateTime("2015-06-18T13:40:00.000+01:00"))
      , SlotBuilder(ConferenceSlotBreaks.coffee, "thursday",
        new DateTime("2015-06-18T15:30:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/London")),
        new DateTime("2015-06-18T16:00:00.000+01:00"))
    , SlotBuilder(ConferenceSlotBreaks.reception, "thursday",
        new DateTime("2015-06-18T17:50:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/London")),
        new DateTime("2015-06-18T19:30:00.000+01:00"))
    )

    // What is Thursday ?
    val thursday: List[Slot] = {
      thursdayBreaks ++ keynoteSlotsThursday ++ conferenceSlotsThursday ++ quickiesSlotsThursday ++ bofThursday ++ hackSlotThursday
    }

    val fridayBreaks = List(
      SlotBuilder(ConferenceSlotBreaks.petitDej, "friday", new DateTime("2015-06-19T08:30:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/London")), new DateTime("2015-06-19T09:30:00.000+01:00"))
      , SlotBuilder(ConferenceSlotBreaks.coffee, "friday", new DateTime("2015-06-19T10:30:00.000+01:00").toDateTime(DateTimeZone.forID("Europe/London")), new DateTime("2015-06-19T10:45:00.000+01:00"))
    )


    val friday: List[Slot] = {
      fridayBreaks // ++ conferenceSlotsFriday ++ bofSlotFriday
    }

    // COMPLETE DEVOXX UK is the 3 days

    def all: List[Slot] = {
      wednesday ++ thursday ++ friday
    }
  }

  // TODO : please customize below.
  def current() = ConferenceDescriptor(
    eventCode = "DevoxxUK2015",
    // You will need to update conf/routes files with this code if modified
    confUrlCode = "devoxxuk2015",
    frLangEnabled = false,
    fromEmail = Play.current.configuration.getString("mail.from").getOrElse("program@devoxx.co.uk"),
    committeeEmail = Play.current.configuration.getString("mail.committee.email").getOrElse("program@devoxx.co.uk"),
    bccEmail = Play.current.configuration.getString("mail.bcc"),
    bugReportRecipient = Play.current.configuration.getString("mail.bugreport.recipient").getOrElse("nicolas.martignole@devoxx.fr"),
    conferenceUrls = ConferenceUrls(
      faq = "http://www.devoxx.co.uk/faq/",
      registration = "http://reg.devoxx.com",
      confWebsite = "http://www.devoxx.co.uk/",
      cfpHostname = Play.current.configuration.getString("cfp.hostname").getOrElse("cfp.devoxx.co.uk")
    ),
    timing = ConferenceTiming(
      datesI18nKey = "17th -19th June 2015",
      speakersPassDuration = 3,
      preferredDayEnabled = true,
      firstDayFr = "17th june",
      firstDayEn = "June, 17th",
      datesFr = "du 17 au 19 juin 2015",
      datesEn = "from 17th to 19th of June, 2015",
      cfpOpenedOn = DateTime.parse("2014-12-17T00:00:00+00:00"),
      cfpClosedOn = DateTime.parse("2015-02-17T23:59:59+01:00"),
      scheduleAnnouncedOn = DateTime.parse("2015-02-23T00:00:00+01:00")
    ),
    hosterName = "Clever-cloud", hosterWebsite = "http://www.clever-cloud.com/#Devoxx",
    hashTag = "#DevoxxUK",
    conferenceSponsor = ConferenceSponsor(showSponsorProposalCheckbox = true, sponsorProposalType = ConferenceProposalTypes.CONF)
    , List("en")
    , "London"
    , showQuestion = false
  )

  val isCFPOpen: Boolean = {
    //    current().timing.cfpOpenedOn.isBeforeNow && current().timing.cfpClosedOn.isAfterNow
    false
  }

}

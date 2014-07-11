package models

import play.api.Play
import org.joda.time.DateTime

/**
 * ConferenceDescriptor.
 * This might be the first file to look at, and to customize.
 * Idea behind this file is to try to collect all configurable parameters for a conference. *
 * @author Frederic Camblor
 */

case class BitbucketProperties(usernameConfigProperty: String, tokenConfigProperty: String, issuesUrlConfigProperty: String)

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

case class TrackDesc(id:String, imgSrc: String, i18nTitleProp: String, i18nDescProp: String)

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
                                comitteeEmail: String,
                                bccEmail: Option[String],
                                bugReportRecipient: String,
                                conferenceUrls: ConferenceUrls,
                                timing: ConferenceTiming,
                                bitbucketProps: BitbucketProperties,
                                hosterName: String,
                                hosterWebsite: String,
                                hashTag: String,
                                conferenceSponsor: ConferenceSponsor)

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
    val START = ProposalConfiguration(id = "start", slotsCount = 20, givesSpeakerFreeEntrance = false, freeEntranceDisplayed = false, htmlClass = "icon-microphone",
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
    val STARTUP = TrackDesc(ConferenceTracks.STARTUP.id, "/assets/images/track/startup.png", "track.startup.title", "track.startup.desc")
    val SSJ = TrackDesc(ConferenceTracks.SSJ.id, "http://devoxx.be/images/tracks/95ddefdd.icon_javaee.png", "track.ssj.title", "track.ssj.desc")
    val JAVA = TrackDesc(ConferenceTracks.JAVA.id, "http://devoxx.be/images/tracks/aae8d181.icon_javase.png", "track.java.title", "track.java.desc")
    val MOBILE = TrackDesc(ConferenceTracks.MOBILE.id, "http://devoxx.be/images/tracks/1d2c40dd.icon_mobile.png", "track.mobile.title", "track.mobile.desc")
    val ARCHISEC = TrackDesc(ConferenceTracks.ARCHISEC.id, "http://devoxx.be/images/tracks/9943da91.icon_architecture.png", "track.archisec.title", "track.archisec.desc")
    val METHOD_DEVOPS = TrackDesc(ConferenceTracks.METHOD_DEVOPS.id, "http://devoxx.be/images/tracks/3ec02a75.icon_methology.png", "track.methodevops.title", "track.methodevops.desc")
    val FUTURE = TrackDesc(ConferenceTracks.FUTURE.id, "http://devoxx.be/images/tracks/a20b9b0a.icon_future.png", "track.future.title", "track.future.desc")
    val LANG = TrackDesc(ConferenceTracks.LANG.id, "http://devoxx.be/images/tracks/6caef5cf.icon_alternative.png", "track.lang.title", "track.lang.desc")
    val CLOUD = TrackDesc(ConferenceTracks.CLOUD.id, "http://devoxx.be/images/tracks/eca0b0a1.icon_cloud.png", "track.cloud.title", "track.cloud.desc")
    val WEB = TrackDesc(ConferenceTracks.WEB.id, "http://devoxx.be/images/tracks/cd5c36df.icon_web.png", "track.web.title", "track.web.desc")
    val ALL = List(STARTUP, SSJ, JAVA, MOBILE, ARCHISEC, METHOD_DEVOPS, FUTURE, LANG, CLOUD, WEB)

    def findTrackDescFor(t: Track): TrackDesc = {
      ALL.find(_.id == t.id).head
    }
  }

  object ConferenceRooms {

    // TODO for Devoxx BE : there are 8 rooms to configure
    val HALL_EXPO = Room("hall", "Espace d'exposition", 1500, recorded = false, "special")

    val ROOM8 = Room("room8", "Room 8", 680, recorded = true, "theatre")

    val SMALL_LAB01 = Room("smallLab01", "Small Lab 01", 50, recorded = false, "classroom")

    val allBigRoom = List(ROOM8)

    val allRoomsLabs = List(SMALL_LAB01)

    val allRoomsBOFs = List(SMALL_LAB01)

    val allRoomsTIA = List(ROOM8)

    val allRooms = allBigRoom

  }

  object ConferenceSlotBreaks {
    val petitDej = SlotBreak("dej", "Welcome and Breakfast", "Accueil et petit-déjeuner", ConferenceRooms.HALL_EXPO)
    val coffee = SlotBreak("coffee", "Coffee Break", "Pause café", ConferenceRooms.HALL_EXPO)
    val lunch = SlotBreak("lunch", "Lunch", "Pause déjeuner", ConferenceRooms.HALL_EXPO)
    val shortBreak = SlotBreak("chgt", "Break", "Pause courte", ConferenceRooms.HALL_EXPO)
  }

  object ConferenceSlots {
    val universitySlots: List[Slot] = {
      val u1 = ConferenceRooms.allBigRoom.map {
        r =>
          SlotBuilder(ConferenceProposalTypes.UNI.id, "monday", new DateTime("2014-11-10T09:30:00.000+02:00"), new DateTime("2014-11-10T12:30:00.000+02:00"), r)
      }
      val u2 = ConferenceRooms.allBigRoom.map {
        r2 =>
          SlotBuilder(ConferenceProposalTypes.UNI.id, "monday", new DateTime("2014-11-10T13:30:00.000+02:00"), new DateTime("2014-11-10T16:30:00.000+02:00"), r2)
      }
      u1 ++ u2
    }

    val toolsInActionSlots: List[Slot] = Nil

    val labsSlots: List[Slot] = Nil

    val quickiesSlotsThursday: List[Slot] = Nil

    val quickiesSlotsFriday: List[Slot] = Nil

    val conferenceSlotsThursday: List[Slot] = Nil

    val conferenceSlotsFriday: List[Slot] = Nil

    val bofSlotsThursday: List[Slot] = Nil

    val wednesday: List[Slot] = Nil

    val thursday: List[Slot] = Nil

    val friday: List[Slot] = Nil

    def all: List[Slot] = {
      wednesday ++ thursday ++ friday
    }
  }

  def current() = ConferenceDescriptor(
    eventCode = "DevoxxBe2014",
    // You will need to update conf/routes files with this code if modified
    confUrlCode = "devoxxbe2014",
    frLangEnabled = false,
    fromEmail = Play.current.configuration.getString("mail.from").getOrElse("program@devoxx.com"),
    comitteeEmail = Play.current.configuration.getString("mail.comittee.email").getOrElse("program@devoxx.com"),
    bccEmail = Play.current.configuration.getString("mail.bcc"),
    bugReportRecipient = Play.current.configuration.getString("mail.bugreport.recipient").getOrElse("nicolas.martignole@devoxx.fr"),
    conferenceUrls = ConferenceUrls(
      faq = "http://www.devoxx.be/faq/",
      registration = "http://reg.devoxx.be",
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
    bitbucketProps = BitbucketProperties("bitbucket.username", "bitbucket.token", "bitbucket.issues.url"),
    hosterName = "Clever-cloud", hosterWebsite = "http://www.clever-cloud.com/#DevoxxFR",
    hashTag = "#DevoxxFR",
    conferenceSponsor = ConferenceSponsor(showSponsorProposalCheckbox = true, sponsorProposalType = ConferenceProposalTypes.CONF)
  )

  def isCFPOpen: Boolean = {
    current().timing.cfpOpenedOn.isBeforeNow && current().timing.cfpClosedOn.isAfterNow
  }

}

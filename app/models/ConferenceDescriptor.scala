package models

import play.api.Play
import play.api.templates.HtmlFormat
import views.html.Application.{devoxxEnglishProposalsHomeFooterBlock, devoxxPreviousVideosHomeRightBlock}
import play.api.i18n.Lang
import org.joda.time.DateTime
import play.api.data.Form
import views.html.CallForPaper.{devoxxProposalHelpBlock, devoxxProposalsGuideFooterBlock}
import models.ConferenceDescriptor.DevoxxSlots
import views.html.emptyContent

case class BitbucketProperties(var usernameConfigProperty: String, var tokenConfigProperty: String, var issuesUrlConfigProperty: String)
case class ConferenceNaming(
    var shortYearlyName: String, var longYearlyName: String,
    var shortName: String, var longName: String,
    var longSplittedName_whiteStart: String, var longSplittedName_colored: String, var longSplittedName_whiteEnd: String
)
case class ConferenceUrls(
    var faq: String, var registration: String,
    var confWebsite: String, var cfpHostname: String
)
case class ConferenceTiming(
    var datesI18nKey: String,
    var speakersPassDuration: Integer,
    var firstDayFr: String,
    var firstDayEn: String,
    var datesFr: String,
    var datesEn: String,
    var cfpOpenedOn: DateTime,
    var cfpClosedOn: DateTime,
    var scheduleAnnouncedOn: DateTime
)
case class ContentBlocks (
   _homeIndexFirstRightBlock: (Lang,ConferenceDescriptor) => HtmlFormat.Appendable,
   _homeIndexFooterBlock: (Lang,ConferenceDescriptor) => HtmlFormat.Appendable,
   _proposalHelpBlock: (Lang,ConferenceDescriptor) => HtmlFormat.Appendable,
   _previewProposalFooterBlock: (Lang, ConferenceDescriptor, String, String, Form[models.Proposal], String) => HtmlFormat.Appendable,
   showSponsorProposalCheckbox: Boolean,
   sponsorProposalType: ProposalType = ProposalType.UNKNOWN
) {
  def homeIndexFirstRightBlock()(implicit lang: Lang, confDesc: ConferenceDescriptor) = _homeIndexFirstRightBlock(lang, confDesc)
  def homeIndexFooterBlock()(implicit lang: Lang, confDesc: ConferenceDescriptor) = _homeIndexFooterBlock(lang, confDesc)
  def proposalHelpBlock()(implicit lang: Lang, confDesc: ConferenceDescriptor) = _proposalHelpBlock(lang, confDesc)
  def previewProposalFooterBlock(htmlSummary: String, privateMessage: String, newProposal: Form[models.Proposal], currentUser: String)(implicit lang: Lang, confDesc: ConferenceDescriptor) = _previewProposalFooterBlock(lang, confDesc, htmlSummary, privateMessage, newProposal, currentUser)
}


case class ConferenceDescriptor(
    var eventCode: String, var confUrlCode: String,
    var naming: ConferenceNaming,
    var frLangEnabled: Boolean,
    var fromEmail: String, var bccEmail: Option[String], var bugReportRecipient: String,
    var conferenceUrls: ConferenceUrls,
    var timing: ConferenceTiming,
    var bitbucketProps: BitbucketProperties,
    var hosterName: String, var hosterWebsite: String,
    var hashTag: String,
    var tracks: Seq[Track],
    var proposalTypes: List[ProposalType],
    var rooms: List[Room],
    var contentBlocks: ContentBlocks
) {
  // Slots should be lazy loaded because it needs ConferenceDescriptor instance
  // to be generated
  def slots : List[Slot] = {
    DevoxxSlots.all
  }
}


object ConferenceDescriptor {

    object DevoxxProposalTypes {
      val CONF = ProposalType(id="conf", simpleLabel="conf.simple.label", label="conf.label",
        slotsCount=89, givesSpeakerFreeEntrance=true, freeEntranceDisplayed=true, htmlClass="icon-microphone",
        recorded=true, chosablePreferredDay=true)
      val UNI = ProposalType(id="uni", simpleLabel="uni.simple.label", label="uni.label",
        slotsCount=16, givesSpeakerFreeEntrance=true, freeEntranceDisplayed=true, htmlClass="icon-laptop",
        recorded=true, chosablePreferredDay=false)
      val TIA = ProposalType(id="tia", simpleLabel="tia.simple.label", label="tia.label",
        slotsCount=24, givesSpeakerFreeEntrance=true, freeEntranceDisplayed=true, htmlClass="icon-legal",
        recorded=true, chosablePreferredDay=false)
      val LAB = ProposalType(id="lab", simpleLabel="lab.simple.label", label="lab.label",
        slotsCount=10, givesSpeakerFreeEntrance=true, freeEntranceDisplayed=true, htmlClass="icon-beaker",
        recorded=false, chosablePreferredDay=false)
      val QUICK = ProposalType(id="quick", simpleLabel="quick.simple.label", label="quick.label",
        slotsCount=28, givesSpeakerFreeEntrance=false, freeEntranceDisplayed=false, htmlClass="icon-fast-forward",
        recorded=true, chosablePreferredDay=true)
      val BOF = ProposalType(id="bof", simpleLabel="bof.simple.label", label="bof.label",
        slotsCount=25, givesSpeakerFreeEntrance=false, freeEntranceDisplayed=false, htmlClass="icon-group",
        recorded=false, chosablePreferredDay=false)
      val KEY = ProposalType(id="key", simpleLabel="key.simple.label", label="key.label",
        slotsCount=1, givesSpeakerFreeEntrance=true, freeEntranceDisplayed=false, htmlClass="icon-microphone",
        recorded=true, chosablePreferredDay=true)
      val START = ProposalType(id="start", simpleLabel="start.simple.label", label="start.label",
        slotsCount=20, givesSpeakerFreeEntrance=false, freeEntranceDisplayed=false, htmlClass="icon-microphone",
        recorded=false, chosablePreferredDay=false, impliedSelectedTrack=Option(DevoxxTracks.STARTUP))
      val OTHER = ProposalType(id="other", simpleLabel="other.simple.label", label="other.label",
        slotsCount=1, givesSpeakerFreeEntrance=false, freeEntranceDisplayed=false, htmlClass="icon-microphone",
        recorded=false, chosablePreferredDay=false)
    }

    object DevoxxTracks {
      val STARTUP = Track("startup", "startup.label", "/assets/images/track/startup.png", "track.startup.title", "track.startup.desc")
      val SSJ = Track("ssj", "ssj.label", "http://devoxx.be/images/tracks/95ddefdd.icon_javaee.png", "track.ssj.title", "track.ssj.desc")
      val JAVA = Track("java", "java.label", "http://devoxx.be/images/tracks/aae8d181.icon_javase.png", "track.java.title", "track.java.desc")
      val MOBILE = Track("mobile", "mobile.label", "http://devoxx.be/images/tracks/1d2c40dd.icon_mobile.png", "track.mobile.title", "track.mobile.desc")
      val ARCHISEC = Track("archisec", "archisec.label", "http://devoxx.be/images/tracks/9943da91.icon_architecture.png", "track.archisec.title", "track.archisec.desc")
      val METHOD_DEVOPS = Track("methodevops", "methodevops.label", "http://devoxx.be/images/tracks/3ec02a75.icon_methology.png", "track.methodevops.title", "track.methodevops.desc")
      val FUTURE = Track("future", "future.label", "http://devoxx.be/images/tracks/a20b9b0a.icon_future.png", "track.future.title", "track.future.desc")
      val LANG = Track("lang", "lang.label", "http://devoxx.be/images/tracks/6caef5cf.icon_alternative.png", "track.lang.title", "track.lang.desc")
      val CLOUD = Track("cloud", "cloud.label", "http://devoxx.be/images/tracks/eca0b0a1.icon_cloud.png", "track.cloud.title", "track.cloud.desc")
      val WEB = Track("web", "web.label", "http://devoxx.be/images/tracks/cd5c36df.icon_web.png", "track.web.title", "track.web.desc")
    }

    object DevoxxRooms {
      val HALL_EXPO = Room("hall", "Espace d'exposition", 1500, false, "special")

      val KEYNOTE_SEINE = Room("seine_keynote", "Seine", 980, true, "keynote")
      val SEINE_A = Room("seine_a", "Seine A", 280, true, "theatre")
      val SEINE_B = Room("seine_b", "Seine B", 280, true, "theatre")
      val SEINE_C = Room("seine_c", "Seine C", 260, true, "theatre")
      val AUDITORIUM = Room("auditorium", "Auditorium", 160, true, "theatre")
      val ELLA_FITZGERALD = Room("el_ab_full", "Ella Fitzgerald", 290, true, "theatre")
      val MILES_DAVIS = Room("md_full", "M.Davis", 220, true, "theatre")

      val ELLA_FITZGERALD_AB = Room("el_ab", "Ella Fitzgerald AB", 45, true, "classe")
      val LOUIS_ARMSTRONG_AB = Room("la_ab", "Louis Armstrong AB", 30, true, "classe")
      val LOUIS_ARMSTRONG_CD = Room("la_cd", "Louis Armstrong CD", 30, true, "classe")
      val MILES_DAVIS_A = Room("md_a", "Miles Davis A", 24, true, "classe")
      val MILES_DAVIS_B = Room("md_b", "Miles Davis B", 24, true, "classe")
      val MILES_DAVIS_C = Room("md_c", "Miles Davis C", 48, true, "classe")

      val ELLA_FITZGERALD_AB_TH = Room("el_ab_th", "E.Fitzgerald AB", 80, false, "theatre")
      val LOUIS_ARMSTRONG_AB_TH = Room("la_ab_th", "L.Armstrong AB", 80, false, "theatre")
      val LOUIS_ARMSTRONG_CD_TH = Room("la_cd_th", "L.Armstrong CD", 80, false, "theatre")
      val MILES_DAVIS_A_TH = Room("md_a_th", "M.Davis A", 50, false, "theatre")
      val MILES_DAVIS_B_TH = Room("md_b_th", "M.Davis B", 50, false, "theatre")
      val MILES_DAVIS_C_TH = Room("md_c_th", "M.Davis C", 80, false, "theatre")

      val DUKE_ELLINGTON = Room("duke", "Duke Ellington-CodeStory", 15, false, "classe")
      val FOYER_BAS = Room("foyer_bas", "Foyer bas", 300, false, "classe")
      val LABO = Room("foyer_labo", "Labo", 40, false, "special")

      val allBigRoom = List(DevoxxRooms.SEINE_A, DevoxxRooms.SEINE_B, DevoxxRooms.SEINE_C, DevoxxRooms.AUDITORIUM)

      val allRoomsLabs = List(DevoxxRooms.ELLA_FITZGERALD_AB, DevoxxRooms.LOUIS_ARMSTRONG_AB, DevoxxRooms.LOUIS_ARMSTRONG_CD,
        DevoxxRooms.MILES_DAVIS_A, DevoxxRooms.MILES_DAVIS_B, DevoxxRooms.MILES_DAVIS_C)

      val allRoomsBOFs = List(DevoxxRooms.ELLA_FITZGERALD_AB, DevoxxRooms.LOUIS_ARMSTRONG_AB, DevoxxRooms.AUDITORIUM, DevoxxRooms.LOUIS_ARMSTRONG_CD,
        DevoxxRooms.MILES_DAVIS_A, DevoxxRooms.MILES_DAVIS_B, DevoxxRooms.MILES_DAVIS_C, DevoxxRooms.LABO)

      val allRoomsTIA = List(DevoxxRooms.ELLA_FITZGERALD_AB_TH, DevoxxRooms.LOUIS_ARMSTRONG_AB_TH, DevoxxRooms.LOUIS_ARMSTRONG_CD_TH,
        DevoxxRooms.MILES_DAVIS_A_TH, DevoxxRooms.MILES_DAVIS_B_TH, DevoxxRooms.MILES_DAVIS_C_TH,
        DevoxxRooms.SEINE_A, DevoxxRooms.SEINE_B, DevoxxRooms.SEINE_C, DevoxxRooms.AUDITORIUM)

      val allRooms = allBigRoom ++ List(DevoxxRooms.ELLA_FITZGERALD, DevoxxRooms.MILES_DAVIS)

      // No E.Fitzgerald for Apres-midi des decideurs
      val allRoomsButAMD = List(DevoxxRooms.SEINE_A, DevoxxRooms.SEINE_B, DevoxxRooms.SEINE_C, DevoxxRooms.AUDITORIUM, DevoxxRooms.MILES_DAVIS)
    }

    object DevoxxSlotBreaks {
      val petitDej = SlotBreak("dej", "Welcome and Breakfast", "Accueil et petit-déjeuner", DevoxxRooms.HALL_EXPO)
      val coffee = SlotBreak("coffee", "Coffee Break", "Pause café", DevoxxRooms.HALL_EXPO)
      val lunch = SlotBreak("lunch", "Lunch", "Pause déjeuner", DevoxxRooms.HALL_EXPO)
      val shortBreak = SlotBreak("chgt", "Break", "Pause courte", DevoxxRooms.HALL_EXPO)
    }

    object DevoxxSlots {
      val universitySlots: List[Slot] = {
          val u1 = DevoxxRooms.allBigRoom.map {
            r =>
              SlotBuilder(DevoxxProposalTypes.UNI.id, "mercredi", new DateTime("2014-04-16T09:30:00.000+02:00"), new DateTime("2014-04-16T12:30:00.000+02:00"), r)
          }
          val u2 = DevoxxRooms.allBigRoom.map {
            r2 =>
              SlotBuilder(DevoxxProposalTypes.UNI.id, "mercredi", new DateTime("2014-04-16T13:30:00.000+02:00"), new DateTime("2014-04-16T16:30:00.000+02:00"), r2)
          }
          u1 ++ u2
        }

        val toolsInActionSlots: List[Slot] = {
          val t1 = DevoxxRooms.allRoomsTIA.map {
            r =>
              SlotBuilder(DevoxxProposalTypes.TIA.id, "mercredi", new DateTime("2014-04-16T17:10:00.000+02:00"), new DateTime("2014-04-16T17:40:00.000+02:00"), r)
          }
          val t2 = DevoxxRooms.allRoomsTIA.map {
            r =>
              SlotBuilder(DevoxxProposalTypes.TIA.id, "mercredi", new DateTime("2014-04-16T17:50:00.000+02:00"), new DateTime("2014-04-16T18:20:00.000+02:00"), r)
          }
          val t3 = DevoxxRooms.allRoomsTIA.map {
            r =>
              SlotBuilder(DevoxxProposalTypes.TIA.id, "mercredi", new DateTime("2014-04-16T18:30:00.000+02:00"), new DateTime("2014-04-16T19:00:00.000+02:00"), r)
          }
          t1 ++ t2 ++ t3
        }

        val labsSlots: List[Slot] = {
          val l1 = DevoxxRooms.allRoomsLabs.map {
            r =>
              SlotBuilder(DevoxxProposalTypes.LAB.id, "mercredi", new DateTime("2014-04-16T09:30:00.000+02:00"), new DateTime("2014-04-16T12:30:00.000+02:00"), r)
          }
          val l2 = DevoxxRooms.allRoomsLabs.map {
            r2 =>
              SlotBuilder(DevoxxProposalTypes.LAB.id, "mercredi", new DateTime("2014-04-16T13:30:00.000+02:00"), new DateTime("2014-04-16T16:30:00.000+02:00"), r2)
          }
          l1 ++ l2
        }

        val quickiesSlotsThursday: List[Slot] = {
          val quickie01 = DevoxxRooms.allRoomsButAMD.map {
            r =>
              SlotBuilder(DevoxxProposalTypes.QUICK.id, "jeudi", new DateTime("2014-04-17T12:35:00.000+02:00"), new DateTime("2014-04-17T12:50:00.000+02:00"), r)
          }
          val quickie02 = DevoxxRooms.allRoomsButAMD.map {
            r =>
              SlotBuilder(DevoxxProposalTypes.QUICK.id, "jeudi", new DateTime("2014-04-17T13:00:00.000+02:00"), new DateTime("2014-04-17T13:15:00.000+02:00"), r)
          }
          quickie01 ++ quickie02
        }

        val quickiesSlotsFriday: List[Slot] = {

          val quickie03 = DevoxxRooms.allRooms.map {
            r =>
              SlotBuilder(DevoxxProposalTypes.QUICK.id, "vendredi", new DateTime("2014-04-18T12:45:00.000+02:00"), new DateTime("2014-04-18T13:00:00.000+02:00"), r)
          }
          val quickie04 = DevoxxRooms.allRooms.map {
            r =>
              SlotBuilder(DevoxxProposalTypes.QUICK.id, "vendredi", new DateTime("2014-04-18T13:10:00.000+02:00"), new DateTime("2014-04-18T13:25:00.000+02:00"), r)
          }
          quickie03 ++ quickie04
        }

        val conferenceSlotsThursday: List[Slot] = {
          val c1 = DevoxxRooms.allRooms.map {
            r =>
              SlotBuilder(DevoxxProposalTypes.CONF.id, "jeudi", new DateTime("2014-04-17T11:30:00.000+02:00"), new DateTime("2014-04-17T12:20:00.000+02:00"), r)
          }
          val c2 = DevoxxRooms.allRooms.map {
            r =>
              SlotBuilder(DevoxxProposalTypes.CONF.id, "jeudi", new DateTime("2014-04-17T13:25:00.000+02:00"), new DateTime("2014-04-17T14:15:00.000+02:00"), r)
          }
          val c3 = DevoxxRooms.allRooms.map {
            r =>
              SlotBuilder(DevoxxProposalTypes.CONF.id, "jeudi", new DateTime("2014-04-17T14:30:00.000+02:00"), new DateTime("2014-04-17T15:20:00.000+02:00"), r)
          }
          val c4 = DevoxxRooms.allRooms.map {
            r =>
              SlotBuilder(DevoxxProposalTypes.CONF.id, "jeudi", new DateTime("2014-04-17T15:35:00.000+02:00"), new DateTime("2014-04-17T16:25:00.000+02:00"), r)
          }
          val c5 = DevoxxRooms.allRooms.map {
            r =>
              SlotBuilder(DevoxxProposalTypes.CONF.id, "jeudi", new DateTime("2014-04-17T17:00:00.000+02:00"), new DateTime("2014-04-17T17:50:00.000+02:00"), r)
          }

          val c6 = DevoxxRooms.allRooms.map {
            r =>
              SlotBuilder(DevoxxProposalTypes.CONF.id, "jeudi", new DateTime("2014-04-17T18:05:00.000+02:00"), new DateTime("2014-04-17T18:55:00.000+02:00"), r)
          }
          c1 ++ c2 ++ c3 ++ c4 ++ c5 ++ c6
        }

        val conferenceSlotsFriday: List[Slot] = {
          val c1 = DevoxxRooms.allRooms.map {
            r =>
              SlotBuilder(DevoxxProposalTypes.CONF.id, "vendredi", new DateTime("2014-04-18T10:40:00.000+02:00"), new DateTime("2014-04-18T11:30:00.000+02:00"), r)
          }
          val c2 = DevoxxRooms.allRooms.map {
            r =>
              SlotBuilder(DevoxxProposalTypes.CONF.id, "vendredi", new DateTime("2014-04-18T11:45:00.000+02:00"), new DateTime("2014-04-18T12:35:00.000+02:00"), r)
          }

          val c3 = DevoxxRooms.allRooms.map {
            r =>
              SlotBuilder(DevoxxProposalTypes.CONF.id, "vendredi", new DateTime("2014-04-18T13:30:00.000+02:00"), new DateTime("2014-04-18T14:20:00.000+02:00"), r)
          }
          val c4 = DevoxxRooms.allRooms.map {
            r =>
              SlotBuilder(DevoxxProposalTypes.CONF.id, "vendredi", new DateTime("2014-04-18T14:35:00.000+02:00"), new DateTime("2014-04-18T15:25:00.000+02:00"), r)
          }
          val c5 = DevoxxRooms.allRooms.map {
            r =>
              SlotBuilder(DevoxxProposalTypes.CONF.id, "vendredi", new DateTime("2014-04-18T15:40:00.000+02:00"), new DateTime("2014-04-18T16:30:00.000+02:00"), r)
          }

          val c6 = DevoxxRooms.allRooms.map {
            r =>
              SlotBuilder(DevoxxProposalTypes.CONF.id, "vendredi", new DateTime("2014-04-18T17:00:00.000+02:00"), new DateTime("2014-04-18T17:50:00.000+02:00"), r)
          }

          val c7 = List(DevoxxRooms.MILES_DAVIS, DevoxxRooms.ELLA_FITZGERALD, DevoxxRooms.AUDITORIUM).map {
            r =>
              SlotBuilder(DevoxxProposalTypes.CONF.id, "vendredi", new DateTime("2014-04-18T18:05:00.000+02:00"), new DateTime("2014-04-18T18:55:00.000+02:00"), r)
          }

          c1 ++ c2 ++ c3 ++ c4 ++ c5 ++ c6 ++ c7
        }

        val bofSlotsThursday: List[Slot] = {

          val bof01 = DevoxxRooms.allRoomsBOFs.map {
            r =>
              SlotBuilder(DevoxxProposalTypes.BOF.id, "jeudi", new DateTime("2014-04-17T19:30:00.000+02:00"), new DateTime("2014-04-17T20:30:00.000+02:00"), r)
          }
          val bof02 = DevoxxRooms.allRoomsBOFs.map {
            r =>
              SlotBuilder(DevoxxProposalTypes.BOF.id, "jeudi", new DateTime("2014-04-17T20:30:00.000+02:00"), new DateTime("2014-04-17T21:30:00.000+02:00"), r)
          }
          val bof03 = DevoxxRooms.allRoomsBOFs.map {
            r =>
              SlotBuilder(DevoxxProposalTypes.BOF.id, "jeudi", new DateTime("2014-04-17T21:30:00.000+02:00"), new DateTime("2014-04-17T22:30:00.000+02:00"), r)
          }
          bof01 ++ bof02 ++ bof03
        }

        val wednesday: List[Slot] = {
          val wednesdayBreaks = List(
            SlotBuilder(DevoxxSlotBreaks.petitDej, "mercredi", new DateTime("2014-04-16T08:00:00.000+02:00"), new DateTime("2014-04-16T09:30:00.000+02:00"))
            , SlotBuilder(DevoxxSlotBreaks.lunch, "mercredi", new DateTime("2014-04-16T12:30:00.000+02:00"), new DateTime("2014-04-16T13:30:00.000+02:00"))
            , SlotBuilder(DevoxxSlotBreaks.coffee, "mercredi", new DateTime("2014-04-16T16:30:00.000+02:00"), new DateTime("2014-04-16T17:10:00.000+02:00"))
          )
          val odc = Proposal.findById("ZYE-706")
          val devoxx4Kids = Proposal.findById("USM-170")
          val hackerGarten = Proposal.findById("QIY-889")

          val specialEvents = List(
            SlotBuilder(DevoxxProposalTypes.OTHER.id, "mercredi", new DateTime("2014-04-16T09:30:00.000+02:00"), new DateTime("2014-04-16T18:00:00.000+02:00"), Room.OTHER, odc)
            , SlotBuilder(DevoxxProposalTypes.OTHER.id, "mercredi", new DateTime("2014-04-16T09:30:00.000+02:00"), new DateTime("2014-04-16T18:00:00.000+02:00"), Room.OTHER, devoxx4Kids)
          )
          wednesdayBreaks ++ specialEvents
        }

        val thursday: List[Slot] = {

          val keynoteThursday: List[Slot] = {
            val welcomeKeynote = Proposal.findById("DSD-030")
            val key01 = SlotBuilder(DevoxxProposalTypes.KEY.id, "jeudi", new DateTime("2014-04-17T09:00:00.000+02:00"), new DateTime("2014-04-17T09:10:00.000+02:00"), DevoxxRooms.KEYNOTE_SEINE, welcomeKeynote)

            val babinet=Proposal.findById("IIH-512")
            val key02 = SlotBuilder(DevoxxProposalTypes.KEY.id, "jeudi", new DateTime("2014-04-17T09:10:00.000+02:00"), new DateTime("2014-04-17T09:30:00.000+02:00"), DevoxxRooms.KEYNOTE_SEINE, babinet)

            val guyMamou=Proposal.findById("RCV-236")
            val key03 = SlotBuilder(DevoxxProposalTypes.KEY.id, "jeudi", new DateTime("2014-04-17T09:40:00.000+02:00"), new DateTime("2014-04-17T10:00:00.000+02:00"), DevoxxRooms.KEYNOTE_SEINE, guyMamou)

            val simplon=Proposal.findById("TAX-972")
            val key04 = SlotBuilder(DevoxxProposalTypes.KEY.id, "jeudi", new DateTime("2014-04-17T10:10:00.000+02:00"), new DateTime("2014-04-17T10:30:00.000+02:00"), DevoxxRooms.KEYNOTE_SEINE, simplon)

            List(key01, key02, key03, key04)
          }

          val thursdayBreaks = List(
            SlotBuilder(DevoxxSlotBreaks.petitDej, "jeudi", new DateTime("2014-04-17T07:30:00.000+02:00"), new DateTime("2014-04-17T09:00:00.000+02:00"))
            , SlotBuilder(DevoxxSlotBreaks.coffee, "jeudi", new DateTime("2014-04-17T10:45:00.000+02:00"), new DateTime("2014-04-17T11:30:00.000+02:00"))
            , SlotBuilder(DevoxxSlotBreaks.lunch, "jeudi", new DateTime("2014-04-17T12:20:00.000+02:00"), new DateTime("2014-04-17T13:25:00.000+02:00"))
            , SlotBuilder(DevoxxSlotBreaks.coffee, "jeudi", new DateTime("2014-04-17T16:25:00.000+02:00"), new DateTime("2014-04-17T17:00:00.000+02:00"))
            , SlotBuilder(DevoxxSlotBreaks.shortBreak, "jeudi", new DateTime("2014-04-17T18:55:00.000+02:00"), new DateTime("2014-04-17T19:30:00.000+02:00"))
          )


          thursdayBreaks ++ keynoteThursday
        }

        val friday: List[Slot] = {

        val keynoteFriday: List[Slot] = {
            val geert=Proposal.findById("JEJ-167")
            val key05 = SlotBuilder(DevoxxProposalTypes.KEY.id, "vendredi", new DateTime("2014-04-18T09:00:00.000+02:00"), new DateTime("2014-04-18T09:20:00.000+02:00"), DevoxxRooms.KEYNOTE_SEINE, geert)

            val oracle=Proposal.findById("BHX-731")
            val key06 = SlotBuilder(DevoxxProposalTypes.KEY.id, "vendredi", new DateTime("2014-04-18T09:30:00.000+02:00"), new DateTime("2014-04-18T09:50:00.000+02:00"), DevoxxRooms.KEYNOTE_SEINE, oracle)

            val serge=Proposal.findById("KOC-474")
            val key07 = SlotBuilder(DevoxxProposalTypes.KEY.id, "vendredi", new DateTime("2014-04-18T10:00:00.000+02:00"), new DateTime("2014-04-18T10:20:00.000+02:00"), DevoxxRooms.KEYNOTE_SEINE, serge)

            List(key05, key06, key07)
          }

          val fridayBreaks = List(
            SlotBuilder(DevoxxSlotBreaks.petitDej, "vendredi", new DateTime("2014-04-18T08:00:00.000+02:00"), new DateTime("2014-04-18T09:00:00.000+02:00"))
            , SlotBuilder(DevoxxSlotBreaks.coffee, "vendredi", new DateTime("2014-04-18T10:10:00.000+02:00"), new DateTime("2014-04-18T10:40:00.000+02:00"))
            , SlotBuilder(DevoxxSlotBreaks.lunch, "vendredi", new DateTime("2014-04-18T12:35:00.000+02:00"), new DateTime("2014-04-18T13:30:00.000+02:00"))
            , SlotBuilder(DevoxxSlotBreaks.coffee, "vendredi", new DateTime("2014-04-18T16:30:00.000+02:00"), new DateTime("2014-04-18T17:00:00.000+02:00"))
          )

          fridayBreaks ++ keynoteFriday
        }

      def all: List[Slot] = { wednesday ++ thursday ++ friday }
    }

    def current() = ConferenceDescriptor(
      eventCode = "DevoxxBe2014",
      // You will need to update conf/routes files with this code if modified
      confUrlCode = "devoxxfr2014",
      frLangEnabled = false,
      naming = ConferenceNaming(
        shortYearlyName = "DevoxxBe 2014",
        longYearlyName = "Devoxx Belgium 2014",
        shortName = "DevoxxBe",
        longName = "Devoxx Belgium",
        longSplittedName_whiteStart="Devox", longSplittedName_colored="x", longSplittedName_whiteEnd="Belgium"
      ),
      fromEmail = "program@devoxx.com",
      bccEmail = Option("nicolas.martignole@devoxx.fr"),
      bugReportRecipient = "nicolas.martignole@devoxx.fr",
      conferenceUrls = ConferenceUrls(
        faq = "http://www.devoxx.fr/faq/",
        registration = "http://reg.devoxx.be",
        confWebsite = "http://www.devoxx.be/",
        cfpHostname = Play.current.configuration.getString("cfp.hostname").getOrElse("unknown.cfp.hostname")
      ),
      timing = ConferenceTiming(
        datesI18nKey = "devoxxbe2014.dates",
        speakersPassDuration = 5,
        firstDayFr = "10 novembre",
        firstDayEn = "november 10th",
        datesFr = "du 10 au 14 Novembre 2014",
        datesEn = "from 10th to 14th of November, 2014",
        cfpOpenedOn = DateTime.parse("2014-06-03T00:00:00+02:00"),
        cfpClosedOn = DateTime.parse("2014-07-11T23:59:59+02:00"),
        scheduleAnnouncedOn = DateTime.parse("2014-09-15T00:00:00+02:00")
      ),
      bitbucketProps = BitbucketProperties("bitbucket.username", "bitbucket.token", "bitbucket.issues.url"),
      hosterName = "Clever-cloud", hosterWebsite="http://www.clever-cloud.com/#DevoxxFR",
      hashTag = "#DevoxxFR",

      tracks = List(
        DevoxxTracks.STARTUP,
        DevoxxTracks.SSJ,
        DevoxxTracks.JAVA,
        DevoxxTracks.MOBILE,
        DevoxxTracks.ARCHISEC,
        DevoxxTracks.METHOD_DEVOPS,
        DevoxxTracks.FUTURE,
        DevoxxTracks.LANG,
        DevoxxTracks.CLOUD,
        DevoxxTracks.WEB
      ),
      proposalTypes = List(
        DevoxxProposalTypes.CONF,
        DevoxxProposalTypes.UNI,
        DevoxxProposalTypes.TIA,
        DevoxxProposalTypes.LAB,
        DevoxxProposalTypes.QUICK,
        DevoxxProposalTypes.BOF,
        DevoxxProposalTypes.KEY,
        DevoxxProposalTypes.START,
        DevoxxProposalTypes.OTHER
      ),
      rooms = List(
        DevoxxRooms.KEYNOTE_SEINE,
        DevoxxRooms.SEINE_A,
        DevoxxRooms.SEINE_B,
        DevoxxRooms.SEINE_C,
        DevoxxRooms.AUDITORIUM,
        DevoxxRooms.ELLA_FITZGERALD,
        DevoxxRooms.ELLA_FITZGERALD_AB,
        DevoxxRooms.ELLA_FITZGERALD_AB_TH,
        DevoxxRooms.LOUIS_ARMSTRONG_AB,
        DevoxxRooms.LOUIS_ARMSTRONG_AB_TH,
        DevoxxRooms.LOUIS_ARMSTRONG_CD,
        DevoxxRooms.LOUIS_ARMSTRONG_CD_TH,
        DevoxxRooms.MILES_DAVIS_A,
        DevoxxRooms.MILES_DAVIS_A_TH,
        DevoxxRooms.MILES_DAVIS_B_TH,
        DevoxxRooms.MILES_DAVIS_C_TH,

        DevoxxRooms.MILES_DAVIS,
        DevoxxRooms.DUKE_ELLINGTON,
        DevoxxRooms.FOYER_BAS,
        DevoxxRooms.LABO,
        Room.OTHER
      ),
      contentBlocks = ContentBlocks(
        _homeIndexFirstRightBlock = (lang: Lang, confDesc: ConferenceDescriptor) => devoxxPreviousVideosHomeRightBlock()(lang, confDesc),
        _homeIndexFooterBlock = (lang: Lang, confDesc: ConferenceDescriptor) => emptyContent()(),
        _proposalHelpBlock = (lang: Lang, confDesc: ConferenceDescriptor) => devoxxProposalHelpBlock()(lang, confDesc),
        _previewProposalFooterBlock = (
             lang: Lang, confDesc: ConferenceDescriptor,
             htmlSummary: String, privateMessage: String,
             newProposal: Form[models.Proposal], currentUser: String) => devoxxProposalsGuideFooterBlock(htmlSummary, privateMessage, newProposal, currentUser)(lang, confDesc),
        showSponsorProposalCheckbox = true,
        sponsorProposalType = DevoxxProposalTypes.CONF
      )
    )

    def isCFPOpen: Boolean = {
      current().timing.cfpOpenedOn.isBeforeNow && current().timing.cfpClosedOn.isAfterNow
    }
}


// These are properties not i18n-ed used in various places in the app
trait ConferenceDescriptorImplicit {
  implicit def conferenceDescriptor: ConferenceDescriptor = ConferenceDescriptor.current()
}

package models

import play.api.Play
import java.util.concurrent.Callable
import views.html.main
import play.templates.{Format, BaseScalaTemplate}
import play.api.templates.{Html, HtmlFormat}
import views.html.Application.{footerBlockDevoxx, firstRightBlockDevoxx}
import play.api.i18n.Lang
import java.util.Date
import org.joda.time.{LocalDate, DateTime}

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
    var homeIndexFirstRightBlock: (Lang, ConferenceDescriptor) => HtmlFormat.Appendable,
    var homeIndexFooterBlock: (Lang, ConferenceDescriptor) => HtmlFormat.Appendable
)

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
    var contentBlocks: ContentBlocks
)
object ConferenceDescriptor {
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
        Track("startup", "startup.label", "/assets/images/track/startup.png", "track.startup.title", "track.startup.desc"),
        Track("ssj", "ssj.label", "http://devoxx.be/images/tracks/95ddefdd.icon_javaee.png", "track.ssj.title", "track.ssj.desc"),
        Track("java", "java.label", "http://devoxx.be/images/tracks/aae8d181.icon_javase.png", "track.java.title", "track.java.desc"),
        Track("mobile", "mobile.label", "http://devoxx.be/images/tracks/1d2c40dd.icon_mobile.png", "track.mobile.title", "track.mobile.desc"),
        Track("archisec", "archisec.label", "http://devoxx.be/images/tracks/9943da91.icon_architecture.png", "track.archisec.title", "track.archisec.desc"),
        Track("methodevops", "methodevops.label", "http://devoxx.be/images/tracks/3ec02a75.icon_methology.png", "track.methodevops.title", "track.methodevops.desc"),
        Track("future", "future.label", "http://devoxx.be/images/tracks/a20b9b0a.icon_future.png", "track.future.title", "track.future.desc"),
        Track("lang", "lang.label", "http://devoxx.be/images/tracks/6caef5cf.icon_alternative.png", "track.lang.title", "track.lang.desc"),
        Track("cloud", "cloud.label", "http://devoxx.be/images/tracks/eca0b0a1.icon_cloud.png", "track.cloud.title", "track.cloud.desc"),
        Track("web", "web.label", "http://devoxx.be/images/tracks/cd5c36df.icon_web.png", "track.web.title", "track.web.desc")
      ),
      contentBlocks = ContentBlocks(
        homeIndexFirstRightBlock = (lang: Lang, confDesc: ConferenceDescriptor) => firstRightBlockDevoxx(lang, confDesc),
        homeIndexFooterBlock = (lang: Lang, confDesc: ConferenceDescriptor) => footerBlockDevoxx(lang, confDesc)
      )
    )

  val isCFPOpen: Boolean = {
    current().timing.cfpOpenedOn.isBeforeNow && current().timing.cfpClosedOn.isAfterNow
  }
}


// These are properties not i18n-ed used in various places in the app
trait ConferenceDescriptorImplicit {
  implicit def conferenceDescriptor: ConferenceDescriptor = ConferenceDescriptor.current()
}

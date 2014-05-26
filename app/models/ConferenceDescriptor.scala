package models

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
    var speakersPassDuration: Integer
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
    var hashTag: String
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
        cfpHostname = "cfp.devoxx.be"
      ),
      timing = ConferenceTiming(
        datesI18nKey = "devoxxbe2014.dates",
        speakersPassDuration = 5
      ),
      bitbucketProps = BitbucketProperties("bitbucket.username", "bitbucket.token", "bitbucket.issues.url"),
      hosterName = "Clever-cloud", hosterWebsite="http://www.clever-cloud.com/#DevoxxFR",
      hashTag = "#DevoxxFR"
    )
}


// These are properties not i18n-ed used in various places in the app
trait ConferenceDescriptorImplicit {
  implicit def conferenceDescriptor: ConferenceDescriptor = ConferenceDescriptor.current()
}

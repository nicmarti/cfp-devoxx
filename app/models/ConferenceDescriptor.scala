package models

case class ConferenceDescriptor(
    var eventCode: String, var confUrlCode: String,
    var shortYearlyName: String, var longYearlyName: String,
    var shortName: String, var longName: String,
    var frLangEnabled: Boolean,
    var longSplittedName_whiteStart: String, var longSplittedName_colored: String, var longSplittedName_whiteEnd: String,
    var fromEmail: String, var bccEmail: Option[String], var bugReportRecipient: String,
    var faqUrl: String, var registrationUrl: String, var cfpHostname: String,
    var confDatesI18nKey: String,
    var bitbucketIssuesUrlConfProp: String, var bitbucketCredentialsUsernameConfProp: String, var bitbucketCredentialsTokenConfProp: String
)
object ConferenceDescriptor {
    def current() = ConferenceDescriptor(
      eventCode = "DevoxxBe2014",
      // You will need to update conf/routes files with this code if modified
      confUrlCode = "devoxxfr2014",
      shortYearlyName = "DevoxxBe 2014",
      longYearlyName = "Devoxx Belgium 2014",
      shortName = "DevoxxBe",
      longName = "Devoxx Belgium",
      frLangEnabled = false,
      longSplittedName_whiteStart="Devox", longSplittedName_colored="x", longSplittedName_whiteEnd="Belgium",
      fromEmail = "program@devoxx.com",
      bccEmail = Option("nicolas.martignole@devoxx.fr"),
      bugReportRecipient = "nicolas.martignole@devoxx.fr",
      faqUrl = "http://www.devoxx.fr/faq/",
      registrationUrl = "http://reg.devoxx.be",
      cfpHostname = "cfp.devoxx.be",
      confDatesI18nKey = "devoxxbe2014.dates",
      bitbucketIssuesUrlConfProp = "bitbucket.issues.url",
      bitbucketCredentialsUsernameConfProp = "bitbucket.username",
      bitbucketCredentialsTokenConfProp = "bitbucket.token"
    )
}
// These are properties not i18n-ed used in various places in the app
trait ConferenceDescriptorImplicit {
  implicit def conferenceDescriptor: ConferenceDescriptor = ConferenceDescriptor.current()
}

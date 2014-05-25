package models

case class ConferenceDescriptor(
    var shortName: String, var longName: String,
    var fromEmail: String, var bccEmail: Option[String], var bugReportRecipient: String
)
object ConferenceDescriptor {
    def current() = ConferenceDescriptor(
      shortName = "Devoxx 2014",
      longName = "Devoxx 2014",
      fromEmail = "program@devoxx.com",
      bccEmail = Option("nicolas.martignole@devoxx.fr"),
      bugReportRecipient = "nicolas.martignole@devoxx.fr"
    )
}
// These are properties not i18n-ed used in various places in the app
trait ConferenceDescriptorImplicit {
  implicit def conferenceDescriptor: ConferenceDescriptor = ConferenceDescriptor.current()
}

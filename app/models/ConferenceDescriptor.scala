package models

case class ConferenceDescriptor(var shortName: String, var longName: String)
object ConferenceDescriptor {
    def current() = ConferenceDescriptor("DevoxxFr 2014", "Devoxx France 2014")
}
// These are properties not i18n-ed used in various places in the app
trait ConferenceDescriptorImplicit {
  implicit def conferenceDescriptor: ConferenceDescriptor = ConferenceDescriptor.current()
}

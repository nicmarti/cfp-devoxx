package controllers

import models.Digest

/**
 * @author Stephan Janssen
 */
object DigestController extends SecureCFPController {

    def update() = SecuredAction(IsMemberOf("cfp")) {
        implicit request =>
            val newDigestValue = request.request.getQueryString("value")

            if (newDigestValue.isDefined) {
                val uuid = request.webuser.uuid
                Digest.update(uuid, newDigestValue.get)
                Ok(Digest.message(uuid))
            } else {
                BadRequest("Digest value not defined")
            }
    }

    def setFilter() = SecuredAction(IsMemberOf("cfp")) {
        implicit request =>
            val trackFilters = request.request.getQueryString("value")

            if (trackFilters.isDefined) {
                val uuid = request.webuser.uuid
                Ok(Digest.setTrackFilter(uuid, trackFilters.get))
            } else {
                BadRequest("Track value(s) not defined")
            }
    }

    def getFilter() = SecuredAction(IsMemberOf("cfp")) {
        implicit request =>
            Ok(Digest.getTrackFilter(request.webuser.uuid).getOrElse(""))
    }
}

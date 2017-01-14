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

    def removeFilter() = SecuredAction(IsMemberOf("cfp")) {
        implicit request =>
            val trackFilter = request.request.getQueryString("value")

            if (trackFilter.isDefined) {
                val uuid = request.webuser.uuid
                Digest.delTrackFilter(uuid, trackFilter.get)
                Gone
            } else {
                BadRequest("Track value not defined")
            }
    }


    def addFilter() = SecuredAction(IsMemberOf("cfp")) {
        implicit request =>
            val trackFilter = request.request.getQueryString("value")

            if (trackFilter.isDefined) {
                val uuid = request.webuser.uuid
                Digest.addTrackFilter(uuid, trackFilter.get)
                Ok
            } else {
                BadRequest("Track value(s) not defined")
            }
    }

    def getFilter() = SecuredAction(IsMemberOf("cfp")) {
        implicit request =>
            Ok(Digest.getTrackFilters(request.webuser.uuid).toString())
    }
}

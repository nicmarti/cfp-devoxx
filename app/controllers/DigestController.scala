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
                Ok
            } else {
                BadRequest("Digest value not defined")
            }
    }
}

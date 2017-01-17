package controllers

import play.api.mvc.{Action, Controller}
import sun.misc.BASE64Decoder
import scala.util.control.Exception._
import scala.concurrent.Future

/**
  * @author Stephan Janssen
  */
object BasicAuthentication extends Controller {

  def apply[A](userExists: (String, String) => Boolean)(action: Action[A]): Action[A] =

    Action.async(action.parser) { request =>

      request.headers.get("Authorization").map { authorization =>
        val decoder = new BASE64Decoder()
        authorization.split(" ").drop(1).headOption.filter { encoded =>
          val authInfo = new String(new BASE64Decoder().decodeBuffer(encoded)).split(":").toList

          allCatch.opt {
            val (email, password) = (authInfo.head, authInfo(1))
            userExists(email, password)
          } getOrElse false
        }
      }.map(_ => action(request)).getOrElse {
        Future.successful(Unauthorized("Authentication Failed"))
      }
    }
}
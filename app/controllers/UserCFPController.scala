/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2013 Association du Paris Java User Group.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package controllers

import play.api.mvc._

import play.api.i18n.Messages
import scala.concurrent.Future
import play.api.mvc.SimpleResult
import play.api.libs.Crypto
import javax.crypto.IllegalBlockSizeException
import models.Webuser

/**
 * A Secure controller that checks if a user is authenticated or not.
 *
 * @author : nmartignole
 */
trait UserCFPController extends Controller {

  /**
   * A secured action.  If there is no user in the session the request is redirected
   * to the login page
   */
  object SecuredAction extends SecuredActionBuilder[SecuredRequest[_]] {
    /**
     * Creates a secured action
     */
    def apply[A]() = new SecuredActionBuilder[A](None)

    /**
     * Creates a secured action
     * @param authorize an Authorize object that checks if the user is authorized to invoke the action
     */
    def apply[A](authorize: Authorization) = new SecuredActionBuilder[A](Some(authorize))
  }

  /**
   * A builder for secured actions
   *
   * @param authorize an Authorize object that checks if the user is authorized to invoke the action
   * @tparam A for action
   */
  class SecuredActionBuilder[A](authorize: Option[Authorization] = None) extends ActionBuilder[({type R[A] = SecuredRequest[A]})#R] {

    def invokeSecuredBlock[A](authorize: Option[Authorization],
                              request: Request[A],
                              block: SecuredRequest[A] => Future[SimpleResult]): Future[SimpleResult] = {
      implicit val req = request
      val result = for (
        authenticator <- SecureCFPController.findAuthenticator;
        user <- SecureCFPController.lookupWebuser(authenticator)
      ) yield {
        if (authorize.isEmpty || authorize.get.isAuthorized(user)) {
          block(SecuredRequest(user, request))
        } else {
          Future.successful {
            Redirect(routes.Application.homeVisitor()).flashing("error" -> "Cannot access this resource, your profile does not belong to this security group")
          }
        }
      }

      result.getOrElse({
        val response = {
          Redirect(routes.Application.homeVisitor()).flashing("error" -> Messages("Please authenticate or sign-up."))
        }
        Future.successful(response)
      })
    }

    def invokeBlock[A](request: Request[A], block: SecuredRequest[A] => Future[SimpleResult]) =
      invokeSecuredBlock(authorize, request, block)
  }

}

object UserCFPController {

  def findAuthenticator(implicit request: RequestHeader): Option[String] = {
    try {
      val res = request.session.get("uuid").orElse(request.cookies.get("cfp_rm").map(v => Crypto.decryptAES(v.value)))
      res
    } catch {
      case _: IllegalBlockSizeException => None
      case _: Exception => None
    }
  }

  def lookupWebuser(uuid: String): Option[Webuser] = {
    Webuser.findByUUID(uuid)
  }

  def isLoggedIn(implicit request: RequestHeader): Boolean = {
    findAuthenticator.isDefined
  }

  def getCurrentUser(implicit request:RequestHeader):Option[Webuser]={
    findAuthenticator.flatMap(uuid => lookupWebuser(uuid))
  }

}


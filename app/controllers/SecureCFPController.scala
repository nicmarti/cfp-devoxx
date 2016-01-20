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

import javax.crypto.IllegalBlockSizeException

import models.Webuser
import play.api.i18n.Messages
import play.api.libs.Crypto
import play.api.libs.json.Json
import play.api.mvc.{SimpleResult, _}

import scala.concurrent.Future


/**
  * A complex Secure controller, compatible with Play 2.2.x new EssentialAction.
  * I used SecureSocial as a starting point, then adapted this code to my own use-case
  *
  * @author : nmartignole
  */

/**
  * A request that adds the User for the current call
  */
case class SecuredRequest[A](webuser: Webuser, request: Request[A]) extends WrappedRequest(request)

/**
  * A request that adds the User for the current call
  */
case class RequestWithUser[A](webuser: Option[Webuser], request: Request[A]) extends WrappedRequest(request)

/**
  * Defines an Authorization for the CFP Webuser
  */
trait Authorization {
  def isAuthorized(webuser: Webuser): Boolean
}

/**
  * Checks if user is member of a security group
  */
case class IsMemberOf(securityGroup: String) extends Authorization {
  def isAuthorized(webuser: Webuser): Boolean = {
    Webuser.isMember(webuser.uuid, securityGroup)
  }
}

/**
  * Check if a user belongs to one of the specified groups.
  */
case class IsMemberOfGroups(groups: List[String]) extends Authorization {
  def isAuthorized(webuser: Webuser): Boolean = {
    groups.exists(securityGroup => Webuser.isMember(webuser.uuid, securityGroup))
  }
}

trait SecureCFPController extends Controller {


  protected val notAuthenticatedJson = Unauthorized(Json.toJson(Map("error" -> "Credentials required"))).as(JSON)
  protected val notAuthorizedJson = Forbidden(Json.toJson(Map("error" -> "Not authorized"))).as(JSON)

  def notAuthenticatedResult[A](implicit request: Request[A]): Future[SimpleResult] = {
    Future.successful {
      render {
        case Accepts.Json() => notAuthenticatedJson
        case Accepts.Html() => Redirect(routes.Application.home).
          flashing("error" -> Messages("error.loginRequired"))
          .withNewSession
        case _ => Unauthorized("Credentials required")
      }
    }
  }

  def notAuthorizedResult[A](implicit request: Request[A]): Future[SimpleResult] = {
    Future.successful {
      render {
        case Accepts.Json() => notAuthorizedJson
        case Accepts.Html() => {
          Redirect(routes.Application.index()).flashing("error" -> "Not Authorized")
        }
        case _ => Forbidden("Not authorized")
      }
    }
  }

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
      *
      * @param authorize an Authorize object that checks if the user is authorized to invoke the action
      */
    def apply[A](authorize: Authorization) = new SecuredActionBuilder[A](Some(authorize))

    /**
      * Creates a secured Action and redirect the user to the route if not authenticated
      */
    def apply[A](authorize: Authorization, routes: String) = new SecuredActionBuilder[A](Some(authorize))
  }

  /**
    * A builder for secured actions
    *
    * @param authorize an Authorize object that checks if the user is authorized to invoke the action
    * @tparam A for action
    */
  class SecuredActionBuilder[A](authorize: Option[Authorization] = None, redirect: Option[Call] = None) extends ActionBuilder[({type R[A] = SecuredRequest[A]})#R] {

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
          notAuthorizedResult(request)
        }
      }

      result.getOrElse({
        notAuthenticatedResult(request)
      })
    }

    def invokeBlock[A](request: Request[A], block: SecuredRequest[A] => Future[SimpleResult]) =
      invokeSecuredBlock(authorize, request, block)
  }


  /**
    * An action that adds the current user in the request if it's available.
    */
  object UserAwareAction extends ActionBuilder[RequestWithUser] {
    protected def invokeBlock[A](request: Request[A],
                                 block: (RequestWithUser[A]) => Future[SimpleResult]): Future[SimpleResult] = {
      implicit val req = request
      val user = for (
        authenticator <- SecureCFPController.findAuthenticator;
        user <- SecureCFPController.lookupWebuser(authenticator)
      ) yield {
        user
      }
      block(RequestWithUser(user, request))
    }
  }

  /**
    * Get the current logged in user.  This method can be used from public actions that need to
    * access the current user if there's any
    */
  def currentUser[A](implicit request: RequestHeader): Option[Webuser] = {
    request match {
      case securedRequest: SecuredRequest[_] => Some(securedRequest.webuser)
      case userAware: RequestWithUser[_] => userAware.webuser
      case _ => for (
        authenticator <- SecureCFPController.findAuthenticator;
        webuser <- SecureCFPController.lookupWebuser(authenticator)
      ) yield {
        webuser
      }
    }
  }
}

object SecureCFPController {

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

  def hasAccessToCFP(implicit request: RequestHeader): Boolean = {
    findAuthenticator.exists(uuid =>
      Webuser.isSpeaker(uuid)
    )
  }

  def hasAccessToCFPAdmin(implicit request: RequestHeader): Boolean = {
    findAuthenticator.exists(uuid =>
      Webuser.hasAccessToCFP(uuid)
    )
  }

  def hasAccessToAdmin(implicit request: RequestHeader): Boolean = {
    findAuthenticator.exists(uuid =>
      Webuser.hasAccessToAdmin(uuid)
    )
  }

  def hasAccessToGoldenTicket(implicit request: RequestHeader): Boolean = {
    findAuthenticator.exists(uuid =>
      Webuser.hasAccessToGoldenTicket(uuid)
    )
  }

  def getCurrentUser(implicit request: RequestHeader): Option[Webuser] = {
    findAuthenticator.flatMap(uuid => lookupWebuser(uuid))
  }

}

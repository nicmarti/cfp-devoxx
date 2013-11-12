/**
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

import models._
import play.api.mvc._
import play.api.Logger
import play.api.Play
import play.api.libs.Crypto
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation.Constraints._
import play.api.libs.ws.WS
import play.api.libs.json.Json
import org.apache.commons.lang3.RandomStringUtils
import play.api.data.format.Formats._
import notifiers.Mails
import org.apache.commons.codec.binary.Base64
import java.security.SecureRandom
import java.math.BigInteger
import play.api.i18n.Messages

import play.api.libs.ws._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import org.apache.commons.codec.digest.DigestUtils


/**
 * Signup and Signin.
 *
 * Author: nicolas
 * Created: 27/09/2013 09:59
 */
object Authentication extends Controller {
  val loginForm = Form(tuple("email" -> (email verifying nonEmpty), "password" -> nonEmptyText))

  def prepareSignup = Action {
    implicit request =>
      Ok(views.html.Authentication.prepareSignup(newWebuserForm))
  }

  def forgetPassword = Action {
    Ok(views.html.Authentication.forgetPassword(emailForm))
  }

  val emailForm = Form("email" -> (email verifying nonEmpty))

  def doForgetPassword() = Action {
    implicit request =>
      emailForm.bindFromRequest.fold(
        errorForm => BadRequest(views.html.Authentication.forgetPassword(errorForm)),
        validEmail => {
          Mails.sendResetPasswordLink(validEmail, routes.Authentication.resetPassword(Crypto.sign(validEmail.toLowerCase.trim), new String(Base64.encodeBase64(validEmail.toLowerCase.trim.getBytes("UTF-8")), "UTF-8")
          ).absoluteURL())
          Redirect(routes.Application.index()).flashing("success" -> Messages("forget.password.confirm"))
        })
  }

  def resetPassword(t: String, a: String) = Action {
    implicit request =>
      val email = new String(Base64.decodeBase64(a), "UTF-8")
      if (Crypto.sign(email) == t) {
        val futureMaybeWebuser = Webuser.findByEmail(email)
        futureMaybeWebuser.map {
          w =>
            val newPassword = Webuser.changePassword(w) // it is generated
            Redirect(routes.Application.index()).flashing("success" -> ("Your new password is " + newPassword + " (case-sensitive)"))
        }.getOrElse {
          Redirect(routes.Application.index()).flashing("error" -> "Sorry, this email is not registered in your system.")
        }
      } else {
        Redirect(routes.Application.index()).flashing("error" -> "Sorry, we could not validate your authentication token. Are you sure that this email is registered?")
      }
  }

  def login = Action {
    implicit request =>
      loginForm.bindFromRequest.fold(
        invalidForm => BadRequest(views.html.Application.home(invalidForm)),
        validForm =>
          Webuser.checkPassword(validForm._1, validForm._2) match {
            case true =>
              Redirect(routes.CallForPaper.homeForSpeaker).withSession("email" -> validForm._1)
            case false =>
              Redirect(routes.Application.home).flashing("error" -> Messages("login.error"))
          }
      )
  }

  def githubLogin = Action {
    implicit request =>
      Play.current.configuration.getString("github.client_id").map {
        clientId: String =>
          val redirectUri = routes.Authentication.callbackGithub.absoluteURL()
          val gitUrl = "https://github.com/login/oauth/authorize?client_id=" + clientId + "&scope=user&state=" + Crypto.sign("ok") + "&redirect_uri=" + redirectUri
          Redirect(gitUrl)
      }.getOrElse {
        InternalServerError("github.client_id is not set in application.conf")
      }
  }

  //POST https://github.com/login/oauth/access_token
  val oauthForm = Form(tuple("code" -> text, "state" -> text))
  val accessTokenForm = Form("access_token" -> text)

  def callbackGithub = Action {
    implicit request =>
      oauthForm.bindFromRequest.fold(invalidForm => {
        BadRequest(views.html.Application.home(loginForm)).flashing("error" -> "Invalid form")
      }, validForm => {
        validForm match {
          case (code, state) if state == Crypto.sign("ok") => {
            val auth = for (clientId <- Play.current.configuration.getString("github.client_id");
                            clientSecret <- Play.current.configuration.getString("github.client_secret")) yield (clientId, clientSecret)
            auth.map {
              case (clientId, clientSecret) => {
                val url = "https://github.com/login/oauth/access_token"
                val wsCall = WS.url(url).post(Map("client_id" -> Seq(clientId), "client_secret" -> Seq(clientSecret), "code" -> Seq(code)))
                Async {
                  wsCall.map {
                    result =>
                      result.status match {
                        case 200 => {
                          val b = result.body
                          try {
                            val accessToken = b.substring(b.indexOf("=") + 1, b.indexOf("&"))
                            Redirect(routes.Authentication.createFromGithub()).withSession("access_token" -> accessToken)
                          } catch {
                            case e: IndexOutOfBoundsException => {
                              Redirect(routes.Application.index()).flashing("error" -> "access token not found in query string")
                            }
                          }
                        }
                        case _ => {
                          Redirect(routes.Application.index()).flashing("error" -> ("Could not complete Github OAuth, got HTTP response" + result.status + " " + result.body))
                        }
                      }
                  }
                }
              }
            }.getOrElse {
              InternalServerError("github.client_secret is not configured in application.conf")
            }
          }
          case other => BadRequest(views.html.Application.home(loginForm)).flashing("error" -> "Invalid state code")
        }
      })
  }

  def showAccessToken = Action {
    implicit request =>
      Ok(request.session.get("access_token").getOrElse("No access token in your current session"))
  }

  val newWebuserForm: Form[Webuser] = Form(
    mapping(
      "email" -> (email verifying nonEmpty),
      "firstName" -> nonEmptyText,
      "lastName" -> nonEmptyText
    )(Webuser.createSpeaker)(Webuser.unapplyForm))

  val webuserForm = Form(
    mapping(
      "email" -> (email verifying nonEmpty),
      "firstName" -> nonEmptyText,
      "lastName" -> nonEmptyText,
      "password" -> nonEmptyText,
      "profile" -> nonEmptyText
    ) {
      (email, firstName, lastName, password, profile) =>
        Webuser(email,
          firstName,
          lastName,
          password,
          profile)
    } {
      w =>
        Some(
          (w.email,
            w.firstName,
            w.lastName,
            w.password,
            w.profile)
        )
    }
  )

  def createFromGithub = Action {
    implicit request =>

      val url = "https://api.github.com/user?access_token=" + request.session.get("access_token").getOrElse("")
      val futureResult = WS.url(url).withHeaders("User-agent" -> "nicmarti devoxxfr", "Accept" -> "application/json").get()
      val lang = request.headers.get("Accept-Language")
      Async {
        futureResult.map {
          result =>
            result.status match {
              case 200 => {
                //               Ok(result.body).as("application/json")
                val json = Json.parse(result.body)
                val resultParse = for (email <- json.\("email").asOpt[String].toRight("email not found").right;
                                       name <- json.\("name").asOpt[String].toRight("name not found").right;
                                       bio <- json.\("bio").asOpt[String].toRight("bio not found").right) yield (email, name, bio)

                resultParse.fold(missingField => BadRequest("Sorry, cannot import your github profile due to : [" + missingField + "]"),
                  validFields => {
                    validFields match {
                      case (emailS, nameS, bioS) =>
                        val avatarUrl = Some("http://www.gravatar.com/avatar/" + DigestUtils.md5Hex(emailS))
                        val company = json.\("company").asOpt[String]
                        val blog = json.\("blog").asOpt[String]

                        // Try to lookup the speaker
                        Webuser.findByEmail(emailS).map {
                          w =>
                            Redirect(routes.CallForPaper.homeForSpeaker()).withSession("email" -> w.email)
                        }.getOrElse {
                          // Create a new one but ask for confirmation
                          val (firstName, lastName) = if (nameS.indexOf(" ") != -1) {
                            (nameS.substring(0, nameS.indexOf(" ")), nameS.substring(nameS.indexOf(" ") + 1))
                          } else {
                            (nameS, nameS)
                          }
                          val w = Webuser(emailS, firstName, lastName, RandomStringUtils.randomAlphanumeric(7), "speaker")
                          val s = Speaker(emailS, bioS, lang, None, avatarUrl, company, blog)
                          Ok(views.html.Authentication.confirmImport(newWebuserForm.fill(w), CallForPaper.speakerForm.fill(s)))
                        }
                    }
                  })

              }
              case other => {
                play.Logger.error("Unable to complete call " + result.status + " " + result.statusText + " " + result.body)
                BadRequest("Unable to complete the Github User API call")
              }
            }
        }
      }
  }

  def saveNewSpeaker = Action {
    implicit request =>
      newWebuserForm.bindFromRequest.fold(
        invalidForm => BadRequest(views.html.Authentication.prepareSignup(invalidForm)),
        validForm => {
          Webuser.saveNewSpeakerEmailNotValidated(validForm)
          Mails.sendValidateYourEmail(validForm.email, routes.Authentication.validateYourEmail(Crypto.sign(validForm.email.toLowerCase.trim), new String(Base64.encodeBase64(validForm.email.toLowerCase.trim.getBytes("UTF-8")), "UTF-8")).absoluteURL())
          Created(views.html.Authentication.created(validForm.email))
        }
      )
  }

  def validateYourEmail(t: String, a: String) = Action {
    implicit request =>
      val email = new String(Base64.decodeBase64(a), "UTF-8")
      if (Crypto.sign(email) == t) {
        val futureMaybeWebuser = Webuser.findNewUserByEmail(email)
        futureMaybeWebuser.map {
          webuser =>
            Webuser.validateEmailForSpeaker(webuser) // it is generated
            SpeakerHelper.save(SpeakerHelper.createSpeaker(email, "", None, None, Some("http://www.gravatar.com/avatar/" + webuser.gravatarHash), None, None))
            Mails.sendAccessCode(webuser.email, webuser.password)
            Redirect(routes.CallForPaper.editProfile()).flashing("success" -> ("Your account has been validated. Your new password is " + webuser.password + " (case-sensitive)")).withSession("email" -> email)
        }.getOrElse {
          Redirect(routes.Application.index()).flashing("error" -> "Sorry, this email is not registered in your system.")
        }
      } else {
        Redirect(routes.Application.index()).flashing("error" -> "Sorry, we could not validate your authentication token. Are you sure that this email is registered?")
      }
  }

  def validateImportedSpeaker = Action {
    implicit request =>
      newWebuserForm.bindFromRequest.fold(
        invalidForm => BadRequest(views.html.Authentication.confirmImport(invalidForm, CallForPaper.speakerForm.bindFromRequest)),
        validWebuser => {
          Webuser.validateEmailForSpeaker(validWebuser)
          CallForPaper.speakerForm.bindFromRequest.fold(
            invalidForm2 => BadRequest(views.html.Authentication.confirmImport(newWebuserForm.bindFromRequest, invalidForm2)),
            validSpeakerForm => {
              SpeakerHelper.save(validSpeakerForm)
              Ok(views.html.Authentication.validateImportedSpeaker(validWebuser.email, validWebuser.password)).withSession("email" -> validWebuser.email)
            }
          )
        }
      )
  }


  // See Google documentation https://developers.google.com/accounts/docs/OAuth2Login
  def googleLogin = Action {
    implicit request =>
      Play.current.configuration.getString("google.client_id").map {
        clientId: String =>
          val redirectUri = routes.Authentication.callbackGoogle().absoluteURL()
          val state = new BigInteger(130, new SecureRandom()).toString(32)
          val gitUrl = "https://accounts.google.com/o/oauth2/auth?client_id=" + clientId + "&scope=openid%20email%20profile&state=" + Crypto.sign(state) + "&redirect_uri=" + redirectUri + "&response_type=code"
          Redirect(gitUrl).withSession("state" -> state)
      }.getOrElse {
        InternalServerError("google.client_id is not set in application.conf")
      }
  }

  implicit val googleFormat = Json.format[GoogleToken]

  def callbackGoogle = Action {
    implicit request =>
      println("request callbackGoogle")
      oauthForm.bindFromRequest.fold(invalidForm => {
        BadRequest(views.html.Application.home(loginForm)).flashing("error" -> "Invalid form")
      }, validForm => {
        validForm match {
          case (code, state) if state == Crypto.sign(session.get("state").getOrElse("")) => {
            val auth = for (clientId <- Play.current.configuration.getString("google.client_id");
                            clientSecret <- Play.current.configuration.getString("google.client_secret")) yield (clientId, clientSecret)
            auth.map {
              case (clientId, clientSecret) => {
                val url = "https://accounts.google.com/o/oauth2/token"
                val redirect_uri = routes.Authentication.callbackGoogle().absoluteURL()
                val wsCall = WS.url(url).withHeaders(("Accept" -> "application/json"), ("User-Agent" -> "Devoxx France CFP nmartignole@gmail.com")).post(Map("client_id" -> Seq(clientId), "client_secret" -> Seq(clientSecret), "code" -> Seq(code), "grant_type" -> Seq("authorization_code"), "redirect_uri" -> Seq(redirect_uri)))
                Async {
                  wsCall.map {
                    result =>
                      result.status match {
                        case 200 => {
                          val b = result.body
                          println("body received from google " + b)
                          val googleToken = Json.parse(result.body).as[GoogleToken]
                          Redirect(routes.Authentication.createFromGoogle).withSession("google_token" -> googleToken.access_token)
                        }
                        case _ => {
                          Redirect(routes.Application.index()).flashing("error" -> ("error with Google OAuth2.0 : got HTTP response " + result.status + " " + result.body))
                        }
                      }
                  }
                }
              }
            }.getOrElse {
              InternalServerError("github.client_secret is not configured in application.conf")
            }
          }
          case other => BadRequest(views.html.Application.home(loginForm)).flashing("error" -> "Invalid state code")
        }
      })
  }

  def createFromGoogle = Action {
    implicit request =>
      request.session.get("google_token").map {
        access_token =>
        //for Google account
          val url = "https://www.googleapis.com/oauth2/v3/userinfo?access_token=" + access_token

          // For Google+ profile
          //val url = "https://www.googleapis.com/plus/v1/people/me?access_token=" + access_token+"&"
          val futureResult = WS.url(url).withHeaders("User-agent" -> "CFP www.devoxx.fr", "Accept" -> "application/json").get()
          val lang = request.headers.get("Accept-Language")
          Async {
            futureResult.map {
              result =>
                result.status match {
                  case 200 => {
                    //Ok(result.body).as("application/json")
                    val json = Json.parse(result.body)

                    val email = json.\("email").as[String]
                    val firstName = json.\("given_name").asOpt[String]
                    val lastName = json.\("family_name").asOpt[String]
                    val blog = json.\("profile").asOpt[String]
                    val photo = json.\("picture").asOpt[String]

                    // Try to lookup the speaker
                    Webuser.findByEmail(email).map {
                      w =>
                        Redirect(routes.CallForPaper.homeForSpeaker()).withSession("email" -> w.email)
                    }.getOrElse {
                      // Create a new one but ask for confirmation
                      val w = Webuser(email, firstName.getOrElse("?"), lastName.getOrElse("?"), RandomStringUtils.randomAlphanumeric(7), "speaker")
                      val s = Speaker(email, "", lang, None, photo, None, blog)
                      Ok(views.html.Authentication.confirmImport(newWebuserForm.fill(w), CallForPaper.speakerForm.fill(s)))
                    }
                  }
                  case other => {
                    play.Logger.error("Unable to complete call " + result.status + " " + result.statusText + " " + result.body)
                    BadRequest("Unable to complete the Github User API call")
                  }
                }
            }
          }

      }.getOrElse {
        Redirect(routes.Application.index()).flashing("error" -> "Your Google Access token has expired, please reauthenticate")
      }
  }

}

case class GoogleToken(access_token: String, token_type: String, expires_in: Long, id_token: String)

/**
 * Provide security features
 */
trait Secured {

  /**
   * Retrieve the connected user email.
   */
  private def username(request: RequestHeader) = request.session.get("email")

  /**
   * Redirect to login if the user in not authorized.
   */
  private def onUnauthorized(request: RequestHeader) = Results.Redirect(routes.Application.index).flashing("error" -> "Unauthorized : you are not authenticated or your session has expired. Please authenticate.")

  /**
   * Action for authenticated users.
   */
  def IsAuthenticated(f: => String => Request[AnyContent] => Result) = {
    Security.Authenticated(username, onUnauthorized) {
      user =>
        Action(request => f(user)(request))
    }
  }

  /**
   * Check if the connected user is a member of this security group.
   */
  def IsMemberOf(securityGroup: String)(f: => String => Request[AnyContent] => Result) = IsAuthenticated {
    email => request =>
      if (Webuser.isMember(email, securityGroup)) {
        f(email)(request)
      } else {
        Results.Forbidden("Sorry, you cannot access this resource. Your email is not a member of "+securityGroup)
      }
  }

}

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

import com.google.api.client.googleapis.auth.oauth2.{GoogleAuthorizationCodeTokenRequest, GoogleCredential, GoogleTokenResponse}
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.oauth2.Oauth2
import com.google.api.services.oauth2.model.{Tokeninfo, Userinfoplus}
import models._
import notifiers.TransactionalEmails
import org.apache.commons.codec.binary.Base64
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.lang3.StringUtils
import org.joda.time.DateTime
import pdi.jwt.{Jwt, JwtAlgorithm}
import play.api.Play
import play.api.Play.current
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation.Constraints._
import play.api.i18n.Messages
import play.api.libs.Crypto
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws._
import play.api.mvc._

import java.math.BigInteger
import java.security.SecureRandom
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
  * Signup and Signin.
  *
  * Author: nicolas martignole
  * Created: 27/09/2013 09:59
  */
object Authentication extends Controller {
  val loginForm = Form(tuple("email" -> (email verifying nonEmpty), "password" -> nonEmptyText))

  def forgetPassword = Action {
    implicit request =>
      Ok(views.html.Authentication.forgetPassword(emailForm))
  }

  val emailForm = Form("email" -> (email verifying(nonEmpty, maxLength(50))))

  val isSecure: Boolean = Play.current.configuration.getBoolean("cfp.activateHTTPS").getOrElse(false)

  def doForgetPassword() = Action {
    implicit request =>
      emailForm.bindFromRequest.fold(
        errorForm => BadRequest(views.html.Authentication.forgetPassword(errorForm)),
        validEmail => {
          if (Webuser.isEmailRegistered(validEmail)) {
            val resetURL = routes.Authentication.resetPassword(Crypto.sign(validEmail.toLowerCase.trim), new String(Base64.encodeBase64(validEmail.toLowerCase.trim.getBytes("UTF-8")), "UTF-8")).absoluteURL(isSecure)
            TransactionalEmails.sendResetPasswordLink(validEmail, resetURL)
            Redirect(routes.Application.index()).flashing("success" -> Messages("forget.password.confirm"))
          } else {
            Redirect(routes.Authentication.forgetPassword()).flashing("error" -> Messages("forget.password.notfound"))
          }
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
            Ok(views.html.Authentication.resetPassword(loginForm.fill((w.email, newPassword)), newPassword))
        }.getOrElse {
          Redirect(routes.Application.index()).flashing("error" -> "Sorry, this email is not registered in your system.")
        }
      } else {
        Redirect(routes.Application.index()).flashing("error" -> "Sorry, we could not validate your authentication token. Are you sure that this email is registered?")
      }
  }

  def login(visitor: Boolean) = Action {
    implicit request =>
      loginForm.bindFromRequest.fold(
        invalidForm => {
          if (visitor)
            BadRequest(views.html.Application.homeVisitor(invalidForm))
          else
            BadRequest(views.html.Application.home(invalidForm))
        },
        validForm =>
          Webuser.checkPassword(validForm._1, validForm._2) match {
            case Some(webUser) if visitor =>
              val cookie = createCookie(webUser)
              Redirect(routes.Publisher.homePublisher()).withSession("uuid" -> webUser.uuid).withCookies(cookie)
            case Some(webUser) =>
              val cookie = createCookie(webUser)
              Redirect(routes.CallForPaper.homeForSpeaker()).flashing("warning" -> Messages("cfp.reminder.proposals")).withSession("uuid" -> webUser.uuid).withCookies(cookie)
            case None if visitor =>
              Redirect(routes.Application.homeVisitor()).flashing("error" -> Messages("login.error"))
            case None =>
              Redirect(routes.Application.home()).flashing("error" -> Messages("login.error"))
          }
      )
  }

  def logout = Action {
    implicit request =>
      val discardingCookie = DiscardingCookie("cfp_rm", "/", None, secure = false)
      Redirect(routes.Application.index()).discardingCookies(discardingCookie).withNewSession
  }

  def githubLogin(visitor: Boolean) = Action {
    implicit request =>
      Play.current.configuration.getString("github.client_id").map {
        clientId: String =>
          val redirectUri = routes.Authentication.callbackGithub(visitor).absoluteURL(isSecure)
          val gitUrl = "https://github.com/login/oauth/authorize?scope=user:email&client_id=" + clientId + "&state=" + Crypto.sign("ok32") + "&redirect_uri=" + redirectUri
          Redirect(gitUrl)
      }.getOrElse {
        InternalServerError("github.client_id is not set in application.conf")
      }
  }

  //POST https://github.com/login/oauth/access_token
  val oauthForm = Form(tuple("code" -> text, "state" -> text, "error" -> optional(text), "error_description" -> optional(text)))
  val accessTokenForm = Form("access_token" -> text)

  def callbackGithub(visitor: Boolean) = Action.async {
    implicit request =>
      oauthForm.bindFromRequest.fold(invalidForm => {
        // code and state are missing or not valid
        Future.successful(InternalServerError(s"Internal error with the return from Github. Error=${invalidForm("error")} Description=${invalidForm("error_description")}"))
      }, {
        case (code, state, _, _) if state == Crypto.sign("ok32") =>
          val auth = for (clientId <- Play.current.configuration.getString("github.client_id");
                          clientSecret <- Play.current.configuration.getString("github.client_secret")) yield (clientId, clientSecret)
          auth.map {
            case (clientId, clientSecret) =>
              val url = "https://github.com/login/oauth/access_token"
              val wsCall = WS.url(url).post(Map("client_id" -> Seq(clientId), "client_secret" -> Seq(clientSecret), "code" -> Seq(code)))
              wsCall.map {
                result =>
                  result.status match {
                    case 200 =>
                      val b = result.body
                      try {
                        val accessToken = b.substring(b.indexOf("=") + 1, b.indexOf("&"))
                        Redirect(routes.Authentication.createFromGithub(visitor)).withSession("access_token" -> accessToken)
                      } catch {
                        case e: IndexOutOfBoundsException =>
                          Redirect(routes.Application.index()).flashing("error" -> "access token not found in query string")
                      }
                    case _ =>
                      Redirect(routes.Application.index()).flashing("error" -> ("Could not complete Github OAuth, got HTTP response" + result.status + " " + result.body))
                  }
              }
          }.getOrElse {
            Future.successful(InternalServerError("github.client_secret is not configured in application.conf"))
          }
        case _ => Future.successful(BadRequest(views.html.Application.home(loginForm)).flashing("error" -> "Invalid state code"))
      })
  }

  val importSpeakerForm = Form(tuple(
    "email" -> email,
    "firstName" -> nonEmptyText(maxLength = 50),
    "lastName" -> nonEmptyText(maxLength = 50),
    "bio" -> nonEmptyText(maxLength = 750),
    "company" -> optional(text),
    "twitter" -> optional(text),
    "blog" -> optional(text),
    "avatarUrl" -> optional(text),
    "qualifications" -> nonEmptyText(maxLength = 750)
  ))

  val newWebuserForm: Form[Webuser] = Form(
    mapping(
      "email" -> (email verifying nonEmpty),
      "firstName" -> nonEmptyText(maxLength = 50),
      "lastName" -> nonEmptyText(maxLength = 50)
    )(Webuser.createSpeaker)(Webuser.unapplyForm))

  val newVisitorForm: Form[Webuser] = Form(
    mapping(
      "email" -> (email verifying nonEmpty),
      "firstName" -> nonEmptyText(maxLength = 50),
      "lastName" -> nonEmptyText(maxLength = 50)
    )(Webuser.createVisitor)(Webuser.unapplyForm))

  val webuserForm = Form(
    mapping(
      "uuid" -> ignored(""),
      "email" -> (email verifying nonEmpty),
      "firstName" -> nonEmptyText(maxLength = 50),
      "lastName" -> nonEmptyText(maxLength = 50),
      "password" -> nonEmptyText(maxLength = 50),
      "profile" -> nonEmptyText
    ) {
      (uuid, email, firstName, lastName, password, profile) =>
        Webuser(uuid,
          email,
          firstName,
          lastName,
          password,
          profile)
    } {
      w =>
        Some(
          (w.uuid,
            w.email,
            w.firstName,
            w.lastName,
            w.password,
            w.profile)
        )
    }
  )

  def createFromGithub(visitor: Boolean) = Action.async {
    implicit request =>

      // Fix https://developer.github.com/changes/2020-02-10-deprecating-auth-through-query-param/
      val url = "https://api.github.com/user"
      val futureResult = WS
        .url(url)
        .withHeaders(
          "User-agent" -> ("CFP " + ConferenceDescriptor.current().conferenceUrls.cfpHostname),
          "Accept" -> "application/json",
          "Authorization" -> s"token ${request.session.get("access_token").getOrElse("?")}"
        )
        .get()
      futureResult.map {
        result =>
          result.status match {
            case 200 =>
              val json = Json.parse(result.body)
              val resultParse = for (email <- json.\("email").asOpt[String].toRight("github.importprofile.error.emailnotfound").right;
                                     name <- json.\("name").asOpt[String].toRight("github.importprofile.error.namenotfound").right)
                yield (email, name)

              resultParse.fold(missingField =>
                Redirect(routes.Application.home()).flashing(
                  "error" -> List("github.importprofile.error", missingField, "github.importprofile.error.advice").map(Messages(_)).mkString(" ")), {
                case (emailS, nameS) =>
                  /* bio : "Recommendation: Do not use this attribute. It is obsolete." http://developer.github.com/v3/ */
                  val bioS = json.\("bio").asOpt[String].getOrElse("")
                  val avatarUrl = Option("http://www.gravatar.com/avatar/" + DigestUtils.md5Hex(emailS))
                  val company = json.\("company").asOpt[String]
                  val blog = json.\("blog").asOpt[String]

                  // Try to lookup the speaker
                  Webuser.findByEmail(emailS).map {
                    w =>
                      val cookie = createCookie(w)
                      if (visitor) {
                        Redirect(routes.Favorites.welcomeVisitor()).withSession("uuid" -> w.uuid).withCookies(cookie)
                      } else {
                        Redirect(routes.CallForPaper.homeForSpeaker()).flashing("warning" -> Messages("cfp.reminder.proposals")).withSession("uuid" -> w.uuid).withCookies(cookie)
                      }
                  }.getOrElse {
                    // Create a new one but ask for confirmation
                    val (firstName, lastName) = if (nameS.indexOf(" ") != -1) {
                      (nameS.substring(0, nameS.indexOf(" ")), nameS.substring(nameS.indexOf(" ") + 1))
                    } else {
                      (nameS, nameS)
                    }

                    if (visitor) {
                      val newWebuser = Webuser.createVisitor(emailS, firstName, lastName)
                      Ok(views.html.Authentication.confirmImportVisitor(newWebuserForm.fill(newWebuser)))
                    } else {
                      val defaultValues = (emailS, firstName, lastName, StringUtils.abbreviate(bioS, 750), company, None, blog, avatarUrl, "No experience")
                      Ok(views.html.Authentication.confirmImport(importSpeakerForm.fill(defaultValues)))
                    }
                  }
              })
            case other =>
              play.Logger.error("Unable to complete call " + result.status + " " + result.statusText + " " + result.body)
              BadRequest("Unable to complete the Github User API call")
          }
      }

  }

  def saveNewSpeaker = Action {
    implicit request =>
      newWebuserForm.bindFromRequest.fold(
        invalidForm => BadRequest(views.html.Authentication.prepareSignup(invalidForm)),
        validForm => {
          Webuser.saveNewWebuserEmailNotValidated(validForm)
          TransactionalEmails.sendValidateYourEmail(validForm.email, routes.Authentication.validateYourEmailForSpeaker(Crypto.sign(validForm.email.toLowerCase.trim), new String(Base64.encodeBase64(validForm.email.toLowerCase.trim.getBytes("UTF-8")), "UTF-8")).absoluteURL(isSecure))
          Ok(views.html.Authentication.created(validForm.email))
        }
      )
  }

  def saveNewVisitor = Action {
    implicit request =>
      newVisitorForm.bindFromRequest.fold(
        invalidForm => BadRequest(views.html.Authentication.prepareSignupVisitor(invalidForm)),
        validForm => {
          Webuser.saveNewWebuserEmailNotValidated(validForm)
          TransactionalEmails.sendValidateYourEmail(validForm.email, routes.Authentication.validateYourEmailForVisitor(Crypto.sign(validForm.email.toLowerCase.trim), new String(Base64.encodeBase64(validForm.email.toLowerCase.trim.getBytes("UTF-8")), "UTF-8")).absoluteURL(isSecure))
          Ok(views.html.Authentication.created(validForm.email))
        }
      )
  }

  def validateYourEmailForSpeaker(t: String, a: String) = Action {
    implicit request =>
      val email = new String(Base64.decodeBase64(a), "UTF-8")
      if (Crypto.sign(email) == t) {
        val futureMaybeWebuser = Webuser.findNewUserByEmail(email)
        futureMaybeWebuser.map {
          webuser =>
            val uuid = Webuser.saveAndValidateWebuser(webuser) // it is generated
            val someLang = request.acceptLanguages.headOption.map(_.code)
            Speaker.save(Speaker.createSpeaker(uuid, email, webuser.lastName, "", someLang, None, Some("http://www.gravatar.com/avatar/" + Webuser.gravatarHash(webuser.email)), None, None, webuser.firstName, "No experience", None))
            TransactionalEmails.sendAccessCode(webuser.email, webuser.password)
            Redirect(routes.CallForPaper.editProfile()).flashing("success" -> ("Your account has been validated. Your new access code is " + webuser.password + " (case-sensitive)")).withSession("uuid" -> webuser.uuid)
        }.getOrElse {
          Redirect(routes.Application.index()).flashing("error" -> "Sorry, this email is not registered in your system.")
        }
      } else {
        Redirect(routes.Application.index()).flashing("error" -> "Sorry, we could not validate your authentication token. Are you sure that this email is registered?")
      }
  }

  def validateYourEmailForVisitor(t: String, a: String) = Action {
    implicit request =>
      val email = new String(Base64.decodeBase64(a), "UTF-8")
      if (Crypto.sign(email) == t) {
        val futureMaybeWebuser = Webuser.findNewUserByEmail(email)
        futureMaybeWebuser.map {
          webuser =>
            val newUUID = Webuser.saveAndValidateWebuser(webuser) // it is generated
            TransactionalEmails.sendAccessCode(webuser.email, webuser.password)
            val cookie = createCookie(webuser)
            Redirect(routes.Favorites.welcomeVisitor()).withSession("uuid" -> newUUID).withCookies(cookie).flashing("success" -> ("Your account has been validated. Your new access code is " + webuser.password + " (case-sensitive)")).withSession("uuid" -> webuser.uuid)
        }.getOrElse {
          Redirect(routes.Application.index()).flashing("error" -> "Sorry, your invitation has expired.")
        }
      } else {
        Redirect(routes.Application.index()).flashing("error" -> "Sorry, we could not validate your authentication token. Are you sure that this email is registered?")
      }
  }

  def validateImportedSpeaker = Action {
    implicit request =>
      importSpeakerForm.bindFromRequest.fold(
        invalidForm => BadRequest(views.html.Authentication.confirmImport(invalidForm)).flashing("error" -> "Please check your profile, invalid webuser."),
        validFormData => {
          val email = validFormData._1
          val firstName = validFormData._2
          val lastName = validFormData._3
          val bio = validFormData._4
          val company = validFormData._5
          val twitter = validFormData._6
          val blog = validFormData._7
          val avatarUrl = validFormData._8
          val qualifications = validFormData._9

          val validWebuser = if (Webuser.isEmailRegistered(email)) {
            // An existing webuser might have been created with a different play.secret key
            // or from a Golden ticket/visitor profile
            val existingUUID = Webuser.getUUIDfromEmail(email).getOrElse(Webuser.generateUUID(email))
            Webuser.findByUUID(existingUUID).getOrElse(Webuser.createSpeaker(email, firstName, lastName))
          } else {
            val newWebuser = Webuser.createSpeaker(email, firstName, lastName)
            Webuser.saveAndValidateWebuser(newWebuser)
            newWebuser
          }

          val lang = request.acceptLanguages.headOption.map(_.code)
          val newSpeaker = Speaker.createSpeaker(validWebuser.uuid, email, validWebuser.lastName, StringUtils.abbreviate(bio, 750), lang, twitter, avatarUrl, company, blog, validWebuser.firstName, qualifications, None)
          Speaker.save(newSpeaker)
          Webuser.addToSpeaker(validWebuser.uuid)

          Ok(views.html.Authentication.validateImportedSpeaker(validWebuser.email, validWebuser.password)).withSession("uuid" -> validWebuser.uuid).withCookies(createCookie(validWebuser))
        }
      )
  }

  def validateImportedVisitor = Action {
    implicit request =>
      newWebuserForm.bindFromRequest.fold(
        invalidForm => BadRequest(views.html.Authentication.confirmImportVisitor(invalidForm)).flashing("error" -> "Please check your profile, invalid webuser."),
        webuserForm => {
          val validWebuser = Webuser.createVisitor(webuserForm.email, webuserForm.firstName, webuserForm.lastName)
          Webuser.saveAndValidateWebuser(validWebuser)
          Ok(views.html.Authentication.validateImportedVisitor(validWebuser.email, validWebuser.password)).withSession("uuid" -> validWebuser.uuid).withCookies(createCookie(validWebuser))
        }
      )
  }

  // See LinkedIn documentation https://developer.linkedin.com/documents/authentication
  def linkedinLogin(visitor: Boolean) = Action {
    implicit request =>
      Play.current.configuration.getString("linkedin.client_id").map {
        clientId: String =>
          val redirectUri = routes.Authentication.callbackLinkedin().absoluteURL(isSecure)
          val state = new BigInteger(130, new SecureRandom()).toString(32)
          val gitUrl = "https://www.linkedin.com/oauth/v2/authorization?client_id=" + clientId + "&scope=r_liteprofile%20r_emailaddress&state=" + Crypto.sign(state) + "&redirect_uri=" + redirectUri + "&response_type=code"
          Redirect(gitUrl).withSession("state" -> state)
      }.getOrElse {
        InternalServerError("linkedin.client_id is not set in application.conf")
      }
  }

  def callbackLinkedin = Action.async {
    implicit request =>
      oauthForm.bindFromRequest.fold(invalidForm => {
        val error = invalidForm("error").value.getOrElse("?")
        val errorDesc = invalidForm("error_description").value.getOrElse("")
        play.Logger.error(s"OAuth2 Error with LinkedIn due to $error / $errorDesc")
        Future.successful(InternalServerError(s"Internal error with the return from LinkedIn OAuth2. Error=${error} Description=${errorDesc}"))
      }, {
        case (code, state, _, _) if state == Crypto.sign(request.session.get("state").getOrElse("")) =>
          val auth = for (clientId <- Play.current.configuration.getString("linkedin.client_id");
                          clientSecret <- Play.current.configuration.getString("linkedin.client_secret")) yield (clientId, clientSecret)
          auth.map {
            case (clientId, clientSecret) =>
              val url = "https://www.linkedin.com/oauth/v2/accessToken"
              val redirect_uri = routes.Authentication.callbackLinkedin().absoluteURL(isSecure)
              val wsCall = WS.url(url).withHeaders("Accept" -> "application/json", "Content-Type" -> "application/x-www-form-urlencoded")
                .post(Map("client_id" -> Seq(clientId), "client_secret" -> Seq(clientSecret), "code" -> Seq(code), "grant_type" -> Seq("authorization_code"), "redirect_uri" -> Seq(redirect_uri)))
              wsCall.map {
                result =>
                  result.status match {
                    case 200 =>
                      val json = Json.parse(result.body)
                      val token = json.\("access_token").as[String]
                      Redirect(routes.Authentication.createFromLinkedin()).withSession("linkedin_token" -> token)
                    case _ =>
                      Redirect(routes.Application.index()).flashing("error" -> ("error with LinkedIn OAuth2.0 : got HTTP response " + result.status + " " + result.body))
                  }
              }
          }.getOrElse {
            Future.successful {
              InternalServerError("linkedin.client_id and linkedin.client_secret are not configured in application.conf")
            }
          }
        case other => Future.successful {
          play.Logger.error("LinkedIn error in callback. Form details=" + other)
          Redirect(routes.Application.index()).flashing("error" -> "Unable to authenticate or to import your profile from LinkedIn. The session has expired, try again.")
        }
      })
  }

  def createFromLinkedin = Action.async {
    implicit request =>
      request.session.get("linkedin_token").map {
        access_token =>
          val urlForEmail = "https://api.linkedin.com/v2/emailAddress?q=members&projection=(elements*(handle~))"
          val futureResult = WS.url(urlForEmail).withHeaders(
            "User-agent" -> ("CFP v2 " + ConferenceDescriptor.current().conferenceUrls.cfpHostname),
            "Accept" -> "application/json",
            "Authorization" -> s"Bearer ${access_token}"
          ).get()

          futureResult.flatMap {
            result =>
              result.status match {
                case 200 =>
                  val json = Json.parse(result.body)
                  val jsValueForEmail: Option[String] = json.\\("elements").seq.headOption
                    .flatMap(j => j.\\("handle~").headOption.map(j2 => j2.\("emailAddress").as[String]))

                  jsValueForEmail match {
                    case Some(email) => {
                      // Try to lookup the speaker
                      Webuser.findByEmail(email).map {
                        w =>
                          val cookie = createCookie(w)
                          Future.successful(Redirect(routes.CallForPaper.homeForSpeaker()).flashing("warning" -> Messages("cfp.reminder.proposals")).withSession("uuid" -> w.uuid).withCookies(cookie))
                      }.getOrElse {
                        val urlForProfile = "https://api.linkedin.com/v2/me"
                        val futureResult2 = WS.url(urlForProfile).withHeaders(
                          "User-agent" -> ("CFP v2 " + ConferenceDescriptor.current().conferenceUrls.cfpHostname),
                          "Accept" -> "application/json",
                          "Authorization" -> s"Bearer ${access_token}"
                        ).get()

                        val resultFinal: Future[Result] = futureResult2.map {
                          r2 =>
                            r2.status match {
                              case 200 =>
                                val json2 = Json.parse(r2.body)
                                val firstName = json2.\("localizedFirstName").asOpt[String]
                                val lastName = json2.\("localizedLastName").asOpt[String]
                                val photo = json2.\("pictureUrl").asOpt[String]
                                val summary = json2.\("summary").asOpt[String]

                                val defaultValues = (email.toString(), firstName.getOrElse("?"), lastName.getOrElse("?"), summary.getOrElse("?"), None, None, None, photo, "No experience")
                                Ok(views.html.Authentication.confirmImport(importSpeakerForm.fill(defaultValues)))
                              case _ =>
                                Redirect(routes.Application.index()).flashing("error" -> "Unable to extract your details from your LinkedIn profile. Please use another OAuth2 provider.")
                            }
                        }
                        resultFinal
                      }
                    }
                    case None => Future.successful(Redirect(routes.Application.index()).flashing("error" -> "Unable to extract your email address from your LinkedIn profile. Please use another OAuth2 provider."))
                  }
                case _ =>
                  play.Logger.error("Unable to complete call " + result.status + " " + result.statusText)
                  Future.successful(BadRequest("Unable to complete the LinkedIn User API call"))
              }
          }
      }.getOrElse {
        Future.successful {
          Redirect(routes.Application.index()).flashing("error" -> "Your LinkedIn Access token has expired, please reauthenticate")
        }
      }
  }

  // See Google documentation https://developers.google.com/accounts/docs/OAuth2Login
  def googleLogin(visitor: Boolean) = Action {
    implicit request =>
      Play.current.configuration.getString("google.client_id").map {
        clientId: String =>
          val redirectUri = routes.Authentication.callbackGoogle().absoluteURL(isSecure)
          val state = new BigInteger(130, new SecureRandom()).toString(32)
          val theUrl = "https://accounts.google.com/o/oauth2/auth?client_id=" + clientId +
            "&scope=openid%20email%20profile" +
            "&state=" + Crypto.sign(state) +
            "&redirect_uri=" + redirectUri +
            "&response_type=code"
          Redirect(theUrl).withSession("state" -> state)
      }.getOrElse {
        InternalServerError("google.client_id is not set in application.conf or GOOGLE_ID environment variable is not set.")
      }
  }

  implicit val googleFormat = Json.format[GoogleToken]

  val googleURL = "https://oauth2.googleapis.com/token"

  private val transport: NetHttpTransport = new NetHttpTransport()
  private val jsonFactory: JacksonFactory = new JacksonFactory()

  val clientId: String = Play.current.configuration.getString("google.client_id").getOrElse("")
  val clientSecret: String = Play.current.configuration.getString("google.client_secret").getOrElse("")

  def callbackGoogle = Action {
    implicit request =>
      oauthForm.bindFromRequest.fold(invalidForm => {
        play.Logger.error(s"Callback eror with google oauth $invalidForm")
        val error: Option[String] = invalidForm.error("error").map(_.message)
        val errorDesc: Option[String] = invalidForm.error("error_description").map(_.message)
        InternalServerError(s"Internal error with the return from Google OAuth2. Error=${
          error
        } Description=${
          errorDesc
        }")

      }, {
        case (code, state, _, _) => {
          if (state != Crypto.sign(request.session.get("state").getOrElse("?"))) {
            BadRequest("Invalid request, state is not valid")
          } else {
            val redirect_uri = routes.Authentication.callbackGoogle().absoluteURL(isSecure)

            val tokenResponse: GoogleTokenResponse = new GoogleAuthorizationCodeTokenRequest(
              transport, jsonFactory, clientId, clientSecret, code, redirect_uri
            ).execute()

            val credential = new GoogleCredential.Builder()
              .setJsonFactory(jsonFactory)
              .setTransport(transport)
              .setClientSecrets(clientId, clientSecret).build()
              .setFromTokenResponse(tokenResponse)

            Redirect(routes.Authentication.createFromGoogle()).withSession("google_token" -> credential.getAccessToken)
          }
        }
      })
  }

  def createFromGoogle = Action {
    implicit request =>
      request.session.get("google_token").map {
        accessToken =>

          val credential: GoogleCredential = new GoogleCredential.Builder()
            .setJsonFactory(jsonFactory)
            .setTransport(transport)
            .setClientSecrets(clientId, clientSecret).build()
            .setAccessToken(accessToken)

          val oauth2: Oauth2 = new Oauth2.Builder(
            transport, jsonFactory, credential).setApplicationName("Lunatech Google Openconnect").build()

          val tokenInfo: Tokeninfo = oauth2.tokeninfo().setAccessToken(credential.getAccessToken).execute()
          val userInfo: Userinfoplus = oauth2.userinfo().get().execute()

          if (tokenInfo.containsKey("error")) {
            play.Logger.error(s"Unable to decrypt Google token, tokenInfo=$tokenInfo")
            InternalServerError("Error with Google Token")
          } else {
            val email = userInfo.getEmail
            val firstName = userInfo.getGivenName
            val lastName = userInfo.getFamilyName
            val photo = userInfo.getPicture
            Webuser.findByEmail(email).map {
              w =>
                val cookie = createCookie(w)
                Redirect(routes.CallForPaper.homeForSpeaker()).flashing("warning" -> Messages("cfp.reminder.proposals")).withSession("uuid" -> w.uuid).withCookies(cookie)
            }.getOrElse {
              val defaultValues = (email, firstName, lastName, "", None, None, None, Option(photo), "No experience")
              Ok(views.html.Authentication.confirmImport(importSpeakerForm.fill(defaultValues)))
            }
          }
      }.getOrElse {
        Redirect(routes.Application.index()).flashing("error" -> "Your Google Access token has expired, please reauthenticate")
      }
  }

  def prepareSignup(visitor: Boolean) = Action {
    implicit request =>
      if (visitor) {
        if (ConferenceDescriptor.isMyDevoxxActive) {
          play.Logger.info("Redirecting to MyDevoxx")
          Redirect(ConferenceDescriptor.myDevoxxURL(),
            Map(
              "redirect_uri" -> Seq(routes.Authentication.jwtCallback(Crypto.sign("secure_callback_" + DateTime.now(ConferenceDescriptor.current().timezone).withMinuteOfHour(0).withSecondOfMinute(0).withMillisOfSecond(0))).absoluteURL(ConferenceDescriptor.isHTTPSEnabled))
            ),
            SEE_OTHER
          )

        } else {
          Ok(views.html.Authentication.prepareSignupVisitor(newVisitorForm))
        }
      } else {
        Ok(views.html.Authentication.prepareSignup(newWebuserForm))
      }
  }

  def jwtCallback(token: String) = Action.async {
    implicit request =>
      if (token == Crypto.sign("secure_callback_" + DateTime.now(ConferenceDescriptor.current().timezone).withMinuteOfHour(0).withSecondOfMinute(0).withMillisOfSecond(0))) {
        request.getQueryString("jwtToken") match {
          case None =>
            Future.successful(Unauthorized("No JWT Token"))
          case Some(jwtToken) =>

            Jwt.decode(jwtToken, ConferenceDescriptor.jwtSharedSecret(), Seq(JwtAlgorithm.HS256)) match {
              case Success(decodedString) =>
                val json = Json.parse(decodedString)
                val firstName = (json \ "firstName").as[String]
                val lastName = (json \ "lastName").as[String]
                val uuid = (json \ "userId").as[String]
                val _email = (json \ "email").as[String]

                // Check if the user is not already a speaker or a valid webuser
                val maybeWebuser = Webuser.findByEmail(_email)

                val (webuser: Webuser, newUUID: String) = maybeWebuser match {
                  case w if w.isDefined =>
                    Webuser.addToDevoxxians(maybeWebuser.get.uuid)
                    (maybeWebuser.get, maybeWebuser.get.uuid)
                  case other =>
                    val webuser = Webuser.createDevoxxian(_email, Some("MY_DEVOXX_FR"), Some("00000"))
                    val newUUID = Webuser.saveAndValidateWebuser(webuser)
                    Webuser.addToDevoxxians(newUUID)
                    (webuser, newUUID)
                }

                val cookie = createCookie(webuser)
                Future.successful(
                  Redirect(
                    request.headers.toSimpleMap.getOrElse(REFERER, routes.Publisher.homePublisher().absoluteURL(ConferenceDescriptor.isHTTPSEnabled))
                  ).flashing("success" -> Messages("mydevoxx.authenticated")).withSession("uuid" -> newUUID).withCookies(cookie)
                )
              case Failure(_) => Future.successful(Unauthorized("Not Authorized - token is invalid"))
            }
        }
      } else {
        Future.successful(Unauthorized("Invalid secure token"))
      }
  }

  private def createCookie(webuser: Webuser) = {
    Cookie("cfp_rm"
      , value = Crypto.encryptAES(webuser.uuid)
      , maxAge = Some(588000)
      , secure = ConferenceDescriptor.isHTTPSEnabled
      , httpOnly = true)
  }
}

case class GoogleToken(access_token: String, token_type: String, expires_in: Long, id_token: String)
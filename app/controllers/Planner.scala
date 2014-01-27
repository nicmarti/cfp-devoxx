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

import play.api.mvc.{Action, Controller}
import play.api.Play
import java.math.BigInteger
import java.security.SecureRandom
import play.api.libs.Crypto
import play.api.libs.json.Json
import play.api.libs.ws.WS
import models.Webuser
import library.Contexts
import java.net.URLEncoder

/**
 * Planner object that interacts with Google Calendar API.
 * Created by nicolas on 25/01/2014.
 */
object Planner extends Controller with Secured{

  def home=IsMemberOf("admin"){
    implicit uuid => implicit request=>
//    https://www.googleapis.com/calendar/v3/users/me/calendarList

    Ok(views.html.Planner.home())
  }
  // See Google documentation https://developers.google.com/accounts/docs/OAuth2Login
  def googleLogin = Action {
    implicit request =>
      Play.current.configuration.getString("google.client_id").map {
        clientId: String =>
          val redirectUri = routes.Planner.callbackGoogle().absoluteURL()
          val state = new BigInteger(130, new SecureRandom()).toString(32)
          val googleURL = "https://accounts.google.com/o/oauth2/auth?client_id=" + clientId + "&scope=https://www.googleapis.com/auth/calendar&state=" + Crypto.sign(state) + "&redirect_uri=" + redirectUri + "&response_type=code"
          Redirect(googleURL).withSession("state" -> state)
      }.getOrElse {
        InternalServerError("google.client_id is not set in application.conf")
      }
  }

  case class PlannerGoogleToken(access_token: String, token_type: String, expires_in: Long)

  implicit val plannerGoogleFormat = Json.format[PlannerGoogleToken]

  def callbackGoogle = Action {
    implicit request =>
      import Contexts.statsContext

      Authentication.oauthForm.bindFromRequest.fold(invalidForm => {
        BadRequest("Bad request").flashing("error" -> "Invalid form")
      }, validForm => {
        validForm match {
          case (code, state) if state == Crypto.sign(session.get("state").getOrElse("")) => {
            val auth = for (clientId <- Play.current.configuration.getString("google.client_id");
                            clientSecret <- Play.current.configuration.getString("google.client_secret")) yield (clientId, clientSecret)
            auth.map {
              case (clientId, clientSecret) => {
                val url = "https://accounts.google.com/o/oauth2/token"
                val redirect_uri = routes.Planner.callbackGoogle().absoluteURL()
                val wsCall = WS.url(url).withHeaders(("Accept" -> "application/json"), ("User-Agent" -> "Devoxx France CFP")).post(Map("client_id" -> Seq(clientId), "client_secret" -> Seq(clientSecret), "code" -> Seq(code), "grant_type" -> Seq("authorization_code"), "redirect_uri" -> Seq(redirect_uri)))
                Async {
                  wsCall.map {
                    result =>
                      result.status match {
                        case 200 => {
                          val b = result.body
println("Google body" + b)
                          val googleToken = Json.parse(result.body).as[PlannerGoogleToken]
                          Redirect(routes.Planner.listCalendars).withSession("google_token" -> googleToken.access_token)
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
          case other => BadRequest("Bad request, invalid state code").flashing("error" -> "Invalid state code")
        }
      })
  }

  def listCalendars = Action {
    implicit request =>
      import Contexts.statsContext

      request.session.get("google_token").map {
        access_token =>
          val url = "https://www.googleapis.com/calendar/v3/users/me/calendarList?access_token=" + access_token
          val futureResult = WS.url(url).withHeaders("User-agent" -> "CFP www.devoxx.fr", "Accept" -> "application/json").get()
          Async {
            futureResult.map {
              result =>
                result.status match {
                  case 200 => {
                    Ok(result.body).as("application/json")
                  }
                  case other => {
                    play.Logger.error("Unable to complete call " + result.status + " " + result.statusText + " " + result.body)
                    BadRequest("Unable to complete the Google API call due to "+result.statusText)
                  }
                }
            }
          }

      }.getOrElse {
        Redirect(routes.Planner.home()).flashing("error" -> "Your Google Access token has expired, please reauthenticate")
      }
  }

  case class GoogleDate(dateTime:String)
  case class NewEvent(start:GoogleDate, end:GoogleDate,location:String,summary:String,description:String, colorId:String)

  implicit val GoogleDateWriter=Json.writes[GoogleDate]
  implicit val NewEventWriter=Json.writes[NewEvent]

  def createEvent()=Action{
    implicit request=>
      import Contexts.statsContext

      request.session.get("google_token").map {
        access_token =>
          val calendar_id="m8uht8cilrjs45247qq2ari1b0%40group.calendar.google.com"

          val newEvent=  Json.toJson(NewEvent(
            GoogleDate("2014-01-26T10:00:02+00:00"),
            GoogleDate("2014-01-26T11:00:02+00:00"),
            "La Seine A",
            "Couleur 4",
            "description et formatage de test",
            "4"
          )
          )


          val url = s"https://www.googleapis.com/calendar/v3/calendars/$calendar_id/events?maxAttendees=1&sendNotifications=false&access_token=$access_token"
          val futureResult = WS.url(url).withHeaders("User-agent" -> "CFP www.devoxx.fr", "Accept" -> "application/json").post(newEvent)
          Async {
            futureResult.map {
              result =>
                result.status match {
                  case 200 => {
                    Ok(result.body).as("application/json")
                  }
                  case other => {
                    play.Logger.error("Unable to complete call " + result.status + " " + result.statusText + " " + result.body)
                    BadRequest("Unable to complete the Google API call due to "+result.statusText)
                  }
                }
            }
          }

      }.getOrElse {
        Redirect(routes.Planner.home()).flashing("error" -> "Your Google Access token has expired, please reauthenticate")
      }
      //POST

  }
}

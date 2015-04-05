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

import scala.concurrent._
import ExecutionContext.Implicits.global

import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.libs.ws._
import play.api.mvc._
import play.api.libs._
import play.api.libs.iteratee._
import play.Play
import java.net.URLEncoder
import models._
import play.api.i18n.Messages
import play.api.libs.oauth.OAuth
import play.api.libs.json.JsString
import scala.Some
import play.api.libs.oauth.ServiceInfo
import play.api.libs.oauth.RequestToken
import play.api.libs.oauth.OAuthCalculator
import play.api.libs.oauth.ConsumerKey
import akka.util.Timeout
import java.util.concurrent.TimeUnit
import play.api.libs.concurrent.Akka
import akka.actor.{ActorRef, Props, Actor}

/**
 * Tweet wall, using new Twitter Stream API.
 *
 * Original version created by Nicolas Martignole (@nmartignole) march 2014
 * Actor support added by Mathieu Ancelin (@TrevorReznik)
 */
object Tweetwall extends Controller  {

  val cfg = Play.application.configuration

  val KEY = ConsumerKey(cfg.getString("twitter.consumerKey"), cfg.getString("twitter.consumerSecret"))

  val tweetActor = Akka.system(play.api.Play.current).actorOf(Props[TweetsBroadcaster])
  implicit val timeout = Timeout(1, TimeUnit.SECONDS)

  val TWITTER = OAuth(ServiceInfo(
    "https://api.twitter.com/oauth/request_token",
    "https://api.twitter.com/oauth/access_token",
    "https://api.twitter.com/oauth/authorize", KEY),
    use10a = false)

  // Check if we have a valid Twitter token session, else authenticate
  def index = Action {
    implicit request =>
      request.session.get("token").map {
        token: String =>
          Ok(views.html.Tweetwall.showWall())
      }.getOrElse {
        Redirect(routes.Tweetwall.authenticate)
      }
  }

  // Twitter OAuth
  def authenticate = Action {
    implicit request =>
      request.queryString.get("oauth_verifier").flatMap(_.headOption).map {
        verifier =>
          val tokenPair = sessionTokenPair(request).get
          // We got the verifier; now get the access token, store it and back to index
          TWITTER.retrieveAccessToken(tokenPair, verifier) match {
            case Right(t) => {
              // We received the unauthorized tokens in the OAuth object - store it before we proceed
              Redirect(routes.Tweetwall.index).withSession("token" -> t.token, "secret" -> t.secret)
            }
            case Left(e) => throw e
          }
      }.getOrElse(
          TWITTER.retrieveRequestToken(routes.Tweetwall.authenticate.absoluteURL()) match {
            case Right(t) => {
              // We received the unauthorized tokens in the OAuth object - store it before we proceed
              Redirect(TWITTER.redirectUrl(t.token)).withSession("token" -> t.token, "secret" -> t.secret)
            }
            case Left(e) => throw e
          })
  }

  // Starts to watch tweets
  def watchTweets(keywords: String) = Action.async {
    implicit request =>
      import akka.pattern.ask
      (tweetActor ? WallRequest(keywords, request)).mapTo[WallResponse].map {
        response =>
          Ok.feed(response.enumerator &> Enumeratee.map[Array[Byte]](Json.parse) &> EventSource()).as("text/event-stream")
      }
  }

  def sessionTokenPair(implicit request: RequestHeader): Option[RequestToken] = {
    for {
      token <- request.session.get("token")
      secret <- request.session.get("secret")
    } yield {
      RequestToken(token, secret)
    }
  }

  // Loads the next talks,
  def loadNextTalks() = Action {
    implicit request =>
      import scala.concurrent.duration._
      import play.api.libs.concurrent.Promise

      val nextTalks: Enumerator[Seq[Slot]] = Enumerator.generateM[Seq[Slot]] {
        // 1) For production
        //  play.api.libs.concurrent.Promise.timeout(ScheduleConfiguration.loadNextTalks(), 20 seconds)
        // 2) For demo
        Promise.timeout(ScheduleConfiguration.loadRandomTalks(), 20 seconds)
      }

      val proposalsToJSON: Enumeratee[Seq[Slot], JsValue] = Enumeratee.map {
        slots =>
          val jsonObject = Json.toJson(
            slots.map {
              slot =>
                slot match {
                  case s if slot.proposal.isDefined => {
                    val proposal = slot.proposal.get
                    Json.toJson(
                      Map(
                        "id" -> JsString(proposal.id),
                        "title" -> JsString(proposal.title),
                        "speakers" -> JsString(proposal.allSpeakers.map(s => s.cleanName).mkString(", ")),
                        "room" -> JsString(slot.room.name),
                        "track" -> JsString(Messages(proposal.track.label)),
                        "from" -> JsString(slot.from.toString("HH:mm")),
                        "to" -> JsString(slot.to.toString("HH:mm"))
                      )
                    )
                  }
                  case s if slot.break.isDefined => {
                    val zeBreak = slot.break.get
                    Json.toJson(
                      Map(
                        "id" -> JsString(zeBreak.id),
                        "title" -> JsString(zeBreak.nameFR),
                        "room" -> JsString(zeBreak.room.name),
                        "track" -> JsString(""),
                        "speakers" -> JsString(""),
                        "from" -> JsString(slot.from.toString("HH:mm")),
                        "to" -> JsString(slot.from.toString("HH:mm"))
                      )
                    )
                  }
                }
            }
          )
          Json.toJson(jsonObject)
      }

      Ok.feed(nextTalks &> proposalsToJSON &> EventSource()).as("text/event-stream")
  }

}

case class WallRequest(keywords: String, request: RequestHeader)

case class WallResponse(enumerator: Enumerator[Array[Byte]])

case class ResetConnection()

class TweetsBroadcaster extends Actor {

  import context.become

  val (enumerator, channel) = Concurrent.broadcast[Array[Byte]]

  def beforeAuth: Receive = {
    case WallRequest(keywords, request) => {
      val myself = self
      sender ! WallResponse(enumerator)
      become(afterAuth)
      streamIt(keywords, request, myself)
    }
    case _ =>
  }


  def afterAuth: Receive = {
    case WallRequest(keywords, request) => sender ! WallResponse(enumerator)
    case ResetConnection() => become(beforeAuth)
    case _ =>
  }

  def streamIt(keywords: String, request: RequestHeader, myself: ActorRef) {
    WS.url(s"https://stream.twitter.com/1.1/statuses/filter.json?stall_warnings=true&filter_level=none&language=fr,en&track=" + URLEncoder.encode(keywords, "UTF-8"))
      .withRequestTimeout(-1) // else we close the stream
      .sign(OAuthCalculator(Tweetwall.KEY, Tweetwall.sessionTokenPair(request).get))
      .withHeaders("Connection" -> "keep-alive") // not sure we really need this
      .postAndRetrieveStream("")(_ => Iteratee.foreach[Array[Byte]](bytes => channel.push(bytes))).flatMap(_.run).onComplete {
      case other => {
        println("Tweetwall : reset connection "+other)
        myself ! ResetConnection()
      }
    }
  }




  def receive = beforeAuth
}
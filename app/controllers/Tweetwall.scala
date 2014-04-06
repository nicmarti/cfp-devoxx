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

import play.api.libs.iteratee._


import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.ws._
import play.api.libs.oauth._
import play.api.mvc._
import play.api.libs._
import play.api.libs.iteratee._
import play.Play
import java.net.URLEncoder
import play.api.libs.oauth.OAuth
import play.api.libs.oauth.ServiceInfo
import play.api.libs.oauth.RequestToken
import play.api.libs.oauth.OAuthCalculator
import play.api.libs.oauth.ConsumerKey


/**
 * Tweet wall, using new Twitter Stream API.
 *
 * Created by nmartignole on 03/04/2014.
 */
object Tweetwall extends Controller {

  val cfg = Play.application.configuration

  val KEY = ConsumerKey(cfg.getString("twitter.consumerKey"), cfg.getString("twitter.consumerSecret"))


  val TWITTER = OAuth(ServiceInfo(
    "https://api.twitter.com/oauth/request_token",
    "https://api.twitter.com/oauth/access_token",
    "https://api.twitter.com/oauth/authorize", KEY),
    use10a = false)

  def index = Action {
    implicit request =>
      request.session.get("token").map {
        token: String =>
          Ok(views.html.Tweetwall.wallDevoxxFR2014())
      }.getOrElse {
        Redirect(routes.Tweetwall.authenticate)
      }
  }


  def outputRequest() = Action {
    implicit request =>

      println("URI "+request.uri)
      request.headers.toSimpleMap.foreach{token=>
        println("Header "+ token._1+"="+token._2)
      }
      println("Body "+request.body.asFormUrlEncoded)
      println("---")
      Ok("Done")
  }

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

  def watchTweets(keywords: String) = Action {
    implicit request =>

      val (tweetsOut, tweetChanel) = Concurrent.broadcast[JsValue]
    // See Twitter parameters doc https://dev.twitter.com/docs/streaming-apis/parameters
     WS.url(s"https://stream.twitter.com/1.1/statuses/filter.json?stall_warnings=true&filter_level=none&track=" + URLEncoder.encode(keywords, "UTF-8"))
        .withRequestTimeout(-1) // Connected forever
        .sign(OAuthCalculator(KEY, sessionTokenPair.get))
        .withHeaders("Connection"->"keep-alive")
        .postAndRetrieveStream("")(headers => Iteratee.foreach[Array[Byte]] {
        ba =>
          val msg = new String(ba, "UTF-8")
          val tweet = Json.parse(msg)
          tweetChanel.push(tweet)
      }).flatMap(_.run)

     Ok.feed(tweetsOut &> EventSource()).as("text/event-stream")
  }

  def sessionTokenPair(implicit request: RequestHeader): Option[RequestToken] = {
    for {
      token <- request.session.get("token")
      secret <- request.session.get("secret")
    } yield {
      RequestToken(token, secret)
    }
  }

  def testCSS()=Action{
    implicit request=>
      Ok(views.html.Tweetwall.testCSS())
  }

}
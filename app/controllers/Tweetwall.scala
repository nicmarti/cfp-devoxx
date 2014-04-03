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
import com.ning.http.client.Realm.AuthScheme
import play.api.libs.oauth.{ConsumerKey, OAuthCalculator}
import play.api.libs.ws._
import play.api.libs.oauth._
import play.api.mvc._
import play.api.libs._
import play.api.libs.concurrent._
import play.api.libs.iteratee._
import com.ning.http.client.Realm.AuthScheme
import play.api.libs.ws.WS.WSRequestHolder
import org.joda.time.Instant

/**
 * Tweet wall, using new Twitter Stream API.
 *
 * Created by nmartignole on 03/04/2014.
 */
object Tweetwall extends Controller {

  val KEY = ConsumerKey("EfaMkGwGeCFyPzAMQQU4iaQ6E", "S8LtQQotNLBm79K6TuhqnS4UkaUJKEdeUAYcihy2iP9k5FmW7C")

  val TWITTER = OAuth(ServiceInfo(
    "https://api.twitter.com/oauth/request_token",
    "https://api.twitter.com/oauth/access_token",
    "https://api.twitter.com/oauth/authorize", KEY), use10a = true)

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

  def sessionTokenPair(implicit request: RequestHeader): Option[RequestToken] = {
    for {
      token <- request.session.get("token")
      secret <- request.session.get("secret")
    } yield {
      RequestToken(token, secret)
    }
  }

  implicit val writeTweetAsJson = Json.writes[Tweet]

  def search(query: String) = Action {
    implicit request =>
      val asJson: Enumeratee[Tweet, JsValue] = Enumeratee.map {
        case t => Json.toJson(t)
      }
      val tokens = sessionTokenPair(request).get

      // Serve a 200 OK text/event-stream
      Ok.feed(Tweet.search(query, tokens) &> asJson &> EventSource()
      ).as("text/event-stream")

  }

  case class Tweet(id: String, text: String, image: String)

  object Tweet {

    // Reads the twitter search API response, as a Seq[Tweet]
    implicit val readTweet: Reads[Seq[Tweet]] =

    // Start with 'results' property
      (__ \ "results").read(
        // It contains an array, so for each item
        seq(
          // Read the tweet id encoded as String
          (__ \ "id_str").read[String] and

            // Read the tweet text content
            (__ \ "text").read[String] and

            // If there is an {entities: {media: [] ... property
            (__ \ "entities" \ "media").readNullable(
              // It contains an array, so for each item
              seq(
                // Read the image URL
                (__ \ "media_url").read[String]
              )

              // Let's transform the Option[Seq[String]] to an simple Option[String]
              // since we care only about the first image URL if there is any.
            ).map(_.flatMap(_.headOption))

            // Transform all this to a (String, String, Option[String]) tuple
            tupled
        )
          .map(
            // Keep only the tuple containing an Image (third part of the tuple is Some)
            // and transform them to Tweet instances.
            _.collect {
              case (id, text, Some(image)) => Tweet(id, text, image)
            }
          )
      )

    def fetchPage(query: String, page: Int, secureToken: RequestToken): Future[Seq[Tweet]] = {
      println("Fetch page...")
      // Fetch the twitter search API with the corresponding parameters (see the Twitter API documentation)
      WS.url("http://localhost")
        .sign(OAuthCalculator(KEY, secureToken))
        .withQueryString("track"->query)
        .get()
        .map(r => r.status match {

        // We got a 200 OK response, try to convert the JSON body to a Seq[Tweet]
        // by using the previously defined implicit Reads[Seq[Tweet]]
        case 200 => {
          play.Logger.of("application.Tweetwall").info("Received " + r.body)
          r.json.asOpt[Seq[Tweet]].getOrElse(Nil)
        }

        // Really? There is nothing todo for us
        case x => {
          play.Logger.of("application.Tweetwall").error("2 Unable to fetch Twitter Stream api " + r.statusText)
          play.Logger.of("application.Tweetwall").error(r.getAHCResponse.getHeaders().toString)
          sys.error("Fatal")

        }
      })

    }


    /**
     * Create a stream of Tweet object from a Twitter query (such as #devoxx)
     */
    def search(query: String, secureToken: RequestToken): Enumerator[Tweet] = {

      // Flatenize an Enumerator[Seq[Tweet]] as n Enumerator[Tweet]
      val flatenize = Enumeratee.mapConcat[Seq[Tweet]](identity)

      // Schedule
      val schedule = Enumeratee.mapM[Tweet](t => play.api.libs.concurrent.Promise.timeout(t, 1000))

      // Create a stream of tweets from multiple twitter API calls
      val tweets = Enumerator.unfoldM(1) {
        case page => fetchPage(query, page, secureToken).map {
          tweets =>
            Option(tweets).filterNot(_.isEmpty).map(tweets => (page + 1, tweets))
        }
      }

      // Compose the final stream
      tweets &> flatenize &> schedule
    }

  }

}

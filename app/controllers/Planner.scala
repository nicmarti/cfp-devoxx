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

import play.api.mvc.{RequestHeader, Action, Controller}
import play.api.Play
import java.math.BigInteger
import java.security.SecureRandom
import play.api.libs.Crypto
import play.api.libs.json.Json
import play.api.libs.ws.WS
import models.{ProposalType, Proposal, Webuser}
import library.Contexts
import java.net.URLEncoder
import play.api.libs.oauth._
import play.api.libs.oauth.ServiceInfo
import play.api.libs.oauth.OAuth
import play.api.libs.oauth.RequestToken
import play.api.libs.oauth.ConsumerKey
import scala.Some
import Contexts.statsContext
import com.julienvey.trello.impl.TrelloImpl
import com.julienvey.trello.domain.Card
import scala.collection.JavaConverters._
import com.julienvey.trello.impl.domaininternal.Label

/**
 * Planner object that interacts with Google Calendar API.
 * Created by nicolas on 25/01/2014.
 */
object Planner extends Controller with Secured {

  val KEY = ConsumerKey("65eee1bd3ca6ece922cf66ece724dd66", "bdb165bd38af368eb05c266255b325007942d26c50e0141d67da845baa472f88")

  val TRELLO_OAUTH = OAuth(ServiceInfo(
    "https://trello.com/1/OAuthGetRequestToken",
    "https://trello.com/1/OAuthGetAccessToken",
    "https://trello.com/1/OAuthAuthorizeToken?scope=read,write", KEY),
    use10a = false)

  def index = IsMemberOf("admin") {
    implicit uuid => implicit request =>
    Ok(views.html.Planner.index())

  }

  def getBoard=IsMemberOf("admin"){
    implicit uuid=>
      implicit request=>
        sessionTokenPair match {
          case Some(credentials) =>

            val trelloAccessToken = credentials.token
            val trelloApi = new TrelloImpl("65eee1bd3ca6ece922cf66ece724dd66", trelloAccessToken)
            val boardId = "zUx2QoSx"

            val board = trelloApi.getBoard(boardId)

            //val lists= board.fetchLists()

            val listId="52e69c284ae96664717a1492"

            val universities = Proposal.allSubmitted().filter(_.talkType==ProposalType.UNI).take(1)

            universities.foreach{
              proposal=>
            val card = new Card()
              card.setDesc(proposal.summary)
              card.setIdBoard(boardId)
              card.setName(proposal.title)
              card.setIdList("52e69c284ae96664717a1492")
              val label = new com.julienvey.trello.domain.Label()
              label.setColor("orange")
              label.setName("Labs")
              card.setLabels(List(label).asJava)
              trelloApi.createCard(listId,card)
            }
            Ok("Created cards on Trello: "+universities.size)

          case _ => Redirect(routes.Planner.authenticate)
        }
  }


  def authenticate = Action {
    request =>
      request.queryString.get("oauth_verifier").flatMap(_.headOption).map {
        verifier =>
          val tokenPair = sessionTokenPair(request).get
          // We got the verifier; now get the access token, store it and back to index
          TRELLO_OAUTH.retrieveAccessToken(tokenPair, verifier) match {
            case Right(t) => {
              // We received the authorized tokens in the OAuth object - store it before we proceed
              Redirect(routes.Planner.index).withSession("token" -> t.token, "secret" -> t.secret)
            }
            case Left(e) => throw e
          }
      }.getOrElse(
          TRELLO_OAUTH.retrieveRequestToken("http://localhost:9000/trello/auth") match {
            case Right(t) => {
              // We received the unauthorized tokens in the OAuth object - store it before we proceed
              Redirect(TRELLO_OAUTH.redirectUrl(t.token)).withSession("token" -> t.token, "secret" -> t.secret)
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

  def logout = Action {
    Redirect(routes.Planner.index).withNewSession
  }
}

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

package library.wordpress

import models.{Proposal, Slot}

/**
 * WordpressPublisher is a simple client, able to connect to a remote Wordpress,
 * perfoms XMLRPC operations then return a status.
 * See http://codex.wordpress.org/XML-RPC_WordPress_API/Posts#wp.newPost
 *
 * Created by nicolas on 21/05/2014.
 */
object WordpressPublisher {

  val xmlRpcUrl = play.api.Play.current.configuration.getString("wordpress.xmlrpcURL").getOrElse("http://www.devoxx.fr/xmlrpc.php")
  val wordpressUsername = play.api.Play.current.configuration.getString("wordpress.publisher").getOrElse("no_user")
  val wordpressPassword = play.api.Play.current.configuration.getString("wordpress.password").getOrElse("no_password")

  def doPublish(slot: Slot) {
    println("Do publish " + slot.proposal)

    val proposal: Proposal = slot.proposal.get

    val newPost = new Page(proposal.title
      , proposal.summaryAsHtml
      , Option(new Category("0", "Programme2014", "Related to Devoxx France 2014"))
      , Set(Tag(proposal.track.id, proposal.track.label, proposal.track.label))
    )

    val wp = WordpressClient(wordpressUsername, wordpressPassword, xmlRpcUrl)
    val result = wp.newPost(newPost)

    println("Got a result " + result)


  }
}

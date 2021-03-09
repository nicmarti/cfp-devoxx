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

package library.search

import com.sksamuel.elastic4s.ElasticDsl.{SearchHandler, dismax, matchAllQuery, matchQuery, search}
import com.sksamuel.elastic4s.{RequestFailure, RequestSuccess}
import com.sksamuel.elastic4s.requests.searches.SearchResponse
import models.{ApprovedProposal, ConferenceDescriptor, ProposalType, Track}
import org.joda.time.DateTime
import play.api.libs.ws.WS
import play.api.libs.concurrent.Execution.Implicits._

import scala.util.{Failure, Success, Try}
import scala.concurrent.Future
import play.api.Play
import play.api.Play.current
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.WSAuthScheme.BASIC
/**
 * Wrapper and helper, to reuse the ElasticSearch REST API.
 *
 * Author: nicolas martignole
 * Created: 23/09/2013 12:31
 */
object ElasticSearch {

  val indexNames:String = "speakers,proposals"

  def doAdvancedSearch(indexName: String, query: Option[String], p: Option[Int]): Future[Either[RequestFailure, SearchResponse]] = {

    val someQuery: Option[String] = query.filterNot(_ == "").filterNot(_ == "*")

    // Elastic4S does not support https://username:password@host.domain.com
    // 1. it needs a port number, else it assumes default port is 9200 (and not 443)
    // 2. the username:password should be removed, else you get a connection error / hostname invalid
    val client = ESClient.open()
    val pageSize = 25
    val pageUpdated: Int = p match {
      case None => 0
      case Some(page) if page <= 0 => 0
      case Some(other) => (other - 1) * 25
    }

    val futureRes = someQuery match {
      case None => search(indexName).query(matchAllQuery()).from(pageUpdated).size(pageSize)
      case Some(term) =>
        search(indexName).query(dismax(
          matchQuery("title", term),
          matchQuery("name", term),
          matchQuery("firstName", term),
          matchQuery("mainSpeaker", term),
          matchQuery("secondarySpeaker", term),
          matchQuery("summary", term),
          matchQuery("otherSpeakers", term),
          matchQuery("tags", term),
          matchQuery("id", term)
        )).from(pageUpdated).size(pageSize)
    }
    val result = client.execute(futureRes)

    result.map {
      case failure: RequestFailure =>
        client.close()
        Left(failure)
      case results: RequestSuccess[SearchResponse] =>
        client.close()
        Right(results.result)
    }
  }


}

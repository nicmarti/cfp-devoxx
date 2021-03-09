package library.search

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

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s._
import com.sksamuel.elastic4s.requests.bulk.BulkCompatibleRequest
import com.sksamuel.elastic4s.requests.mappings.{Analysis, KeywordField, TextField}
import com.sksamuel.elastic4s.requests.searches.SearchResponse
import models._
import play.api.Play
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.concurrent.Future

/**
  * New Wrapper based on Elastic4S for the Publisher part of the CFP.
  * This is only for the public web pages with talks, speakers, agenda, room, schedules.
  *
  * Author: nicolas martignole
  * Created: 01/03/2021
  */
object ElasticSearchPublisher {

  val cfpLang: Option[String] = Play.current.configuration.getString("application.langs")

  val logger = play.Logger.of("application.library.ElasticSearchV2")

  def createConfigureIndex(): Unit = {
    val client = ESClient.open()

    client.execute(
      deleteIndex(ApprovedProposal.elasticSearchIndex())
    ).await

    cfpLang match {
      case Some(lang) if lang.toLowerCase.startsWith("fr") =>
        logger.debug("Using FR for ElasticSearch Analyzer")
        client.execute(
          createIndex(ApprovedProposal.elasticSearchIndex()).mapping(
            properties(
              TextField("id"),
              TextField("title", boost = Some(3.0), analysis = Analysis(analyzer = Some("french"))),
              TextField("summary", analysis = Analysis(analyzer = Some("french"))),
              TextField("mainSpeaker", boost = Some(2.0)),
              TextField("secondarySpeaker"),
              TextField("otherSpeakers"),
              KeywordField("tags")
            )
          )
        ).await
      case _ =>
        client.execute(
          createIndex(ApprovedProposal.elasticSearchIndex()).mapping(
            properties(
              TextField("id"),
              TextField("title", boost = Some(3.0)),
              TextField("summary"),
              TextField("mainSpeaker", boost = Some(2.0)),
              TextField("secondarySpeaker"),
              TextField("otherSpeakers"),
              KeywordField("tags")
            )
          )
        ).await
    }

    client.close()
  }

  def doIndexAllAccepted() {
    val proposals = Proposal.allAccepted()

    val indexName = ApprovedProposal.elasticSearchIndex()
    logger.debug(s"Do index all accepted ${proposals.size} to index $indexName")

    val cachedSpeakers = Speaker.allSpeakers()

    val requests: Iterable[BulkCompatibleRequest] = proposals.map {
      p =>
        val mainSpeaker: String = cachedSpeakers.find(_.uuid == p.mainSpeaker).map(_.cleanName).getOrElse("")
        val secondarySpeaker: String = p.secondarySpeaker.map(sec => cachedSpeakers.find(_.uuid == sec).map(_.cleanName).getOrElse("")).orNull
        val otherSpeakers: String = p.otherSpeakers.map(sec => cachedSpeakers.find(_.uuid == sec).map(_.cleanName).getOrElse("")).mkString(", ")

        indexInto(indexName)
          .fields(
            "id" -> p.id
            , "title" -> p.title
            , "summary" -> p.summary
            , "mainSpeaker" -> mainSpeaker
            , "secondarySpeaker" -> secondarySpeaker
            , "otherSpeakers" -> otherSpeakers
            , "tags" -> p.tags.map(sTags => sTags.filterNot(_.id == "0")).orNull
          )
          .id(p.id)
    }

    val client = ESClient.open()

    client.execute(
      bulk(requests)
    ).await

    client.close()

    play.Logger.of("application.library.ElasticSearchV2").debug("Done indexing all acceptedproposals")
  }

  def doPublisherSearch(query: Option[String], p: Option[Int]): Future[Either[RequestFailure, SearchResponse]] = {
    val indexName = ApprovedProposal.elasticSearchIndex()
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
          matchQuery("mainSpeaker", term),
          matchQuery("secondarySpeaker", term),
          matchQuery("summary", term),
          matchQuery("otherSpeakers", term),
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



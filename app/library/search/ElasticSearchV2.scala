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
import com.sksamuel.elastic4s.http.{JavaClient, NoOpRequestConfigCallback}
import com.sksamuel.elastic4s.requests.bulk.BulkCompatibleRequest
import com.sksamuel.elastic4s.requests.mappings.{Analysis, TextField}
import com.sksamuel.elastic4s.requests.searches.SearchResponse
import models._
import org.apache.http.auth.{AuthScope, UsernamePasswordCredentials}
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder
import org.elasticsearch.client.RestClientBuilder.HttpClientConfigCallback
import play.api.Play
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.concurrent.Future

/**
  * New Wrapper based on Elastic4S
  *
  * Author: nicolas martignole
  * Created: 01/03/2021
  */
object ElasticSearchV2 {

  val host = Play.current.configuration.getString("elasticsearch.host").getOrElse("http://localhost")
  val port = Play.current.configuration.getString("elasticsearch.port").getOrElse("9200")
  val username = Play.current.configuration.getString("elasticsearch.username").getOrElse("")
  val password = Play.current.configuration.getString("elasticsearch.password").getOrElse("")
  val cfpLang: Option[String] = Play.current.configuration.getString("application.langs")

  val logger = play.Logger.of("application.library.ElasticSearchV2")

  val callback = new HttpClientConfigCallback {
    override def customizeHttpClient(httpClientBuilder: HttpAsyncClientBuilder): HttpAsyncClientBuilder = {
      val creds = new BasicCredentialsProvider()
      creds.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password))
      httpClientBuilder.setDefaultCredentialsProvider(creds)
    }
  }

  def doPublisherSearch(query: Option[String], p: Option[Int]): Future[Either[RequestFailure, SearchResponse]] = {
    val indexName = ApprovedProposal.elasticSearchIndex()
    val someQuery: Option[String] = query.filterNot(_ == "").filterNot(_ == "*")

    logger.debug(s"search term someQuery=$someQuery")

    // Elastic4S does not support https://username:password@host.domain.com
    // 1. it needs a port number, else it assumes default port is 9200 (and not 443)
    // 2. the username:password should be removed, else you get a connection error / hostname invalid
    val client = ElasticClient(
      JavaClient(ElasticProperties(host + ":" + port),
        requestConfigCallback = NoOpRequestConfigCallback,
        httpClientConfigCallback = callback)
    )
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

  def createConfigureIndex(): Unit = {
    val client = ElasticClient(JavaClient(ElasticProperties(host + ":" + port), requestConfigCallback = NoOpRequestConfigCallback, httpClientConfigCallback = callback))

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
              TextField("title", analysis = Analysis(analyzer = Some("french"))),
              TextField("summary", analysis = Analysis(analyzer = Some("french"))),
              TextField("mainSpeaker"),
              TextField("secondarySpeaker"),
              TextField("otherSpeakers")
            )
          )
        ).await
      case _ =>
        client.execute(
          createIndex(ApprovedProposal.elasticSearchIndex()).mapping(
            properties(
              TextField("id"),
              TextField("title"),
              TextField("summary"),
              TextField("mainSpeaker"),
              TextField("secondarySpeaker"),
              TextField("otherSpeakers")
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

    val allSpeakers = Speaker.allSpeakers()

    val requests: Iterable[BulkCompatibleRequest] = proposals.map {
      p =>
        val mainSpeaker: String = allSpeakers.find(_.uuid == p.mainSpeaker).map(_.cleanName).getOrElse("")
        val secondarySpeaker: String = p.secondarySpeaker.map(sec => allSpeakers.find(_.uuid == sec).map(_.cleanName).getOrElse("")).orNull
        val otherSpeakers: String = p.otherSpeakers.map(sec => allSpeakers.find(_.uuid == sec).map(_.cleanName).getOrElse("")).mkString(", ")

        indexInto(indexName)
          .fields(
            "id" -> p.id
            , "title" -> p.title
            , "summary" -> p.summary
            , "mainSpeaker" -> mainSpeaker
            , "secondarySpeaker" -> secondarySpeaker
            , "otherSpeakers" -> otherSpeakers
          )
          .id(p.id)
    }

    val client = ElasticClient(JavaClient(ElasticProperties(host + ":" + port), requestConfigCallback = NoOpRequestConfigCallback, httpClientConfigCallback = callback))

    client.execute(
      bulk(requests)
    ).await

    client.close()

    play.Logger.of("application.library.ElasticSearchV2").debug("Done indexing all acceptedproposals")
  }
}



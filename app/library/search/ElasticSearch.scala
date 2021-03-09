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

//  val port = Play.current.configuration.getString("elasticsearch.port").getOrElse("9200")
//  val username = Play.current.configuration.getString("elasticsearch.username").getOrElse("")
//  val password = Play.current.configuration.getString("elasticsearch.password").getOrElse("")
//  val host = Play.current.configuration.getString("elasticsearch.host").map(s => s + ":" + port).getOrElse("http://localhost:9200")
//
//  def index(index: String, json: String) = {
//    if (play.Logger.of("library.ElasticSearch").isDebugEnabled) {
//      play.Logger.of("library.ElasticSearch").debug(s"Indexing to $index $json")
//    }
//    val futureResponse = WS.url(host + "/" + index)
//      .withAuth(username, password, BASIC)
//      .put(json)
//    futureResponse.map {
//      response =>
//        response.status match {
//          case 201 => Success(response.body)
//          case 200 => Success(response.body)
//          case other => Failure(new RuntimeException("Unable to index, HTTP Code " + response.status + ", ElasticSearch responded " + response.body))
//        }
//    }
//  }
//
//  def indexBulk(indexName: String, json: String) = {
//    if (play.Logger.of("library.ElasticSearch").isDebugEnabled) {
//      play.Logger.of("library.ElasticSearch").debug(s"Bulk index $indexName started")
//    }
//
//    val futureResponse = WS.url(s"$host/$indexName/_bulk")
//      .withAuth(username, password,BASIC)
//      .withHeaders("Content-Type"-> "application/x-ndjson")
//      .post(json)
//    futureResponse.map {
//      response =>
//        response.status match {
//          case 201 =>
//            if (play.Logger.of("library.ElasticSearch").isDebugEnabled) {
//              play.Logger.of("library.ElasticSearch").debug(s"Bulk index [$indexName] done")
//            }
//            Success(response.body)
//          case 200 =>
//            if (play.Logger.of("library.ElasticSearch").isDebugEnabled) {
//              play.Logger.of("library.ElasticSearch").debug(s"Bulk index [$indexName] done")
//            }
//            Success(response.body)
//          case other => Failure(new RuntimeException(s"Unable to bulk import [$indexName], HTTP Code " + response.status + ", ElasticSearch responded " + response.body))
//        }
//    }
//  }
//
//  def createIndexWithSettings(index: String, settings: String) = {
//    if (play.Logger.of("library.ElasticSearch").isDebugEnabled) {
//      play.Logger.of("library.ElasticSearch") debug s"Create index $index with custom settings"
//    }
//    val url = s"$host/${index.toLowerCase}"
//    val futureResponse = WS.url(url)
//      .withAuth(username, password,BASIC)
//      .withHeaders("Content-Type" -> "application/x-ndjson")
//      .put(settings)
//    futureResponse.map {
//      response =>
//        response.status match {
//          case 201 =>
//            if (play.Logger.of("library.ElasticSearch").isDebugEnabled) {
//              play.Logger.of("library.ElasticSearch") debug s"Created index $index"
//            }
//            Success(response.body)
//          case 200 =>
//            if (play.Logger.of("library.ElasticSearch").isDebugEnabled) {
//              play.Logger.of("library.ElasticSearch") debug s"Created index $index"
//            }
//            Success(response.body)
//          case _ =>
//            play.Logger.of("library.ElasticSearch").warn("Unable to create index with settings due to " + response.body)
//            Failure(new RuntimeException("Unable to createSettings, HTTP Code " + response.status + ", ElasticSearch responded " + response.body))
//        }
//    }
//  }
//
//  // PUT /speakers/speaker/_mapping?ignore_conflicts=true
//  def createMapping(index: String, mapping: String) = {
//    val url = s"$host/$index/_mapping?ignore_conflicts=true"
//    val futureResponse = WS.url(url)
//      .withAuth(username, password,BASIC)
//      .withRequestTimeout(6000)
//      .withHeaders("Content-Type" -> "application/x-ndjson")
//      .put(mapping)
//    futureResponse.map {
//      response =>
//        response.status match {
//          case 201 => Success(response.body)
//          case 200 => Success(response.body)
//          case _ => Failure(new RuntimeException(s"Unable to createMapping for $index, HTTP Code " + response.status + ", ElasticSearch responded " + response.body))
//        }
//    }
//  }
//
//  def refresh() = {
//    // http://localhost:9200/_refresh
//    val url = s"$host/_refresh"
//    val futureResponse = WS.url(url)
//      .withRequestTimeout(6000)
//      .withAuth(username, password,BASIC)
//      .withHeaders("Content-Type" -> "application/x-ndjson")
//      .post("{}")
//    futureResponse.map {
//      response =>
//        response.status match {
//          case 201 => Success(response.body)
//          case 200 => Success(response.body)
//          case _ => Failure(new RuntimeException("Unable to createMapping, HTTP Code " + response.status + ", ElasticSearch responded " + response.body))
//        }
//    }
//  }
//
//  def deleteIndex(indexName: String) = {
//    if (play.Logger.of("library.ElasticSearch").isDebugEnabled) {
//      play.Logger.of("library.ElasticSearch").debug(s"Deleting index $indexName")
//    }
//    val futureResponse = WS.url(host + "/" + indexName + "/")
//      .withAuth(username, password,BASIC)
//      .delete()
//    futureResponse.map {
//      response =>
//        response.status match {
//          case 201 => Success(response.body)
//          case 200 => Success(response.body)
//          case _ => Failure(new RuntimeException("Unable to delete index, HTTP Code " + response.status + ", ElasticSearch responded " + response.body))
//        }
//    }
//  }

//  def doSearch(query: String): Future[Try[String]] = {
//    val serviceParams = Seq(("q", query))
//    val futureResponse = WS.url(host + "/_search")
//      .withAuth(username, password,BASIC)
//      .withQueryString(serviceParams: _*).get()
//    futureResponse.map {
//      response =>
//        response.status match {
//          case 200 => Success(response.body)
//          case _ => Failure(new RuntimeException("Unable to search, HTTP Code " + response.status + ", ElasticSearch responded " + response.body))
//        }
//    }
//  }

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

//  // This is interesting if you want to build a cloud of Words.
//  def getTag(index: String) = {
//
//    val json: String =
//      """
//        |{
//        |  "query" : {
//        |     "match_all" : {}
//        |  },
//        |  "facets" : {
//        |     "tags" : {
//        |       "terms" : {
//        |         "fields" : ["summary"],
//        |         "size":100,
//        |         "exclude": ["a","on","de","et","les","la","des","pour","le","en","un","à","vous","une","est","dans","cette",
//        |         "avant","faut","site","certains","quoi","dont","me","deux","trois","quatre","fois","façon","vers",
//        |        "du","que","avec","comment","nous","sur","ce","plus","qui","au","ou","il","votre","pas",
//        |        "mais","par","applications","ne","tout","présentation","faire","vos","peut","sont","you",
//        |        "si","aussi","se","son","can","ces","je","bien","être","tous","comme","we","sans","mettre",
//        |        "verrons","permet","quelques","avez","aux","y","travers","notre","entre",
//        |        "cet","ont","même","mise","soit","permettant","développeur","also",
//        |        "your", "quand", "temps", "systèmes", "système","permettent",
//        |        "réel", "hands", "facile", "rencontre", "puissance", "outils","peuvent","etc",
//        |        "minutes", "why", "who", "webs", "vivre", "vite", "tour", "time","l'occasion",
//        |        "testez", "sérialisez", "suite", "side", "recommandation","d’un","qu'il",
//        |        "pourquoi","cela","like","point","chaque","bonnes","ans","when",
//        |        "alors","lors","leur","leurs","pourtant","peu","elle","il","là","toutes",
//        |        "venez", "temps", "ses", "talk", "sa", "allons", "all", "différents",
//        |        "mieux", "have", "propose", "new", "place", "également", "fait","from",
//        |        "about", "base", "autres", "très", "ça", "what", "some", "do", "want", "using",
//        |        "s","so", "7","2", "8", "30", "them", "session", "application", "moins","moi","ainsi",
//        |        "how", "c'est", "d'un", "d'une", "souvent", "depuis", "sera", "cas",  "après", "sous", "encore",
//        |        "non", "use","n'est", "utilisateurs", "utilisant","more","plusieurs","nombreux","été","vie",
//        |        "i","look","has","grâce","différentes","take","toute","get","devient","afin","surtout","toujours",
//        |        "via","tels","avons","d'expérience",
//        |        "va","user","seront","déjà","mode","avoir","most",
//        |        "où","mon","see","which","quel","donc", "nos","d'applications", "aujourd'hui", "used", "learn",
//        |        "and","the","or","to","of","in","this",
//        |        "is", "for", "with",  "it", "will", "that",  "but",
//        |         "as",  "an", "are", "be", "by","at", "these", "quels", "not", "enfin", "c’est"
//        |        ] } }
//        |       }
//        |     }
//        |  }
//        |}
//      """.stripMargin
//
//    val futureResponse = WS.url(host + "/" + index + "/_search?search_type=count")
//      .withAuth(username, password,BASIC)
//      .withHeaders("Content-Type" -> "application/x-ndjson")
//      .post(json)
//    futureResponse.map {
//      response =>
//        response.status match {
//          case 201 => Success(response.body)
//          case 200 => Success(response.body)
//          case _ => Failure(new RuntimeException("Unable to load tag, HTTP Code " + response.status + ", ElasticSearch responded " + response.body))
//        }
//    }
//  }
//
//  def doStats(zeQuery: String, index: String, maybeUserFilter: Option[String]) = {
//    val json: String =
//      s"""
//        |{
//        |  "from" : 0, "size" : 10,
//        |   $zeQuery
//        |   , "facets" : {
//        |       "villeFacet" : {
//        |        "terms" : {
//        |           "field" : "ville",
//        |           "all_terms":false,
//        |           "order" : "count",
//        |           "size":50
//        |         }
//        |         ${maybeUserFilter.getOrElse("")}
//        |      },
//        |     "idRaisonAppelFacet" : {
//        |        "terms" : {
//        |          "field" : "idRaisonAppel",
//        |          "all_terms":true,
//        |          "order" : "term",
//        |          "size":50
//        |        }
//        |        ${maybeUserFilter.getOrElse("")}
//        |      },
//        |      "clotureFacet":{
//        |       "terms" : {
//        |         "field" : "cloture"
//        |       }
//        |       ${maybeUserFilter.getOrElse("")}
//        |     },
//        |      "statusFacet" : {
//        |        "terms" : {
//        |          "field" : "status",
//        |          "all_terms":true,
//        |          "order" : "term",
//        |          "size":20
//        |        }
//        |        ${maybeUserFilter.getOrElse("")}
//        |      },
//        |      "agenceFacet" : {
//        |        "terms" : {
//        |          "field" : "idAgence",
//        |          "all_terms":false,
//        |          "order" : "term",
//        |          "size":50
//        |        }
//        |       ${maybeUserFilter.getOrElse("")}
//        |      },
//        |     "histoWeek" : {
//        |        "date_histogram" : {
//        |          "field" : "dateSaisie",
//        |          "interval" : "day"
//        |        }
//        |        ${maybeUserFilter.getOrElse("")}
//        |     },
//        |     "statsTicket":{
//        |       "statistical":{
//        |         "field":"delaiIntervention"
//        |       }
//        |       ${maybeUserFilter.getOrElse("")}
//        |     },
//        |     "typeInterFacet" : {
//        |      "terms":{
//        |        "field":"delaiStatus",
//        |        "size":100
//        |       }
//        |       ${maybeUserFilter.getOrElse("")}
//        |     }
//        |     ,
//        |     "statsAgeFacet" : {
//        |      "statistical":{
//        |         "field":"age"
//        |       }
//        |       ${maybeUserFilter.getOrElse("")}
//        |     }
//        |     ,
//        |     "statsReactionFacet" : {
//        |      "statistical":{
//        |         "field":"tempsReactionToMinute"
//        |       }
//        |       ${maybeUserFilter.getOrElse("")}
//        |     }
//        |   }
//        | }
//      """.stripMargin
//
//    if (play.Logger.of("ElasticSearch").isDebugEnabled) {
//      play.Logger.of("ElasticSearch").debug("Sending to ES request:")
//      play.Logger.of("ElasticSearch").debug(json)
//    }
//
//    val futureResponse = WS.url(host + "/" + index + "/_search")
//      .withHeaders("Content-Type" -> "application/x-ndjson")
//      .withRequestTimeout(4000).post(json)
//    futureResponse.map {
//      response =>
//        response.status match {
//          case 200 => Success(response.body)
//          case _ => Failure(new RuntimeException("Unable to index, HTTP Code " + response.status + ", ElasticSearch responded " + response.body))
//        }
//    }
//  }
//
//  def doAdvancedTalkSearch(query: AdvancedSearchParam) = {
//   val indexName = "schedule_" + ConferenceDescriptor.current().eventCode.toLowerCase
//    val zeQuery =
//      s"""
//        |"dis_max": {
//        |   "queries": [
//        |                { "match": { "name":"${query.format.getOrElse("%")}"}},
//        |                { "match": { "day":"${query.day.getOrElse("%")}"}},
//        |                { "match": { "from":"${query.after.getOrElse("2017-01-01T00:15:00.000Z")}"}},
//        |                { "match": { "room":"${query.room.getOrElse("%")}"}},
//        |                { "match": { "title": { "query":"${query.topic.getOrElse("%")}","boost":2 }}},
//        |                { "match": { "summary": {"query":"${query.topic.getOrElse("%")}","boost":3 }}},
//        |                { "match": { "track.id":"${query.track.getOrElse("%")}"}},
//        |                { "match": { "talkType.id":"${query.format.getOrElse("%")}"}},
//        |                { "match": { "mainSpeaker": {"query":"${query.speaker.getOrElse("%")}","boost":1.3} }},
//        |                { "match": { "secondarySpeaker":"${query.speaker.getOrElse("%")}" }},
//        |                { "match": { "otherSpeakers":"${query.speaker.getOrElse("%")}" }},
//        |                { "match": { "company": {"query":"${query.company.getOrElse("%")}", "boost":1.6 }}}
//        |            ],
//        |            "tie_breaker": 0.3
//        |}
//      """.stripMargin
//
//    val json: String = s"""
//        |{
//        | "from" : 0,
//        | "size" : 10,
//        | "query" : {
//        |   $zeQuery
//        | }
//        |}
//      """.stripMargin
//
//    if (play.Logger.of("library.ElasticSearch").isDebugEnabled) {
//      play.Logger.of("library.ElasticSearch").debug(s"Elasticsearch advanced talk search query $json")
//    }
//
//    val futureResponse = WS.url(host + "/" + indexName + "/_search")
//      .withFollowRedirects(true)
//      .withRequestTimeout(4000)
//      .withAuth(username, password,BASIC)
//      .withHeaders("Content-Type" -> "application/x-ndjson")
//      .post(json)
//    futureResponse.map {
//      response =>
//        response.status match {
//          case 200 => Success(response.body)
//          case other => Failure(new RuntimeException("Unable to perform search, HTTP Code " + response.status + ", ElasticSearch responded " + response.body))
//        }
//    }
//  }

}

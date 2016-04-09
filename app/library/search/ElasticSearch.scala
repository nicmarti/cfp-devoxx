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

import models.ApprovedProposal
import play.api.libs.ws.WS

import akka.actor._
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import scala.util.{Try, Failure, Success}
import scala.concurrent.Future
import play.api.Play
import com.ning.http.client.Realm.AuthScheme

/**
 * Wrapper and helper, to reuse the ElasticSearch REST API.
 *
 * Author: nicolas martignole
 * Created: 23/09/2013 12:31
 */
object ElasticSearch {

  val host = Play.current.configuration.getString("elasticsearch.host").getOrElse("http://localhost:9200")
  val username = Play.current.configuration.getString("elasticsearch.username").getOrElse("")
  val password = Play.current.configuration.getString("elasticsearch.password").getOrElse("")

  def index(index: String, json: String) = {
    if (play.Logger.of("library.ElasticSearch").isDebugEnabled) {
      play.Logger.of("library.ElasticSearch").debug(s"Indexing to $index $json")
    }
    val futureResponse = WS.url(host + "/" + index)
      .withAuth(username, password, AuthScheme.BASIC)
      .put(json)
    futureResponse.map {
      response =>
        response.status match {
          case 201 => Success(response.body)
          case 200 => Success(response.body)
          case other => Failure(new RuntimeException("Unable to index, HTTP Code " + response.status + ", ElasticSearch responded " + response.body))
        }
    }
  }

  def indexBulk(json: String, indexName: String) = {
    if (play.Logger.of("library.ElasticSearch").isDebugEnabled) {
      play.Logger.of("library.ElasticSearch").debug(s"Bulk index ${indexName} started to $host")
    }

    val futureResponse = WS.url(s"$host/$indexName/_bulk")
      .withAuth(username, password, AuthScheme.BASIC)
      .post(json)
    futureResponse.map {
      response =>
        response.status match {
          case 201 =>
            if (play.Logger.of("library.ElasticSearch").isDebugEnabled) {
              play.Logger.of("library.ElasticSearch").debug(s"Bulk index [$indexName] created")
            }
            Success(response.body)
          case 200 =>
            if (play.Logger.of("library.ElasticSearch").isDebugEnabled) {
              play.Logger.of("library.ElasticSearch").debug(s"Bulk index [$indexName] created")
            }
            Success(response.body)
          case other => Failure(new RuntimeException(s"Unable to bulk import [$indexName], HTTP Code " + response.status + ", ElasticSearch responded " + response.body))
        }
    }
  }

  def createIndexWithSettings(index: String, settings: String) = {
    if (play.Logger.of("library.ElasticSearch").isDebugEnabled) {
      play.Logger.of("library.ElasticSearch") debug (s"Create index ${index} with settings ${settings}")
    }
    val url = s"$host/${index.toLowerCase}"
    val futureResponse = WS.url(url)
      .withAuth(username, password, AuthScheme.BASIC)
      .post(settings)
    futureResponse.map {
      response =>
        response.status match {
          case 201 =>
            if (play.Logger.of("library.ElasticSearch").isDebugEnabled) {
              play.Logger.of("library.ElasticSearch") debug (s"Created index $index")
            }
            Success(response.body)
          case 200 =>
            if (play.Logger.of("library.ElasticSearch").isDebugEnabled) {
              play.Logger.of("library.ElasticSearch") debug (s"Created index $index")
            }
            Success(response.body)
          case other =>
            play.Logger.of("library.ElasticSearch").warn("Unable to create index with settings due to " + response.body)
            Failure(new RuntimeException("Unable to createSettings, HTTP Code " + response.status + ", ElasticSearch responded " + response.body))
        }
    }
  }

  // PUT /speakers/speaker/_mapping?ignore_conflicts=true
  def createMapping(index: String, mapping: String) = {
    val url = s"$host/$index/_mapping?ignore_conflicts=true"
    val futureResponse = WS.url(url)
      .withAuth(username, password, AuthScheme.BASIC)
      .withRequestTimeout(6000)
      .put(mapping)
    futureResponse.map {
      response =>
        response.status match {
          case 201 => Success(response.body)
          case 200 => Success(response.body)
          case other => Failure(new RuntimeException(s"Unable to createMapping for $index, HTTP Code " + response.status + ", ElasticSearch responded " + response.body))
        }
    }
  }

  def refresh() = {
    // http://localhost:9200/_refresh
    val url = s"$host/_refresh"
    val futureResponse = WS.url(url)
      .withRequestTimeout(6000)
      .withAuth(username, password, AuthScheme.BASIC)
      .post("{}")
    futureResponse.map {
      response =>
        response.status match {
          case 201 => Success(response.body)
          case 200 => Success(response.body)
          case other => Failure(new RuntimeException("Unable to createMapping, HTTP Code " + response.status + ", ElasticSearch responded " + response.body))
        }
    }
  }


  def deleteIndex(indexName: String) = {
    if (play.Logger.of("library.ElasticSearch").isDebugEnabled) {
      play.Logger.of("library.ElasticSearch").debug(s"Deleting index $indexName")
    }
    val futureResponse = WS.url(host + "/" + indexName + "/")
      .withAuth(username, password, AuthScheme.BASIC)
      .delete()
    futureResponse.map {
      response =>
        response.status match {
          case 201 => Success(response.body)
          case 200 => Success(response.body)
          case other => Failure(new RuntimeException("Unable to delete index, HTTP Code " + response.status + ", ElasticSearch responded " + response.body))
        }
    }

  }

  def doSearch(query: String): Future[Try[String]] = {
    val serviceParams = Seq(("q", query))
    val futureResponse = WS.url(host + "/_search")
      .withAuth(username, password, AuthScheme.BASIC)
      .withQueryString(serviceParams: _*).get()
    futureResponse.map {
      response =>
        response.status match {
          case 200 => Success(response.body)
          case other => Failure(new RuntimeException("Unable to search, HTTP Code " + response.status + ", ElasticSearch responded " + response.body))
        }
    }
  }

  def doAdvancedSearch(index: String, query: Option[String], p: Option[Int]) = {

    val someQuery = query.filterNot(_ == "").filterNot(_ == "*")
    val zeQuery = someQuery.map { q => "\"query_string\" : { \"query\": \"" + q + "\"}"}.getOrElse("\"match_all\" : { }")
    val pageSize = 25

    val pageUpdated: Int = p match {
      case None => 0
      case Some(page) if page <= 0 => 0
      case Some(other) => (other - 1) * 25
    }

    val json: String = s"""
        |{
        | "from" : $pageUpdated,
        | "size" : $pageSize,
        | "query" : {
        |   $zeQuery
        | }
        |}
      """.stripMargin

    if (play.Logger.of("library.ElasticSearch").isDebugEnabled) {
      play.Logger.of("library.ElasticSearch").debug(s"Page $p")
      play.Logger.of("library.ElasticSearch").debug(s"$pageUpdated")
      play.Logger.of("library.ElasticSearch").debug(s"Elasticsearch query $json")
    }

    val futureResponse = WS.url(host + "/" + index + "/_search")
      .withFollowRedirects(true)
      .withRequestTimeout(4000)
      .withAuth(username, password, AuthScheme.BASIC)
      .post(json)
    futureResponse.map {
      response =>
        response.status match {
          case 200 => Success(response.body)
          case other => Failure(new RuntimeException("Unable to perform search, HTTP Code " + response.status + ", ElasticSearch responded " + response.body))
        }
    }
  }

  def doPublisherSearch(query: Option[String], p: Option[Int]) = {
    val index = ApprovedProposal.elasticSearchIndex()
    val someQuery = query.filterNot(_ == "").filterNot(_ == "*")
    val zeQuery = someQuery.map(_.toLowerCase).map { q =>
      s"""
        |"dis_max": {
        |   "queries": [
        |                { "match": { "title":"$q"}},
        |                { "match": { "mainSpeaker":"$q"}},
        |                { "match": { "secondarySpeaker":"$q"}},
        |                { "match": { "summary":"$q"}},
        |                { "match": { "otherSpeakers":"$q" }},
        |                { "match": { "id":"$q"}}
        |            ]
        |}
      """.stripMargin

    }.getOrElse("\"match_all\":{}")
    val pageSize = 25
    val pageUpdated: Int = p match {
      case None => 0
      case Some(page) if page <= 0 => 0
      case Some(other) => (other - 1) * 25
    }

    val json: String = s"""
        |{
        | "from" : $pageUpdated,
        | "size" : $pageSize,
        | "query" : {
        |   $zeQuery
        | }
        |}
      """.stripMargin

    if (play.Logger.of("library.ElasticSearch").isDebugEnabled) {
      play.Logger.of("library.ElasticSearch").debug(s"Page $p")
      play.Logger.of("library.ElasticSearch").debug(s"$pageUpdated")
      play.Logger.of("library.ElasticSearch").debug(s"Elasticsearch query $json")
    }

    val futureResponse = WS.url(host + "/" + index + "/_search")
      .withFollowRedirects(true)
      .withRequestTimeout(4000)
      .withAuth(username, password, AuthScheme.BASIC)
      .post(json)
    futureResponse.map {
      response =>
        response.status match {
          case 200 => Success(response.body)
          case other => Failure(new RuntimeException("Unable to perform search, HTTP Code " + response.status + ", ElasticSearch responded " + response.body))
        }
    }
  }


  // This is interesting if you want to build a cloud of Words.
  def getTag(index: String) = {

    val json: String =
      """
        |{
        |  "query" : {
        |     "match_all" : {}
        |  },
        |  "facets" : {
        |     "tags" : {
        |       "terms" : {
        |         "fields" : ["summary"],
        |         "size":100,
        |         "exclude": ["a","on","de","et","les","la","des","pour","le","en","un","à","vous","une","est","dans","cette",
        |        "du","que","avec","comment","nous","sur","ce","plus","qui","au","ou","il","votre","pas",
        |        "mais","par","applications","ne","tout","présentation","faire","vos","peut","sont","you",
        |        "si","aussi","se","son","can","ces","je","bien","être","tous","comme","we","sans","mettre",
        |        "verrons","permet","quelques","avez","aux","y","travers","notre","entre",
        |        "cet","ont","même","mise","soit","permettant","développeur","also",
        |        "your", "quand", "temps", "systèmes", "data", "système","permettent",
        |        "réel", "hands", "facile", "rencontre", "puissance", "outils","peuvent","etc",
        |        "minutes", "why", "who", "webs", "vivre", "vite", "tour", "time","l'occasion",
        |        "testez", "sérialisez", "suite", "side", "recommandation","d’un","qu'il",
        |        "programming", "programmation", "pourquoi","cela","like","point","chaque","bonnes","ans","when",
        |        "alors","lors","leur","leurs","pourtant","peu","elle","il","là","toutes",
        |        "venez", "temps", "ses", "talk", "sa", "allons", "all", "différents",
        |        "mieux", "have", "propose", "new", "place", "également", "fait","from",
        |        "about", "base", "autres", "très", "ça", "what", "some", "do", "want", "using",
        |        "s","so", "7","2", "8", "30", "them", "session", "application", "moins","moi","ainsi",
        |        "how", "c'est", "d'un", "d'une", "souvent", "depuis", "sera", "cas",  "après", "sous", "encore",
        |        "non", "use","n'est", "utilisateurs", "utilisant","more","plusieurs","nombreux","été","vie",
        |        "i","look","has","grâce","différentes","take","toute","get","devient","afin","surtout","toujours",
        |        "via","tels","avons","d'expérience",
        |        "va","user","seront","déjà","mode","avoir","most",
        |        "où","mon","see","which","quel","donc", "nos","d'applications", "aujourd'hui", "used", "learn",
        |        "and","the","or","to","of","in","this",
        |        "is", "for", "with",  "it", "will", "that",  "but",
        |         "as",  "an", "are", "be", "by","at", "these", "quels", "not", "enfin", "c’est"
        |        ] } }
        |       }
        |     }
        |  }
        |}
      """.stripMargin

    val futureResponse = WS.url(host + "/" + index + "/_search?search_type=count")
      .withAuth(username, password, AuthScheme.BASIC)
      .post(json)
    futureResponse.map {
      response =>
        response.status match {
          case 201 => Success(response.body)
          case 200 => Success(response.body)
          case other => Failure(new RuntimeException("Unable to load tag, HTTP Code " + response.status + ", ElasticSearch responded " + response.body))
        }
    }
  }

  def doStats(zeQuery: String, index: String, maybeUserFilter: Option[String]) = {
    val json: String =
      s"""
        |{
        |  "from" : 0, "size" : 10,
        |   $zeQuery
        |   , "facets" : {
        |       "villeFacet" : {
        |        "terms" : {
        |           "field" : "ville",
        |           "all_terms":false,
        |           "order" : "count",
        |           "size":50
        |         }
        |         ${maybeUserFilter.getOrElse("")}
        |      },
        |     "idRaisonAppelFacet" : {
        |        "terms" : {
        |          "field" : "idRaisonAppel",
        |          "all_terms":true,
        |          "order" : "term",
        |          "size":50
        |        }
        |        ${maybeUserFilter.getOrElse("")}
        |      },
        |      "clotureFacet":{
        |       "terms" : {
        |         "field" : "cloture"
        |       }
        |       ${maybeUserFilter.getOrElse("")}
        |     },
        |      "statusFacet" : {
        |        "terms" : {
        |          "field" : "status",
        |          "all_terms":true,
        |          "order" : "term",
        |          "size":20
        |        }
        |        ${maybeUserFilter.getOrElse("")}
        |      },
        |      "agenceFacet" : {
        |        "terms" : {
        |          "field" : "idAgence",
        |          "all_terms":false,
        |          "order" : "term",
        |          "size":50
        |        }
        |       ${maybeUserFilter.getOrElse("")}
        |      },
        |     "histoWeek" : {
        |        "date_histogram" : {
        |          "field" : "dateSaisie",
        |          "interval" : "day"
        |        }
        |        ${maybeUserFilter.getOrElse("")}
        |     },
        |     "statsTicket":{
        |       "statistical":{
        |         "field":"delaiIntervention"
        |       }
        |       ${maybeUserFilter.getOrElse("")}
        |     },
        |     "typeInterFacet" : {
        |      "terms":{
        |        "field":"delaiStatus",
        |        "size":100
        |       }
        |       ${maybeUserFilter.getOrElse("")}
        |     }
        |     ,
        |     "statsAgeFacet" : {
        |      "statistical":{
        |         "field":"age"
        |       }
        |       ${maybeUserFilter.getOrElse("")}
        |     }
        |     ,
        |     "statsReactionFacet" : {
        |      "statistical":{
        |         "field":"tempsReactionToMinute"
        |       }
        |       ${maybeUserFilter.getOrElse("")}
        |     }
        |   }
        | }
      """.stripMargin


    if (play.Logger.of("ElasticSearch").isDebugEnabled) {
      play.Logger.of("ElasticSearch").debug("Sending to ES request:")
      play.Logger.of("ElasticSearch").debug(json)
    }

    val futureResponse = WS.url(host + "/" + index + "/_search").withRequestTimeout(4000).post(json)
    futureResponse.map {
      response =>
        response.status match {
          case 200 => Success(response.body)
          case other => Failure(new RuntimeException("Unable to index, HTTP Code " + response.status + ", ElasticSearch responded " + response.body))
        }
    }
  }
}


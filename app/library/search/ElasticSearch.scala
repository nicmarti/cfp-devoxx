package library.search

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

  def createIndexWithSettings(index: String, settings: String) = {
    println("Create index with settings")
    val url = s"$host/${index.toLowerCase}"
    val futureResponse = WS.url(url).withRequestTimeout(6000).post(settings)
    futureResponse.map {
      response =>
        response.status match {
          case 201 =>
            println("Created index "+response.body)
            Success(response.body)
          case 200 =>
            println("OK index "+response.body)
            Success(response.body)
          case other =>
            play.Logger.of("library.ElasticSearch").warn("Unable to create index with settings due to "+response.body)
            Failure(new RuntimeException("Unable to createSettings, HTTP Code " + response.status + ", ElasticSearch responded " + response.body))
        }
    }
  }

  // PUT /speakers/speaker/_mapping?ignore_conflicts=true
  def createMapping(index: String, mapping: String) = {
    val url = s"$host/$index/_mapping?ignore_conflicts=true"
    val futureResponse = WS.url(url).withRequestTimeout(6000).put(mapping)
    futureResponse.map {
      response =>
        response.status match {
          case 201 => Success(response.body)
          case 200 => Success(response.body)
          case other => Failure(new RuntimeException(s"Unable to createMapping for $index, HTTP Code " + response.status + ", ElasticSearch responded " + response.body))
        }
    }
  }

  def refresh()={
    // http://localhost:9200/_refresh
    val url = s"$host/_refresh"
    val futureResponse = WS.url(url).withRequestTimeout(6000).post("{}")
    futureResponse.map {
      response =>
        response.status match {
          case 201 => Success(response.body)
          case 200 => Success(response.body)
          case other => Failure(new RuntimeException("Unable to createMapping, HTTP Code " + response.status + ", ElasticSearch responded " + response.body))
        }
    }
  }

  def indexBulk(json: String, indexName:String) = {
    if (play.Logger.of("library.ElasticSearch").isDebugEnabled) {
      play.Logger.of("library.ElasticSearch").debug(s"Bulk index started to $host")
    }

    val futureResponse = WS.url(s"$host/$indexName/_bulk?refresh=true")
      .withAuth(username, password, AuthScheme.BASIC)
      .post(json)
    futureResponse.map {
      response =>
        response.status match {
          case 201 =>
             if (play.Logger.of("library.ElasticSearch").isDebugEnabled) {
               play.Logger.of("library.ElasticSearch").debug("Bulk index created")
             }
            Success(response.body)
          case 200 =>
            if (play.Logger.of("library.ElasticSearch").isDebugEnabled) {
               play.Logger.of("library.ElasticSearch").debug("Bulk index created")
             }
            Success(response.body)
          case other => Failure(new RuntimeException("Unable to bulk import, HTTP Code " + response.status + ", ElasticSearch responded " + response.body))
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

    val json: String =
      s"""
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
        |         "exclude": ["how","what","you", "we", "can", "your", "talk", "from",
        |         "session", "have", "use", "all", "using", "about", "like", "also",
        |         "more", "new", "some", "has", "which", "one", "do", "i",
        |         "when", "so", "many", "our", "make", "used", "presentation", "based", "them",
        |         "most", "way", "see", "other", "open", "get", "real", "through", "features",
        |         "out", "need", "well", "world", "up", "8",  "look", "been", "its", "even", "just",
        |         "work", "want", "us", "own", "over",  "both", "write", "where", "take",
        |         "should", "come", "show", "while", "provide","much","than",
        |         "years","year","one","two","three","lot","any","live","still","very","each","we'll",
        |         "several","provides", "same","those","really","next","first","give","few",
        |         "would","now","end","does","only","my","makes"
        |         ]
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
}


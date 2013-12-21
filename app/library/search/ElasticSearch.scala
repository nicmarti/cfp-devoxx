package library.search

import play.api.libs.ws.WS
import play.api.libs.json.Json
import akka.actor._
import play.api.Play.current
import controllers.routes
import play.api.libs.concurrent.Execution.Implicits._
import scala.util.{Try, Failure, Success}
import java.net.URLEncoder
import scala.concurrent.{Future, Promise}

/**
 * Wrapper and helper, to reuse the ElasticSearch REST API.
 *
 * Author: nicolas martignole
 * Created: 23/09/2013 12:31
 */
object ElasticSearch {
  def index(index: String, json: String) = {
    val futureResponse = WS.url("http://localhost:9200/" + index + "?ttl=1d").put(json)
    futureResponse.map {
      response =>
        response.status match {
          case 201 => Success(response.body)
          case 200 => Success(response.body)
          case other => Failure(new UnknownError("Unable to index, HTTP Code " + response.status + ", ElasticSearch responded " + response.body))
        }
    }
  }


  def doSearch(query: String):Future[Try[String]] = {
     val serviceParams = Seq(("q", query))
     val futureResponse = WS.url("http://localhost:9200/_search").withQueryString(serviceParams: _*).get()
     val prom=futureResponse.map {
       response =>
         response.status match {
           case 200 => Success(response.body)
           case other => Failure(new UnknownError("Unable to index, HTTP Code " + response.status + ", ElasticSearch responded " + response.body))
         }
     }
     prom
   }

  def doSearch(index: String, query: String) = {
    val serviceParams = Seq(("q", query))
    val futureResponse = WS.url("http://localhost:9200/" + index + "/_search").withQueryString(serviceParams: _*).get()
    futureResponse.map {
      response =>
        response.status match {
          case 200 => Success(response.body)
          case other => Failure(new UnknownError("Unable to index, HTTP Code " + response.status + ", ElasticSearch responded " + response.body))
        }
    }
  }
}

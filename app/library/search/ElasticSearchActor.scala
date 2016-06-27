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

import java.io.{PrintWriter, FileWriter, File}

import play.api.libs.json.Json
import akka.actor._
import play.api.libs.concurrent.Execution.Implicits._
import models._
import org.joda.time.{DateTimeZone, DateMidnight, DateTime}

import scala.concurrent.Future
import scala.util.Try

/**
 * ElasticSearch Akka Actor. Yes, I should write more doc, I know.
 * Give me a beer and I'll explain how does it work.
 *
 * Author: nicolas martignole
 * Created: 20 dec 2013.
 */
object ElasticSearchActor {
  val system = ActorSystem("ElasticSearch")
  val masterActor = system.actorOf(Props[IndexMaster], "masterActorIndex")
  val reaperActor = system.actorOf(Props[Reaper], "reaperActor")
}

// Messages
sealed class ElasticSearchRequest

trait ESType {
  def path: String

  def id: String

  def label: String = id

  def toJson: play.api.libs.json.JsValue
}

case class ESSpeaker(speaker: Speaker) extends ESType {

  import models.Speaker.speakerFormat

  def toJson = Json.toJson(speaker)

  def path = "/speakers/speaker"

  def id = speaker.uuid

  override def label = speaker.cleanName
}

case class ESProposal(proposal: Proposal) extends ESType {

  import models.Proposal.proposalFormat

  def toJson = Json.toJson(proposal)

  def path = "/proposals/proposal"

  def id = proposal.id
}

case class DoIndexProposal(proposal: Proposal)

case object DoIndexAllProposals

case object DoIndexAllReviews

case class DoIndexSpeaker(speaker: Speaker)

case object DoIndexAllSpeakers

case object DoIndexAllAccepted

case object DoIndexAllHitViews

case class Index(obj: ESType)

case object StopIndex

case object DoCreateConfigureIndex

trait ESActor extends Actor {

  import scala.language.implicitConversions

  implicit def SpeakerToESSpeaker(speaker: Speaker) = ESSpeaker(speaker)

  implicit def ProposalToESProposal(proposal: Proposal) = ESProposal(proposal)
}

// Main actor for dispatching
class IndexMaster extends ESActor {
  def receive = {
    case DoIndexSpeaker(speaker: Speaker) => doIndexSpeaker(speaker)
    case DoIndexAllSpeakers => doIndexAllSpeakers()
    case DoIndexProposal(proposal: Proposal) => doIndexProposal(proposal)
    case DoIndexAllProposals => doIndexAllProposals()
    case DoIndexAllAccepted => doIndexAllAccepted()
    case DoIndexAllHitViews => doIndexAllHitViews()
    case StopIndex => stopIndex()
    case DoCreateConfigureIndex => doCreateConfigureIndex()
    case other => play.Logger.of("application.IndexMaster").error("Received an invalid actor message: " + other)
  }

  def stopIndex() {
    ElasticSearchActor.reaperActor ! akka.actor.PoisonPill
  }

  def doIndexSpeaker(speaker: Speaker) {
    play.Logger.of("application.IndexMaster").debug("Do index speaker")

    ElasticSearchActor.reaperActor ! Index(speaker)

    play.Logger.of("application.IndexMaster").debug("Done indexing speaker")
  }


  def doIndexAllSpeakers() {
    play.Logger.of("application.IndexMaster").debug("Do index speaker")

    val speakers = Speaker.allSpeakers()

    val sb = new StringBuilder
    speakers.foreach {
      speaker: Speaker =>
        sb.append("{\"index\":{\"_index\":\"speakers\",\"_type\":\"speaker\",\"_id\":\"" + speaker.uuid + "\"}}")
        sb.append("\n")
        sb.append(Json.toJson(speaker))
        sb.append("\n")
    }
    sb.append("\n")

    ElasticSearch.indexBulk(sb.toString(), "speakers")

    play.Logger.of("application.IndexMaster").debug("Done indexing all speakers")
  }

  def doIndexProposal(proposal: Proposal) {
    play.Logger.of("application.IndexMaster").debug("Do index proposal")
    ElasticSearchActor.reaperActor ! Index(proposal)

    play.Logger.of("application.IndexMaster").debug("Done indexing proposal")
  }

  def doIndexAllProposals() {
    play.Logger.of("application.IndexMaster").debug("Do index all proposals")

    val allAccepted = Proposal.allAccepted()
    val allSubmitted = Proposal.allSubmitted()

    if(play.Logger.of("application.IndexMaster").isDebugEnabled){
      play.Logger.of("application.IndexMaster").debug(s"Indexing ${allAccepted.size} accepted proposals")
      play.Logger.of("application.IndexMaster").debug(s"Indexing ${allSubmitted.size} submitted proposals")
    }

    // We cannot index all proposals, if the size > 1mb then Elasticsearch on clevercloud
    // returns a 413 Entity too large
    allAccepted.sliding(200, 200).foreach { gop =>
      indexProposalsToElasticSearch(gop)
    }

    allSubmitted.sliding(200, 200).foreach { groupOfProposals =>
      indexProposalsToElasticSearch(groupOfProposals)
    }

    ElasticSearch.refresh()

    play.Logger.of("application.IndexMaster").debug("Indexed all proposals")
  }

  private def indexProposalsToElasticSearch(proposals: List[Proposal]) = {
    if (play.Logger.of("application.IndexMaster").isDebugEnabled) {
      play.Logger.of("application.IndexMaster").debug("Indexing proposals " + proposals.size)
    }
    val sb = new StringBuilder
    proposals.foreach {
      proposal: Proposal =>
        sb.append("{\"index\":{\"_index\":\"proposals\",\"_type\":\"proposal\",\"_id\":\"" + proposal.id + "\"}}")
        sb.append("\n")
        sb.append(Json.toJson(
          proposal.copy(privateMessage = "",
          mainSpeaker = Speaker.findByUUID(proposal.mainSpeaker).map(_.cleanName).getOrElse(proposal.mainSpeaker),
          secondarySpeaker = proposal.secondarySpeaker.flatMap(s=>Speaker.findByUUID(s).map(_.cleanName)),
          otherSpeakers = proposal.otherSpeakers.flatMap(s=>Speaker.findByUUID(s).map(_.cleanName))
          )
        )) // do not index the private message
        sb.append("\n")
    }
    sb.append("\n")

    ElasticSearch.indexBulk(sb.toString(), "proposals")
  }

  def doIndexAllAccepted() {
    val proposals = Proposal.allApproved()++Proposal.allAccepted()

    val indexName = ApprovedProposal.elasticSearchIndex()
    play.Logger.of("application.IndexMaster").debug(s"Do index all accepted ${proposals.size} to index $indexName")

    val sb = new StringBuilder
    proposals.foreach {
      proposal: Proposal =>
        sb.append("{\"index\":{\"_index\":\"")
        sb.append(indexName)
        sb.append("\",\"_type\":\"proposal\",\"_id\":\"" + proposal.id + "\"}}")
        sb.append("\n")
        sb.append(Json.toJson(proposal.copy(
          privateMessage = "",
          mainSpeaker = Speaker.findByUUID(proposal.mainSpeaker).map(_.cleanName).getOrElse(proposal.mainSpeaker),
          secondarySpeaker = proposal.secondarySpeaker.flatMap(s=>Speaker.findByUUID(s).map(_.cleanName)),
          otherSpeakers = proposal.otherSpeakers.flatMap(s=>Speaker.findByUUID(s).map(_.cleanName))
        )))
        sb.append("\n")
    }
    sb.append("\n")

    ElasticSearch.indexBulk(sb.toString(), indexName)

    play.Logger.of("application.IndexMaster").debug("Done indexing all acceptedproposals")
  }

  def doIndexAllReviews() {
    play.Logger.of("application.IndexMaster").debug("Do index all reviews")

    val reviews = models.Review.allVotes()

    val sb = new StringBuilder
    reviews.foreach {
      case (proposalId, reviewAndVotes) =>
        Proposal.findById(proposalId).map {
          proposal=>
          sb.append("{\"index\":{\"_index\":\"reviews\",\"_type\":\"review\",\"_id\":\"" + proposalId + "\"}}")
          sb.append("\n")
          sb.append("{")
          sb.append("\"totalVoters\": " + reviewAndVotes._2 + ", ")
          sb.append("\"totalAbstentions\": " + reviewAndVotes._3 + ", ")
          sb.append("\"average\": " + reviewAndVotes._4 + ", ")
          sb.append("\"standardDeviation\": " + reviewAndVotes._5 + ", ")
          sb.append("\"title\": \"" + proposal.title + "\",")
          sb.append("\"track\": \"" + proposal.track.id + "\",")
          sb.append("\"lang\": \"" + proposal.lang + "\",")
          sb.append("\"sponsor\": \"" + proposal.sponsorTalk + "\",")
          sb.append("\"type\": \"" + proposal.talkType.id + "\"")
          sb.append("}\n")
          sb.append("\n")
        }
    }
    sb.append("\n")

    ElasticSearch.indexBulk(sb.toString(), "reviews")

    play.Logger.of("application.IndexMaster").debug("Done indexing all proposals")
  }

  def doIndexAllHitViews() {

    ElasticSearch.deleteIndex("hitviews")

    HitView.allStoredURL().foreach {
      url =>
        val hits = HitView.loadHitViews(url, new DateMidnight().toDateTime(DateTimeZone.forID("Europe/Brussels")).minusDays(1).toDateTime, new DateTime().toDateTime(DateTimeZone.forID("Europe/Brussels")))

        val sb = new StringBuilder
        hits.foreach {
          hit: HitView =>
            sb.append("{\"index\":{\"_index\":\"hitviews\", \"_type\":\"hitview\",\"_id\":\"" + hit.hashCode().toString + "\", \"_timestamp\":{\"enabled\":true}}}")
            sb.append("\n")
            val date = new DateTime(hit.date * 1000).toDateTime(DateTimeZone.forID("Europe/Brussels")).toString()
            sb.append("{\"@tags\":\"").append(hit.url).append("\",\"@messages\":\"")
            //.append(hit.objRef).append(" ")
            sb.append(hit.objName.replaceAll("[-,\\s+]", "_")).append("\",\"@timestamp\":\"").append(date).append("\"}")
            sb.append("\n")
        }
        sb.append("\n")

        ElasticSearch.indexBulk(sb.toString(), "hitviews")

    }
  }

  def doCreateConfigureIndex() = {
    val maybeSuccess = _createConfigureIndex()
    maybeSuccess.map {
      case r if r.isSuccess =>
        play.Logger.of("library.ElasticSearch").info(s"Configured indexes on ES for speaker and proposal. Result : " + r.get)
      case r if r.isFailure =>
        play.Logger.of("library.ElasticSearch").warn(s"Error $r")
    }
  }

  // Set the analyzer to français if the content is not in English
  private val speakerJsonMapping: String = {
    s"""
      "speaker": {
                "properties": {
                    "avatarUrl": {
                        "type": "string",
                        "index" : "not_analyzed"
                    },
                    "bio": {
                        "type": "string",
                        "analyzer":"francais"
                    },
                    "blog": {
                        "type": "string",
                        "index" : "not_analyzed"
                    },
                    "company": {
                        "type": "string"
                    },
                    "email": {
                        "type": "string"
                    },
                    "firstName": {
                        "type": "string"
                    },
                    "lang": {
                        "type": "string",
                        "analyzer": "analyzer_keyword"
                    },
                    "name": {
                        "type": "string"
                    },
                    "qualifications": {
                        "type": "string",
                        "analyzer":"francais"
                    },
                    "twitter": {
                        "type": "string",
                        "analyzer": "analyzer_keyword"
                    },
                    "uuid": {
                        "type": "string",
                        "index" : "not_analyzed"
                    }
                }
            }
     """.stripMargin
  }

  private val proposalJsonMapping: String = {
    s"""
    "proposal": {
        "properties": {
            "audienceLevel": {
                "type": "string",
                "index": "not_analyzed"
            },
            "demoLevel": {
                "type": "string",
                "index": "not_analyzed"
            },
            "event": {
                "type": "string",
                "index": "no",
                "store": "no"
            },
            "id": {
                "type": "string",
                "index": "not_analyzed"
            },
            "lang": {
                "type": "string",
                "index": "not_analyzed"
            },
            "mainSpeaker": {
                "type": "string"
            },
            "otherSpeakers": {
                "type": "string"
            },
            "privateMessage": {
                "type": "string",
                "index": "no",
                "store": "no"
            },
            "secondarySpeaker": {
                "type": "string"
            },
            "sponsorTalk": {
                "type": "boolean",
                "index": "not_analyzed"
            },
            "state": {
                "properties": {
                    "code": {
                        "type": "string"
                    }
                }
            },
            "summary": {
                "type": "string",
                "analyzer": "francais"
            },
            "talkType": {
                "properties": {
                    "id": {
                        "type": "string",
                        "index": "not_analyzed"
                    },
                    "label": {
                        "type": "string",
                        "index": "no"
                    }
                }
            },
            "title": {
                "stored":"true",
                "search_analyzer":"analyzer_startswith",
                "index_analyzer":"analyzer_startswith",
                "type":"string"
            },
            "track": {
                "properties": {
                    "id": {
                        "type": "string",
                        "index": "not_analyzed"
                    },
                    "label": {
                        "type": "string",
                        "index": "no"
                    }
                }
            },
            "userGroup": {
                "type": "boolean",
                "index": "not_analyzed"
            }
        }
    }
""".stripMargin
  }

  private def _createConfigureIndex(): Future[Try[String]] = {
    // This is important for French content
    // Leave it, even if your CFP is in English
    def settingsFrench =
    """
        |    {
        |    	"settings" : {
        |    		"index":{
        |    			"analysis":{
        |    				"analyzer":{
        |              "analyzer_keyword":{
        |                 "tokenizer":"keyword",
        |                 "filter":"lowercase"
        |              },
        |              "analyzer_startswith":{
        |                      "tokenizer":"keyword",
        |                      "filter":"lowercase"
        |             },
        |    					"francais":{
        |    						"type":"custom",
        |    						"tokenizer":"standard",
        |    						"filter":["lowercase", "fr_stemmer", "stop_francais", "asciifolding", "elision"]
        |    					}
        |    				},
        |    				"filter":{
        |    					"stop_francais":{
        |    						"type":"stop",
        |    						"stopwords":["_french_"]
        |    					},
        |    					"fr_stemmer" : {
        |    						"type" : "stemmer",
        |    						"name" : "french"
        |    					},
        |    					"elision" : {
        |    						"type" : "elision",
        |    						"articles" : ["l", "m", "t", "qu", "n", "s", "j", "d"]
        |    					}
        |    				}
        |    			}
        |    		}
        |    	}
        | }
      """.stripMargin


    def settingsProposalsEnglish =
      s"""
        |{
        |    "mappings": {
        |     $proposalJsonMapping
        |    },
        |    "settings": {
        |        "index": {
        |            "analysis": {
        |                "analyzer": {
        |                    "english": {
        |                        "type": "custom",
        |                        "tokenizer": "standard",
        |                        "filter": [
        |                            "standard",
        |                            "lowercase",
        |                            "english_stop"
        |                        ]
        |                    },
        |                    "analyzer_keyword":{
        |                       "tokenizer":"keyword",
        |                       "filter":"lowercase"
        |                     },
        |                    "analyzer_startswith":{
        |                      "tokenizer":"keyword",
        |                      "filter":"lowercase"
        |                    }
        |                },
        |                "filter": {
        |                    "english_stop": {
        |                        "type": "stop",
        |                        "stopwords": "_english_"
        |                    }
        |                }
        |            }
        |        }
        |    }
        |}
      """.stripMargin

    def settingsSpeakersEnglish =
      s"""
        |{
        |    "mappings": {
        |     $speakerJsonMapping
        |    },
        |    "settings": {
        |        "index": {
        |            "analysis": {
        |                "analyzer": {
        |                    "english": {
        |                        "type": "custom",
        |                        "tokenizer": "standard",
        |                        "filter": [
        |                            "standard",
        |                            "lowercase",
        |                            "english_stop"
        |                        ]
        |                    },
        |                    "analyzer_keyword":{
        |                       "tokenizer":"keyword",
        |                       "filter":"lowercase"
        |                     }
        |                },
        |                "filter": {
        |                    "english_stop": {
        |                        "type": "stop",
        |                        "stopwords": "_english_"
        |                    }
        |                }
        |            }
        |        }
        |    }
        |}
      """.stripMargin


    // We use a for-comprehension on purporse so that each action is executed sequentially.
    // res2 is executed when res1 is done
    val resFinal = for (res1 <- ElasticSearch.deleteIndex("proposals");
                        res2 <- ElasticSearch.createIndexWithSettings("proposals", settingsFrench)
    ) yield {
      res2
    }

     val resFinal2 = for (res1 <- ElasticSearch.deleteIndex("acceptedproposals_fr2016");
                        res2 <- ElasticSearch.createIndexWithSettings("acceptedproposals_fr2016", settingsFrench)
    ) yield {
      res2
    }

    val resFinalSpeakers = for (res1 <- ElasticSearch.deleteIndex("speakers");
                                res2 <- ElasticSearch.createIndexWithSettings("speakers", settingsFrench)

    ) yield {
      res2
    }

    resFinal
  }


}

// Actor that is in charge of Indexing content
class Reaper extends ESActor {
  def receive = {
    case Index(obj: ESType) => doIndex(obj)
    case other => play.Logger.of("application.Reaper").warn("unknown message received " + other)
  }

  import scala.util.Try
  import scala.concurrent.Future

  def doIndex(obj: ESType) =
    logResult(obj, sendRequest(obj))

  def sendRequest(obj: ESType): Future[Try[String]] =
    ElasticSearch.index(obj.path + "/" + obj.id, Json.stringify(obj.toJson))

  def logResult(obj: ESType, maybeSuccess: Future[Try[String]]) =
    maybeSuccess.map {
      case r if r.isSuccess =>
        play.Logger.of("application.Reaper").debug(s"Indexed ${obj.getClass.getSimpleName} ${obj.label}")
      case r if r.isFailure =>
        play.Logger.of("application.Reaper").warn(s"Could not index speaker ${obj} due to ${r}")
    }
}

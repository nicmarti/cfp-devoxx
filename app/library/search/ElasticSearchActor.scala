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

import akka.actor._
import models._
import org.apache.commons.lang3.StringEscapeUtils
import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.{JsValue, Json}

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

/**
  * ElasticSearch Akka Actor. Yes, I should write more doc, I know.
  * Give me a beer and I'll explain how does it work.
  *
  * Author: nicolas martignole
  * Created: 20 dec 2013.
  */
object ElasticSearchActor {
  val system = ActorSystem("ElasticSearch")
  val masterActor: ActorRef = system.actorOf(Props[IndexMaster], "masterActorIndex")
  val reaperActor: ActorRef = system.actorOf(Props[Reaper], "reaperActor")
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

  def toJson: JsValue = Json.toJson(speaker)

  def path = "/speakers/speaker"

  def id: String = speaker.uuid

  override def label: String = speaker.cleanName
}

case class ESProposal(proposal: Proposal) extends ESType {

  import models.Proposal.proposalFormat

  def toJson: JsValue = Json.toJson(proposal)

  def path = "/proposals/proposal"

  def id: String = proposal.id
}

case class DoIndexProposal(proposal: Proposal)

case object DoIndexAllProposals

case object DoIndexAllReviews

case class DoIndexSpeaker(speaker: Speaker)

case object DoIndexAllSpeakers

case object DoIndexAllAccepted

case object DoIndexAllHitViews

case object DoIndexSchedule

case class Index(obj: ESType)

case object StopIndex

case object DoCreateConfigureIndex

trait ESActor extends Actor {

  import scala.language.implicitConversions

  implicit def SpeakerToESSpeaker(speaker: Speaker): ESSpeaker = ESSpeaker(speaker)

  implicit def ProposalToESProposal(proposal: Proposal): ESProposal = ESProposal(proposal)
}

// Main actor for dispatching
class IndexMaster extends ESActor {
  def receive: PartialFunction[Any, Unit] = {
    case DoIndexSpeaker(speaker: Speaker) => doIndexSpeaker(speaker)
    case DoIndexAllSpeakers => doIndexAllSpeakers()
    case DoIndexProposal(proposal: Proposal) => doIndexProposal(proposal)
    case DoIndexAllProposals => doIndexAllProposals()
    case DoIndexAllAccepted => doIndexAllAccepted()
    case DoIndexAllHitViews => doIndexAllHitViews()
    case DoIndexSchedule => doIndexSchedule()
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

    if (play.Logger.of("application.IndexMaster").isDebugEnabled) {
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
            secondarySpeaker = proposal.secondarySpeaker.flatMap(s => Speaker.findByUUID(s).map(_.cleanName)),
            otherSpeakers = proposal.otherSpeakers.flatMap(s => Speaker.findByUUID(s).map(_.cleanName))
          )
        )
        ) // do not index the private message
        sb.append("\n")
    }
    sb.append("\n")

    ElasticSearch.indexBulk(sb.toString(), "proposals")
  }

  def doIndexAllAccepted() {
    val proposals = Proposal.allApproved() ++ Proposal.allAccepted()

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
          secondarySpeaker = proposal.secondarySpeaker.flatMap(s => Speaker.findByUUID(s).map(_.cleanName)),
          otherSpeakers = proposal.otherSpeakers.flatMap(s => Speaker.findByUUID(s).map(_.cleanName))
        )))
        sb.append("\n")
    }
    sb.append("\n")

    ElasticSearch.indexBulk(sb.toString(), indexName)

    play.Logger.of("application.IndexMaster").debug("Done indexing all acceptedproposals")
  }

  // Create an ES index with the agenda
  def doIndexSchedule(): Unit = {
    val allAgendas = ScheduleConfiguration.loadAllConfigurations()
    val slots = allAgendas.flatMap(_.slots).filterNot(_.break.isDefined)
    val indexName = "schedule_" + ConferenceDescriptor.current().confUrlCode

    play.Logger.of("application.IndexMaster").debug(s"Send to index [$indexName] ${slots.size} slots for schedule")
    val sb = new StringBuilder
    slots.foreach {
      slot: Slot =>

        val secondarySpeaker: Option[String] = slot.proposal.flatMap {
          p =>
            p.secondarySpeaker.flatMap {
              uuid: String =>
                Speaker.findByUUID(uuid).map {
                  speaker =>
                   speaker.cleanName
                }
            }
        }
        val otherSpeakers: Option[String] = slot.proposal.map {
          p =>
            p.otherSpeakers.map {
              uuid: String =>
                Speaker.findByUUID(uuid).map {
                  speaker =>
                    speaker.cleanName
                }
            }.mkString(",")
        }

        sb.append("{\"index\":{\"_index\":\"")
        sb.append(indexName)
        sb.append("\",\"_type\":\"schedule\",\"_id\":\"" + slot.id + "\"}}")
        sb.append("\n")
        sb.append(
          s"""{
             | "name":"${slot.name}",
             | "day":"${slot.day}",
             | "from":"${slot.from}",
             | "to":"${slot.to}",
             | "room":"${slot.room.name}",
             | "title":"${StringEscapeUtils.escapeJson(slot.proposal.map(_.title).getOrElse(""))}",
             | "summary":"${StringEscapeUtils.escapeJson(slot.proposal.map(_.summary.replaceAll("\r\n", "")).getOrElse(""))}",
             | "track":${slot.proposal.map(p => Json.toJson(p.track).toString).getOrElse("")},
             | "talkType":${slot.proposal.map(p => Json.toJson(p.talkType).toString).getOrElse("")},
             | "mainSpeaker":${slot.proposal.flatMap(p => Speaker.findByUUID(p.mainSpeaker).map(s => "\"" + s.cleanName + "\"")).getOrElse("")},
             | "secondarySpeaker": "${secondarySpeaker.getOrElse("")}",
             | "otherSpeakers": "${otherSpeakers.getOrElse("")}",
             | "company": "${slot.proposal.map(p => p.allSpeakers.map(s =>  s.company.getOrElse("") ).mkString(", ")).getOrElse("")}"
             |}
          """.stripMargin.replaceAll("\n", ""))
        sb.append("\n")
    }
    sb.append("\n")

     if (play.Logger.of("library.ElasticSearch").isDebugEnabled) {
       play.Logger.of("library.ElasticSearch").debug("---------------- ES Actor")
       play.Logger.of("library.ElasticSearch").debug(sb.toString())
       play.Logger.of("library.ElasticSearch").debug("---------------- ES Actor")
     }

    ElasticSearch.indexBulk(sb.toString(), indexName).map {
      case Success(ok) =>
        play.Logger.of("application.IndexMaster").debug(s"Indexed ${slots.size} to $indexName")
      case Failure(ex) =>
        play.Logger.of("application.IndexMaster").error(s"Could not indexed ${slots.size} to $indexName due to ${ex.getMessage}", ex)
    }

    play.Logger.of("application.IndexMaster").debug(s"Done indexing schedule to index $indexName")

  }

  def doIndexAllReviews() {
    play.Logger.of("application.IndexMaster").debug("Do index all reviews")

    val reviews = models.Review.allVotes()

    val sb = new StringBuilder
    reviews.foreach {
      case (proposalId, reviewAndVotes) =>
        Proposal.findById(proposalId).map {
          proposal =>
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

    play.Logger.of("application.IndexMaster").debug("Done indexing all reviews")
  }

  def doIndexAllHitViews() {

    ElasticSearch.deleteIndex("hitviews")

    HitView.allStoredURL().foreach {
      url =>
        val todayMidnight:DateTime = DateTime.now(ConferenceDescriptor.current().timezone).withTimeAtStartOfDay()
        val yesterdayMidnight:DateTime = todayMidnight.minusDays(1)
        val hits = HitView.loadHitViews(url, yesterdayMidnight, todayMidnight)

        val sb = new StringBuilder
        hits.foreach {
          hit: HitView =>
            sb.append("{\"index\":{\"_index\":\"hitviews\", \"_type\":\"hitview\",\"_id\":\"" + hit.hashCode().toString + "\", \"_timestamp\":{\"enabled\":true}}}")
            sb.append("\n")
            val date = new DateTime(hit.date * 1000).toDateTime(ConferenceDescriptor.current().timezone).toString()
            sb.append("{\"@tags\":\"").append(hit.url).append("\",\"@messages\":\"")
            sb.append(hit.objName.replaceAll("[-,\\s+]", "_")).append("\",\"@timestamp\":\"").append(date).append("\"}")
            sb.append("\n")
        }
        sb.append("\n")

        ElasticSearch.indexBulk(sb.toString(), "hitviews")

    }
  }

  def doCreateConfigureIndex(): Future[Unit] = {
    _createConfigureIndex("proposals", settingsFrench).map {
      case r if r.isSuccess =>
        play.Logger.of("library.ElasticSearch").info(s"Configured indexes on ES for speaker and proposal. Result : " + r.get)
      case r if r.isFailure =>
        play.Logger.of("library.ElasticSearch").warn(s"Error $r")
    }

    _createConfigureIndex(ApprovedProposal.elasticSearchIndex(), settingsFrench).map {
      case Success(r) =>
        play.Logger.of("library.ElasticSearch").info(s"Configured indexes ${ApprovedProposal.elasticSearchIndex()} on ES for speaker and proposal. Result : " + r)
      case Failure(ex) =>
        play.Logger.of("library.ElasticSearch").warn(s"Error with index ${ApprovedProposal.elasticSearchIndex()} $ex", ex)
    }
    _createConfigureIndex("speakers", settingsFrench).map {
      case Success(r) =>
        play.Logger.of("library.ElasticSearch").info(s"Configured indexes speakers on ES for speaker and proposal. Result : " + r)
      case Failure(ex) =>
        play.Logger.of("library.ElasticSearch").warn(s"Error with index speakers $ex", ex)
    }

    val scheduleIndexName = "schedule_" + ConferenceDescriptor.current().confUrlCode

    _createConfigureIndex(scheduleIndexName, settingsAgenda).map {
      case Success(r) =>
        play.Logger.of("library.ElasticSearch").info(s"Configured indexes $scheduleIndexName on ES for speaker and proposal. Result : " + r)
      case Failure(ex) =>
        play.Logger.of("library.ElasticSearch").warn(s"Error with index $scheduleIndexName $ex", ex)
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

  private val scheduleJsonMapping: String = {
    s"""
       | "schedule": {
       |         "properties": {
       |              "name": {
       |                  "type": "string",
       |                  "analyzer":"english"
       |              },
       |              "day": {
       |                  "type": "string",
       |                  "index" : "not_analyzed"
       |              },
       |              "from": {
       |                  "type": "date",
       |                  "format" : "date_time"
       |              },
       |              "to": {
       |                  "type": "date",
       |                  "format" : "date_time"
       |              },
       |              "room": {
       |                "type": "string",
       |                "analyzer": "english"
       |              },
       |              "title": {
       |                "type": "string",
       |                "analyzer": "francais"
       |              },
       |              "summary": {
       |                "type": "string",
       |                "analyzer": "francais"
       |              },
       |              "track": {
       |                "properties": {
       |                    "id": {
       |                        "type": "string",
       |                        "index": "not_analyzed"
       |                    },
       |                    "label": {
       |                        "type": "string",
       |                        "index": "no"
       |                    }
       |                }
       |             },
       |             "talkType": {
       |                "properties": {
       |                    "id": {
       |                        "type": "string",
       |                        "index": "not_analyzed"
       |                    },
       |                    "label": {
       |                        "type": "string",
       |                        "index": "no"
       |                    }
       |                }
       |            },
       |            "mainSpeaker": {
       |                "type":"string", "index": "francais"
       |            },
       |            "secondarySpeaker": {
       |                "type":"string","index": "francais"
       |            },
       |            "otherSpeakers": {
       |                "type":"string","index": "francais"
       |            },
       |            "company": {
       |                "type":"string"
       |            }
       |         }
       |     }
     """.stripMargin
  }

  // This is important for French content
  // Leave it, even if your CFP is in English
  def settingsFrench: String =
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

  def settingsProposalsEnglish: String =
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

  def settingsSpeakersEnglish: String =
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

  def settingsAgenda: String = settingsFrench

  private def _createConfigureIndex(zeIndexName: String, settings: String): Future[Try[String]] = {

    // We use a for-comprehension on purpose so that each action is executed sequentially.
    // res2 is executed when res1 is done
    val resFinal = for (res1 <- ElasticSearch.deleteIndex(zeIndexName);
                        res2 <- ElasticSearch.createIndexWithSettings(zeIndexName, settings)
    ) yield {
      res1 match {
        case Failure(ex) =>
          play.Logger.of("library.ElasticSearch").warn(s"Unable to delete index $zeIndexName due to ${ex.getMessage}")
        case Success(_) =>
          play.Logger.of("library.ElasticSearch").debug(s"Deleted index $zeIndexName")
      }
      res2 match {
        case Failure(ex) =>
          play.Logger.of("library.ElasticSearch").warn(s"Unable to create index [$zeIndexName] with settings due to ${ex.getMessage}")
        case Success(_) =>
          play.Logger.of("library.ElasticSearch").debug(s"Created index $zeIndexName")
      }
      res2
    }

    resFinal
  }
}

// Actor that is in charge of Indexing content
class Reaper extends ESActor {
  def receive: PartialFunction[Any, Unit] = {
    case Index(obj: ESType) => doIndex(obj)
    case other => play.Logger.of("application.Reaper").warn("unknown message received " + other)
  }

  import scala.concurrent.Future
  import scala.util.Try

  def doIndex(obj: ESType): Future[Unit] =
    logResult(obj, sendRequest(obj))

  def sendRequest(obj: ESType): Future[Try[String]] =
    ElasticSearch.index(obj.path + "/" + obj.id, Json.stringify(obj.toJson))

  def logResult(obj: ESType, maybeSuccess: Future[Try[String]]): Future[Unit] =
    maybeSuccess.map {
      case r if r.isSuccess =>
        play.Logger.of("application.Reaper").debug(s"Indexed ${obj.getClass.getSimpleName} ${obj.label}")
      case r if r.isFailure =>
        play.Logger.of("application.Reaper").warn(s"Could not index speaker $obj due to $r")
    }
}

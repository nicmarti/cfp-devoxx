package library.search

import play.api.libs.json.Json
import akka.actor._
import play.api.libs.concurrent.Execution.Implicits._
import models._
import org.joda.time.DateMidnight
import org.joda.time.DateTime

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

  override def label = speaker.name.getOrElse(speaker.email)
}

case class ESProposal(proposal: Proposal) extends ESType {

  import models.Proposal.proposalFormat

  def toJson = Json.toJson(proposal)

  def path = "/proposals/proposal"

  def id = proposal.id
}


case class ESEvent(event: Event) extends ESType {

  import models.Event.eventFormat

  def toJson = Json.toJson(event)

  def path = "/events/event"

  def id = play.api.libs.Crypto.sign(event.toString)
}


case class DoIndexEvent()

case class DoIndexProposal(proposal: Proposal)

case class DoIndexAllProposals()

case class DoIndexAllReviews()

case class DoIndexSpeaker(speaker: Speaker)

case class DoIndexAllSpeakers()

case class DoIndexAllApproved()

case class DoIndexAllEvents()

case class DoIndexAllHitViews()

case class Index(obj: ESType)

case class StopIndex()

trait ESActor extends Actor {

  import scala.language.implicitConversions

  implicit def SpeakerToESSpeaker(speaker: Speaker) = ESSpeaker(speaker)

  implicit def ProposalToESProposal(proposal: Proposal) = ESProposal(proposal)

  implicit def EventToESEvent(event: Event) = ESEvent(event)
}

// Main actor for dispatching
class IndexMaster extends ESActor {
  def receive = {
    case DoIndexSpeaker(speaker: Speaker) => doIndexSpeaker(speaker)
    case DoIndexAllSpeakers() => doIndexAllSpeakers()
    case DoIndexProposal(proposal: Proposal) => doIndexProposal(proposal)
    case DoIndexAllProposals() => doIndexAllProposals()
    case DoIndexAllApproved() => doIndexAllApproved()
    case DoIndexAllReviews() => doIndexAllReviews()
    case DoIndexAllEvents() => doIndexAllEvents()
    case DoIndexEvent() => doIndexEvent()
    case DoIndexAllHitViews() => doIndexAllHitViews()
    case StopIndex() => stopIndex()
    case other => play.Logger.of("application.IndexMaster").error("Received an invalid actor message: " + other)
  }

  def stopIndex() {
    ElasticSearchActor.reaperActor ! akka.actor.PoisonPill
  }

  def doIndexEvent() {
    play.Logger.of("application.IndexMaster").debug("Do index event")

    // Load events by 100
    val totalEvents = Event.totalEvents()

    for (page <- 0 to totalEvents.toInt / 100) {
      play.Logger.of("application.IndexMaster").debug("Loading event page " + page)
      Event.loadEvents(100, page).map {
        event => ElasticSearchActor.reaperActor ! Index(event)
      }
    }
    play.Logger.of("application.IndexMaster").debug("Done indexing Event")
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

    ElasticSearch.indexBulk(sb.toString())

    play.Logger.of("application.IndexMaster").debug("Done indexing all speakers")
  }

  def doIndexProposal(proposal: Proposal) {
    play.Logger.of("application.IndexMaster").debug("Do index proposal")
    ElasticSearchActor.reaperActor ! Index(proposal)

    play.Logger.of("application.IndexMaster").debug("Done indexing proposal")
  }

  def doIndexAllProposals() {
    play.Logger.of("application.IndexMaster").debug("Do index all proposals")

    val proposals = Proposal.allSubmitted()

    val sb = new StringBuilder
    proposals.foreach {
      proposal: Proposal =>
        sb.append("{\"index\":{\"_index\":\"proposals\",\"_type\":\"proposal\",\"_id\":\"" + proposal.id + "\"}}")
        sb.append("\n")
        sb.append(Json.toJson(proposal))
        sb.append("\n")
    }
    sb.append("\n")

    ElasticSearch.indexBulk(sb.toString())

    play.Logger.of("application.IndexMaster").debug("Done indexing all proposals")
  }


  def doIndexAllApproved() {
    play.Logger.of("application.IndexMaster").debug("Do index all proposals")

    val proposals = ApprovedProposal.allApproved()

    val sb = new StringBuilder
    proposals.foreach {
      proposal: Proposal =>
        sb.append("{\"index\":{\"_index\":\"proposals\",\"_type\":\"accepted\",\"_id\":\"" + proposal.id + "\"}}")
        sb.append("\n")
        sb.append(Json.toJson(proposal))
        sb.append("\n")
    }
    sb.append("\n")

    ElasticSearch.indexBulk(sb.toString())

    play.Logger.of("application.IndexMaster").debug("Done indexing all proposals")
  }

  def doIndexAllReviews() {
    play.Logger.of("application.IndexMaster").debug("Do index all reviews")

    val reviews = models.Review.allVotes()

    val sb = new StringBuilder
    reviews.foreach {
      case (proposalId, reviewAndVotes) =>
        val proposal = Proposal.findById(proposalId).get
        sb.append("{\"index\":{\"_index\":\"reviews\",\"_type\":\"review\",\"_id\":\"" + proposalId + "\"}}")
        sb.append("\n")
        sb.append("{")
        sb.append("\"totalVoters\": " + reviewAndVotes._2 + ", ")
        sb.append("\"totalAbstentions\": " + reviewAndVotes._3 + ", ")
        sb.append("\"average\": " + reviewAndVotes._4 + ", ")
        sb.append("\"standardDeviation\": " +reviewAndVotes._5 +", ")
        sb.append("\"median\": " + reviewAndVotes._6 + ",")
        sb.append("\"title\": \"" + proposal.title + "\",")
        sb.append("\"track\": \"" + proposal.track.id + "\",")
        sb.append("\"lang\": \"" + proposal.lang + "\",")
        sb.append("\"sponsor\": \"" + proposal.sponsorTalk + "\",")
        sb.append("\"type\": \"" + proposal.talkType.id + "\"")
        sb.append("}\n")
        sb.append("\n")
    }
    sb.append("\n")

    ElasticSearch.indexBulk(sb.toString())

    play.Logger.of("application.IndexMaster").debug("Done indexing all proposals")
  }

  def doIndexAllEvents() {
    play.Logger.of("application.IndexMaster").debug("Do index all events")

    val events = Event.loadEvents(Event.totalEvents().toInt, 0)

    val sb = new StringBuilder
    events.foreach {
      event: Event =>
        sb.append("{\"index\":{\"_index\":\"events\",\"_type\":\"event\",\"_id\":\"" + play.api.libs.Crypto.sign(event.toString) + "\"}}")
        sb.append("\n")
        sb.append(Json.toJson(event))
        sb.append("\n")
    }
    sb.append("\n")

    ElasticSearch.indexBulk(sb.toString())

    play.Logger.of("application.IndexMaster").debug("Done indexing all events")
  }

  def doIndexAllHitViews(){

    HitView.allStoredURL().foreach{url=>
      val hits = HitView.loadHitViews(url,new DateMidnight().minusDays(1).toDateTime, new DateTime())
      hits.foreach{hit=>
        println("hit "+hit)
      }
    }

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

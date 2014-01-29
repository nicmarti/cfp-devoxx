package library.search

import play.api.libs.json.Json
import akka.actor._
import play.api.libs.concurrent.Execution.Implicits._
import models.{Proposal, Speaker, Event}

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

case class DoIndexProposal()

case class DoIndexSpeaker()

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
    case DoIndexSpeaker() => doIndexSpeaker()
    case DoIndexProposal() => doIndexProposal()
    case DoIndexEvent() => doIndexEvent()
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

    for (page <- 0 to totalEvents / 100) {
      play.Logger.of("application.IndexMaster").debug("Loading event page " + page)
      Event.loadEvents(100, page).map {
        event => ElasticSearchActor.reaperActor ! Index(event)
      }
    }
    play.Logger.of("application.IndexMaster").debug("Done indexing Event")
  }

  def doIndexSpeaker() {
    play.Logger.of("application.IndexMaster").debug("Do index speaker")

    Speaker.allSpeakers().foreach {
      speaker => ElasticSearchActor.reaperActor ! Index(speaker)
    }

    play.Logger.of("application.IndexMaster").debug("Done indexing speaker")
  }

  def doIndexProposal() {
    play.Logger.of("application.IndexMaster").debug("Do index proposal")

    Proposal.allSubmitted().foreach {
      proposal => ElasticSearchActor.reaperActor ! Index(proposal)
    }

    play.Logger.of("application.IndexMaster").debug("Done indexing proposal")
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

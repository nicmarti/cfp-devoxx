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
  val masterActor = system.actorOf(Props[IndexMaster])
  val reaperActor = system.actorOf(Props[Reaper])
}

// Messages
case class DoIndexEvent()

case class DoIndexProposal()

case class DoIndexSpeaker()

case class IndexEvent(event: Event)

case class IndexSpeaker(speaker: Speaker)

case class IndexProposal(proposal: Proposal)

case class StopIndex()

// Main actor for dispatching
class IndexMaster extends Actor {
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
        event =>
          ElasticSearchActor.reaperActor ! IndexEvent(event)
      }
    }
    play.Logger.of("application.IndexMaster").debug("Done indexing Event")
  }

  def doIndexSpeaker() {
    play.Logger.of("application.IndexMaster").debug("Do index speaker")

    Speaker.allSpeakers().foreach {speaker=>
      ElasticSearchActor.reaperActor ! IndexSpeaker(speaker)
    }

    play.Logger.of("application.IndexMaster").debug("Done indexing speaker")
  }

  def doIndexProposal() {
    play.Logger.of("application.IndexMaster").debug("Do index proposal")

    Proposal.allSubmitted().foreach {proposal=>
      ElasticSearchActor.reaperActor ! IndexProposal(proposal)
    }

    play.Logger.of("application.IndexMaster").debug("Done indexing proposal")
  }

}

// Actor that is in charge of Indexing content
class Reaper extends Actor {

  def receive = {
    case IndexEvent(event: Event) => doIndexEvent(event)
    case IndexSpeaker(s: Speaker) => doIndexSpeaker(s)
    case IndexProposal(p: Proposal) => doIndexProposal(p)
    case other => play.Logger.of("application.Reaper").warn("unknown message received " + other)
  }

  def doIndexEvent(event: Event) {
    import models.Event.eventFormat

    val jsonObj = Json.toJson(event)
    val json: String = Json.stringify(jsonObj)

    val maybeSuccess = ElasticSearch.index("/events/event/" + event.uuid, json)

    maybeSuccess.map {
      case r if r.isSuccess =>
        play.Logger.of("application.Reaper").debug("Indexed event " + event.uuid)
      case r if r.isFailure =>
        play.Logger.of("application.Reaper").warn("Could not index event " + event.uuid + " due to " + r.toString)
    }
  }

  def doIndexSpeaker(speaker: Speaker) {
    import models.Speaker.speakerFormat

    val jsonObj = Json.toJson(speaker)
    val json: String = Json.stringify(jsonObj)

    val maybeSuccess = ElasticSearch.index("/speakers/speaker/" + speaker.uuid, json)

    maybeSuccess.map {
      case r if r.isSuccess =>
        play.Logger.of("application.Reaper").debug("Indexed speaker " + speaker.name)
      case r if r.isFailure =>
        play.Logger.of("application.Reaper").warn("Could not index speaker " + speaker + " due to " + r.toString)
    }
  }

  def doIndexProposal(proposal: Proposal) {
    import models.Proposal.proposalFormat
    import models.ProposalState.proposalStateFormat

    val jsonObj = Json.toJson(proposal)
    val json: String = Json.stringify(jsonObj)

    val maybeSuccess = ElasticSearch.index("/proposals/proposal/" + proposal.id, json)

    maybeSuccess.map {
      case r if r.isSuccess =>
        play.Logger.of("application.Reaper").debug("Indexed proposal " + proposal.id)
      case r if r.isFailure =>
        play.Logger.of("application.Reaper").warn("Could not index proposal " + proposal + " due to " + r.toString)
    }
  }
}

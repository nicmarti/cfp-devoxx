package models

import java.util.UUID

import library.Redis
import models.ConferenceDescriptor.ConferenceProposalTypes
import org.joda.time.DateTime
import play.api.libs.json.Json

case class ProgramSchedule(
        id: String,
        eventCode: String,
        name: String,
        lastModifiedByName: String,
        lastModified: DateTime,
        scheduleConfigurationsPerProposalType: Map[String, String],
        isTheOnePublished: Boolean,
        isEditable: Boolean) {

  def scheduleConfigurations: Map[ProposalType, String] = {
    scheduleConfigurationsPerProposalType.map {
      case(proposalTypeStr, scheduleConfigId) => (ConferenceProposalTypes.valueOf(proposalTypeStr), scheduleConfigId)
    }
  }
}

object ProgramSchedule {
  implicit val programScheduleFormat = Json.format[ProgramSchedule]

  def allProgramSchedulesForCurrentEvent(): List[ProgramSchedule] = Redis.pool.withClient {
    implicit client =>
      client.hgetAll(s"ProgramSchedules:${ConferenceDescriptor.current().eventCode}").map {
        case(id, json) => (id, Json.parse(json).as[ProgramSchedule])
      }.values.toList.sortBy(_.lastModified.getMillis)
  }

  def createAndPublishEmptyProgramSchedule(creator: Webuser) = Redis.pool.withClient {
    implicit client =>
      val uuid = UUID.randomUUID().toString
      val emptySchedule = ProgramSchedule(
        uuid, ConferenceDescriptor.current().eventCode, "Empty schedule", s"${creator.firstName} ${creator.lastName}",
        DateTime.now(), Map(), true, false
      )
      client.hset(s"ProgramSchedules:${ConferenceDescriptor.current().eventCode}", uuid, Json.stringify(Json.toJson(emptySchedule)))
      emptySchedule
  }
}
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
        scheduleConfigurations: Map[ProposalType, String],
        isTheOnePublished: Boolean, // will not be persisted
        isEditable: Boolean) {

  def toPersistedProgramSchedule: PersistedProgramSchedule = {
    PersistedProgramSchedule(id, eventCode, name, lastModifiedByName, lastModified, scheduleConfigurations.map {
      case(proposalType, scheduleConfigId) => (proposalType.id, scheduleConfigId)
    }, isEditable)
  }
}

case class PersistedProgramSchedule(
        id: String,
        eventCode: String,
        name: String,
        lastModifiedByName: String,
        lastModified: DateTime,
        scheduleConfigurations: Map[String, String],
        isEditable: Boolean)


object ProgramSchedule {
  implicit val persistedProgramScheduleFormat = Json.format[PersistedProgramSchedule]

  def allProgramSchedulesForCurrentEvent(): List[ProgramSchedule] = Redis.pool.withClient {
    implicit client =>
      val publishedProgramScheduleId = client.get(s"ProgramSchedules:${ConferenceDescriptor.current().eventCode}:Published").getOrElse("")
      client.hgetAll(s"ProgramSchedules:${ConferenceDescriptor.current().eventCode}").map {
        case(id, json) => (id, parsePersistedProgramSchedule(json, publishedProgramScheduleId))
      }.values.toList.sortBy(_.lastModified.getMillis).reverse
  }

  def createAndPublishEmptyProgramSchedule(creator: Webuser) = Redis.pool.withClient {
    implicit client =>
      val uuid = UUID.randomUUID().toString
      val emptySchedule = ProgramSchedule(
        uuid, ConferenceDescriptor.current().eventCode, "Empty schedule", s"${creator.firstName} ${creator.lastName}",
        DateTime.now(), Map(), true, false
      )
      client.hset(s"ProgramSchedules:${ConferenceDescriptor.current().eventCode}", uuid, Json.stringify(Json.toJson(emptySchedule.toPersistedProgramSchedule)))
      client.set(s"ProgramSchedules:${ConferenceDescriptor.current().eventCode}:Published", uuid)
      emptySchedule
  }

  def fromPersisted(s: PersistedProgramSchedule, publishedProgramScheduleId: String): ProgramSchedule = {
    ProgramSchedule(s.id, s.eventCode, s.name, s.lastModifiedByName, s.lastModified, s.scheduleConfigurations.map {
      case (proposalTypeId, scheduleConfigId) => (ConferenceProposalTypes.valueOf(proposalTypeId), scheduleConfigId)
    }, s.id == publishedProgramScheduleId, s.isEditable)
  }

  def findById(uuid: String) = Redis.pool.withClient {
    implicit client =>
      val publishedProgramScheduleId = client.get(s"ProgramSchedules:${ConferenceDescriptor.current().eventCode}:Published").getOrElse("")
      client.hget(s"ProgramSchedules:${ConferenceDescriptor.current().eventCode}", uuid).map { json =>
        parsePersistedProgramSchedule(json, publishedProgramScheduleId)
      }
  }

  def parsePersistedProgramSchedule(json: String, publishedProgramScheduleId: String) = fromPersisted(Json.parse(json).as[PersistedProgramSchedule], publishedProgramScheduleId)

  def createProgramSchedule(programSchedule: PersistedProgramSchedule, creator: Webuser) = Redis.pool.withClient {
    implicit client =>
      val uuid = UUID.randomUUID().toString
      persistProgramSchedule(uuid, programSchedule, creator)
  }

  def updateProgramSchedule(uuid: String, programSchedule: PersistedProgramSchedule, creator: Webuser) = Redis.pool.withClient {
    implicit client =>
      persistProgramSchedule(uuid, programSchedule, creator)
  }

  def deleteProgramSchedule(uuid: String)  = Redis.pool.withClient {
    implicit client =>
      client.get(s"ProgramSchedules:${ConferenceDescriptor.current().eventCode}:Published").map { publishedProgramScheduleId =>
        // We shouldn't be able to delete published schedule
        if(publishedProgramScheduleId != uuid) {
          client.hdel(s"ProgramSchedules:${ConferenceDescriptor.current().eventCode}", uuid)
        }
      }
  }

  def publishProgramSchedule(uuid: String)  = Redis.pool.withClient {
    implicit client =>
      client.set(s"ProgramSchedules:${ConferenceDescriptor.current().eventCode}:Published", uuid)
  }


  def persistProgramSchedule(uuid: String, programSchedule: PersistedProgramSchedule, creator: Webuser) = Redis.pool.withClient {
    implicit client =>
      val persistedProgramSchedule = programSchedule.copy(
        id = uuid,
        eventCode = ConferenceDescriptor.current().eventCode,
        lastModifiedByName = s"${creator.firstName} ${creator.lastName}",
        lastModified = DateTime.now(),
        isEditable = true
      )

      client.hset(s"ProgramSchedules:${ConferenceDescriptor.current().eventCode}", uuid, Json.stringify(Json.toJson(persistedProgramSchedule)))

      persistedProgramSchedule
  }

}
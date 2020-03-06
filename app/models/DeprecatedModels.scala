package models

import library.Redis
import org.joda.time.DateTime
import play.api.libs.json.Json

// TODO: Remove this file in next version

case class OldPersistedProgramSchedule(
     id: String,
     eventCode: String,
     name: String,
     lastModifiedByName: String,
     lastModified: DateTime,
     scheduleConfigurations: Map[String, String],
     isEditable: Boolean,
     specificScheduleCSSSnippet: Option[String] = None
) {
  def toNewProgramSchedule(): PersistedProgramSchedule = PersistedProgramSchedule(id, eventCode, name, lastModifiedByName, lastModified, scheduleConfigurations, isEditable, specificScheduleCSSSnippet, false, false, false)
}
object OldPersistedProgramSchedule {
  implicit val oldPersistedProgramScheduleFormat = Json.format[OldPersistedProgramSchedule]
  implicit val persistedProgramScheduleFormat = Json.format[PersistedProgramSchedule]

  def migrateProgramSchedules(): List[PersistedProgramSchedule] = Redis.pool.withClient {
    implicit client =>
      client.hgetAll(s"ProgramSchedules:${ConferenceDescriptor.current().eventCode}")
        .flatMap { case(programScheduleId: String, json: String) =>
          // Converting to new ScheduleConfig data structure
          val maybeOldProgramSchedule = Json.parse(json).validate[OldPersistedProgramSchedule]
          maybeOldProgramSchedule.fold(errors => {
            play.Logger.of("models.OldPersistedProgramSchedule").warn("Unable to reload a OldPersistedProgramSchedule due to JSON error")
            play.Logger.of("models.OldPersistedProgramSchedule").warn(s"Got error : ${library.ZapJson.showError(errors)} ")
            None
          }, oldProgramSchedule => {
            val newProgramSchedule = oldProgramSchedule.toNewProgramSchedule()
            client.hset(s"ProgramSchedules:${ConferenceDescriptor.current().eventCode}", programScheduleId, Json.stringify(Json.toJson(newProgramSchedule)))

            // Backuping old value only lately, it will avoid to overwrite backups in case of
            // calling migrateScheduleConfigurations twice
            client.hset("OldProgramSchedules", programScheduleId, json)

            Option(newProgramSchedule)
          })
        }.toList
  }
}

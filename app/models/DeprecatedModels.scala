package models

import library.Redis
import org.joda.time.DateTime
import play.api.libs.json.Json

// TODO: Remove this file in next version

case class OldScheduleConfiguration(confType: String, slots: List[OldSlotWithRoom], timeSlots: List[TimeSlot]) {
  def toNewSchedule(): ScheduleConfiguration = {
    ScheduleConfiguration(confType, slots.map(_.toRawSlot()), timeSlots)
  }
}

object OldScheduleConfiguration {
  implicit val oldRoomFormat = Json.format[OldRoom]
  implicit val oldSlotFormat = Json.format[OldSlotWithRoom]
  implicit val timeSlotFormat = Json.format[TimeSlot]
  implicit val oldScheduleConfFormat = Json.format[OldScheduleConfiguration]

  def migrateScheduleConfigurations(): List[ScheduleConfiguration] = Redis.pool.withClient {
    implicit client =>
      client.hgetAll("ScheduleConfigurationByID")
        .flatMap { case(configId: String, json: String) =>
          // Converting to new ScheduleConfig data structure
          val maybeOldScheduleConf = Json.parse(json).validate[OldScheduleConfiguration]
          maybeOldScheduleConf.fold(errors => {
            play.Logger.of("models.OldScheduledConfiguration").warn("Unable to reload a OldSlotConfiguration due to JSON error")
            play.Logger.of("models.OldScheduledConfiguration").warn(s"Got error : ${library.ZapJson.showError(errors)} ")
            None
          }, oldScheduleConfig => {
            val newSchedule = oldScheduleConfig.toNewSchedule()
            client.hset("ScheduleConfigurationByID", configId, Json.stringify(Json.toJson(newSchedule)))

            // Backuping old value only lately, it will avoid to overwrite backups in case of
            // calling migrateScheduleConfigurations twice
            client.hset("OldScheduleConfigurationByID", configId, json)

            Option(newSchedule)
          })
        }.toList
  }
}

case class OldSlotWithRoom(id: String, name: String, day: String, from: DateTime, to: DateTime, room: OldRoom,
                        proposal: Option[Proposal], break: Option[SlotBreak], fillerForSlotId: Option[String]) {
  def toRawSlot(): Slot = {
    Slot(id, name, day, from, to, room.id, proposal, break, fillerForSlotId)
  }
}

case class OldRoom(id: String, name: String, capacity: Int, setup: String, recorded: String) extends Ordered[OldRoom] {
  def compare(that: OldRoom): Int = {
    // Hack for Devoxx France => I cannot change the Room IDs so I fix the order in an IndexedSeq here
    if (Room.fixedOrderForRoom.indexOf(this.id) < Room.fixedOrderForRoom.indexOf(that.id)) {
      return -1
    }
    if (Room.fixedOrderForRoom.indexOf(this.id) > Room.fixedOrderForRoom.indexOf(that.id)) {
      return 1
    }
    return 0
  }
}

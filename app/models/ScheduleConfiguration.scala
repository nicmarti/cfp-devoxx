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

package models

import library.Redis
import models.ConferenceDescriptor.ConferenceProposalTypes
import play.api.libs.json.Json
import org.apache.commons.lang3.RandomStringUtils

import scala.util.Random
import org.joda.time.{DateTime, DateTimeZone}

/**
 * Slots that are scheduled.
 *
 * Created by Nicolas Martignole on 27/02/2014.
 */
case class TimeSlot(start: DateTime, end: DateTime) {
  override def hashCode(): Int = {
    start.hashCode() + end.hashCode()
  }

  override def equals(obj: scala.Any): Boolean = {
    obj match {
      case d2: TimeSlot =>
        d2.start.equals(this.start) && d2.end.equals(this.end)
      case _ =>
        false
    }
  }
}

case class ScheduleConfiguration(confType: String, slots: List[Slot], timeSlots: List[TimeSlot])

case class ScheduleSaved(id: String, confType: String, createdBy: String, hashCodeSlots: Int)

object ScheduleConfiguration {
  implicit val timeSlotFormat = Json.format[TimeSlot]
  implicit val scheduleConfFormat = Json.format[ScheduleConfiguration]
  implicit val scheduleSavedFormat = Json.format[ScheduleSaved]

  def persist(confType: String, slots: List[Slot], createdBy: Webuser):String = Redis.pool.withClient {
    implicit client =>
      val id = confType + "-" + (RandomStringUtils.randomAlphabetic(3) + "-" + RandomStringUtils.randomNumeric(2)).toLowerCase
      val key = ScheduleSaved(id, confType, createdBy.firstName, slots.hashCode())
      val jsonKey = Json.toJson(key).toString()
      client.zadd("ScheduleConfiguration", System.currentTimeMillis() / 1000, jsonKey)

      val timeSlots = slots.map {
        slot =>
          TimeSlot(slot.from, slot.to)
      }.sortBy(_.start.getMillis)

      val config = ScheduleConfiguration(confType, slots, timeSlots)
      val json = Json.toJson(config).toString()
      client.hset("ScheduleConfigurationByID", id, json)
      id
  }

  def delete(id: String) = Redis.pool.withClient {
    implicit client =>
      val scheduledSlotsKey = client.zrevrangeWithScores("ScheduleConfiguration", 0, -1)
      val tx = client.multi()

      scheduledSlotsKey.map {
        case (key: String, _) =>
          val scheduleSaved = Json.parse(key).as[ScheduleSaved]
          if (scheduleSaved.id == id) {
            tx.zrem("ScheduleConfiguration", key)
          }
      }
      tx.hdel("ScheduleConfigurationByID", id)
      tx.exec()
  }

  def allScheduledConfigurationWithLastModified(): List[(String, Double)] = Redis.pool.withClient {
    implicit client =>
      client.zrevrangeWithScores("ScheduleConfiguration", 0, -1)
  }

  def loadScheduledConfiguration(id: String): Option[ScheduleConfiguration] = Redis.pool.withClient {
    implicit client =>
      client.hget("ScheduleConfigurationByID", id).flatMap {
        json: String =>
          val maybeScheduledConf = Json.parse(json).validate[ScheduleConfiguration]
          maybeScheduledConf.fold(errors => {
            play.Logger.of("models.ScheduledConfiguration").warn("Unable to reload a SlotConfiguration due to JSON error")
            play.Logger.of("models.ScheduledConfiguration").warn(s"Got error : ${library.ZapJson.showError(errors)} ")
            None
          }, scheduleConfig => {
            Option(scheduleConfig.copy(
              // Replacing every slots/timeslots to convert them to current conf timezone, as we're generally
              // using local time everywhere in the code, whereas after JSON deserialization, it's UTC-based
              slots = scheduleConfig.slots.map(slot => {
                slot.copy(
                  from = slot.from.toDateTime(ConferenceDescriptor.current().timezone),
                  to = slot.to.toDateTime(ConferenceDescriptor.current().timezone)
                )
              }),
              timeSlots = scheduleConfig.timeSlots.map(timeSlot => {
                timeSlot.copy(
                  start = timeSlot.start.toDateTime(ConferenceDescriptor.current().timezone),
                  end = timeSlot.end.toDateTime(ConferenceDescriptor.current().timezone)
                )
              })
            ))
          })
      }
  }

  def getPublishedSchedule(confType: String, secretPublishKey: Option[String] = None): Option[String] = Redis.pool.withClient {
    implicit client =>
      val maybeProgramSchedule = secretPublishKey match {
        case Some(secretKey) => ProgramSchedule.findById(secretKey)
        case None => ProgramSchedule.publishedProgramSchedule()
      }

      maybeProgramSchedule.flatMap { programSchedule =>
        programSchedule.scheduleConfigurations.get(ConferenceProposalTypes.valueOf(confType))
      }
  }

  def getPublishedScheduleByDay(day: String, secretPublishKey: Option[String] = None): List[Slot] = {

    def extractSlot(allSlots: List[Slot], day: String) = {
      val configured = loadSlots(secretPublishKey).filter(_.day == day)
      val configuredIDs = configured.map(_.id)
      val filtered = allSlots.filterNot(s => configuredIDs.contains(s.id))
      configured ++ filtered
    }

    val listOfSlots = day match {
      case "monday" =>
        extractSlot(ConferenceDescriptor.ConferenceSlots.mondaySchedule, "monday")
      case "tuesday" =>
        extractSlot(ConferenceDescriptor.ConferenceSlots.tuesdaySchedule, "tuesday")
      case "wednesday" =>
        extractSlot(ConferenceDescriptor.ConferenceSlots.wednesdaySchedule, "wednesday")
      case "thursday" =>
        extractSlot(ConferenceDescriptor.ConferenceSlots.thursdaySchedule, "thursday")
      case "friday" =>
        extractSlot(ConferenceDescriptor.ConferenceSlots.fridaySchedule, "friday")
      case other =>
        play.Logger.of("ScheduleConfiguration").warn("Could not match " + other + " in getPublishedScheduleByDay")
        Nil
    }

    listOfSlots.sortBy(_.from.getMillis)
  }

  def loadSlots(secretPublishKey: Option[String]): List[Slot] = {
    ConferenceDescriptor.ConferenceProposalTypes.ALL.flatMap {
      t: ProposalType => loadSlotsForConfType(t.id, secretPublishKey)
    }
  }

  def loadSlotsForConfType(confType: String, secretPublishKey: Option[String] = None): List[Slot] = {
    getPublishedSchedule(confType, secretPublishKey).flatMap {
      id: String =>
        loadScheduledConfiguration(id).map {
          scheduledConf => scheduledConf.slots
        }
    }.getOrElse(List.empty[Slot])
  }

  // Retrieve the time slot for a specific proposalId
  def findSlotForConfType(confType: String, proposalId: String): Option[Slot] = {
    loadSlotsForConfType(confType).filter(_.proposal.isDefined).find(_.proposal.get.id == proposalId)
  }

  def loadAllConfigurations(secretPublishKey: Option[String] = None) = {
    val allConfs = for (confType <- ProposalType.allIDsOnly;
                        slotId <- ScheduleConfiguration.getPublishedSchedule(confType, secretPublishKey);
                        configuration <- ScheduleConfiguration.loadScheduledConfiguration(slotId)
    ) yield configuration

    allConfs
  }

  def loadAllPublishedSlots(secretPublishKey: Option[String] = None):List[Slot]={
    loadAllConfigurations(secretPublishKey).flatMap {
      sc => sc.slots
    }
  }

  def loadNextTalks() = {
    val allAgendas = ScheduleConfiguration.loadAllConfigurations()
    val slots = allAgendas.flatMap(_.slots)
    Option(slots.filter(_.from.isAfter(new DateTime().toDateTime(ConferenceDescriptor.current().timezone))).sortBy(_.from.toDate.getTime).take(10))
  }

  def loadRandomTalks() = {
    val allAgendas = ScheduleConfiguration.loadAllConfigurations()
    val slots = allAgendas.flatMap(_.slots)
    Option(Random.shuffle(slots).take(10))
  }
}
package models

import library.Redis
import org.joda.time.DateTime

case class ProgramSchedule(
        id: String,
        eventCode: String,
        name: String,
        lastModifiedByName: String,
        lastModified: DateTime,
        scheduleConfigurations: Map[ProposalType, String],
        isTheOnePublished: Boolean,
        isEditable: Boolean)

object ProgramSchedule {
  def allProgramSchedulesForCurrentEvent(): List[ProgramSchedule] = Redis.pool.withClient {
    implicit client =>
      // Temporary impl, waiting for proper Redis storage for this data class
      List(
        ProgramSchedule(
          "a4c5b5c9-5ab9-4c07-a2f3-5f0c491e46aa", ConferenceDescriptor.current().eventCode, "Empty schedule", "Fred", DateTime.parse("2020-02-15T08:05:30Z"), Map(), true, false),
        ProgramSchedule(
          "4b939288-3958-44f4-90cc-831dd24a12d1", ConferenceDescriptor.current().eventCode, "Test schedule", "Fred", DateTime.parse("2020-02-15T11:25:30Z"), Map(
          ConferenceDescriptor.ConferenceProposalTypes.LAB -> "lab-tza-22",
          ConferenceDescriptor.ConferenceProposalTypes.QUICK -> "quick-rah-03"
        ), false, true)
      )
  }
}
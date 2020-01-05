package notifiers

import models.{ConferenceDescriptor, Proposal, ProposalEdit, Webuser}
import play.api.Play
import play.api.Play.current
import play.api.i18n.Messages
import play.api.libs.json.Json
import play.api.libs.ws.WS

import scala.concurrent.ExecutionContext.Implicits.global


case class SlackConfig(hookUrl: String) {
  lazy val maybeSlackBotHookUrl = Play.current.configuration.getString(hookUrl)
}

/**
  * Created by fcamblor on 05/01/20
  */
object Slacks {

  val COMITEE_ONLY_SLACK = List(SlackConfig("slackbot.comitee.hook.url"))
  val GOLDEN_TICKETS_ONLY_SLACK = List(SlackConfig("slackbot.gt.hook.url"))
  val ALL_SLACKS = List.concat(COMITEE_ONLY_SLACK, GOLDEN_TICKETS_ONLY_SLACK)

  lazy val slackbotEmoji = Play.current.configuration.getString("slackbot.emoji").get
  lazy val slackbotUsername = Play.current.configuration.getString("slackbot.username").get

  def newDraftSubmitted(proposal: Proposal): Unit = {
    sendSlackNotification(
      target = COMITEE_ONLY_SLACK,
      msg = s":writing_hand:*${proposal.allSpeakers.map(s => s.cleanName).mkString(", ")}* " +
          s"created a new draft of type *${Messages(proposal.talkType.label)}* in " +
          s"track *${Messages(proposal.track.label)}* :\n" +
        s"*[${proposal.id}]* ${proposal.title}\n" +
        s"=> ${proposalUrl(proposal)} :writing_hand::writing_hand::writing_hand:",
      channels = List("#cfp-new-drafts", s"#cfp-${proposal.talkType.id}-new-drafts"))
  }

  def newProposalSubmitted(proposal: Proposal): Unit = {
    sendSlackNotification(
      target = ALL_SLACKS,
      msg = s":writing_hand:*${proposal.allSpeakers.map(s => s.cleanName).mkString(", ")}* " +
          s"submitted new proposal of type *${Messages(proposal.talkType.label)}* in " +
          s"track *${Messages(proposal.track.label)}* :\n" +
        s"*[${proposal.id}]* ${proposal.title}\n" +
        s"=> ${proposalUrl(proposal)} :writing_hand::writing_hand::writing_hand:",
      channels = List("#cfp-new-proposals", s"#cfp-${proposal.talkType.id}-new-proposals"))
  }

  def newMessageSentToSpeaker(proposal: Proposal, reporter: Webuser, message: String): Unit = {
    sendSlackNotification(
      target = COMITEE_ONLY_SLACK,
      msg = s":large_blue_circle:*Jury ${reporter.firstName} ${reporter.lastName}* published " +
          s"a new *:large_blue_circle:public comment:large_blue_circle:* on proposal " +
          s"*[${proposal.id}] ${proposal.title}* :\n" +
        s"${message}\n" +
        s"=> ${proposalUrl(proposal)} :large_blue_circle::large_blue_circle::large_blue_circle:",
      channels = List("#cfp-comments", s"#cfp-${proposal.talkType.id}-comments"))
  }

  def newMessageSentToComitee(proposal: Proposal, reporter: Webuser, message: String): Unit = {
    sendSlackNotification(
      target = COMITEE_ONLY_SLACK,
      msg = s":large_blue_circle:*Speaker ${reporter.firstName} ${reporter.lastName}* published " +
          s"a new *:large_blue_circle:public comment:large_blue_circle:* on proposal " +
          s"*[${proposal.id}] ${proposal.title}* :\n" +
        s"${message}\n" +
        s"=> ${proposalUrl(proposal)} :large_blue_circle::large_blue_circle::large_blue_circle:",
      channels = List("#cfp-comments", s"#cfp-${proposal.talkType.id}-comments"))
  }

  def newInternalMessagePosted(proposal: Proposal, reporter: Webuser, message: String): Unit = {
    sendSlackNotification(
      target = COMITEE_ONLY_SLACK,
      msg = s":red_circle:*${reporter.firstName} ${reporter.lastName}* published a new " +
          s"*:red_circle:private message:red_circle:* on proposal *[${proposal.id}] ${proposal.title}* :\n" +
        s"${message}\n" +
        s"=> ${proposalUrl(proposal)} :red_circle::red_circle::red_circle:",
      channels = List("#cfp-comments", s"#cfp-${proposal.talkType.id}-comments"))
  }

  def proposalEditDetected(proposal: Proposal, proposalEdits: List[ProposalEdit], reporter: Webuser): Unit = {
    sendSlackNotification(
      target = COMITEE_ONLY_SLACK,
      msg = s":writing_hand:*${reporter.firstName} ${reporter.lastName}* modified his proposal " +
        s"*[${proposal.id}] ${proposal.title}* :\n" +
        s"${proposalEdits.map(edit => s"- ${edit.message}\n").mkString("")}" +
        s"=> ${proposalUrl(proposal)} :writing_hand::writing_hand::writing_hand:",
      channels = List("#cfp-proposal-edits", s"#cfp-${proposal.talkType.id}-proposal-edits"))
  }


  def proposalUrl(proposal: Proposal) = s"https://${ConferenceDescriptor.current().conferenceUrls.cfpHostname}/cfpadmin/proposal/${proposal.id}"

  def sendSlackNotification(target: List[SlackConfig], msg: String, channels: List[String]) = {
    target.foreach(slackConfig => {
      if(slackConfig.maybeSlackBotHookUrl.isDefined && !slackConfig.maybeSlackBotHookUrl.get.isEmpty) {
        channels.foreach(channel => {
          val payload = Json.obj(
            "channel" -> channel,
            "username" -> slackbotUsername,
            "text" -> msg,
            "icon_emoji" -> slackbotEmoji
          )
          WS.url(slackConfig.maybeSlackBotHookUrl.get)
            .post(payload)
            .map {
              result =>
                result.status match {
                  case 200 => {
                    play.Logger.debug("Slack message sent !")
                  }
                  case _ => {
                    play.Logger.error("Slack message sending error : "+Json.obj(
                      "hookUrl" -> slackConfig.maybeSlackBotHookUrl.get,
                      "payload" -> payload,
                      "resultStatus" -> result.status,
                      "resultBody" -> result.body
                    ).toString())
                  }
                }
            }
        })
      }
    })
  }
}
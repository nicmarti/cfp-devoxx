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

package notifiers

import models._
import play.api.Play.current
import play.api.i18n.Messages
import play.api.libs.mailer.{Email, MailerPlugin}
import controllers.LeaderBoardParams
import play.api.Play
import play.twirl.api.Html

/**
  * Sends all emails
  *
  * Author: nmartignole
  * Created: 04/10/2013 15:56
  */

object Mails {

  val fromSender: String = ConferenceDescriptor.current().fromEmail
  val committeeEmail: String = ConferenceDescriptor.current().committeeEmail

  // FIXME : I did not want to break the ConferenceDescriptor object... so I use the configuration directly
  // This mail on GMail can be the targer group alias (such as program@devoxx.fr) on which we add +notifications
  // Doing that ease the classification in GMail
  val botNotificationEmail: String = Play.current.configuration.getString("mail.notification.email").getOrElse("program+notifications@devoxx.fr")
  val bugReportRecipient: String = ConferenceDescriptor.current().bugReportRecipient
  val bccEmail: Option[String] = ConferenceDescriptor.current().bccEmail

  /**
    * Send a message to a set of Speakers.
    * This function used to send 2 emails in the previous version.
    *
    * @return the rfc 822 Message-ID
    */
  def sendMessageToSpeakers(fromWebuser: Webuser, toWebuser: Webuser, proposal: Proposal, msg: String, inReplyTo: Option[String]): String = {
    val listOfEmails = extractOtherEmails(proposal)

    val inReplyHeaders: Seq[(String, String)] = inReplyTo.map {
      replyId: String =>
        Seq("In-Reply-To" -> replyId)
    }.getOrElse(Seq.empty[(String, String)])

    val email = Email(
      subject = s"[${proposal.id}] ${proposal.title}",
      from = fromSender,
      to = Seq(toWebuser.email),
      cc = listOfEmails, // Send the email to the speaker and co-speakers
      bcc = bccEmail.map(s => List(s)).getOrElse(Seq.empty[String]),
      bodyText = Some(views.txt.Mails.sendMessageToSpeaker(fromWebuser.cleanName, proposal, msg).toString()),
      bodyHtml = Some(views.html.Mails.sendMessageToSpeaker(fromWebuser.cleanName, proposal, msg).toString()),
      charset = Some("utf-8"),
      headers = inReplyHeaders
    )
    val newMessageId = MailerPlugin.send(email) // returns the message-ID

    newMessageId
  }

  def sendMessageToCommittee(fromWebuser: Webuser, proposal: Proposal, msg: String, inReplyTo: Option[String]): String = {
    val listOfOtherSpeakersEmail = extractOtherEmails(proposal)

    val inReplyHeaders: Seq[(String, String)] = inReplyTo.map {
      replyId: String =>
        Seq("In-Reply-To" -> replyId)
    }.getOrElse(Seq.empty[(String, String)])

    val email = Email(
      subject = s"[${proposal.id}] ${proposal.title}", // please keep a generic subject => perfect for Mail Thread
      from = fromSender,
      to = Seq(committeeEmail),
      cc = listOfOtherSpeakersEmail,
      bcc = bccEmail.map(s => List(s)).getOrElse(Seq.empty[String]),
      bodyText = Some(views.txt.Mails.sendMessageToCommitte(fromWebuser.cleanName, proposal, msg).toString()),
      bodyHtml = Some(views.html.Mails.sendMessageToCommitte(fromWebuser.cleanName, proposal, msg).toString()),
      charset = Some("utf-8"),
      headers = inReplyHeaders
    )
    MailerPlugin.send(email) // returns the message-ID
  }

   def sendBotMessageToCommittee(fromWebuser: Webuser, proposal: Proposal, msg: String, inReplyTo: Option[String]): String = {
    val listOfOtherSpeakersEmail = extractOtherEmails(proposal)

    val inReplyHeaders: Seq[(String, String)] = inReplyTo.map {
      replyId: String =>
        Seq("In-Reply-To" -> replyId)
    }.getOrElse(Seq.empty[(String, String)])

    val email = Email(
      subject = s"[${proposal.id}] ${proposal.title}", // please keep a generic subject => perfect for Mail Thread
      from = fromSender,
      to = Seq(botNotificationEmail),
      cc = listOfOtherSpeakersEmail,
      bcc = bccEmail.map(s => List(s)).getOrElse(Seq.empty[String]),
      bodyText = Some(views.txt.Mails.sendMessageToCommitte(fromWebuser.cleanName, proposal, msg).toString()),
      bodyHtml = Some(views.html.Mails.sendMessageToCommitte(fromWebuser.cleanName, proposal, msg).toString()),
      charset = Some("utf-8"),
      headers = inReplyHeaders
    )
    MailerPlugin.send(email) // returns the message-ID
  }

  def sendNotifyProposalSubmitted(fromWebuser: Webuser, proposal: Proposal) = {
    val listOfOtherSpeakersEmail = extractOtherEmails(proposal)
    val subjectEmail: String = Messages("mail.notify_proposal.subject", fromWebuser.cleanName, proposal.title)

    val email = Email(
      subject = subjectEmail,
      from = fromSender,
      to = Seq(botNotificationEmail),
      cc = listOfOtherSpeakersEmail,
      bcc = bccEmail.map(s => List(s)).getOrElse(Seq.empty[String]),
      bodyText = Some(views.txt.Mails.sendNotifyProposalSubmitted(fromWebuser.cleanName, proposal.id, proposal.title, Messages(proposal.track.label), Messages(proposal.talkType.id)).toString()),
      bodyHtml = Some(views.html.Mails.sendNotifyProposalSubmitted(fromWebuser.cleanName, proposal.id, proposal.title, Messages(proposal.track.label), Messages(proposal.talkType.id)).toString()),
      charset = Some("utf-8"),
      headers = Seq()
    )
    MailerPlugin.send(email)
  }

  /**
    * Post a new message to SMTP with an optional In-Reply-To, so that Mail clients can order by / group by all messages together.
    * Message-ID cannot be set here. MimeMessages updateMessageID() method would need to be overloaded but it's too complex.
    *
    * @return the RFC 822 Message-ID generated by MimeMessages
    */
  def postInternalMessage(fromWebuser: Webuser, proposal: Proposal, msg: String, inReplyTo: Option[String]): String = {
    val subjectEmail: String = s"[PRIVATE][${proposal.id}] ${proposal.title}"

    val inReplyHeaders: Seq[(String, String)] = inReplyTo.map {
      replyId: String =>
        Seq("In-Reply-To" -> replyId)
    }.getOrElse(Seq.empty[(String, String)])

    val email = Email(
      subject = subjectEmail,
      from = fromSender,
      to = Seq(committeeEmail),
      bcc = bccEmail.map(s => List(s)).getOrElse(Seq.empty[String]),
      bodyText = Some(views.txt.Mails.postInternalMessage(fromWebuser.cleanName, proposal, msg).toString()),
      bodyHtml = Some(views.html.Mails.postInternalMessage(fromWebuser.cleanName, proposal, msg).toString()),
      charset = Some("utf-8"),
      headers = inReplyHeaders
    )

    // Mailjet does not keep the Message-ID, you must use Mailgun if you want this code to work
    val messageId = MailerPlugin.send(email)
    messageId
  }

  def sendReminderForDraft(speaker: Webuser, proposals: List[Proposal]) = {
    val subjectEmail = proposals.size match {
      case x if x > 1 => Messages("mail.draft_multiple_reminder.subject", proposals.size, Messages("longYearlyName"))
      case _ => Messages("mail.draft_single_reminder.subject", Messages("longYearlyName"))
    }

    val email = Email(
      subject = subjectEmail,
      from = fromSender,
      to = Seq(speaker.email),
      bcc = bccEmail.map(s => List(s)).getOrElse(Seq.empty[String]),
      bodyText = Some(views.txt.Mails.sendReminderForDraft(speaker.firstName, proposals).toString()),
      bodyHtml = Some(views.html.Mails.sendReminderForDraft(speaker.firstName, proposals).toString()),
      charset = Some("utf-8")
    )

    MailerPlugin.send(email)
  }

  def sendProposalApproved(speaker: Webuser, proposal: Proposal) = {
    val subjectEmail: String = Messages("mail.proposal_approved.subject", proposal.title)
    val otherSpeakers = extractOtherEmails(proposal)

    val email = Email(
      subject = subjectEmail,
      from = fromSender,
      to = Seq(speaker.email),
      cc = otherSpeakers,
      bcc = bccEmail.map(s => List(s)).getOrElse(Seq.empty[String]),
      bodyText = Some(views.txt.Mails.acceptrefuse.sendProposalApproved(proposal,speaker.email).toString()),
      bodyHtml = Some(views.html.Mails.acceptrefuse.sendProposalApproved(proposal,speaker.email).toString()),
      charset = Some("utf-8")
    )

    MailerPlugin.send(email)
  }

  def sendProposalRefused(speaker: Webuser, proposal: Proposal) = {
    val subjectEmail: String = Messages("mail.proposal_refused.subject", proposal.title)
    val otherSpeakers = extractOtherEmails(proposal)

    val email = Email(
      subject = subjectEmail,
      from = fromSender,
      to = Seq(speaker.email),
      cc = otherSpeakers,
      bcc = bccEmail.map(s => List(s)).getOrElse(Seq.empty[String]),
      bodyText = Some(views.txt.Mails.acceptrefuse.sendProposalRefused(proposal,speaker.email).toString()),
      bodyHtml = Some(views.html.Mails.acceptrefuse.sendProposalRefused(proposal,speaker.email).toString()),
      charset = Some("utf-8")
    )

    MailerPlugin.send(email)
  }

  /**
    * Mail digest.
    */
  def sendDigest(digest: Digest, webuser: Webuser, bodyHtml: Html, bodyText: String): String = {

    val subjectEmail: String = Messages("mail.digest.subject", digest.value, Messages("longYearlyName"))

    val email = Email(
      subject = subjectEmail,
      from = fromSender,
      to = Seq(webuser.email),
      bodyText = Some(bodyText),
      bodyHtml = Some(bodyHtml.toString()),
      charset = Some("utf-8")
    )

    MailerPlugin.send(email)
  }

  def sendInvitationForSpeaker(speakerEmail: String, message: String, requestId: String) = {
    val subjectEmail: String = Messages("shortYearlyName") + " special request"

    val email = Email(
      subject = subjectEmail,
      from = fromSender,
      to = Seq(speakerEmail),
      bcc = bccEmail.map(s => List(s)).getOrElse(Seq.empty[String]),
      bodyText = Some(views.txt.Mails.sendInvitationForSpeaker(message, requestId).toString()),
      bodyHtml = Some(views.html.Mails.sendInvitationForSpeaker(message, requestId).toString()),
      charset = Some("utf-8")
    )

    MailerPlugin.send(email)
  }

  def sendGoldenTicketEmail(webuser: Webuser, gt: GoldenTicket) = {
    val subjectEmail: String = Messages("mail.goldenticket.subject", Messages("shortYearlyName"))

    val email = Email(
      subject = subjectEmail,
      from = fromSender,
      to = Seq(webuser.email),
      bcc = bccEmail.map(s => List(s)).getOrElse(Seq.empty[String]),
      bodyText = Some(views.txt.Mails.goldenticket.sendGoldenTicketEmail(webuser, gt).toString()),
      bodyHtml = Some(views.html.Mails.goldenticket.sendGoldenTicketEmail(webuser, gt).toString()),
      charset = Some("utf-8")
    )

    MailerPlugin.send(email)
  }

  private def extractOtherEmails(proposal: Proposal): List[String] = {
    val maybeSecondSpeaker = proposal.secondarySpeaker.flatMap(uuid => Webuser.getEmailFromUUID(uuid))
    val maybeOtherEmails = proposal.otherSpeakers.flatMap(uuid => Webuser.getEmailFromUUID(uuid))
    maybeOtherEmails ++ maybeSecondSpeaker.toList
  }

  private def extractAllEmails(proposal: Proposal): Iterable[String] = {
    val mainSpeakerEmail = Webuser.getEmailFromUUID(proposal.mainSpeaker)
    mainSpeakerEmail ++ extractOtherEmails(proposal)
  }

}

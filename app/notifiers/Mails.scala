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


import com.typesafe.plugin._
import play.api.Play.current
import org.joda.time.DateTime
import models._
import play.api.i18n.Messages

/**
 * Sends all emails
 *
 * Author: nmartignole
 * Created: 04/10/2013 15:56
 */

object Mails {
  lazy val from = ConferenceDescriptor.current().fromEmail
  lazy val bugReportRecipient = ConferenceDescriptor.current().bugReportRecipient
  lazy val bcc = ConferenceDescriptor.current().bccEmail
  
  def sendResetPasswordLink(email: String, resetUrl: String) = {
    val emailer = current.plugin[MailerPlugin].map(_.email).getOrElse(sys.error("Problem with the MailerPlugin"))
    val timestamp: String = new DateTime().toString("HH:mm dd/MM")
    val subject:String = Messages("mail.reset_password_link.subject",timestamp)
    emailer.setSubject(subject)
    emailer.addFrom(from)
    emailer.addRecipient(email)
    // If you want to receive a copy for validation
    bcc.map(bccEmail => emailer.addBcc(bccEmail))
    emailer.setCharset("utf-8")
    emailer.send(views.txt.Mails.sendResetLink(resetUrl).toString(), views.html.Mails.sendResetLink(resetUrl).toString)
  }

  def sendAccessCode(email: String, code: String) = {
    val emailer = current.plugin[MailerPlugin].map(_.email).getOrElse(sys.error("Problem with the MailerPlugin"))
    val subject:String = Messages("mail.access_code.subject")
    emailer.setSubject(subject)
    emailer.addFrom(from)
    emailer.addRecipient(email)
    bcc.map(bccEmail => emailer.addBcc(bccEmail))
    emailer.setCharset("utf-8")
    emailer.send(
      views.txt.Mails.sendAccessCode(email, code).toString(),
      views.html.Mails.sendAccessCode(email, code).toString
    )
  }

  def sendWeCreatedAnAccountForYou(email: String, firstname: String, tempPassword: String) = {
    val emailer = current.plugin[MailerPlugin].map(_.email).getOrElse(sys.error("Problem with the MailerPlugin"))
    val subject: String = Messages("mail.account_created.subject")
    emailer.setSubject(subject)
    emailer.addFrom(from)
    bcc.map(bccEmail => emailer.addBcc(bccEmail))
    emailer.addRecipient(email)
    emailer.setCharset("utf-8")
    emailer.send(views.txt.Mails.sendAccountCreated(firstname, email, tempPassword).toString(), views.html.Mails.sendAccountCreated(firstname, email, tempPassword).toString)
  }

  def sendValidateYourEmail(email: String, validationLink: String) = {
    val emailer = current.plugin[MailerPlugin].map(_.email).getOrElse(sys.error("Problem with the MailerPlugin"))
    val subject: String = Messages("mail.email_validation.subject")
    emailer.setSubject(subject)
    emailer.addFrom(from)
    bcc.map(bccEmail => emailer.addBcc(bccEmail))
    emailer.addRecipient(email)
    emailer.setCharset("utf-8")
    emailer.send(
      views.txt.Mails.sendValidateYourEmail(validationLink).toString(),
      views.html.Mails.sendValidateYourEmail(validationLink).toString()
    )
  }

  def sendBugReport(bugReport: Issue) = {
    val emailer = current.plugin[MailerPlugin].map(_.email).getOrElse(sys.error("Problem with the MailerPlugin"))
    val subject: String = Messages("mail.issue_reported.subject")
    emailer.setSubject(subject)
    emailer.addFrom(from)
    emailer.addCc(bugReport.reportedBy)
    bcc.map(bccEmail => emailer.addBcc(bccEmail))
    emailer.addRecipient(bugReportRecipient)
    emailer.setCharset("utf-8")
    emailer.send(
      views.html.Mails.sendBugReport(bugReport).toString(),
      views.html.Mails.sendBugReport(bugReport).toString()
    )
  }

  def sendMessageToSpeakers(fromWebuser: Webuser, toWebuser: Webuser, proposal: Proposal, msg: String) = {
    val emailer = current.plugin[MailerPlugin].map(_.email).getOrElse(sys.error("Problem with the MailerPlugin"))
    val subject: String = Messages("mail.cfp_message_to_speaker.subject",proposal.title)
    emailer.setSubject(subject)
    emailer.addFrom(from)
    bcc.map(bccEmail => emailer.addBcc(bccEmail))
    emailer.addRecipient(toWebuser.email)

    // The Java Mail API accepts varargs... Thus we have to concatenate and turn Scala to Java
    // I am a Scala coder, please get me out of here...
    val maybeSecondSpeaker = proposal.secondarySpeaker.flatMap(uuid => Webuser.getEmailFromUUID(uuid))
    val maybeOtherEmails = proposal.otherSpeakers.flatMap(uuid => Webuser.getEmailFromUUID(uuid))
    val listOfEmails = maybeOtherEmails ++ maybeSecondSpeaker.toList
    emailer.addCc(listOfEmails.toSeq: _*) // magic trick to create a java varargs from a scala List

    emailer.setCharset("utf-8")
    emailer.send(
      views.txt.Mails.sendMessageToSpeaker(fromWebuser.cleanName, proposal, msg).toString(),
      views.html.Mails.sendMessageToSpeaker(fromWebuser.cleanName, proposal, msg).toString()
    )

    // For Program committee
    emailer.setSubject(s"[${proposal.title} ${proposal.id}]")
    emailer.addFrom(from)
    emailer.addRecipient(from)
    bcc.map(bccEmail => emailer.addBcc(bccEmail))

    emailer.setCharset("utf-8")
    emailer.send(
      views.txt.Mails.sendMessageToSpeakerCommittee(fromWebuser.cleanName, toWebuser.cleanName, proposal, msg).toString(),
      views.html.Mails.sendMessageToSpeakerCommitte(fromWebuser.cleanName, toWebuser.cleanName, proposal, msg).toString()
    )
  }

  def sendMessageToCommitte(fromWebuser: Webuser, proposal: Proposal, msg: String) = {
    val emailer = current.plugin[MailerPlugin].map(_.email).getOrElse(sys.error("Problem with the MailerPlugin"))
    val subject: String = Messages("mail.speaker_message_to_cfp.subject", proposal.title,fromWebuser.cleanName)
    emailer.setSubject(subject)
    emailer.addFrom(from)
    emailer.addRecipient(from)
    bcc.map(bccEmail => emailer.addBcc(bccEmail))
    emailer.setCharset("utf-8")

    // Send also a copy of the message to the other speakers
    val maybeSecondSpeaker = proposal.secondarySpeaker.flatMap(uuid => Webuser.getEmailFromUUID(uuid))
    val mainSpeaker = Webuser.getEmailFromUUID(proposal.mainSpeaker)
    val maybeOtherEmails = proposal.otherSpeakers.flatMap(uuid => Webuser.getEmailFromUUID(uuid))
    val listOfEmails = mainSpeaker ++ maybeOtherEmails ++ maybeSecondSpeaker.toList
    emailer.addCc(listOfEmails.toSeq: _*) // magic trick to create a java varargs from a scala List

    emailer.send(
      views.txt.Mails.sendMessageToCommitte(fromWebuser.cleanName, proposal, msg).toString(),
      views.html.Mails.sendMessageToCommitte(fromWebuser.cleanName, proposal, msg).toString()
    )
  }

  def postInternalMessage(fromWebuser: Webuser, proposal: Proposal, msg: String) = {
    val emailer = current.plugin[MailerPlugin].map(_.email).getOrElse(sys.error("Problem with the MailerPlugin"))
    emailer.setSubject(s"[${proposal.title}][PRIVATE] ${fromWebuser.cleanName}")
    emailer.addFrom(from)
    emailer.addRecipient(from)
    bcc.map(bccEmail => emailer.addBcc(bccEmail))

    emailer.setCharset("utf-8")
    emailer.send(
      views.txt.Mails.postInternalMessage(fromWebuser.cleanName, proposal, msg).toString(),
      views.html.Mails.postInternalMessage(fromWebuser.cleanName, proposal, msg).toString()
    )
  }

  def sendReminderForDraft(speaker: Webuser, proposals: List[Proposal]) = {
    val emailer = current.plugin[MailerPlugin].map(_.email).getOrElse(sys.error("Problem with the MailerPlugin"))
    if (proposals.size == 1) {
      val subject: String = Messages("mail.draft_single_reminder.subject")
      emailer.setSubject(subject)
    }
    if (proposals.size > 1) {
      val subject: String = Messages("mail.draft_multiple_reminder.subject",proposals.size)
      emailer.setSubject(subject)
    }
    emailer.addFrom(from)
    emailer.addRecipient(speaker.email)
    bcc.map(bccEmail => emailer.addBcc(bccEmail))

    emailer.setCharset("utf-8")
    emailer.send(
      views.txt.Mails.sendReminderForDraft(speaker.firstName, proposals).toString(),
      views.html.Mails.sendReminderForDraft(speaker.firstName, proposals).toString()
    )
  }

  def sendProposalApproved(toWebuser: Webuser, proposal: Proposal) = {
    val emailer = current.plugin[MailerPlugin].map(_.email).getOrElse(sys.error("Problem with the MailerPlugin"))
    val subject: String = Messages("mail.proposal_approved.subject",proposal.title)
    emailer.setSubject(subject)
    emailer.addFrom(from)
    emailer.addRecipient(toWebuser.email)
    bcc.map(bccEmail => emailer.addBcc(bccEmail))

    // The Java Mail API accepts varargs... Thus we have to concatenate and turn Scala to Java
    // I am a Scala coder, please get me out of here...
    val maybeSecondSpeaker = proposal.secondarySpeaker.flatMap(uuid => Webuser.getEmailFromUUID(uuid))
    val maybeOtherEmails = proposal.otherSpeakers.flatMap(uuid => Webuser.getEmailFromUUID(uuid))
    val listOfEmails = maybeOtherEmails ++ maybeSecondSpeaker.toList
    emailer.addCc(listOfEmails.toSeq: _*) // magic trick to create a java varargs from a scala List

    emailer.setCharset("utf-8")
    emailer.send(
      views.txt.Mails.acceptrefuse.sendProposalApproved(proposal).toString(),
      views.html.Mails.acceptrefuse.sendProposalApproved(proposal).toString()
    )
  }

  def sendProposalRefused(toWebuser: Webuser, proposal: Proposal) = {
    val emailer = current.plugin[MailerPlugin].map(_.email).getOrElse(sys.error("Problem with the MailerPlugin"))
    val subject: String = Messages("mail.proposal_refused.subject",proposal.title)
    emailer.setSubject(subject)
    emailer.addFrom(from)
    emailer.addRecipient(toWebuser.email)
    bcc.map(bccEmail => emailer.addBcc(bccEmail))

    // The Java Mail API accepts varargs... Thus we have to concatenate and turn Scala to Java
    val maybeSecondSpeaker = proposal.secondarySpeaker.flatMap(uuid => Webuser.getEmailFromUUID(uuid))
    val maybeOtherEmails = proposal.otherSpeakers.flatMap(uuid => Webuser.getEmailFromUUID(uuid))
    val listOfEmails = maybeOtherEmails ++ maybeSecondSpeaker.toList
    emailer.addCc(listOfEmails.toSeq: _*) // magic trick to create a java varargs from a scala List

    emailer.setCharset("utf-8")
    emailer.send(
      views.txt.Mails.acceptrefuse.sendProposalRefused(proposal).toString(),
      views.html.Mails.acceptrefuse.sendProposalRefused(proposal).toString()
    )
  }

  def sendResultToSpeaker(speaker: Speaker, listOfApprovedProposals: Set[Proposal], listOfRefusedProposals: Set[Proposal]) = {
    val emailer = current.plugin[MailerPlugin].map(_.email).getOrElse(sys.error("Problem with the MailerPlugin"))

    val subject: String = Messages("mail.speaker_cfp_results.subject")
    emailer.setSubject(subject)
    emailer.addFrom(from)
    emailer.addRecipient(speaker.email)
    bcc.map(bccEmail => emailer.addBcc(bccEmail))

    emailer.setCharset("utf-8")
    emailer.send(
      views.txt.Mails.acceptrefuse.sendResultToSpeaker(speaker, listOfApprovedProposals, listOfRefusedProposals).toString(),
      views.html.Mails.acceptrefuse.sendResultToSpeaker(speaker, listOfApprovedProposals, listOfRefusedProposals).toString()
    )
  }

  def sendInvitationForSpeaker(speakerEmail: String, message: String, requestId: String) = {
    val emailer = current.plugin[MailerPlugin].map(_.email).getOrElse(sys.error("Problem with the MailerPlugin"))

    emailer.setSubject(s"Devoxx BE 2014 special request")
    emailer.addFrom(from)
    emailer.addRecipient(speakerEmail)
    bcc.map(bccEmail => emailer.addBcc(bccEmail))

    emailer.setCharset("utf-8")
    emailer.send(
      views.txt.Mails.sendInvitationForSpeaker(message, requestId).toString(),
      views.html.Mails.sendInvitationForSpeaker(message, requestId).toString()
    )
  }

}

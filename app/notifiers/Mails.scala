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
import models.{Webuser, Proposal, Issue}

/**
 * Sends all emails
 *
 * Author: nmartignole
 * Created: 04/10/2013 15:56
 */

object Mails {

  def sendResetPasswordLink(email: String, resetUrl: String) = {
    val emailer = current.plugin[MailerPlugin].map(_.email).getOrElse(sys.error("Problem with the MailerPlugin"))
    emailer.setSubject("You asked to reset your Devoxx France speaker's password at " + new DateTime().toString("HH:mm dd/MM"))
    emailer.addFrom("program@devoxx.fr")
    emailer.addRecipient(email)
    emailer.setCharset("utf-8")
    emailer.send(views.txt.Mails.sendResetLink(resetUrl).toString(), views.html.Mails.sendResetLink(resetUrl).toString)
  }

  def sendAccessCode(email: String, code: String) = {
    val emailer = current.plugin[MailerPlugin].map(_.email).getOrElse(sys.error("Problem with the MailerPlugin"))
    emailer.setSubject("Your Devoxx France speaker's access code")
    emailer.addFrom("program@devoxx.fr")
    emailer.addRecipient(email)
    emailer.setCharset("utf-8")
    emailer.send(
      views.txt.Mails.sendAccessCode(email, code).toString(),
      views.html.Mails.sendAccessCode(email, code).toString
    )
  }

  def sendWeCreatedAnAccountForYou(email: String, firstname: String, tempPassword: String) = {
    val emailer = current.plugin[MailerPlugin].map(_.email).getOrElse(sys.error("Problem with the MailerPlugin"))
    emailer.setSubject("Welcome to the CFP for Devoxx France ! ")
    emailer.addFrom("program@devoxx.fr")
    emailer.addBcc("nicolas.martignole@devoxx.fr")
    emailer.addRecipient(email)
    emailer.setCharset("utf-8")
    emailer.send(views.txt.Mails.sendAccountCreated(firstname, email, tempPassword).toString(), views.html.Mails.sendAccountCreated(firstname, email, tempPassword).toString)
  }

  def sendValidateYourEmail(email: String, validationLink: String) = {
    val emailer = current.plugin[MailerPlugin].map(_.email).getOrElse(sys.error("Problem with the MailerPlugin"))
    emailer.setSubject("Devoxx France, please validate your email address now")
    emailer.addFrom("program@devoxx.fr")
    emailer.addRecipient(email)
    emailer.setCharset("utf-8")
    emailer.send(
      views.txt.Mails.sendValidateYourEmail(validationLink).toString(),
      views.html.Mails.sendValidateYourEmail(validationLink).toString()
    )
  }

  def sendBugReport(bugReport: Issue) = {
    val emailer = current.plugin[MailerPlugin].map(_.email).getOrElse(sys.error("Problem with the MailerPlugin"))
    emailer.setSubject(s"New issue reported on CFP web site")
    emailer.addFrom("program@devoxx.fr")
    emailer.addCc(bugReport.reportedBy)
    emailer.addRecipient("nicolas.martignole@devoxx.fr")
    emailer.setCharset("utf-8")
    emailer.send(
      views.html.Mails.sendBugReport(bugReport).toString(),
      views.html.Mails.sendBugReport(bugReport).toString()
    )
  }

  def sendMessageToSpeakers(fromWebuser: Webuser, toWebuser: Webuser, proposal: Proposal, msg: String) = {
    val emailer = current.plugin[MailerPlugin].map(_.email).getOrElse(sys.error("Problem with the MailerPlugin"))
    emailer.setSubject(s"[DevoxxFr2014] Message about your presentation ${proposal.title}")
    emailer.addFrom("program@devoxx.fr")
    emailer.addHeader("Message-ID", proposal.id)
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
    emailer.addFrom(fromWebuser.email)
    emailer.addRecipient("program@devoxx.fr")
    emailer.setCharset("utf-8")
    emailer.addHeader("Message-ID", proposal.id + "_c")
    emailer.send(
      views.txt.Mails.sendMessageToSpeakerCommittee(fromWebuser.cleanName, toWebuser.cleanName, proposal, msg).toString(),
      views.html.Mails.sendMessageToSpeakerCommitte(fromWebuser.cleanName, toWebuser.cleanName, proposal, msg).toString()
    )
  }

  def sendMessageToComite(fromWebuser: Webuser, proposal: Proposal, msg: String) = {
    val emailer = current.plugin[MailerPlugin].map(_.email).getOrElse(sys.error("Problem with the MailerPlugin"))
    emailer.setSubject(s"[${proposal.title}] ${fromWebuser.cleanName} posted a new message")
    emailer.addFrom("program@devoxx.fr")
    emailer.addRecipient("program@devoxx.fr")
    emailer.setCharset("utf-8")
    emailer.addHeader("Message-ID", proposal.id)

    // Send also a copy of the message to the other speakers
    val maybeSecondSpeaker = proposal.secondarySpeaker.flatMap(uuid => Webuser.getEmailFromUUID(uuid))
    val maybeOtherEmails = proposal.otherSpeakers.flatMap(uuid => Webuser.getEmailFromUUID(uuid))
    val listOfEmails = maybeOtherEmails ++ maybeSecondSpeaker.toList
    emailer.addCc(listOfEmails.toSeq: _*) // magic trick to create a java varargs from a scala List

    emailer.send(
      views.txt.Mails.sendMessageToComite(fromWebuser.cleanName, proposal, msg).toString(),
      views.html.Mails.sendMessageToComite(fromWebuser.cleanName, proposal, msg).toString()
    )
  }

  def postInternalMessage(fromWebuser: Webuser, proposal: Proposal, msg: String) = {
    val emailer = current.plugin[MailerPlugin].map(_.email).getOrElse(sys.error("Problem with the MailerPlugin"))
    emailer.setSubject(s"[${proposal.title}][PRIVATE] ${fromWebuser.cleanName}")
    emailer.addFrom("program@devoxx.fr")
    emailer.addRecipient("program@devoxx.fr")
    emailer.setCharset("utf-8")
    emailer.addHeader("Message-ID", proposal.id + "_c")
    emailer.send(
      views.txt.Mails.postInternalMessage(fromWebuser.cleanName, proposal, msg).toString(),
      views.html.Mails.postInternalMessage(fromWebuser.cleanName, proposal, msg).toString()
    )
  }

  def sendReminderForDraft(speaker: Webuser, proposals: List[Proposal]) = {
    val emailer = current.plugin[MailerPlugin].map(_.email).getOrElse(sys.error("Problem with the MailerPlugin"))
    if (proposals.size == 1) {
      emailer.setSubject("Devoxx France 2014 reminder : you have one proposal with status 'Draft'")
    }
    if (proposals.size > 1) {
      emailer.setSubject(s"Devoxx France 2014 reminder : you have ${proposals.size} proposals to submit")
    }
    emailer.addFrom("program@devoxx.fr")
    emailer.addRecipient(speaker.email)

    emailer.setCharset("utf-8")
    emailer.send(
      views.txt.Mails.sendReminderForDraft(speaker.firstName, proposals).toString(),
      views.html.Mails.sendReminderForDraft(speaker.firstName, proposals).toString()
    )
  }

  def sendProposalAccepted(toWebuser: Webuser, proposal: Proposal) = {
    val emailer = current.plugin[MailerPlugin].map(_.email).getOrElse(sys.error("Problem with the MailerPlugin"))
    emailer.setSubject(s"[TEST] Your proposal has been accepted ${proposal.title}")
    emailer.addFrom("program@devoxx.fr")
    emailer.addHeader("Message-ID", proposal.id)
    emailer.addRecipient(toWebuser.email)

    // The Java Mail API accepts varargs... Thus we have to concatenate and turn Scala to Java
    // I am a Scala coder, please get me out of here...
    val maybeSecondSpeaker = proposal.secondarySpeaker.flatMap(uuid => Webuser.getEmailFromUUID(uuid))
    val maybeOtherEmails = proposal.otherSpeakers.flatMap(uuid => Webuser.getEmailFromUUID(uuid))
    val listOfEmails = maybeOtherEmails ++ maybeSecondSpeaker.toList
    emailer.addCc(listOfEmails.toSeq: _*) // magic trick to create a java varargs from a scala List

    emailer.setCharset("utf-8")
    emailer.send(
      views.txt.Mails.acceptrefuse.sendProposalAccepted(proposal).toString(),
      views.html.Mails.acceptrefuse.sendProposalAccepted(proposal).toString()
    )

  }

  def sendProposalRejected(toWebuser: Webuser, proposal: Proposal) = {
    val emailer = current.plugin[MailerPlugin].map(_.email).getOrElse(sys.error("Problem with the MailerPlugin"))
    emailer.setSubject(s"[TEST] Your proposal has been refused ${proposal.title}")
    emailer.addFrom("program@devoxx.fr")
    emailer.addHeader("Message-ID", proposal.id)
    emailer.addRecipient(toWebuser.email)

    // The Java Mail API accepts varargs... Thus we have to concatenate and turn Scala to Java
    val maybeSecondSpeaker = proposal.secondarySpeaker.flatMap(uuid => Webuser.getEmailFromUUID(uuid))
    val maybeOtherEmails = proposal.otherSpeakers.flatMap(uuid => Webuser.getEmailFromUUID(uuid))
    val listOfEmails = maybeOtherEmails ++ maybeSecondSpeaker.toList
    emailer.addCc(listOfEmails.toSeq: _*) // magic trick to create a java varargs from a scala List

    emailer.setCharset("utf-8")
    emailer.send(
      views.txt.Mails.acceptrefuse.sendProposalRejected(proposal).toString(),
      views.html.Mails.acceptrefuse.sendProposalRejected(proposal).toString()
    )

  }


}
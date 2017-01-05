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
import org.joda.time.{DateTime, DateTimeZone}
import play.api.i18n.Messages
import play.api.Play.current
import play.api.libs.mailer.{Email, MailerPlugin}

/**
  * All emails for password reset, new user, etc.
  *
  * @author created by N.Martignole, Innoteria, on 04/11/2016.
  */
object TransactionalEmails {
  val fromSender = ConferenceDescriptor.current().fromEmail
  val committeeEmail = ConferenceDescriptor.current().committeeEmail
  val bugReportRecipient = ConferenceDescriptor.current().bugReportRecipient
  val bccEmail = ConferenceDescriptor.current().bccEmail

  def sendResetPasswordLink(emailAddress: String, resetUrl: String) = {
    val timestamp: String = new DateTime().toDateTime(DateTimeZone.forID("Europe/Brussels")).toString("HH:mm dd/MM")
    val subjectEmail: String = Messages("mail.reset_password_link.subject", timestamp, Messages("longName"))

    val email = Email(
      subject = subjectEmail,
      from = fromSender,
      to = Seq(emailAddress),
      bcc = bccEmail.map(s => List(s)).getOrElse(Seq.empty[String]),
      bodyText = Some(views.txt.Mails.sendResetLink(resetUrl).toString()),
      bodyHtml = Some(views.html.Mails.sendResetLink(resetUrl).toString()),
      charset = Some("utf-8")
    )
    MailerPlugin.send(email)
  }

  def sendAccessCode(emailAddress: String, code: String) = {
    val subjectEmail: String = Messages("mail.access_code.subject", Messages("longName"))
    val email = Email(
      subject = subjectEmail,
      from = fromSender,
      to = Seq(emailAddress),
      bcc = bccEmail.map(s => List(s)).getOrElse(Seq.empty[String]),
      bodyText = Some(views.txt.Mails.sendAccessCode(emailAddress, code).toString()),
      bodyHtml = Some(views.html.Mails.sendAccessCode(emailAddress, code).toString),
      charset = Some("utf-8")
    )
    MailerPlugin.send(email)
  }

  def sendWeCreatedAnAccountForYou(emailAddress: String, firstname: String, tempPassword: String) = {
    val subjectEmail: String = Messages("mail.account_created.subject", Messages("longName"))
    val email = Email(
      subject = subjectEmail,
      from = fromSender,
      to = Seq(emailAddress),
      bcc = bccEmail.map(s => List(s)).getOrElse(Seq.empty[String]),
      bodyText = Some(views.txt.Mails.sendAccountCreated(firstname, emailAddress, tempPassword).toString()),
      bodyHtml = Some(views.html.Mails.sendAccountCreated(firstname, emailAddress, tempPassword).toString()),
      charset = Some("utf-8")
    )
    MailerPlugin.send(email)
  }

  def sendValidateYourEmail(emailAddress: String, validationLink: String) = {
    val conferenceName = Messages("longName")
    val subjectEmail: String = Messages("mail.email_validation.subject", conferenceName)

    val email = Email(
      subject = subjectEmail,
      from = fromSender,
      to = Seq(emailAddress),
      bcc = bccEmail.map(s => List(s)).getOrElse(Seq.empty[String]),
      bodyText = Some(views.txt.Mails.sendValidateYourEmail(validationLink, conferenceName).toString()),
      bodyHtml = Some(views.html.Mails.sendValidateYourEmail(validationLink, conferenceName).toString()),
      charset = Some("utf-8")
    )
    MailerPlugin.send(email)
  }

  def sendBugReport(bugReport: Issue) = {
    val subjectEmail: String = Messages("mail.issue_reported.subject")

    val email = Email(
      subject = subjectEmail,
      from = fromSender,
      to = Seq(bugReportRecipient),
      cc = Seq(bugReport.reportedBy),
      bcc = bccEmail.map(s => List(s)).getOrElse(Seq.empty[String]),
      bodyText = Some(views.html.Mails.sendBugReport(bugReport).toString()),
      bodyHtml = Some(views.html.Mails.sendBugReport(bugReport).toString()),
      charset = Some("utf-8")
    )
    MailerPlugin.send(email)
  }
}

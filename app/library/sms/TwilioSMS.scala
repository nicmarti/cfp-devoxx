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

package library.sms

import org.apache.commons.lang3.RandomStringUtils
import play.api.data.Form
import play.api.data.Forms._

/**
  *
  * @author created by N.Martignole, Innoteria, on 21/03/2017.
  */
case class TwilioSMS(messageSid: String, // A 34 character unique identifier for the message. May be used to later retrieve this message from the REST API.
                     accountSid: String, // The 34 character id of the Account this message is associated with.
                     messagingServiceSid: Option[String], // 	The 34 character id of the Messaging Service associated to the message.
                     from: String, // Sender
                     to: String, // To
                     body: String, // up to 1600
                     numMedia: Int
                    )

object TwilioSMS {
  def createRandom() = TwilioSMS(
    messageSid = RandomStringUtils.randomAlphanumeric(34),
    accountSid = RandomStringUtils.randomAlphanumeric(34),
    messagingServiceSid = None,
    from = "33663204850",
    to = "33644642531",
    body = "talks",
    numMedia = 0
  )

  val smsForm = Form(mapping(
    "MessageSid" -> text(maxLength = 34),
    "AccountSid" -> text(maxLength = 34),
    "MessagingServiceSid" -> optional(text(maxLength = 34)),
    "From" -> nonEmptyText(maxLength = 16),
    "To" -> nonEmptyText(maxLength = 16),
    "Body" -> nonEmptyText(maxLength = 1600),
    "NumMedia" -> number
  )(TwilioSMS.apply)(TwilioSMS.unapply))


}

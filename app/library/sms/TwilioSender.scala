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

import com.twilio.`type`.PhoneNumber
import models.ConferenceDescriptor
import com.twilio.http.TwilioRestClient
import com.twilio.rest.api.v2010.account.MessageCreator


/**
  * Class for SMS Twilio service. Used to send SMS to Speakers.
  *
  * @author created by N.Martignole, Innoteria, on 20/03/2017.
  */
object TwilioSender {

  val client = new TwilioRestClient.Builder(ConferenceDescriptor.twilioAccountSid,ConferenceDescriptor.twilioAuthToken).build()

  def send(phoneNumber:String, message:String)={
    play.Logger.of("library.sms.TwilioSender").info("---- SHORT SMS ----")
    play.Logger.of("library.sms.TwilioSender").info(s"To $phoneNumber")
    play.Logger.of("library.sms.TwilioSender").info(message)
    play.Logger.of("library.sms.TwilioSender").info("--------------------")


    if(ConferenceDescriptor.twilioMockSMS==false) {
          val msg = new MessageCreator(
              new PhoneNumber(phoneNumber),
              new PhoneNumber(ConferenceDescriptor.twilioSenderNumber),
            message
          )
          msg.create(client)
    }
  }

}

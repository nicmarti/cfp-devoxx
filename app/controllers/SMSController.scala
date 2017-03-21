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

package controllers

import library.sms._
import models.Speaker
import play.api.mvc.Action
import play.api.data.Form
import play.api.data.Forms._

/**
  * SMS Technical controller.
  *
  * @author created by N.Martignole, Innoteria, on 21/03/2017.
  */
object SMSController extends SecureCFPController {

  def allSpeakers() = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      val speakers = Speaker.allSpeakers().filter(_.phoneNumber isDefined)
      Ok(views.html.SMSController.allSpeakers(speakers))
  }

  def testCallback = SecuredAction(IsMemberOf("cfp")) {
    implicit request =>
      val twilioDefault = TwilioSMS.createRandom()
      Ok(views.html.SMSController.testCallback(TwilioSMS.smsForm.fill(twilioDefault)))
  }

  def callback() = Action {
    implicit request =>
      TwilioSMS.smsForm.bindFromRequest.fold(
        hasErrors => {
          play.Logger.error("Unable to parse a SMS, due to " + hasErrors)
          BadRequest(views.html.SMSController.testCallback(hasErrors))
        },
        validSMS => {
          validSMS.body match {
            case t if t.toLowerCase.contains("talk") => SmsActor.actor ! SendListOfTalks(validSMS.from)
            case t if t.toLowerCase.contains("help") => SmsActor.actor ! SendHelpMessage(validSMS.from, t)
            case other => SmsActor.actor ! SendWelcomeAndHelp(validSMS.from)
          }

          Ok("Thanks for your message... give me one minut to find the answer")
        }
      )
  }

  def sendTalksDetails(phonenumber:String)=SecuredAction(IsMemberOf("cfp")){
    implicit request=>
      SmsActor.actor ! SendListOfTalks(phonenumber)
      Redirect(routes.SMSController.allSpeakers())
  }

  def sendWelcomeMessage(phonenumber:String)=SecuredAction(IsMemberOf("cfp")){
    implicit request=>
      SmsActor.actor ! SendWelcomeAndHelp(phonenumber)
      Redirect(routes.SMSController.allSpeakers())
  }
}
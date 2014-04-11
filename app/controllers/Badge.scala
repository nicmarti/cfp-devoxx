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

import play.api.mvc.Action
import java.io.File
import library.{ProcessCSVDir, ZapActor, ProcessCSVFile}

/**
 * Badge controller for QRCode generation.
 *
 * Created by nicolas on 10/04/2014.
 */
object Badge  extends SecureCFPController {

  def home() = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      Ok(views.html.Badge.home())
  }

  def uploadCSV = Action(parse.temporaryFile) { request =>
    request.body.moveTo(new File("/tmp/upload.csv"), replace = true)
    ZapActor.actor!ProcessCSVFile("/tmp/upload.csv")
    Redirect(routes.Badge.home).flashing("success"->"File uploaded... please wait a few seconds")
  }

  def test()=Action{
    ZapActor.actor!ProcessCSVDir("/Users/nicolas/Dropbox (parisjug.org)/Devoxx France (3)/Devoxx France 2014/Badges/Attendees")
    Ok("Done")
  }

}
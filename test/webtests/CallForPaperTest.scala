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

package webtests

import models.ConferenceDescriptor
import play.api.test._
import play.api.test.Helpers._
import play.api.libs.ws._
import org.specs2.mutable._
import java.util.Locale
import play.api.libs.iteratee.Iteratee
import play.api.libs.ws.ResponseHeaders
import scala.concurrent.ExecutionContext.Implicits.global


/**
 * Created by nicolas on 18/06/2014.
 */
class CallForPaperTest extends Specification {
  "an Application" should{
    "run in a browser" in new WithBrowser() {
      browser.goTo("/")
      browser.$("#title").getTexts().get(0) must startWith(ConferenceDescriptor.current().hosterName)

      browser.$("a").click()

      browser.url must equalTo("/")
      browser.$("#title").getTexts().get(0) must equalTo("Hello Coco")
    }
  }
}

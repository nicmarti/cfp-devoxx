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

package library

/**
 * Created by nicolas on 15/03/2014.
 */
case class VCard(firstName: Option[String],lastName: Option[String], company: Option[String], email: Option[String], website: Option[String],
                 phonenumber:Option[String], title:Option[String]) {
  val NAME = "N:"
  val COMPANY = "ORG:"
  val TITLE = "TITLE:"
  val PHONE = "TEL:"
  val WEB = "URL:"
  val EMAIL = "EMAIL:"
  val ADDRESS = "ADR:"

  override def toString: String = {
    val sb = new StringBuffer()
    sb.append("BEGIN:VCARD\n")

    if (firstName.isDefined || lastName.isDefined) {
      sb.append(NAME + lastName.map(s=>s+";").getOrElse("") + firstName.getOrElse(""))
    }
    if (company.isDefined) {
      sb.append("\n" + COMPANY + company.get)
    }
    if (title.isDefined) {
      sb.append("\n" + TITLE + title.get)
    }
    if (phonenumber.isDefined) {
      sb.append("\n" + PHONE + phonenumber.get)
    }
    if (website.isDefined) {
      sb.append("\n" + WEB + website.get)
    }
    if (email.isDefined) {
      sb.append("\n" + EMAIL + email.get)
    }
    sb.append("\nEND:VCARD")
    sb.toString
  }
}
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

package library.csv

/**
 * Created by nicolas on 12/04/2014.
 */
case class Attendee(id: String, firstName: String, lastName: String, email: String,
                    position: String, opt_in_phone: String, phone: String,
                    company: String, organization: String, attendee_type: String,
                    registration_type: String){
  val getQRCodeString:String={
    id +"," +firstName+","+lastName+","+email+","+position+","+company+","+registration_type+","+attendee_type
  }
}

object Attendee {
  def parse(line: String): Attendee = {
    val tokens = line.split(";")
    val id = tokens(0)
    val firstName = tokens(1)
    val lastName = tokens(2)
    val email = tokens(3)
    val position = tokens(4)
    val opt_in_phone = tokens(5)
    val phone = tokens(6)
    val company_name = tokens(7)
    val organization = tokens(8)
    val attendee_type = tokens(9)
    val registration_type = tokens(10)
    Attendee(id, firstName, lastName, email, position, opt_in_phone, phone, company_name, organization, attendee_type, registration_type)
  }


}
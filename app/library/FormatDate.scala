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

import org.joda.time.{Period, PeriodType, DateTime}
import org.joda.time.format.PeriodFormatterBuilder

/**
 * Small helper cause I did not want to add this code in template.
 * Author: nicolas
 * Created: 13/11/2013 16:30
 */
object FormatDate {

  val formatter = new PeriodFormatterBuilder()
    .printZeroNever()
    .appendYears()
    .appendSuffix(" year", " years")
    .appendSeparator(" and ")
    .appendWeeks()
    .appendSuffix(" week"," weeks")
    .appendSeparator(" and ")
    .appendDays()
    .appendSuffix(" day", " days")
    .appendSeparator(", ")
    .appendHours()
    .appendSuffix(" hour", " hours")
    .appendSeparator(", ")
    .appendMinutes()
    .appendSuffix(" minute", " minutes")
    .printZeroRarelyLast()
    .appendSeparator(" and ")
    .appendSeconds()
    .appendSuffix(" second", " seconds")
    .appendSuffix(" ago")
    .toFormatter()

  def ellapsed(maybeEventDate: Option[DateTime]): String = {
    maybeEventDate.map {
      eventDate =>
        val period: Period = new Period(eventDate, new DateTime(), PeriodType.yearWeekDayTime())
        formatter.print(period)
    }.getOrElse("Unknown")
  }
}

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

package models

import play.api.libs.json.Json

/**
 * A Track is a general topic (Java, Architecture, Security)
 *
 * Author: nicolas martignole
 * Created: 06/11/2013 01:41
 */
case class Track(id: String, label: String)

object Track {
  implicit val trackFormat = Json.format[Track]

  val UNKNOWN=Track("unknown", "unknown.label")

  val all = ConferenceDescriptor.ConferenceTracks.ALL

  val allAsIdsAndLabels:Seq[(String,String)] = all.map(a=>(a.id,a.label)).toSeq.sorted

  val allIDs=ConferenceDescriptor.ConferenceTracks.ALL.map(_.id)


  // Compute diff between two Set of Track then returns a ready-to-use list of id/label
  def diffFrom(otherTracks:Set[Track]):Seq[(String,String)] ={
    val diffSet = ConferenceDescriptor.ConferenceTracks.ALL.toSet.diff(otherTracks)
    diffSet.map(a=>(a.id,a.label)).toSeq.sorted
  }
  
  def parse(session:String):Track={
    ConferenceDescriptor.ConferenceTracks.ALL.find(t => t.id == session).getOrElse(UNKNOWN)
  }
}

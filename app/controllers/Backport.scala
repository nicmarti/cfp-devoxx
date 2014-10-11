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

import controllers.CallForPaper._
import models._

/**
 * This controller generates SQL script for the old Devoxx BE CFP.
 * We had to use this trick as a hack, so that digital footage and mobile apps from
 * previous devoxx are still up and running.
 *
 * @author created by N.Martignole, Innoteria, on 11/10/2014.
 */
object Backport extends SecureCFPController {
  def allAcceptedSpeakers() = SecuredAction {
    implicit request =>

      val speakers = Speaker.allSpeakersWithAcceptedTerms().sortBy(_.cleanName)


      Ok(views.html.Backport.sqlForSpeakers(speakers))
  }

  def allProposals() = SecuredAction {
    implicit request =>

      val allProposalTypes = ConferenceDescriptor.ConferenceProposalTypes.ALL
      val allTracks =  ConferenceDescriptor.ConferenceTracks.ALL

      val scheduledProposals = allProposalTypes.map(pt=>ScheduleConfiguration.getPublishedSchedule(pt.id))


      Ok(views.html.Backport.sqlForProposals(allProposalTypes, allTracks, scheduledProposals))
  }

}

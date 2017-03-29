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

package library.search

import models.{Proposal, ProposalState, ScheduleConfiguration, Slot}

/**
  * Created for a demo with Guillaume Laforge and Stephan Janssen.
  *
  * @author created by N.Martignole, Innoteria, on 29/03/2017.
  */

case class AdvancedSearchParam(topic:Option[String],
                               track:Option[String],
                               format:Option[String],
                               room:Option[String],
                               day:Option[String],
                               after:Option[String],
                               speaker:Option[String],
                               company:Option[String])

object AdvancedSearch {


  def advancedSearch()={
     import scalaz._
    import Scalaz._

    val allPublishedSlots: List[Slot] = ScheduleConfiguration.loadAllPublishedSlots().filter(_.proposal.isDefined)
    val approvedOrAccepted = allPublishedSlots.filter(p => p.proposal.get.state == ProposalState.APPROVED || p.proposal.get.state == ProposalState.ACCEPTED)

    val publishedProposalIDs: Set[String] = approvedOrAccepted.map(_.proposal.get.id).toSet

    // Build the list of Speakers
//    val allSpeakersUUID: Set[String] = approvedOrAccepted.flatMap(_.proposal.get.allSpeakerUUIDs).toSet
//    val allSpeakers = Speaker.loadSpeakersFromSpeakerIDs(allSpeakersUUID)

    val allValidProposals = Proposal.loadAndParseProposals(publishedProposalIDs)

    type ProposalAndError=(Proposal, String, String,String)

    def checkSameTitle: (Proposal, Proposal) => ValidationNel[ProposalAndError, String] = (p1,p2) => {
      if (p1.title == p2.title) "Same title".successNel
      else (p1, "Title was changed",p1.title, p2.title).failureNel
    }

    def checkSameSummary: (Proposal, Proposal) => ValidationNel[ProposalAndError, String] = (p1,p2) => {
      if (p1.summary == p2.summary) "Same summary".successNel
      else (p1,"Summary was changed",p1.summary, p2.summary).failureNel
    }

    def checkSameMainSpeaker: (Proposal, Proposal) =>ValidationNel[ProposalAndError, String] = (p1,p2) => {
      if (p1.mainSpeaker == p2.mainSpeaker) "Same main speaker".successNel
      else (p1, "Main speaker was changed", p1.mainSpeaker, p2.mainSpeaker).failureNel
    }

    def checkSameSecondSpeakers: (Proposal, Proposal) => ValidationNel[ProposalAndError, String] = (p1,p2) => {
      if (p1.secondarySpeaker == p2.secondarySpeaker) "Same secondary speaker".successNel
      else (p1,"Secondary speaker was changed",p1.secondarySpeaker.getOrElse("?"), p2.secondarySpeaker.getOrElse("?")).failureNel
    }

    def checkSameOtherSpeakers: (Proposal, Proposal) => ValidationNel[ProposalAndError, String] = (p1,p2) => {
      if (p1.otherSpeakers == p2.otherSpeakers) "Same other speaker".successNel
      else (p1, "Other speaker was changed", p1.otherSpeakers.mkString("/") , p2.otherSpeakers.mkString("/")).failureNel
    }

    val validateProposals= for {
      a <- checkSameTitle
      b <- checkSameSummary
      c <- checkSameMainSpeaker
      d <- checkSameSecondSpeakers
      e <- checkSameOtherSpeakers
    } yield( a |@| b |@| c |@| d |@| e ) // |@| is an Applicative Builder -> it accumulates the combined result as a tuple

    def doNothing(p1:String, p2:String, p3:String,p4:String, p5:String) = s"$p1 $p2 $p3 $p4 $p5"

   val messages:List[ValidationNel[ProposalAndError,String]]= allPublishedSlots.map {
      s: Slot =>
        val publishedProposal = s.proposal.get
        // cause we did a filter where proposal is Defined
        val fromCFPProposal = allValidProposals(publishedProposal.id)
        validateProposals(publishedProposal, fromCFPProposal)(doNothing)
    }

    val onlyErrors= messages.filter(_.isFailure).flatMap { f: Validation[NonEmptyList[ProposalAndError], String] =>
      val noEmpty = f.toEither.left.get
      noEmpty.toList
    }

    onlyErrors
  }
}

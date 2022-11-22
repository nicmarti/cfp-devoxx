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

import library.{ComputeLeaderboard, ComputeVotesAndScore, _}
import models._
import play.api.i18n.Messages
import play.api.mvc.{Action, AnyContent}

/**
  * Controller for Leaderboard stats.
  *
  * @author created by N.Martignole, Innoteria, on 06/01/2017.
  */

object LeaderboardController extends SecureCFPController {

  /**
    * Constructs the LeaderBoardParams, also used by the digest emails.
    *
    * @return LeaderBoardParams
    */
  def getLeaderBoardParams: LeaderBoardParams = {
    val totalSpeakers = Leaderboard.totalSpeakers()
    val totalProposals = Leaderboard.totalProposals()
    val totalVotes = Leaderboard.totalVotes()
    val totalWithVotes = Leaderboard.totalWithVotes()
    val totalNoVotes = Leaderboard.totalNoVotes()
    val mostReviewed = Leaderboard.mostReviewed().toList
    val bestReviewers = Review.allReviewersAndStatsWithOneReviewAtLeast()
    val lazyOnes = Leaderboard.lazyOnes()

    val totalSubmittedByTrack = Leaderboard.totalSubmittedByTrack()
    val totalSubmittedByType = Leaderboard.totalSubmittedByType()
    val totalAcceptedByTrack = Leaderboard.totalAcceptedByTrack()
    val totalAcceptedByType = Leaderboard.totalAcceptedByType()

    val totalSlotsToAllocate = ApprovedProposal.getTotal
    val totalApprovedSpeakers = Leaderboard.totalApprovedSpeakers()
    val totalWithTickets = Leaderboard.totalWithTickets()
    val totalRefusedSpeakers = Leaderboard.totalRefusedSpeakers()
    val totalCommentsPerProposal = Leaderboard.totalCommentsPerProposal().map { case (k, v) => (k.toString, v) }.toList

    val allApproved = ApprovedProposal.allApproved()

    val allApprovedByTrack: Map[String, Int] = allApproved.groupBy(_.track.label).map(trackAndProposals => (trackAndProposals._1, trackAndProposals._2.size))
    val allApprovedByTalkType: Map[String, Int] = allApproved.groupBy(_.talkType.id).map(trackAndProposals => (trackAndProposals._1, trackAndProposals._2.size))

    // TODO Would it be better to have the following two statements in the Leaderboard.computeStats method instead?
    def generousVoters: List[(String, BigDecimal)] =
      bestReviewers.filter(_.totalTalksReviewed > 0)
        .map(b => (b.uuid, b.average))

    def proposalsBySpeakers: List[(String, Int)] =
      Speaker.allSpeakers()
        .map(speaker => (speaker.uuid, Proposal.allMySubmittedProposals(speaker.uuid).size))
        .filter(_._2 > 0)

    LeaderBoardParams(totalSpeakers,
      totalProposals,
      totalVotes,
      mostReviewed,
      bestReviewers,
      lazyOnes,
      generousVoters,
      proposalsBySpeakers,
      totalSubmittedByTrack,
      totalSubmittedByType,
      totalCommentsPerProposal,
      totalAcceptedByTrack,
      totalAcceptedByType,
      totalSlotsToAllocate,
      totalApprovedSpeakers,
      totalWithTickets,
      totalRefusedSpeakers,
      allApprovedByTrack,
      allApprovedByTalkType,
      totalWithVotes, totalNoVotes)
  }

  def leaderBoard: Action[AnyContent] = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>

      val totalGTickets = ReviewByGoldenTicket.totalGoldenTickets()
      val totalGTStats = ReviewByGoldenTicket.allReviewersAndStats()

      def goldenTicketParam = GoldenTicketsParams(totalGTickets, totalGTStats)

      Ok(views.html.LeaderboardController.leaderBoard(getLeaderBoardParams, goldenTicketParam))
  }

  def allReviewersAndStats = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      Ok(views.html.LeaderboardController.allReviewersAndStatsAsChart(isHTTPS=play.Play.application().isProd))
  }

  def dataForAllReviewersAndStats = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      // Do not keep someone that did zero review
      val data = Review.allReviewersAndStats().filterNot(_.totalTalksReviewed == 0).flatMap {
        case ReviewerStats(uuid, totalPoints, nbReview, nbAbstentions, average) =>
          Webuser.findByUUID(uuid).map {
            webuser =>
              val webuserNick = webuser.firstName.take(1).toUpperCase + webuser.lastName.replaceAll(" ", "").take(2).toUpperCase()
              val reviewer = webuser.firstName + " " + webuser.lastName
              s"{c:[{v:'$webuserNick'},{v:$nbReview},{v:$average},{v:'$reviewer'},{v:$nbAbstentions}]}"
          }
      }.mkString("[", ",", "]")

      val response: String =
        s"""google.visualization.Query.setResponse(
           |{version:'0.6',
           |reqId:'0',
           |status:'ok',sig:'5982206968295329967',
           |table:{
           |cols:[{label:'ID',type:'string'},{label:'Number of Review (incl. Abs)',type:'number'},{label:'Average Rate',type:'number'},{label:'Reviewer',type:'string'},{label:'Number of abstentions',type:'number'}],
           |rows:$data
           |}});
        """.stripMargin

      Ok(response).as(JSON)
  }

  def doComputeLeaderBoard() = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      library.ZapActor.actor ! ComputeLeaderboard()
      library.ZapActor.actor ! ComputeVotesAndScore()
      Redirect(routes.CFPAdmin.index()).flashing("success" -> Messages("leaderboard.compute"))
  }

  def doComputeVotesTotal() = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      ZapActor.actor ! ComputeVotesAndScore()
      Redirect(routes.CFPAdmin.allVotes()).flashing("success" -> "Recomputing votes and scores...")
  }

  def allProposalsByCompany() = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>

      val allInteresting = Proposal.allProposalIDs.diff(Proposal.allProposalIDsDeletedArchivedOrDraft())

      val allInterestingProposals = Proposal.loadAndParseProposals(allInteresting)

      val allSpeakersUUIDs: Iterable[String] = allInterestingProposals.values.flatMap(p => p.allSpeakerUUIDs)

      val uniqueSetOfSpeakersUUID: Set[String] = allSpeakersUUIDs.toSet

      val allSpeakers: List[Speaker] = Speaker.loadSpeakersFromSpeakerIDs(uniqueSetOfSpeakersUUID)

      val speakers = allSpeakers
        .groupBy(_.company.map(_.toUpperCase.trim).getOrElse("No Company"))
        .toList
        .sortBy(_._2.size)
        .reverse

      val companiesAndProposals: List[(String, Set[Proposal])] = speakers.map {
        case (company, speakerList) =>
          val setOfProposals = speakerList.flatMap {
            s =>
              Proposal.allProposalsByAuthor(s.uuid).filter(p=> p._2.state == ProposalState.SUBMITTED || p._2.state == ProposalState.ACCEPTED || p._2.state == ProposalState.APPROVED).values
          }.toSet
          (company, setOfProposals)
      }.filterNot(_._2.isEmpty)
        .sortBy(p => p._2.size)
        .reverse

      Ok(views.html.LeaderboardController.allProposalsByCompany(companiesAndProposals))
  }

  def allProposalsByCompanyAsGraph() = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      Ok(views.html.LeaderboardController.allProposalsByCompanyAsGraph(isHTTPS=play.Play.application().isProd))
  }

  def dataForAllProposalsByCompany() = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>

      val allInteresting = Proposal.allProposalIDs.diff(Proposal.allProposalIDsDeletedArchivedOrDraft())

      val allInterestingProposals: Set[Proposal] = Proposal.loadAndParseProposals(allInteresting).values.toSet.filter(p=> p.state == ProposalState.SUBMITTED || p.state == ProposalState.ACCEPTED || p.state == ProposalState.APPROVED)

      val allSpeakersUUIDs: Iterable[String] = allInterestingProposals.flatMap(p => p.allSpeakerUUIDs)

      val uniqueSetOfSpeakersUUID: Set[String] = allSpeakersUUIDs.toSet
      val allSpeakers: List[Speaker] = Speaker.loadSpeakersFromSpeakerIDs(uniqueSetOfSpeakersUUID)

      val companyAndSpeakersUUID = allSpeakers
        .groupBy(_.company.map(_.toUpperCase.trim).getOrElse("No Company"))
        .map{
          case(company,listOfSpeakers)=>
            (company, listOfSpeakers.map(_.uuid).toSet)
        }

      val companiesAndProposals2: List[(String,Int, Set[Double], Int)] = companyAndSpeakersUUID.map {
        case (company, setOfSpeakerUUID) =>

          val submittedProposals= setOfSpeakerUUID.flatMap {
            s =>
              Proposal.allProposalsByAuthor(s).filter(p=> p._2.state == ProposalState.SUBMITTED || p._2.state == ProposalState.ACCEPTED || p._2.state == ProposalState.APPROVED)
          }

          val validSpeakersUUID= submittedProposals.flatMap(_._2.allSpeakerUUIDs.toSet)
          val onlySpeakersThatSubmitted = validSpeakersUUID.intersect(setOfSpeakerUUID)
          val withScore = submittedProposals.map {
            prop =>
              Review.averageScore(prop._1)
          }
          val nbOfProposals = submittedProposals.size
          (company, nbOfProposals, withScore, onlySpeakersThatSubmitted.size)
      }.filter(nbOfProposals => nbOfProposals._2>7) // show only companies with at least 7 proposals
        .toList
        .sortBy(_._4).reverse

      val data = companiesAndProposals2.map {
        case (company,nbProposals, proposalIDandScore, totalSpeakers) =>
          val nbReview = proposalIDandScore.size
          val total=proposalIDandScore.sum

          val average = total / nbReview

          s"{c:[{v:'$company'},{v:$nbProposals},{v:$average},{v:'$company'},{v:$totalSpeakers}]}"

      }.mkString("[", ",", "]")

      val response: String =
        s"""google.visualization.Query.setResponse(
           |{version:'0.6',
           |reqId:'0',
           |status:'ok',sig:'5982206968295329967',
           |table:{
           |cols:[{label:'Company',type:'string'},{label:'Nb of talks',type:'number'},{label:'Average Score',type:'number'},{label:'Company name',type:'string'},{label:'Nb speakers',type:'number'}],
           |rows:$data
           |}});
        """.stripMargin

      Ok(response).as(JSON)
  }
}

case class GoldenTicketsParams(
                                totalTickets: Long,
                                stats: List[(String, Int, Int)]
                              )

case class LeaderBoardParams(
                              totalSpeakers: Long,
                              totalProposals: Long,
                              totalVotes: Long,
                              mostReviewed: List[(String, Int)],
                              bestReviewers: List[ReviewerStats],
                              lazyOnes: Map[String, String],
                              generousVoters: List[(String, BigDecimal)],
                              proposalsBySpeakers: List[(String, Int)],
                              totalSubmittedByTrack: Map[String, Int],
                              totalSubmittedByType: Map[String, Int],
                              totalCommentsPerProposal: List[(String, Int)],
                              totalAcceptedByTrack: Map[String, Int],
                              totalAcceptedByType: Map[String, Int],
                              totalSlotsToAllocate: Map[String, Int],
                              totalApprovedSpeakers: Long,
                              totalWithTickets: Long,
                              totalRefusedSpeakers: Long,
                              allApprovedByTrack: Map[String, Int],
                              allApprovedByTalkType: Map[String, Int],
                              totalWithVotes: Long,
                              totalNoVotes: Long
                            )
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

import library.Redis

/**
 * Leaderboard for stats, used by ZapActor.
 * Created by nicolas on 21/01/2014.
 */
object Leaderboard {
  def computeStats() = Redis.pool.withClient {
    implicit client =>

      // First, stats on Proposal, cause Computed:Reviewer:ReviewedOne is used for
      // the lazy ones
      Review.computeAndGenerateVotes()

      val tx = client.multi()
      tx.del("Leaderboard:totalByCategories")
      tx.del("Leaderboard:totalByType")

      val totalSpeakers = Speaker.countAll()
      tx.set("Leaderboard:totalSpeakers", totalSpeakers.toString())

      val totalProposals = Proposal.countAll()
      tx.set("Leaderboard:totalProposals", totalProposals.toString())

      val totalVotes = Review.countAll()
      tx.set("Leaderboard:totalVotes", totalVotes.toString())

      val totalWithVotes = Review.countWithVotes()
      tx.set("Leaderboard:totalWithVotes", totalWithVotes.toString())

      val totalNoVotes = Review.countWithNoVotes()
      tx.set("Leaderboard:totalNoVotes", totalNoVotes.toString())

      val mostReviewed = Review.mostReviewed()
      mostReviewed.map{
        mr=>
        tx.set("Leaderboard:mostReviewed:proposal", mr._1)
        tx.set("Leaderboard:mostReviewed:score", mr._2.toString)
      }.getOrElse{
        tx.del("Leaderboard:mostReviewed:proposal")
        tx.del("Leaderboard:mostReviewed:score")
      }

      Review.bestReviewer().map {
        bestReviewer =>
          tx.set("Leaderboard:bestReviewer:uuid", bestReviewer._1)
          tx.set("Leaderboard:bestReviewer:score", bestReviewer._2.toString())
      }.getOrElse{
        tx.del("Leaderboard:bestReviewer:uuid")
        tx.del("Leaderboard:bestReviewer:score")
      }

      Review.worstReviewer().map{
        worstReviewer =>
          tx.set("Leaderboard:worstReviewer:uuid", worstReviewer._1)
          tx.set("Leaderboard:worstReviewer:score", worstReviewer._2.toString())
      }.getOrElse{
        tx.del("Leaderboard:worstReviewer:uuid")
        tx.del("Leaderboard:worstReviewer:score")
      }

      val totalSubmittedByTrack = Proposal.totalSubmittedByTrack()
      tx.del("Leaderboard:totalSubmittedByTrack")
      totalSubmittedByTrack.map {
        case (track: Track, total: Int) =>
          tx.hset("Leaderboard:totalSubmittedByTrack", track.id, total.toString)
      }

      tx.del("Leaderboard:totalSubmittedByType")
      val totalSubmittedByType = Proposal.totalSubmittedByType()
      totalSubmittedByType.toList.map {
        case (propType: ProposalType, total: Int) =>
          tx.hset("Leaderboard:totalSubmittedByType", propType.id, total.toString)
      }

      val totalAcceptedByTrack = Proposal.totalAcceptedByTrack()
      tx.del("Leaderboard:totalAcceptedByTrack")
      totalAcceptedByTrack.map {
        case (track: Track, total: Int) =>
          tx.hset("Leaderboard:totalAcceptedByTrack", track.label, total.toString)
      }

      val totalAcceptedByType = Proposal.totalAcceptedByType()
      tx.del("Leaderboard:totalAcceptedByType")
      totalAcceptedByType.toList.map {
        case (propType: ProposalType, total: Int) =>
          tx.hset("Leaderboard:totalAcceptedByType", propType.id, total.toString)
      }

      val allWebusers= Webuser.allCFPWebusers().toSet
      val totalApprovedSpeakers = ApprovedProposal.allApprovedSpeakerIDs().diff(allWebusers.map(_.uuid)).size
      tx.set("Leaderboard:totalApprovedSpeakers", totalApprovedSpeakers.toString)

      val totalWithTickets = ApprovedProposal.allApprovedSpeakersWithFreePass().map(_.uuid).diff(allWebusers.map(_.uuid)).size
      tx.set("Leaderboard:totalWithTickets", totalWithTickets.toString)

    val allCFPWebusers= Webuser.allCFPWebusers().map(w=>w.uuid).toSet
    val allApprovedIDs= ApprovedProposal.allApprovedSpeakerIDs()
    val allRejectedIDs= ApprovedProposal.allRefusedSpeakerIDs()

    val refusedSpeakers = allRejectedIDs.diff(allCFPWebusers).diff(allApprovedIDs)

    val totalRefusedSpeakers = refusedSpeakers.size
    tx.set("Leaderboard:totalRefusedSpeakers", totalRefusedSpeakers.toString)

    tx.exec()
  }

  def totalSpeakers():Long = {
    getFromRedis("Leaderboard:totalSpeakers")
  }

  def totalProposals():Long = {
    getFromRedis("Leaderboard:totalProposals")
  }

  def totalVotes():Long = {
    getFromRedis("Leaderboard:totalVotes")
  }

  def totalWithVotes():Long = {
    getFromRedis("Leaderboard:totalWithVotes")
  }

  def totalNoVotes():Long = {
    getFromRedis("Leaderboard:totalNoVotes")
  }

  def mostReviewed():Option[(String,String)] = {
    Redis.pool.withClient {
      implicit client =>
        for (proposalId <- client.get("Leaderboard:mostReviewed:proposal");
             score <- client.get("Leaderboard:mostReviewed:score")) yield (proposalId, score)
    }
  }

  def bestReviewer():Option[(String,String)] = Redis.pool.withClient {
    implicit client =>
      for (uuid <- client.get("Leaderboard:bestReviewer:uuid");
           score <- client.get("Leaderboard:bestReviewer:score")) yield (uuid, score)
  }

  // Returns the Reviewer that did at least one review, but the fewest reviews.
  def worstReviewer():Option[(String,String)] = Redis.pool.withClient {
    implicit client =>
      for (uuid <- client.get("Leaderboard:worstReviewer:uuid");
           score <- client.get("Leaderboard:worstReviewer:score")) yield (uuid, score)
  }

  // Returns the user that has the lowest reviewed number of proposals and the full list of cfp user that did not
  // yet reviewed any talk
  def lazyOnes():Map[String, String] = Redis.pool.withClient {
    implicit client =>
     val lazyOneWithOneVote = worstReviewer()
      // Take CFP members, remove admin and remove all webuser that reviewed at least one
     val otherThatHaveNoVotes  =  client.sdiff("Webuser:cfp", "Webuser:admin", "Computed:Reviewer:ReviewedOne" ).map(s=>(s,"0"))
     val toReturn = (lazyOneWithOneVote.toSet ++ otherThatHaveNoVotes).toMap
    toReturn
  }

  def totalSubmittedByTrack():Map[String,Int] = Redis.pool.withClient {
    implicit client =>
      client.hgetAll("Leaderboard:totalSubmittedByTrack").map {
        case (key: String, value: String) =>
          (key, value.toInt)
      }
  }

  def totalSubmittedByType():Map[String,Int] = Redis.pool.withClient {
    implicit client =>
      client.hgetAll("Leaderboard:totalSubmittedByType").map {
        case (key: String, value: String) =>
          (key, value.toInt)
      }
  }

  def totalAcceptedByTrack():Map[String,Int] = Redis.pool.withClient {
    implicit client =>
      client.hgetAll("Leaderboard:totalAcceptedByTrack").map {
        case (key: String, value: String) =>
          (key, value.toInt)
      }
  }

  def totalAcceptedByType():Map[String,Int] = Redis.pool.withClient {
    implicit client =>
      client.hgetAll("Leaderboard:totalAcceptedByType").map {
        case (key: String, value: String) =>
          (key, value.toInt)
      }
  }

  private def getFromRedis(key: String): Long = Redis.pool.withClient {
    implicit client =>
      client.get(key).map(_.toLong).getOrElse(0L)
  }

  def totalApprovedSpeakers():Long ={
      getFromRedis("Leaderboard:totalApprovedSpeakers")
  }

  def totalWithTickets():Long ={
      getFromRedis("Leaderboard:totalWithTickets")
  }

  def totalRefusedSpeakers():Long={
    getFromRedis("Leaderboard:totalRefusedSpeakers")
  }
}

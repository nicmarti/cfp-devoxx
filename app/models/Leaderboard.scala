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
      tx.set("Leaderboard:mostReviewed:proposal", mostReviewed.map(_._1).getOrElse("??"))
      tx.set("Leaderboard:mostReviewed:score", mostReviewed.map(_._2.toString()).getOrElse("??"))

      Review.bestReviewer().map {
        bestReviewer =>
          tx.set("Leaderboard:bestReviewer:uuid", bestReviewer._1)
          tx.set("Leaderboard:bestReviewer:score", bestReviewer._2.toString())
      }

      Review.worstReviewer().map{
        worstReviewer =>
          tx.set("Leaderboard:worstReviewer:uuid", worstReviewer._1)
          tx.set("Leaderboard:worstReviewer:score", worstReviewer._2.toString())
      }

      val totalSubmittedByCategories = Proposal.totalSubmittedByTrack()
      totalSubmittedByCategories.map {
        case (track: Track, total: Int) =>
          tx.hset("Leaderboard:totalSubmittedByCategories,ByCategories", track.label, total.toString)
      }

      val totalSubmittedByType = Proposal.totalSubmittedByType()
      totalSubmittedByType.toList.map {
        case (propType: ProposalType, total: Int) =>
          tx.hset("Leaderboard:totalSubmittedByType", propType.id, total.toString)
      }

      val totalAcceptedByCategories = Proposal.totalAcceptedByTrack()
      totalAcceptedByCategories.map {
        case (track: Track, total: Int) =>
          tx.hset("Leaderboard:totalAcceptedByCategories", track.label, total.toString)
      }

      val totalAcceptedByType = Proposal.totalAcceptedByType()

      totalAcceptedByType.toList.map {
        case (propType: ProposalType, total: Int) =>
          tx.hset("Leaderboard:totalAcceptedByType", propType.id, total.toString)
      }

      val allWebusers= Webuser.allCFPWebusers().toSet
      val totalApprovedSpeakers = ApprovedProposal.allApprovedSpeakerIDs().diff(allWebusers.map(_.uuid)).size
      tx.set("Leaderboard:totalApprovedSpeakers", totalApprovedSpeakers.toString)

      val totalWithTickets = ApprovedProposal.allApprovedSpeakersWithFreePass().map(_.uuid).diff(allWebusers.map(_.uuid)).size
      tx.set("Leaderboard:totalWithTickets", totalWithTickets.toString)

      val totalWithOneProposal = Proposal.totalWithOneProposal()
      tx.set("Leaderboard:totalWithOneProposal", totalWithOneProposal.toString)

    val allCFPWebusers= Webuser.allCFPWebusers().map(w=>w.uuid).toSet
    val allApprovedIDs= ApprovedProposal.allApprovedSpeakerIDs()
    val allRejectedIDs= ApprovedProposal.allRefusedSpeakerIDs()

    val refusedSpeakers = allRejectedIDs.diff(allCFPWebusers).diff(allApprovedIDs)

//    val allSpeakers = Speaker.allSpeakers().filter(s=>refusedSpeakers.contains(s.uuid))
//    println("all refused speakers "+allSpeakers.filter(_.lang==Some("en")).size)

    val totalRefusedSpeakers = refusedSpeakers.size
    tx.set("Leaderboard:totalRefusedSpeakers", totalRefusedSpeakers.toString)


      tx.exec()
  }

  def totalSpeakers() = {
    getFromRedis("Leaderboard:totalSpeakers")
  }

  def totalProposals() = {
    getFromRedis("Leaderboard:totalProposals")
  }

  def totalVotes() = {
    getFromRedis("Leaderboard:totalVotes")
  }

  def totalWithVotes() = {
    getFromRedis("Leaderboard:totalWithVotes")
  }

  def totalNoVotes() = {
    getFromRedis("Leaderboard:totalNoVotes")
  }

  def mostReviewed() = {
    Redis.pool.withClient {
      implicit client =>
        for (proposalId <- client.get("Leaderboard:mostReviewed:proposal");
             score <- client.get("Leaderboard:mostReviewed:score")) yield (proposalId, score)
    }
  }

  def bestReviewer() = Redis.pool.withClient {
    implicit client =>
      for (uuid <- client.get("Leaderboard:bestReviewer:uuid");
           score <- client.get("Leaderboard:bestReviewer:score")) yield (uuid, score)
  }

  def lazyOnes():Set[(String, String)] = Redis.pool.withClient {
    implicit client =>
     val lazyOneWithOneVote = for (uuid <- client.get("Leaderboard:worstReviewer:uuid");
           score <- client.get("Leaderboard:worstReviewer:score")) yield (uuid, score)
     val otherThatHaveNoVotes  =  client.sdiff("Webuser:cfp", "Computed:Reviewer:ReviewedOne" ).map(s=>(s,"0"))
     lazyOneWithOneVote.toSet ++ otherThatHaveNoVotes
  }

  def totalSubmittedByCategories() = Redis.pool.withClient {
    implicit client =>
      client.hgetAll("Leaderboard:totalSubmittedByCategories").map {
        case (key: String, value: String) =>
          (key, value.toInt)
      }
  }

  def totalSubmittedByType() = Redis.pool.withClient {
    implicit client =>
      client.hgetAll("Leaderboard:totalSubmittedByType").map {
        case (key: String, value: String) =>
          (key, value.toInt)
      }
  }

  def totalAcceptedByCategories() = Redis.pool.withClient {
    implicit client =>
      client.hgetAll("Leaderboard:totalAcceptedByCategories").map {
        case (key: String, value: String) =>
          (key, value.toInt)
      }
  }

  def totalAcceptedByType() = Redis.pool.withClient {
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

  def totalApprovedSpeakers() ={
      getFromRedis("Leaderboard:totalApprovedSpeakers")
  }

  def totalWithTickets() ={
      getFromRedis("Leaderboard:totalWithTickets")
  }

  def totalWithOneProposal() ={
      getFromRedis("Leaderboard:totalWithOneProposal")
  }

  def totalRefusedSpeakers()={
    getFromRedis("Leaderboard:totalRefusedSpeakers")
  }
}

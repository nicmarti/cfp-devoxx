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

      val bestReviewer = Review.bestReviewer()
      tx.set("Leaderboard:bestReviewer:uuid", bestReviewer._1)
      tx.set("Leaderboard:bestReviewer:score", bestReviewer._2.toString())

      val worstReviewer = Review.worstReviewer()
      tx.set("Leaderboard:worstReviewer:uuid", worstReviewer._1)
      tx.set("Leaderboard:worstReviewer:score", worstReviewer._2.toString())

      val totalByCategories = Proposal.totalSubmittedByTrack()
      totalByCategories.map {
        case (track: Track, total: Int) =>
          tx.hset("Leaderboard:totalByCategories", track.label, total.toString)
      }

      val totalByType = Proposal.totalSubmittedByType()
      totalByType.toList.map {
        case (propType: ProposalType, total: Int) =>
          tx.hset("Leaderboard:totalByType", propType.id, total.toString)
      }
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

  def worstReviewer() = Redis.pool.withClient {
    implicit client =>
      for (uuid <- client.get("Leaderboard:worstReviewer:uuid");
           score <- client.get("Leaderboard:worstReviewer:score")) yield (uuid, score)
  }

  def totalByCategories() = Redis.pool.withClient {
    implicit client =>
      client.hgetAll("Leaderboard:totalByCategories").map {
        case (key: String, value: String) =>
          (key, value.toInt)
      }
  }

  def totalByType() = Redis.pool.withClient {
    implicit client =>
      client.hgetAll("Leaderboard:totalByType").map {
        case (key: String, value: String) =>
          (key, value.toInt)
      }
  }

  private def getFromRedis(key: String): Long = Redis.pool.withClient {
    implicit client =>
      client.get(key).map(_.toLong).getOrElse(0L)
  }
}

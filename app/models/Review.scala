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
import org.joda.time.{Instant, DateTime}
import play.api.libs.json.{Json, Format}

/**
 * When a CFP admin checks or perform a review on a talk, we store this event.
 *
 * We use a SET to store which proposal was reviewed
 * We use a ZSET to store the score
 *
 * Author: nicolas martignole
 * Created: 11/11/2013 10:21
 */
case class Review(reviewer: String, proposalId: String, vote: Int, date: DateTime)

object Review {
  // We use 4 different Redis objects
  // 1) a SET to keep an history of all proposals we voted for
  // 2) a SET to keep an history of all voters for a proposal
  // 3) a Sorted Set where key is "reviewer email" and value is the vote. It keeps only the latest vote.
  //     If you vote more than once for a talk, it keeps only the latest vote
  // 4) a HASH with the Review as JSON. We keep an history of updates on a proposal
  def voteForProposal(proposalId: String, reviewer: String, vote: Int) = Redis.pool.withClient {
    implicit client =>
      val tx = client.multi()
      tx.sadd(s"Proposals:Reviewed:ByAuthor:${reviewer}", proposalId)
      tx.sadd(s"Proposals:Reviewed:ByProposal:${proposalId}", reviewer)
      tx.zadd(s"Proposals:Votes:${proposalId}", vote, reviewer) // if the vote does already exist, Redis updates the existing vote. reviewer is a discriminator on Redis.
      tx.zadd(s"Proposals:Dates:${proposalId}", new Instant().getMillis, reviewer + "__" + vote) // Store when this user voted for this talk
      tx.exec()
  }

  def allProposalsNotReviewed(reviewer: String): List[Proposal] = Redis.pool.withClient {
    implicit client =>
      val allProposalIDsForReview = client.sdiff(s"Proposals:ByState:${ProposalState.SUBMITTED.code}", s"Proposals:Reviewed:ByAuthor:${reviewer}").toSet
      Proposal.loadProposalByIDs(allProposalIDsForReview, ProposalState.SUBMITTED)
  }

  def deleteVoteForProposal(proposalId: String) = Redis.pool.withClient {
    implicit client =>
      val allAuthors = client.smembers(s"Proposals:Reviewed:ByProposal:${proposalId}")
      allAuthors.foreach {
        author: String =>
          client.srem(s"Proposals:Reviewed:ByAuthor:${author}", proposalId)
      }
      client.del(s"Proposals:Reviewed:ByProposal:${proposalId}")
      client.del(s"Proposals:Votes:${proposalId}")
      client.del(s"Proposals:Dates:${proposalId}")
  }

  val ReviewerAndVote = "(\\w+)__(\\d+)".r

  // Returns the history of votes for a proposal. If a reviewer changed its vote, we will also see it.
  def allHistoryOfVotes(proposalId: String): List[Review] = Redis.pool.withClient {
    implicit client =>
      val listOfReviewsAndVotes = client.zrevrangeByScoreWithScores(s"Proposals:Dates:${proposalId}", "+inf", "-inf")
      val history: List[Review] = listOfReviewsAndVotes.flatMap {
        tuple =>
          val reviewerAndVote = tuple._1
          val date = tuple._2
          reviewerAndVote match {
            // Regexp extractor
            case ReviewerAndVote(reviewer, vote) => Option(Review(reviewer, proposalId, vote.toInt, new Instant(date).toDateTime))
            case _ => None
          }
      }
      history
  }

  def currentScore(proposalId:String):Int=Redis.pool.withClient{
    implicit client=>
      val allScores = client.zrevrangeByScoreWithScores(s"Proposals:Votes:${proposalId}",10,0).toList
      allScores.foldRight(0)((scoreAndReview, accumulated:Int) => accumulated + scoreAndReview._2.toInt )
  }

  def totalVoteFor(proposalId:String):Long=Redis.pool.withClient{
    implicit client=>
      client.zcount(s"Proposals:Votes:${proposalId}",0,10) // how many votes between 0 and 10 ?
  }

  // If we remove those who voted "0" for a talk, how many votes do we have?
  def totalVoteCastFor(proposalId:String):Long=Redis.pool.withClient{
    implicit client=>
      client.zcount(s"Proposals:Votes:${proposalId}",1,10)
  }

}

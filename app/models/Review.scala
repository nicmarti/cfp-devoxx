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
  def voteForProposal(proposalId: String, reviewerUUID: String, vote: Int) = Redis.pool.withClient {
    implicit client =>
      val tx = client.multi()
      tx.sadd(s"Proposals:Reviewed:ByAuthor:${reviewerUUID}", proposalId)
      tx.sadd(s"Proposals:Reviewed:ByProposal:${proposalId}", reviewerUUID)
      tx.zadd(s"Proposals:Votes:${proposalId}", vote, reviewerUUID) // if the vote does already exist, Redis updates the existing vote. reviewer is a discriminator on Redis.
      tx.zadd(s"Proposals:Dates:${proposalId}", new Instant().getMillis, reviewerUUID + "__" + vote) // Store when this user voted for this talk
      tx.exec()
      Event.storeEvent(Event(proposalId, reviewerUUID, s"Voted ${vote}"))
  }

  def allProposalsNotReviewed(reviewerUUID: String): List[Proposal] = Redis.pool.withClient {
    implicit client =>
      val allProposalIDsForReview = client.sdiff(s"Proposals:ByState:${ProposalState.SUBMITTED.code}", s"Proposals:Reviewed:ByAuthor:${reviewerUUID}").toSet
      Proposal.loadProposalByIDs(allProposalIDsForReview, ProposalState.SUBMITTED)
  }

  def deleteVoteForProposal(proposalId: String) = Redis.pool.withClient {
    implicit client =>
      val allAuthors = client.smembers(s"Proposals:Reviewed:ByProposal:${proposalId}")
      allAuthors.foreach {
        reviewerUUID: String =>
          client.srem(s"Proposals:Reviewed:ByAuthor:${reviewerUUID}", proposalId)
      }
      client.del(s"Proposals:Reviewed:ByProposal:${proposalId}")
      client.del(s"Proposals:Votes:${proposalId}")
      client.del(s"Proposals:Dates:${proposalId}")
  }

  val ReviewerAndVote = "(\\w+)__(\\d+)".r

  // Returns the history of votes for a proposal. If a reviewer changed its vote, we will also see it.
  def allHistoryOfVotes(proposalId: String): List[Review] = Redis.pool.withClient {
    implicit client =>
    // for instance ZREVRANGEBYSCORE Proposals:Dates:BQX-255 +inf -inf WITHSCORES
    //      1) "b14651a3cd78ab4fd03d522ebef81cdac1d5755c__2" //b14651a3.. = user uuid and 2 is the vote
    //      2) "1387058296080"                               // time stamp when the person voted
    //      3) "0867c4e2182ef84e2dfcd412e33e01a9bc98dac2__8"
    //      4) "1386781873312"

      val listOfReviewsAndVotes = client.zrevrangeByScoreWithScores(s"Proposals:Dates:${proposalId}", "+inf", "-inf")
      val history: List[Review] = listOfReviewsAndVotes.flatMap {
        tuple =>
          val reviewerAndVote = tuple._1
          val date = tuple._2
          reviewerAndVote match {
            // Regexp extractor
            case ReviewerAndVote(reviewer, vote) => Option(Review(reviewer, proposalId, vote.toInt, new Instant(date.toLong).toDateTime))
            case _ => None
          }
      }
      history
  }

  def currentScore(proposalId: String): Int = Redis.pool.withClient {
    implicit client =>
      val allScores = client.zrevrangeByScoreWithScores(s"Proposals:Votes:${proposalId}", 10, 0).toList
      allScores.foldRight(0)((scoreAndReview, accumulated: Int) => accumulated + scoreAndReview._2.toInt)
  }

  def totalVoteFor(proposalId: String): Long = Redis.pool.withClient {
    implicit client =>
      client.zcount(s"Proposals:Votes:${proposalId}", 0, 10) // how many votes between 0 and 10 ?
  }

  // If we remove those who voted "0" for a talk, how many votes do we have?
  def totalVoteCastFor(proposalId: String): Long = Redis.pool.withClient {
    implicit client =>
      client.zcount(s"Proposals:Votes:${proposalId}", 1, 10)
  }

  def allVotesFor(proposalId: String): List[(String, Double)] = Redis.pool.withClient {
    implicit client =>
      client.zrevrangeByScoreWithScores(s"Proposals:Votes:${proposalId}", 10, 0).toList
  }

  type VotesPerProposal = (String, Long)

  def allProposalsAndReviews: List[VotesPerProposal] = Redis.pool.withClient {
    implicit client =>
      val totalPerProposal = client.hkeys("Proposals").toList.map {
        proposalId =>
          (proposalId, client.scard(s"Proposals:Reviewed:ByProposal:${proposalId}"))
      }
      totalPerProposal
  }

  def countAll(): Long = {
    val totalPerProposal = allProposalsAndReviews
    totalPerProposal.map(_._2).sum // total reviewed
  }

  def countWithNoVotes(): Long = {
    val totalPerProposal = allProposalsAndReviews.filter(_._2 == 0)
    totalPerProposal.size
  }

  def countWithVotes(): Long = {
    val totalPerProposal = allProposalsAndReviews.filterNot(_._2 == 0)
    totalPerProposal.size
  }

  def mostReviewed(): Option[VotesPerProposal] = {
    val maybeBestProposal = allProposalsAndReviews.sortBy(_._2).reverse.headOption
    maybeBestProposal
  }

  def bestReviewer(): (String, Long) = {
    totalReviewedByCFPuser().sortBy(_._2).reverse.head
  }

  def worstReviewer(): (String, Long) = {
    totalReviewedByCFPuser().sortBy(_._2).head
  }

  def totalReviewedByCFPuser(): List[(String, Long)] = Redis.pool.withClient {
    implicit client =>
      Webuser.allCFPAdmin().map {
        webuser: Webuser =>
          val uuid = webuser.uuid
          (uuid, client.scard(s"Proposals:Reviewed:ByAuthor:$uuid"))
      }
  }

  def lastVoteByUserForOneProposal(reviewerUUID: String, proposalId: String): Option[Review] = Redis.pool.withClient {
    implicit client =>
    // If I voted for this proposal - O(1) very fast access
      if (client.sismember(s"Proposals:Reviewed:ByAuthor:${reviewerUUID}", proposalId)) {
        // Then ok, load the vote... O(log(N)+M) with N: nb of votes and M the number returned (all...)
        // this method use Redis zrevrangeByScoreWithScores so the list is already sorted
        // from the most recent vote to the oldest vote for a proposal.
        // The first Review with author = reviewerUUID is then the most recent vote for this talk
        allHistoryOfVotes(proposalId).find(review => review.reviewer == reviewerUUID)
      } else {
        None
      }
  }

  def allVotesFromUser(reviewerUUID: String): Set[(String, Option[Double])] = Redis.pool.withClient {
    implicit client =>
      client.smembers(s"Proposals:Reviewed:ByAuthor:$reviewerUUID").flatMap {
        proposalId: String =>
          val score = Option(client.zscore(s"Proposals:Votes:${proposalId}", reviewerUUID))
          score match {
            case None => {
              // Load the state only for the "strange" proposal
              val state = Proposal.findProposalState(proposalId)
              state.flatMap {
                case ProposalState.DRAFT => None
                case ProposalState.DECLINED => None
                case ProposalState.DELETED => None
                case ProposalState.ACCEPTED => None
                case ProposalState.APPROVED => None
                case ProposalState.REJECTED => None
                case ProposalState.UNKNOWN => None
                case other => Option((proposalId, None))
              }
            }
            case Some(_) => {
              Option(proposalId, score.map(_.toDouble))
            }
          }
      }
  }




}

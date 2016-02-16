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

import library.{Stats, Redis}
import models.Review._
import org.joda.time.{Instant, DateTime}
import scala.math.BigDecimal.RoundingMode

/**
 * Represents a ReviewByGoldenTicket with a vote by a webuser with a Golden Ticket
  * 
 * Author: nicolas martignole
 * Created: 26th november 2015
 */
case class ReviewByGoldenTicket(reviewer: String, proposalId: String, vote: Int, date: DateTime)

object ReviewByGoldenTicket {

  def voteForProposal(proposalId: String, reviewerUUID: String, vote: Int) = Redis.pool.withClient {
    implicit client =>
      val secureMaxVote = Math.min(vote,10)
      val tx = client.multi()
      tx.sadd(s"ReviewGT:Reviewed:ByAuthor:$reviewerUUID", proposalId)
      tx.sadd(s"ReviewGT:Reviewed:ByProposal:$proposalId", reviewerUUID)
      tx.zadd(s"ReviewGT:Votes:$proposalId", secureMaxVote, reviewerUUID) // if the vote does already exist, Redis updates the existing vote. reviewer is a discriminator on Redis.
      tx.zadd(s"ReviewGT:Dates:$proposalId", new Instant().getMillis, reviewerUUID + "__" + secureMaxVote) // Store when this user voted for this talk
      tx.exec()
  }

  def countVotesForAllUsers():List[(String,Long)]=Redis.pool.withClient{
    implicit client=>
      val allGoldenTicketUUID:Set[String] = client.smembers("Webuser:gticket")


      val votesPerReviewers = allGoldenTicketUUID.map{
        reviewerUUID:String=>
          val totalVotes = client.scard(s"ReviewGT:Reviewed:ByAuthor:$reviewerUUID")
          (reviewerUUID,totalVotes)
      }.toList.sortBy(_._2).reverse

      votesPerReviewers
  }

  def removeVoteForProposal(proposalId: String, reviewerUUID: String) = Redis.pool.withClient {
    implicit client =>
      val tx = client.multi()
      tx.srem(s"ReviewGT:Reviewed:ByAuthor:$reviewerUUID", proposalId)
      tx.srem(s"ReviewGT:Reviewed:ByProposal:$proposalId", reviewerUUID)
      tx.zrem(s"ReviewGT:Votes:$proposalId", reviewerUUID) // if the vote does already exist, Redis updates the existing vote. reviewer is a discriminator on Redis.
      tx.zrem(s"ReviewGT:Dates:$proposalId", reviewerUUID + "__DEL")
      tx.exec()
  }


  def archiveAllVotesOnProposal(proposalId: String) = Redis.pool.withClient {
    implicit client =>
      val tx = client.multi()
      allVotesFor(proposalId).map {
        case (reviewer, _) =>
          tx.srem(s"ReviewGT:Reviewed:ByAuthor:$reviewer", proposalId)

      }
      tx.del(s"ReviewGT:Reviewed:ByProposal:$proposalId")
      tx.del(s"ReviewGT:Votes:$proposalId") // if the vote does already exist, Redis updates the existing vote. reviewer is a discriminator on Redis.
      tx.del(s"ReviewGT:Dates:$proposalId")
      
      tx.exec()
      ReviewByGoldenTicket.computeAndGenerateVotes()
  }

  def allProposalsNotReviewed(reviewerUUID: String): List[Proposal] = Redis.pool.withClient {
    implicit client =>
      val allProposalIDsForReview = client.sdiff(s"Proposals:ByState:${ProposalState.SUBMITTED.code}",
        "ApprovedById:",
        "RefusedById:",
        s"ReviewGT:Reviewed:ByAuthor:$reviewerUUID"
      )
      Proposal.loadProposalByIDs(allProposalIDsForReview, ProposalState.SUBMITTED)
  }

  def deleteVoteForProposal(proposalId: String) = Redis.pool.withClient {
    implicit client =>
      val allAuthors = client.smembers(s"ReviewGT:Reviewed:ByProposal:$proposalId")
      val tx = client.multi()
      allAuthors.foreach {
        reviewerUUID: String =>
          tx.srem(s"ReviewGT:Reviewed:ByAuthor:$reviewerUUID", proposalId)
      }
      tx.del(s"ReviewGT:Reviewed:ByProposal:$proposalId")
      tx.del(s"ReviewGT:Votes:$proposalId")
      tx.del(s"ReviewGT:Dates:$proposalId")
      tx.exec()
  }

  val ReviewerAndVote = "(\\w+)__(\\d+)".r

  def allHistoryOfVotes(proposalId: String): List[ReviewByGoldenTicket] = Redis.pool.withClient {
    implicit client =>

      val listOfReviewsAndVotes = client.zrevrangeByScoreWithScores(s"ReviewGT:Dates:$proposalId", "+inf", "-inf")
      val history: List[ReviewByGoldenTicket] = listOfReviewsAndVotes.flatMap {
        tuple =>
          val reviewerAndVote = tuple._1
          val date = tuple._2
          reviewerAndVote match {
            // Regexp extractor
            case ReviewerAndVote(reviewer, vote) => Option(ReviewByGoldenTicket(reviewer, proposalId, vote.toInt, new Instant(date.toLong).toDateTime))
            case _ => None
          }
      }
      history
  }

  def currentScore(proposalId: String): Int = Redis.pool.withClient {
    client =>
      val allScores = client.zrevrangeByScoreWithScores(s"ReviewGT:Votes:$proposalId", 10, 0).toList
      allScores.foldRight(0)((scoreAndReview, accumulated: Int) => accumulated + scoreAndReview._2.toInt)
  }

  def totalVoteFor(proposalId: String): Long = Redis.pool.withClient {
    client =>
      client.zcount(s"ReviewGT:Votes:$proposalId", 0, 10) // how many votes between 0 and 10 ?
  }

  // If we remove those who voted "0" for a talk, how many votes do we have?
  def totalVoteCastFor(proposalId: String): Long = Redis.pool.withClient {
    implicit client =>
      client.zcount(s"ReviewGT:Votes:$proposalId", 1, 10)
  }

  def averageScore(proposalId:String):Double = Redis.pool.withClient{
    client=>
      val allScores = client.zrangeByScoreWithScores(s"ReviewGT:Votes:$proposalId", 1, 10).map(_._2)
      Stats.average(allScores)
  }

  type ReviewerAndVote = (String, Double)

  def allVotesFor(proposalId: String): List[ReviewerAndVote] = Redis.pool.withClient {
    implicit client =>
      client.zrevrangeByScoreWithScores(s"ReviewGT:Votes:$proposalId", 10, 0)
  }

  type VotesPerProposal = (String, Long)

  def allProposalsAndReviews: List[VotesPerProposal] = Redis.pool.withClient {
    implicit client =>
      val onlyValidProposalIDs = Proposal.allProposalIDsNotDeleted
      val totalPerProposal = onlyValidProposalIDs.map {
        proposalId =>
          (proposalId, totalVoteCastFor(proposalId))
      }
      totalPerProposal.toList
  }

  def allProposalsWithNoVotes: Map[String, Proposal] = {
    val proposalIDs = allProposalsAndReviews.filter(_._2 == 0).map(_._1).toSet
    Proposal.loadAndParseProposals(proposalIDs)
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
  
  def totalReviewedByCFPuser(): List[(String, Int)] = Redis.pool.withClient {
    implicit client =>
      Webuser.allCFPWebusers().map {
        webuser: Webuser =>
          val uuid = webuser.uuid
          val total = client.sdiff(s"ReviewGT:Reviewed:ByAuthor:$uuid", "Proposals:ByState:" + ProposalState.DELETED.code, "Proposals:ByState:" + ProposalState.ARCHIVED.code, "Proposals:ByState:" + ProposalState.DRAFT.code, "Proposals:ByState:" + ProposalState.DECLINED.code)
          (uuid, total.size)
      }
  }

  def lastVoteByUserForOneProposal(reviewerUUID: String, proposalId: String): Option[ReviewByGoldenTicket] = Redis.pool.withClient {
    implicit client =>
      if (client.sismember(s"ReviewGT:Reviewed:ByAuthor:$reviewerUUID", proposalId)) {
        allHistoryOfVotes(proposalId).find(ReviewByGoldenTicket => ReviewByGoldenTicket.reviewer == reviewerUUID)
      } else {
        None
      }
  }

  def allVotesFromUser(reviewerUUID: String): Set[(String, Option[Double])] = Redis.pool.withClient {
    implicit client =>
      client.smembers(s"ReviewGT:Reviewed:ByAuthor:$reviewerUUID").flatMap {
        proposalId: String =>
          val score = Option(client.zscore(s"ReviewGT:Votes:$proposalId", reviewerUUID))
          score match {
            case None =>
              val state = Proposal.findProposalState(proposalId)
              state.flatMap {
                case ProposalState.DRAFT => None
                case ProposalState.DECLINED => None
                case ProposalState.DELETED => None
                case ProposalState.REJECTED => None
                case ProposalState.ARCHIVED => None
                case ProposalState.UNKNOWN => None
                case other => Option((proposalId, None))
              }
            case Some(_) =>
              val state = Proposal.findProposalState(proposalId)
              state.flatMap {
                case ProposalState.DRAFT => None
                case ProposalState.DECLINED => None
                case ProposalState.DELETED => None
                case ProposalState.REJECTED => None
                case ProposalState.ARCHIVED => None
                case ProposalState.UNKNOWN => None
                case other =>
                  Option((proposalId, score.map(_.toDouble)))
              }
          }
      }
  }


  // internal function that upload to Redis a LUA Script
  // The function returns the Script SHA1
  val loadLUAScript: String = Redis.pool.withClient {
    client =>
      val script =
        """
          |local proposals = redis.call("KEYS", "ReviewGT:Votes:*")
          |redis.call("DEL", "GT:Computed:Reviewer:Total")
          |redis.call("DEL", "GT:Computed:Reviewer:ReviewedOne")
          |redis.call("DEL", "GT:Computed:Scores")
          |redis.call("DEL", "GT:Computed:Voters")
          |redis.call("DEL", "GT:Computed:Average")
          |redis.call("DEL", "GT:Computed:Votes:ScoreAndCount")
          |redis.call("DEL", "GT:Computed:StandardDeviation")
          |redis.call("DEL", "GT:Computed:VotersAbstention")
          |redis.call("DEL", "GT:Computed:Median")
          |
          |for i = 1, #proposals do
          |  redis.log(redis.LOG_DEBUG, "----------------- " .. proposals[i])
          |
          |  redis.call("HSET", "GT:Computed:Scores", proposals[i], 0)
          |  redis.call("HSET", "GT:Computed:Voters", proposals[i], 0)
          |  redis.call("HSET", "GT:Computed:Average", proposals[i], 0)
          |  redis.call("HDEL", "GT:Computed:Votes:ScoreAndCount", proposals[i])
          |  redis.call("HDEL", "GT:Computed:VotersAbstention", proposals[i])
          |  redis.call("HDEL", "GT:Computed:StandardDeviation" , proposals[i])
          |
          |  local uuidAndScores = redis.call("ZRANGEBYSCORE", proposals[i], 1, 11, "WITHSCORES")
          |
          |  for j=1,#uuidAndScores,2 do
          |    redis.log(redis.LOG_DEBUG, "uuid:" ..  uuidAndScores[j] .. " score:" .. uuidAndScores[j + 1])
          |    redis.call("HINCRBY", "GT:Computed:Scores", proposals[i], uuidAndScores[j + 1])
          |    redis.call("HINCRBY", "GT:Computed:Voters", proposals[i], 1)
          |    redis.call("HINCRBY", "GT:Computed:Reviewer:Total", uuidAndScores[j], uuidAndScores[j + 1])
          |    redis.call("SADD", "GT:Computed:Reviewer:ReviewedOne",  uuidAndScores[j])
          |  end
          |
          |redis.call("HDEL", "GT:Computed:Median", proposals[i])
          |
          | local count = redis.call("HGET", "GT:Computed:Voters", proposals[i])
          | local total = redis.call("HGET", "GT:Computed:Scores", proposals[i])
          | local avg = 0
          |   if (count and total) then
          |        avg = tonumber(total)/tonumber(count)
          |        redis.call("HSET", "GT:Computed:Average", proposals[i], avg)
          |   end
          |
          | redis.log(redis.LOG_DEBUG, "Average: " .. avg)
          |
          |  local vm = 0
          |  local sum2 = 0
          |  local count2 = 0
          |  local standardDev
          |
          |  for z=1,#uuidAndScores,2 do
          |      vm = uuidAndScores[z + 1] - avg
          |      sum2 = sum2 + (vm * vm)
          |      count2 = count2 + 1
          |  end
          |
          | redis.log(redis.LOG_DEBUG, "Standard Deviation sum2: " .. sum2)
          | redis.log(redis.LOG_DEBUG, "Standard Deviation count2: " .. count2)
          | if  sum2 < 1  then
          |  standardDev = 0
          | else
          |  if(count2>1) then
          |     standardDev = math.sqrt(sum2 / (count2-1))
          |  else
          |    standardDev = 0
          |  end
          | end
          |
          |  redis.log(redis.LOG_DEBUG, "Standard Deviation: " .. standardDev)
          |  redis.call("HSET", "GT:Computed:StandardDeviation" , proposals[i], standardDev)
          |
          | local countAbstention = redis.call("ZCOUNT", proposals[i], 0, 0)
          | if(countAbstention>0) then
          |    redis.call("HSET", "GT:Computed:VotersAbstention" , proposals[i], countAbstention)
          | end
          |end
          |return #proposals
        """.stripMargin

      val sha1script = client.scriptLoad(script)
      play.Logger.of("models.ReviewByGoldenTicket").info("Uploaded LUA script for Golden ticket to Redis " + sha1script)
      sha1script
  }

  def computeAndGenerateVotes() = Redis.pool.withClient {
    implicit client =>
      if (client.scriptExists(loadLUAScript)) {
        client.evalsha(loadLUAScript, 0)
      } else {
        play.Logger.of("models.ReviewByGoldenTicketReview").error("There is no LUA script to compute scores and votes on Redis")
      }
  }

  def allReviewersAndStats(): List[(String, Int, Int)] = Redis.pool.withClient {
    client =>
      val allVoted = client.hgetAll("GT:Computed:Reviewer:Total").map {
        case (uuid: String, totalPoints: String) =>
          val nbrOfTalksReviewed = client.sdiff(s"ReviewGT:Reviewed:ByAuthor:$uuid",
            "Proposals:ByState:" + ProposalState.DELETED.code,
            "Proposals:ByState:" + ProposalState.ARCHIVED.code,
            "Proposals:ByState:" + ProposalState.DRAFT.code).size
          (uuid, totalPoints.toInt, nbrOfTalksReviewed)
      }

      val noReviews = client.sdiff("Webuser:cfp", "GT:Computed:Reviewer:ReviewedOne")
      val noReviewsAndNote = noReviews.map(uuid =>
        (uuid, 0, 0)
      )
      allVoted.toList ++ noReviewsAndNote.toList


  }

  def diffReviewBetween(firstUUID: String, secondUUID: String): Set[String] = Redis.pool.withClient {
    client =>
      client.sdiff(s"ReviewGT:Reviewed:ByAuthor:$firstUUID",
        s"ReviewGT:Reviewed:ByAuthor:$secondUUID",
        "Proposals:ByState:" + ProposalState.DELETED.code,
        "Proposals:ByState:" + ProposalState.ARCHIVED.code,
        "Proposals:ByState:" + ProposalState.DRAFT.code)
  }


  def allVotes(): Set[(String, (models.Review.Score, models.Review.TotalVoter, models.Review.TotalAbst, models.Review.AverageNote, models.Review.StandardDev))] = Redis.pool.withClient {
    client =>
      val allVoters = client.hgetAll("GT:Computed:Voters")
      val allAbstentions = client.hgetAll("GT:Computed:VotersAbstention")
      val allAverages = client.hgetAll("GT:Computed:Average")
      val allStandardDev = client.hgetAll("GT:Computed:StandardDeviation")

      client.hgetAll("GT:Computed:Scores").map {
        case (proposalKey: String, scores: String) =>
          val proposalId = proposalKey.substring(proposalKey.lastIndexOf(":") + 1)
          (proposalId,
            ( new Score(scores.toDouble),
              new TotalVoter(allVoters.get(proposalKey).map(_.toInt).getOrElse(0)),
              new TotalAbst(allAbstentions.get(proposalKey).map(_.toInt).getOrElse(0)),
              new AverageNote(allAverages.get(proposalKey).filterNot(_ == "nan").filterNot(_ == "-nan").map(d => BigDecimal(d.toDouble).setScale(3, RoundingMode.HALF_EVEN).toDouble).getOrElse(0.toDouble)),
              allStandardDev.get(proposalKey).filterNot(_ == "nan").filterNot(_ == "-nan").map {
                d =>
                 new StandardDev(BigDecimal(d.toDouble).setScale(3, RoundingMode.HALF_EVEN).toDouble)
              }.getOrElse(new StandardDev(0.toDouble))
              )
            )
      }.toSet
  }


  def orderByAverageScore:Ordering[(Proposal, (models.Review.Score, models.Review.TotalVoter, models.Review.TotalAbst, models.Review.AverageNote, models.Review.StandardDev))]={
    Ordering.by[(Proposal, (models.Review.Score, models.Review.TotalVoter, models.Review.TotalAbst, models.Review.AverageNote, models.Review.StandardDev)), Double](_._2._4.n)
  }
}

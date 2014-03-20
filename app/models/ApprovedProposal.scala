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
 * Approve or reject a proposal
 * Created by nicolas on 29/01/2014.
 */
object ApprovedProposal {

  val totalConf = 70
  // 30 sans apres-midi decideur + 39 vendredi
  val totalUni = 8
  val totalLabs = 12
  val totalTia = 30
  val totalQuickies = 22
  val totalBOF = 16

  // What we did in 2013
  val getDevoxx2013Total: Map[String, Int] = {
    Map(
      (ProposalType.CONF.label, totalConf)
      , (ProposalType.UNI.label, totalUni)
      , (ProposalType.TIA.label, totalTia)
      , (ProposalType.LAB.label, totalLabs)
      , (ProposalType.QUICK.label, totalQuickies)
      , (ProposalType.BOF.label, totalBOF)
    )
  }

  def countApproved(talkType: String): Long = Redis.pool.withClient {
    client =>
      talkType match {
        case "all" =>
          client.scard("Approved:conf") + client.scard("Approved:lab") + client.scard("Approved:bof") + client.scard("Approved:tia") + client.scard("Approved:uni") + client.scard("Approved:quick")
        case other =>
          client.scard(s"Approved:$talkType")
      }
  }

  def isApproved(proposal: Proposal): Boolean = {
    isApproved(proposal.id, proposal.talkType.id)
  }

  def isApproved(proposalId: String, talkType: String): Boolean = Redis.pool.withClient {
    client =>
      client.sismember("Approved:" + talkType, proposalId)
  }


  def isRefused(proposal: Proposal): Boolean = {
    isRefused(proposal.id, proposal.talkType.id)
  }

  def isRefused(proposalId: String, talkType: String): Boolean = Redis.pool.withClient {
    client =>
      client.sismember("Refused:" + talkType, proposalId)
  }

  def remainingSlots(talkType: String): Long = {
    talkType match {
      case ProposalType.UNI.id =>
        totalUni - countApproved(talkType)
      case ProposalType.CONF.id =>
        totalConf - countApproved(talkType)
      case ProposalType.TIA.id =>
        totalTia - countApproved(talkType)
      case ProposalType.LAB.id =>
        totalLabs - countApproved(talkType)
      case ProposalType.BOF.id =>
        totalBOF - countApproved(talkType)
      case ProposalType.QUICK.id =>
        totalQuickies - countApproved(talkType)
      case other => (totalUni + totalBOF + totalConf + totalLabs + totalTia + totalQuickies) - countApproved("all")
    }
  }

  def approve(proposal: Proposal) = Redis.pool.withClient {
    implicit client =>
      val tx = client.multi()
      tx.sadd("ApprovedById:", proposal.id.toString)
      tx.sadd("Approved:" + proposal.talkType.id, proposal.id.toString)
      tx.sadd("ApprovedSpeakers:" + proposal.mainSpeaker, proposal.id.toString)
      proposal.secondarySpeaker.map(secondarySpeaker => tx.sadd("ApprovedSpeakers:" + secondarySpeaker, proposal.id.toString))
      proposal.otherSpeakers.foreach {
        otherSpeaker: String =>
          tx.sadd("ApprovedSpeakers:" + otherSpeaker, proposal.id.toString)
      }
      tx.exec()
  }

  def refuse(proposal: Proposal) = Redis.pool.withClient {
    implicit client =>
      cancelApprove(proposal)
      val tx = client.multi()
      tx.sadd("RefusedById:", proposal.id.toString)
      tx.sadd("Refused:" + proposal.talkType.id, proposal.id.toString)

      tx.sadd("RefusedSpeakers:" + proposal.mainSpeaker, proposal.id.toString)
      proposal.secondarySpeaker.map(secondarySpeaker => tx.sadd("RefusedSpeakers:" + secondarySpeaker, proposal.id.toString))
      proposal.otherSpeakers.foreach {
        otherSpeaker: String =>
          tx.sadd("RefusedSpeakers:" + otherSpeaker, proposal.id.toString)
      }
      tx.exec()
  }

  def cancelApprove(proposal: Proposal) = Redis.pool.withClient {
    implicit client =>
      val tx = client.multi()
      tx.srem("ApprovedById:", proposal.id.toString)
      tx.srem("Approved:" + proposal.talkType.id, proposal.id.toString)
      tx.srem("ApprovedSpeakers:" + proposal.mainSpeaker, proposal.id.toString)

      proposal.secondarySpeaker.map {
        secondarySpeaker: String =>
          tx.srem("ApprovedSpeakers:" + secondarySpeaker, proposal.id.toString)
      }

      proposal.otherSpeakers.foreach {
        otherSpeaker: String =>
          tx.srem("ApprovedSpeakers:" + otherSpeaker, proposal.id.toString)
      }
      tx.exec()
  }

  def cancelRefuse(proposal: Proposal) = Redis.pool.withClient {
    implicit client =>
      val tx = client.multi()
      tx.srem("RefusedById:", proposal.id.toString)
      tx.srem("Refused:" + proposal.talkType.id, proposal.id.toString)
      tx.srem("RefusedSpeakers:" + proposal.mainSpeaker, proposal.id.toString)

      proposal.secondarySpeaker.map {
        secondarySpeaker: String =>
          tx.srem("RefusedSpeakers:" + secondarySpeaker, proposal.id.toString)
      }

      proposal.otherSpeakers.foreach {
        otherSpeaker: String =>
          tx.srem("RefusedSpeakers:" + otherSpeaker, proposal.id.toString)
      }
      tx.exec()
  }

    def allRefusedSpeakerIDs(): Set[String] = Redis.pool.withClient {
    implicit client =>
      client.keys("RefusedSpeakers:*").map {
        key =>
          val speakerUUID = key.substring("RefusedSpeakers:".length)
          speakerUUID
      }
  }

  def onlySubmittedRefused(): Iterable[Proposal] = Redis.pool.withClient {
    implicit client =>
      val proposalIDs = client.sinter(s"Proposals:ByState:${ProposalState.SUBMITTED.code}", "RefusedById:")
      Proposal.loadAndParseProposals(proposalIDs).values
  }

  def onlySubmittedNotRefused(): Iterable[Proposal] = Redis.pool.withClient {
    implicit client =>
      val proposalIDs = client.sdiff(s"Proposals:ByState:${ProposalState.SUBMITTED.code}", "RefusedById:", "ApprovedById:")
      Proposal.loadAndParseProposals(proposalIDs).values
  }

  def allApprovedByTalkType(talkType: String): List[Proposal] = Redis.pool.withClient {
    implicit client =>
      val allProposalIDs = client.smembers("Approved:" + talkType)
      val allProposalWithVotes = Proposal.loadAndParseProposals(allProposalIDs.toSet)
      allProposalWithVotes.values.toList
  }

  def allRefusedByTalkType(talkType: String): List[Proposal] = Redis.pool.withClient {
    implicit client =>
      val allProposalIDs = client.smembers("Refused:" + talkType)
      val allProposalWithVotes = Proposal.loadAndParseProposals(allProposalIDs.toSet)
      allProposalWithVotes.values.toList
  }

  def allApproved(): Set[Proposal] = Redis.pool.withClient {
    implicit client =>
      val allKeys = client.keys("Approved:*")
      val finalList = allKeys.map {
        key =>
          val allProposalIDs = client.smembers(key).toList
          val allProposalWithVotes = Proposal.loadAndParseProposals(allProposalIDs.toSet)
          allProposalWithVotes.values.toList
      }.flatten
      finalList
  }

  def allApprovedProposalIDs() = Redis.pool.withClient {
    implicit client =>
      client.smembers("ApprovedById:")
  }

  def allRefusedProposalIDs()= Redis.pool.withClient {
    implicit client =>
      client.smembers("RefusedById:")
  }

  def allApprovedSpeakers() = Redis.pool.withClient {
    implicit client =>
      client.keys("ApprovedSpeakers:*").flatMap {
        key =>
          val speakerUUID = key.substring("ApprovedSpeakers:".length)
          for (speaker <- Speaker.findByUUID(speakerUUID)) yield speaker
      }
  }

  def allApprovedSpeakerIDs(): Set[String] = Redis.pool.withClient {
    implicit client =>
      client.keys("ApprovedSpeakers:*").map {
        key =>
          val speakerUUID = key.substring("ApprovedSpeakers:".length)
          speakerUUID
      }
  }

  def allAcceptedTalksForSpeaker(speakerId: String): Iterable[Proposal] = Redis.pool.withClient {
    implicit client =>
      Proposal.loadAndParseProposals(client.smembers("ApprovedSpeakers:" + speakerId)).values.filter(_.state == ProposalState.ACCEPTED)
  }

  def allAcceptedByTalkType(talkType: String): List[Proposal] = Redis.pool.withClient {
    implicit client =>
      val allProposalIDs = client.smembers("Approved:" + talkType)
      val allProposalWithVotes = Proposal.loadAndParseProposals(allProposalIDs.toSet)
      allProposalWithVotes.values.filter(_.state == ProposalState.ACCEPTED).toList
  }

  def allApprovedSpeakersWithFreePass():Set[Speaker] = Redis.pool.withClient {
    implicit client =>
      val allSpeakers = client.keys("ApprovedSpeakers:*").flatMap {
        key =>
          val speakerUUID = key.substring("ApprovedSpeakers:".length)
          for (speaker <- Speaker.findByUUID(speakerUUID)) yield {
            (speaker,
              Proposal.loadAndParseProposals(client.smembers(key)).values.filter(p=>Proposal.givesSpeakerFreeEntrance(p.talkType))
            )
          }
      }
      val setOfSpeakers = allSpeakers.filterNot(_._2.isEmpty).map(_._1)
      setOfSpeakers
  }
}

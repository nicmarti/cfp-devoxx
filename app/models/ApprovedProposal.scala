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

import library.{Dress, Redis}

/**
 * Approve or reject a proposal
 * Created by nicolas on 29/01/2014.
 */
object ApprovedProposal {

  val getTotal: Map[String, Int] = Map(
    ("uni.label", 10)
    , ("lab.label", 14 + 6 + 6)
    , ("conf.label", 48 + 40 + 1)
    , ("tia.label", 35)
    , ("quick.label", 18 + 16)
    , ("bof.label", 27)
    , ("start.label", 1)
    , ("key.label", 4 + 3)
    , ("cstory.label", 1)
    , ("hack.label", 8)
    , ("other.label", 5)
  )

  /*
 10	University
26	Lab
35	Tools in act.

27	BOF

7	Keynote

88	Conférence

34	Quicky
   */

  def countApproved(talkType: String): Long = Redis.pool.withClient {
    client =>
      talkType match {
        case null => 0
        case "all" =>
          client.scard("Approved:conf") + client.scard("Approved:lab") + client.scard("Approved:bof") +client.scard("Approved:key")+ client.scard("Approved:tia") + client.scard("Approved:uni") + client.scard("Approved:quick")
        case other =>
          client.scard(s"Approved:$talkType")
      }
  }

  def countRefused(talkType: String): Long = Redis.pool.withClient {
    client =>
      talkType match {
        case null => 0
        case "all" =>
          client.scard("Refused:conf") + client.scard("Refused:lab") + client.scard("Refused:bof") + client.scard("Refused:tia") + client.scard("Refused:uni") + client.scard("Refused:quick")
        case other =>
          client.scard(s"Refused:$talkType")
      }
  }

  def reflectProposalChanges(proposal: Proposal) = Redis.pool.withClient {
    implicit client =>
      changeTalkType(proposal.id, proposal.talkType.id)
      recomputeAcceptedSpeakers()
  }

  def recomputeAcceptedSpeakers() = Redis.pool.withClient {
    implicit client =>
      val allSpeakerIDs=client.keys("ApprovedSpeakers:*")

      val tx = client.multi()
      allSpeakerIDs.foreach {
        speakerId=>
        tx.del(s"$speakerId")
      }
      allApproved().map {
        proposal =>
          tx.sadd("ApprovedSpeakers:" + proposal.mainSpeaker, proposal.id.toString)
          proposal.secondarySpeaker.map(secondarySpeaker => tx.sadd("ApprovedSpeakers:" + secondarySpeaker, proposal.id.toString))
          proposal.otherSpeakers.foreach {
            otherSpeaker: String =>
              tx.sadd("ApprovedSpeakers:" + otherSpeaker, proposal.id.toString)
          }
      }
      tx.exec()

  }

  // Update Approved or Refused total by conference type
  def changeTalkType(proposalId: String, newTalkType: String) = Redis.pool.withClient {
    client =>
      ConferenceDescriptor.ConferenceProposalTypes.ALL.foreach {
        proposalType =>
          if (client.sismember(s"Approved:${proposalType.id}", proposalId)) {
            val tx = client.multi()
            tx.srem(s"Approved:${proposalType.id}", proposalId)
            tx.sadd(s"Approved:$newTalkType", proposalId)
            tx.exec()
          }
          if (client.sismember(s"Refused:${proposalType.id}", proposalId)) {
            val tx = client.multi()
            tx.srem(s"Refused:${proposalType.id}", proposalId)
            tx.sadd(s"Refused:$newTalkType", proposalId)
            tx.exec()
          }
      }
  }

  def isApproved(proposal: Proposal): Boolean = {
    isApproved(proposal.id, proposal.talkType.id)
  }

  def isApproved(proposalId: String, talkType: String): Boolean = Redis.pool.withClient {
    client =>
      client.sismember(s"Approved:$talkType", proposalId)
  }

  // This is only for Attic controller, to fix an old bug on data (bug #159)
  // The bug was that a conference is approved, but then the speaker changes the
  // format to quickie, then the Approved:conf collection is not updated correctly
  def _loadApprovedCategoriesForTalk(proposal:Proposal):List[String]={
    ConferenceDescriptor.ConferenceProposalConfigurations.ALL.filter { pc =>
      isApproved(proposal.id, pc.id)
    }.map(_.id)
  }

  def isRefused(proposal: Proposal): Boolean = {
    isRefused(proposal.id, proposal.talkType.id)
  }

  def isRefused(proposalId: String, talkType: String): Boolean = Redis.pool.withClient {
    client =>
      client.sismember(s"Refused:$talkType", proposalId)
  }

  def remainingSlots(talkType: String): Long = {
    var propType = ProposalConfiguration.parse(talkType)
    if (propType == ProposalConfiguration.UNKNOWN) {
      ProposalConfiguration.totalSlotsCount - countApproved("all")
    } else {
      propType.slotsCount - countApproved(talkType)
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
      // Buggy without a 'S'
      tx.srem("ApprovedSpeaker:" + proposal.mainSpeaker, proposal.id.toString)
      // Correct
      tx.srem("ApprovedSpeakers:" + proposal.mainSpeaker, proposal.id.toString)

      proposal.secondarySpeaker.map {
        secondarySpeaker: String =>
          // Buggy without a 'S'
          tx.srem("ApprovedSpeaker:" + secondarySpeaker, proposal.id.toString)
          // Correct
          tx.srem("ApprovedSpeakers:" + secondarySpeaker, proposal.id.toString)
      }

      proposal.otherSpeakers.foreach {
        otherSpeaker: String =>
          // Buggy without a 'S'
          tx.srem("ApprovedSpeaker:" + otherSpeaker, proposal.id.toString)
          // and the correct one
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
      val allProposalIDs = client.smembers("Approved:" + talkType).diff(client.smembers(s"Proposals:ByState:${ProposalState.ARCHIVED.code}"))
      val allProposalWithVotes = Proposal.loadAndParseProposals(allProposalIDs.toSet)
      allProposalWithVotes.values.toList
  }

  def allRefusedByTalkType(talkType: String): List[Proposal] = Redis.pool.withClient {
    implicit client =>
      val allProposalIDs = client.smembers("Refused:" + talkType).diff(client.smembers(s"Proposals:ByState:${ProposalState.ARCHIVED.code}"))
      val allProposalWithVotes = Proposal.loadAndParseProposals(allProposalIDs.toSet)
      allProposalWithVotes.values.toList
  }

  def allApproved(): Set[Proposal] = Redis.pool.withClient {
    implicit client =>
      val allKeys = client.keys("Approved:*")
      val finalList = allKeys.map {
        key =>
          val allProposalIDs = client.smembers(key).diff(client.smembers(s"Proposals:ByState:${ProposalState.ARCHIVED.code}")).toList
          val allProposalWithVotes = Proposal.loadAndParseProposals(allProposalIDs.toSet)
          allProposalWithVotes.values.toList
      }.flatten
      finalList
  }

  def allApprovedProposalIDs() = Redis.pool.withClient {
    implicit client =>
      client.smembers("ApprovedById:")
  }

  def allRefusedProposalIDs() = Redis.pool.withClient {
    implicit client =>
      client.smembers("RefusedById:")
  }

  def allApprovedSpeakers():Set[Speaker] = Redis.pool.withClient {
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

  def allApprovedTalksForSpeaker(speakerId: String): Iterable[Proposal] = Redis.pool.withClient {
    implicit client =>
      val allApprovedProposals = client.smembers("ApprovedSpeakers:" + speakerId)
      val mapOfProposals = Proposal.loadAndParseProposals(allApprovedProposals)
      mapOfProposals.values
  }

  def allAcceptedByTalkType(talkType: String): List[Proposal] = Redis.pool.withClient {
    implicit client =>
      val allProposalIDs = client.smembers("Approved:" + talkType)
      val allProposalWithVotes = Proposal.loadAndParseProposals(allProposalIDs.toSet)
      allProposalWithVotes.values.filter(_.state == ProposalState.ACCEPTED).toList
  }

  def allApprovedSpeakersWithFreePass(): Set[Speaker] = Redis.pool.withClient {
    implicit client =>
      val allSpeakers = client.keys("ApprovedSpeakers:*").flatMap {
        key =>
          val speakerUUID = key.substring("ApprovedSpeakers:".length)
          for (speaker <- Speaker.findByUUID(speakerUUID)) yield {
            (speaker,
              Proposal.loadAndParseProposals(client.smembers(key)).values.filter(p => ConferenceDescriptor.ConferenceProposalConfigurations.doesItGivesSpeakerFreeEntrance(p.talkType))
              )
          }
      }
      val setOfSpeakers = allSpeakers.filterNot(_._2.isEmpty).map(_._1)
      setOfSpeakers
  }
}

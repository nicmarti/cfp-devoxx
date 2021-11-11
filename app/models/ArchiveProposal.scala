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
 * An Entity to represent archived proposal
 * @author created by Nicolas Martignole, on 19/10/2014.
 */

object ArchiveProposal {
  def pruneAllDeleted(): Int = {
    Proposal.allDeleted().foldLeft(0) {
      (cpt: Int, proposal: Proposal) =>
        Proposal.destroy(proposal)
        cpt + 1
    }
  }

  def pruneAllDraft(): Int = {
    Proposal.allDrafts().foldLeft(0) {
      (cpt: Int, proposal: Proposal) =>
        Proposal.destroy(proposal)
        cpt + 1
    }
  }

  def archiveAll(proposalTypeId: String):Int = {
    play.Logger.debug(s"archiveAll for proposalType ${proposalTypeId}")
    val ids=Proposal.allProposalIDsNotArchived
    val proposals = Proposal.loadAndParseProposals(ids).values
    play.Logger.debug(s"archiveAll : loaded proposals")

    // First, check that the approval category is ok (bug #159 on old talks)
    // Rely on the current proposal talkType
    proposals.foreach(p=> ApprovedProposal.changeTalkType(p.id,p.talkType.id))
    play.Logger.debug(s"archiveAll : updated talk type")

    // Then filter and execute archive
    val onlySameType = proposals.filter(_.talkType.id == proposalTypeId)
    play.Logger.debug(s"archiveAll : starting archive...")
    onlySameType.foreach(p2 => archive(p2))
    play.Logger.debug(s"archiveAll : archive done")

    Review.archiveAllReviews()
    play.Logger.debug(s"archiveAll : reviews deleted")

    onlySameType.size
  }

  private def archive(proposal: Proposal) = {
    val proposalId = proposal.id

    play.Logger.debug(s"archiveAll : archive proposalId ${proposalId}")

    // Then
    Some(proposal).filter(ApprovedProposal.isApproved).map {
      approvedProposal: Proposal =>
        archiveApprovedProposal(approvedProposal)
        ApprovedProposal.cancelApprove(approvedProposal)
    }
    play.Logger.debug(s"archiveAll : cancel approve")

    // Some talks with an original talkType of "conf" have been updated to "hack"
    // but the Approved list of talk was not updated, so I have to add this hack
    // in order to be sure to cleanup the Approved:* collections
    ApprovedProposal._loadApprovedCategoriesForTalk(proposal).map{
      talkType:String=>
        archiveApprovedProposal(proposal)
        ApprovedProposal.cancelApprove(proposal)
    }
    play.Logger.debug(s"archiveAll : cancel hack")

    Some(proposal).filter(ApprovedProposal.isRefused).map {
      approvedProposal: Proposal =>
        ApprovedProposal.cancelRefuse(approvedProposal)
    }
    play.Logger.debug(s"archiveAll : cancel refused")

    //Delete all comments
    Comment.deleteAllComments(proposalId)
    play.Logger.debug(s"archiveAll : comments deleted")

    // Remove votes for this talk
    Review.archiveAllVotesOnProposal(proposalId)
    play.Logger.debug(s"archiveAll : all votes archived")

    Proposal.changeProposalState("system", proposalId, ProposalState.ARCHIVED)
  }

  private def archiveApprovedProposal(proposal: Proposal) = Redis.pool.withClient {
    implicit client =>
      val conferenceCode = ConferenceDescriptor.current().eventCode
      val tx = client.multi()
      tx.hset(s"Archived", proposal.id, conferenceCode)
      tx.sadd(s"ArchivedById:${conferenceCode}", proposal.id)
      tx.sadd(s"Archived:${conferenceCode}" + proposal.talkType.id, proposal.id)
      tx.sadd(s"ArchivedSpeakers:${conferenceCode}:" + proposal.mainSpeaker, proposal.id)
      proposal.secondarySpeaker.map(secondarySpeaker => tx.sadd(s"ArchivedSpeakers:${conferenceCode}:" + secondarySpeaker, proposal.id))
      proposal.otherSpeakers.foreach {
        otherSpeaker: String =>
          tx.sadd(s"ArchivedSpeakers:${conferenceCode}:" + otherSpeaker, proposal.id)
      }
      tx.hdel("PreferredDay",proposal.id)
      tx.exec()
  }

  def isArchived(proposalId: String): Boolean = Redis.pool.withClient {
    implicit client =>
      client.hexists("Archived", proposalId)
  }
}

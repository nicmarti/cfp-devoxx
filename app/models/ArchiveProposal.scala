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
 * @author created by N.Martignole, Innoteria, on 19/10/2014.
 */

object ArchiveProposal {
  def pruneAllDeleted() = {
    Proposal.allDeleted().foreach(Proposal.destroy)
  }

  def pruneAllDraft() = {
    Proposal.allDrafts().foreach(Proposal.destroy)
  }

  def doArchive(): String = {
    "ok"
  }

  def archive(proposalId: String) = {

    val maybeProposal = Proposal.findById(proposalId)

    maybeProposal.filter(ApprovedProposal.isApproved).map {
      approvedProposal: Proposal =>
        ApprovedProposal.cancelApprove(approvedProposal)
        archiveApprovedProposal(approvedProposal)
    }

    maybeProposal.filter(ApprovedProposal.isRefused).map {
      approvedProposal: Proposal =>
        ApprovedProposal.cancelApprove(approvedProposal)
        archiveApprovedProposal(approvedProposal)
    }

    // Finally
    Proposal.changeProposalState("system", proposalId, ProposalState.ARCHIVED)

  }

  def archiveApprovedProposal(proposal: Proposal) = Redis.pool.withClient {
    implicit client =>
      val conferenceCode = ConferenceDescriptor.current().eventCode
      val tx = client.multi()
      tx.hset(s"Archived", proposal.id.toString, conferenceCode)
      tx.sadd(s"ArchivedById:${conferenceCode}", proposal.id.toString)
      tx.sadd(s"Archived:${conferenceCode}" + proposal.talkType.id, proposal.id.toString)
      tx.sadd(s"ArchivedSpeakers:${conferenceCode}:" + proposal.mainSpeaker, proposal.id.toString)
      proposal.secondarySpeaker.map(secondarySpeaker => tx.sadd(s"ArchivedSpeakers:${conferenceCode}:" + secondarySpeaker, proposal.id.toString))
      proposal.otherSpeakers.foreach {
        otherSpeaker: String =>
          tx.sadd(s"ArchivedSpeakers:${conferenceCode}:" + otherSpeaker, proposal.id.toString)
      }
      tx.exec()
  }

  def isArchived(proposalId: String): Boolean = Redis.pool.withClient {
    implicit client =>
      client.hexists("Archived", proposalId)
  }
}

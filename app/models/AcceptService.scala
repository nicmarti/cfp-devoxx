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
 * Accepted proposal.
 * Created by nicolas on 29/01/2014.
 */
object AcceptService {

  def countAccepted(talkType:String):Long=Redis.pool.withClient{
    client=>
      client.scard("Accepted:"+talkType)
  }

  def isAccepted(proposalId:String, talkType:String):Boolean=Redis.pool.withClient{
    client=>
      client.sismember("Accepted:"+talkType, proposalId)
  }

  def remainingSlots(talkType:String):Long={
    talkType match {
      case ProposalType.UNI.id =>
        12 - countAccepted(talkType)
      case ProposalType.CONF.id =>
        68 - countAccepted(talkType)
      case ProposalType.TIA.id =>
        30 - countAccepted(talkType)
      case ProposalType.LAB.id =>
        12 - countAccepted(talkType)
      case ProposalType.BOF.id =>
        15 - countAccepted(talkType)
      case ProposalType.QUICK.id =>
        20 - countAccepted(talkType)
      case other => 0
    }
  }

  def accept(proposalId:String, talkType:String)=Redis.pool.withClient{
    implicit client=>
      client.sadd("Accepted:"+talkType, proposalId)
  }

  def cancelAccept(proposalId:String, talkType:String)=Redis.pool.withClient{
    implicit client=>
      client.srem("Accepted:"+talkType, proposalId)
  }


}

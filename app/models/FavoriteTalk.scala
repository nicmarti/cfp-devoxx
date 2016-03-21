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
 * Repository for Favorite Talk
 * @author created by N.Martignole, Innoteria, on 27/10/15.
 */

object FavoriteTalk {

  private val redis = s"FavTalk:${ConferenceDescriptor.current().eventCode}:"


  def favTalk(proposalId: String, webuserId: String) = Redis.pool.withClient {
    implicit client =>
      client.sadd(redis + ":ByProp:" + proposalId, webuserId)
      client.sadd(redis + ":ByUser:" + webuserId, proposalId)
  }

  def isFavByThisUser(proposalId: String, webuserId: String): Boolean = Redis.pool.withClient {
    implicit client =>
      client.sismember(redis + ":ByProp:" + proposalId, webuserId)
  }

  def unfavTalk(proposalId: String, webuserId: String) = Redis.pool.withClient {
    implicit client =>
      client.srem(redis + ":ByProp:" + proposalId, webuserId)
      client.srem(redis + ":ByUser:" + webuserId, proposalId)
  }

  def delFav(proposalId: String) = Redis.pool.withClient {
    implicit client =>
      val allWebusers = client.smembers(redis + ":ByProp:" + proposalId)
      client.del(redis + ":ByProp:" + proposalId)
      allWebusers.foreach { uuid =>
        client.del(redis + ":ByUser:" + uuid, proposalId)
      }
  }

  def allForUser(webuserId: String): Iterable[Proposal] = Redis.pool.withClient {
    implicit client =>
      val ids = client.smembers(redis + ":ByUser:" + webuserId)
      Proposal.loadAndParseProposals(ids).values
  }

  def countForProposal(proposalId:String):Long=Redis.pool.withClient{
    implicit client=>
      client.scard(redis+":ByProp:"+proposalId)
  }

  def all() = Redis.pool.withClient {
    implicit client =>
      val allFav: Set[String] = client.keys(redis + ":ByProp:*")

      val allProposalIDs: Set[String] = allFav.map {
        key: String =>
          key.substring((redis + ":ByProp:").length)
      }

      allProposalIDs.map{
        proposalId=>
          val proposal = Proposal.findById(proposalId)
          val total= client.scard(redis+":ByProp:"+proposalId)
          (proposal,total)
      }.filterNot(_._1.isEmpty)
      .map(t=>(t._1.get,t._2))

  }
}

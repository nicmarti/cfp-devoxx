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
 * A request to attend or to talk for a speaker, the request has been accepted.
 * Created by nicolas martignole on 30/07/2014.
 */

object Invitation {

  private val redisInvitation = "Invitations"

  def inviteSpeaker(speakerId: String, invitedBy: String) = Redis.pool.withClient {
    implicit client =>
      client.hset(redisInvitation, speakerId, invitedBy)
  }

  def isInvited(speakerId: String): Boolean = Redis.pool.withClient {
    implicit client =>
      client.hexists(redisInvitation, speakerId)
  }

  def invitedBy(speakerId: String): Option[String] = Redis.pool.withClient {
    implicit client =>
      client.hget(redisInvitation, speakerId)
  }

  def all=Redis.pool.withClient{
    implicit client=>
      client.hkeys(redisInvitation)
  }

  def removeInvitation(speakerId:String)=Redis.pool.withClient {
    implicit client =>
      client.hdel(redisInvitation, speakerId)
  }

  def deleteAll()=Redis.pool.withClient{
    implicit client=>
    client.del(redisInvitation)
  }
}

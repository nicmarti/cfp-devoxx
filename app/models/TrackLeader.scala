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
 * A track leader is the association between a user and a track.
 * Only one webuser can be assigned as the Track leader.
 * A webuser can lead more than one Track.
 * This simple use-case demonstrates how to implement a one-to-many relationship with Redis
 * Created by @nmartignole on 15/05/2014 for Devoxx BE.
 */
case class TrackLeader(webuser:Webuser, track:Track)

object TrackLeader{

  def assign(webuser:Webuser, track:Track){
    if(Webuser.hasAccessToCFP(webuser.uuid)){
      Redis.pool.withClient{
        client=>
          client.sadd(s"TrackLeader:${webuser.uuid}",track.id)
      }
    }
  }

  def unassign(webuser:Webuser, track:Track){
      Redis.pool.withClient {
        client =>
          client.srem(s"TrackLeader:${webuser.uuid}", track.id)
      }
  }

  def getTracks(webuser:Webuser):Set[Track]={
    Redis.pool.withClient{
      client=>
        client.smembers(s"TrackLeader:${webuser.uuid}").map{
          trackId:String=>
            Track.parse(trackId)
        }
    }
  }

  def deleteTrackLeader(webuserUUID:String)(implicit client:Dress.Wrap)={
    client.del(s"TrackLeader:$webuserUUID")
  }


}

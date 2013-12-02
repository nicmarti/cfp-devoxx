package models

import library.Redis
import org.apache.commons.lang3.RandomStringUtils

/**
 * TODO definition
 *
 * Author: nicolas
 * Created: 03/12/2013 00:46
 */
case class RememberMe(uuid:String, token:String)

object RememberMe{
  def findUUIDForToken(token:String):Option[String]=Redis.pool.withClient{
    implicit client=>
    client.get("Cookie:RememberMe:"+token)
  }

  def newToken(uuid:String):String=Redis.pool.withClient{
    implicit client=>
      val key=RandomStringUtils.randomAlphanumeric(16)
      val redisKey="Cookie:RememberMe:"+key
      client.set(redisKey, uuid)
      client.expire(redisKey,3600*15)
      key
  }



}

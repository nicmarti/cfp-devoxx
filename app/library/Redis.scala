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

package library

import org.apache.commons.lang3.StringUtils
import redis.clients.jedis.{JedisPoolConfig, JedisPool}
import play.api.Play.current
import org.apache.commons.pool.impl.GenericObjectPool

/**
 * Redis connection wrapper.
 * You can configure the server details in the application.conf with:
 * <ul>
 * <li>redis.host=localhost</li>
 * <li>redis.port=6379</li>
 * <li>redis.timeout=2000</li>
 * <li>redis.password=the redis password if required</li>
 * </ul>

 * @author Nicolas MARTIGNOLE
 *
 */

object Redis {
  private lazy val host = current.configuration.getString("redis.host").getOrElse("localhost")
  private lazy val port = current.configuration.getInt("redis.port").getOrElse(6379)
  private lazy val timeout = current.configuration.getInt("redis.timeout").getOrElse(30000)
  private lazy val redisPassword:String = current.configuration.getString("redis.password").map(s=>StringUtils.trimToNull(s)).orNull

  lazy val jedisPoolConfig = {
    var pool = new JedisPoolConfig
    pool.setMaxActive(256)
    pool.setMinIdle(1)
    pool.setMaxWait(10000)
    pool.setWhenExhaustedAction(GenericObjectPool.WHEN_EXHAUSTED_BLOCK)
    pool
  }

  def checkIfConnected() = {
    checkPool(pool, host, port)
  }

  def checkPool(poolCtx: Pool, _host: String, _port: Int):String = {
    poolCtx.withClient {
      implicit client =>
        client.ping()
    }
  }

  lazy val pool = {
    val pool = new Pool(new JedisPool(jedisPoolConfig, host, port, timeout, redisPassword))
    pool
  }

}

package library

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
 */

object Redis {
  private lazy val host = current.configuration.getString("redis.host").getOrElse("localhost")
  private lazy val port = current.configuration.getInt("redis.port").getOrElse(6379)
  private lazy val timeout = current.configuration.getInt("redis.timeout").getOrElse(30000)
  private lazy val redisPassword = current.configuration.getString("redis.password").orNull

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

package library

/**
 * Zedis is the Jedis wrapper for ZapTravel.
 * Inspired by the great work from @pk11 Sedis wrapper but updated to offer 
 * a better support for Scala 2.10 and Jedis commands.
 * Author: nmartignole
 * Created: 06/03/2013 12:24
 */

import redis.clients.jedis._
import scala.Predef.String
import scala.collection.immutable._
import org.apache.commons.lang3.StringUtils

trait Dress {
  implicit def delegateToJedis(d: Wrap) = d.j

  implicit def fromJedistoScala(j: Jedis) = up(j)

  class Wrap(val j: Jedis) {

    import collection.JavaConverters._

    def hmset(key: String, values: Map[String, String]) = {
      j.hmset(key, values.asJava)
    }

    def hmget(key: String, values: String*): List[String] = {
      if (values.isEmpty) {
        Nil
      } else {
        j.hmget(key, values: _*).asScala.toList
      }
    }

    def hmget(key: String, values: Set[String]): List[String] = {
      if (values.isEmpty) {
        Nil
      } else {
        j.hmget(key, values.toSeq: _*).asScala.toList.filterNot(_ == null)
      }
    }

    def hmget(key: String, values: List[String]): List[String] = {
      if (values.isEmpty) {
        Nil
      } else {
        j.hmget(key, values.toSeq: _*).asScala.toList.filterNot(_ == null)
      }
    }

    def zrevrangeByScore(key: String, max: Long, min: Long): Set[String] = {
      j.zrevrangeByScore(key, max, min).asScala.toSet
    }

    def hget(key: String, value: String): Option[String] = {
      Option(StringUtils.trimToNull(j.hget(key, value)))
    }

    def hgetAll(key: String): Map[String, String] = {
      j.hgetAll(key).asScala.toMap
    }

    def smembers(key: String): Set[String] = {
      j.smembers(key).asScala.toSet
    }

    def hkeys(key: String): Set[String] = {
      j.hkeys(key).asScala.toSet
    }

    def hvals(key: String): List[String] = {
      j.hvals(key).asScala.toList
    }

    def get(k: String): Option[String] = {
      val f = j.get(k)
      if (f == null) None else Some(f)
    }

    def lrange(key: String, start: Long, end: Long): List[String] = {
      j.lrange(key, start, end).asScala.toList
    }

    def sort(key: String, params: SortingParams): List[String] = {
      j.sort(key, params).asScala.toList
    }

    def sort(key: String): List[String] = {
      j.sort(key).asScala.toList
    }

    def sinter(setA: String, setB: String): Set[String] = {
      j.sinter(setA, setB).asScala.toSet
    }

    def srandmember(key: String): Option[String] = {
      Option(j.srandmember(key))
    }

    def zrevrangeWithScores(key: String, start: Long, end: Long): List[(String, Double)] = {
      j.zrevrangeWithScores(key, start, end).asScala.toList.map {
        tuple: Tuple =>
          (tuple.getElement, tuple.getScore)
      }
    }

  }

  def up(j: Jedis) = new Wrap(j)
}

object Dress extends Dress

class Pool(val underlying: JedisPool) {

  def withClient[T](body: Dress.Wrap => T): T = {
    val jedis: Jedis = underlying.getResource

    try {
      body(Dress.up(jedis))
    } finally {
      underlying.returnResourceObject(jedis)
    }
  }

  def withJedisClient[T](body: Jedis => T): T = {
    val jedis: Jedis = underlying.getResource
    try {
      body(jedis)
    } finally {
      underlying.returnResourceObject(jedis)
    }
  }

}

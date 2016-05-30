package library

/**
 * Zedis is the Jedis wrapper for ZapTravel.
 * Inspired by the great work from @pk11 Sedis wrapper but updated to offer 
 * a better support for Scala 2.10 and Jedis commands.
 * Author: Nicolas Martignole
 * Created: 06/03/2013 12:24
 */

import org.apache.commons.lang3.StringUtils
import play.api.Play._
import redis.clients.jedis._
import redis.clients.jedis.exceptions.JedisConnectionException

import scala.collection.immutable._

trait Dress {
  implicit def delegateToJedis(d: Wrap): Jedis = d.j

  implicit def fromJedistoScala(j: Jedis): Dress.this.type#Wrap = up(j)

  class Wrap(val j: Jedis) {

    import scala.collection.JavaConverters._

    def hmset(key: String, values: Map[String, String]) = {
      if (play.Logger.of("library.Zedis").isDebugEnabled) {
        play.Logger.of("library.Zedis").debug(s"hmSet $key $values")
      }
      j.hmset(key, values.asJava)
    }

    def hmget(key: String, values: String*): List[String] = {
      if (values.isEmpty) {
        Nil
      } else {
        if (play.Logger.of("library.Zedis").isDebugEnabled) {
          play.Logger.of("library.Zedis").debug(s"hmGet $key $values")
        }
        j.hmget(key, values: _*).asScala.toList
      }
    }

    def hmget(key: String, values: Set[String]): List[String] = {
      if (values.isEmpty) {
        Nil
      } else {
        if (play.Logger.of("library.Zedis").isDebugEnabled) {
          play.Logger.of("library.Zedis").debug(s"hmGet $key $values")
        }
        j.hmget(key, values.toSeq: _*).asScala.toList.filterNot(_ == null)
      }
    }

    def hmget(key: String, values2: scala.collection.mutable.Set[String]): List[String] = {
      if (values2.isEmpty) {
        Nil
      } else {
        if (play.Logger.of("library.Zedis").isDebugEnabled) {
          play.Logger.of("library.Zedis").debug(s"hmGet $key $values2")
        }
        j.hmget(key, values2.toSeq: _*).asScala.toList.filterNot(_ == null)
      }
    }

    def hmget(key: String, values: List[String]): List[String] = {
      if (values.isEmpty) {
        Nil
      } else {
        if (play.Logger.of("library.Zedis").isDebugEnabled) {
          play.Logger.of("library.Zedis").debug(s"hmGet $key $values")
        }
        j.hmget(key, values.toSeq: _*).asScala.toList.filterNot(_ == null)
      }
    }

    def zrevrangeByScore(key: String, max: Long, min: Long): Set[String] = {
      if (play.Logger.of("library.Zedis").isDebugEnabled) {
        play.Logger.of("library.Zedis").debug(s"zrevrangeByScore $key $max $min")
      }
      j.zrevrangeByScore(key, max, min).asScala.toSet
    }

    def hget(key: String, value: String): Option[String] = {
      if (play.Logger.of("library.Zedis").isDebugEnabled) {
        play.Logger.of("library.Zedis").debug(s"hget $key $value")
      }
      Option(StringUtils.trimToNull(j.hget(key, value)))
    }

    def hgetAll(key: String): Map[String, String] = {
      if (play.Logger.of("library.Zedis").isDebugEnabled) {
        play.Logger.of("library.Zedis").debug(s"hgetAll $key")
      }
      j.hgetAll(key).asScala.toMap
    }

    def smembers(key: String): Set[String] = {
      if (play.Logger.of("library.Zedis").isDebugEnabled) {
        play.Logger.of("library.Zedis").debug(s"smembers $key")
      }
      j.smembers(key).asScala.toSet
    }

    def sunion(key1: String, key2: String): Set[String] = {
      if (play.Logger.of("library.Zedis").isDebugEnabled) {
        play.Logger.of("library.Zedis").debug(s"sunion $key1 $key2")
      }
      j.sunion(key1, key2).asScala.toSet
    }

    def hkeys(key: String): Set[String] = {
      if (play.Logger.of("library.Zedis").isDebugEnabled) {
        play.Logger.of("library.Zedis").debug(s"hkeys $key")
      }
      j.hkeys(key).asScala.toSet
    }

    def hvals(key: String): List[String] = {
      if (play.Logger.of("library.Zedis").isDebugEnabled) {
        play.Logger.of("library.Zedis").debug(s"hvals $key")
      }
      j.hvals(key).asScala.toList
    }

    def get(key: String): Option[String] = {
      if (play.Logger.of("library.Zedis").isDebugEnabled) {
        play.Logger.of("library.Zedis").debug(s"get $key")
      }
      val f = j.get(key)
      if (f == null) None else Some(f)
    }

    def lrange(key: String, start: Long, end: Long): List[String] = {
      if (play.Logger.of("library.Zedis").isDebugEnabled) {
        play.Logger.of("library.Zedis").debug(s"lrange $key $start $end")
      }
      j.lrange(key, start, end).asScala.toList
    }

    def sort(key: String, params: SortingParams): List[String] = {
      if (play.Logger.of("library.Zedis").isDebugEnabled) {
        play.Logger.of("library.Zedis").debug(s"sort $key $params")
      }
      j.sort(key, params).asScala.toList
    }

    def sort(key: String): List[String] = {
      if (play.Logger.of("library.Zedis").isDebugEnabled) {
        play.Logger.of("library.Zedis").debug(s"sort $key")
      }
      j.sort(key).asScala.toList
    }

    def sinter(setA: String, setB: String): Set[String] = {
      if (play.Logger.of("library.Zedis").isDebugEnabled) {
        play.Logger.of("library.Zedis").debug(s"sinter $setA $setB")
      }
      j.sinter(setA, setB).asScala.toSet
    }

    def srandmember(key: String): Option[String] = {
      if (play.Logger.of("library.Zedis").isDebugEnabled) {
        play.Logger.of("library.Zedis").debug(s"srandmember $key")
      }
      Option(j.srandmember(key))
    }

    def zrevrangeWithScores(key: String, start: Long, end: Long): List[(String, Double)] = {
      if (play.Logger.of("library.Zedis").isDebugEnabled) {
        play.Logger.of("library.Zedis").debug(s"zrevrangeWithScores $key $start $end")
      }
      j.zrevrangeWithScores(key, start, end).asScala.toList.map {
        tuple: Tuple =>
          (tuple.getElement, tuple.getScore)
      }
    }

    def zrevrangeByScoreWithScores(key: String, max: Int, min: Int): List[(String, Double)] = {
      if (play.Logger.of("library.Zedis").isDebugEnabled) {
        play.Logger.of("library.Zedis").debug(s"zrevrangeByScoreWithScores $key $max $min")
      }
      zrevrangeByScoreWithScores(key, max.toString, min.toString)
    }

    def zrevrangeByScoreWithScores(key: String, max: String, min: String): List[(String, Double)] = {
      if (play.Logger.of("library.Zedis").isDebugEnabled) {
        play.Logger.of("library.Zedis").debug(s"zrevrangeByScoreWithScores $key $max $min")
      }
      j.zrevrangeByScoreWithScores(key, max, min).asScala.toList.map {
        tuple: Tuple =>
          (tuple.getElement, tuple.getScore)
      }
    }

    def zrangeByScoreWithScores(key: String, min: Double, max: Double): List[(String, Double)] = {
      if (play.Logger.of("library.Zedis").isDebugEnabled) {
        play.Logger.of("library.Zedis").debug(s"zrangeByScoreWithScores $key $min $max")
      }
      j.zrangeByScoreWithScores(key, min, max).asScala.toList.map {
        tuple: Tuple =>
          (tuple.getElement, tuple.getScore)
      }
    }

    def zrangeByScoreWithScores(key: String, min: String, max: String): List[(String, Double)] = {
      if (play.Logger.of("library.Zedis").isDebugEnabled) {
        play.Logger.of("library.Zedis").debug(s"zrangeByScoreWithScores $key $min $max")
      }
      j.zrangeByScoreWithScores(key, min, max).asScala.toList.map {
        tuple: Tuple =>
          (tuple.getElement, tuple.getScore)
      }
    }

    def zrangeByScore(key: String, min: Long, max: Long): Set[String] = {
      if (play.Logger.of("library.Zedis").isDebugEnabled) {
        play.Logger.of("library.Zedis").debug(s"zrangeByScore $key $min $max")
      }
      j.zrangeByScore(key, min, max).asScala.toSet
    }

    def zrangeByScore(key: String, min: Double, max: Double): Set[String] = {
      if (play.Logger.of("library.Zedis").isDebugEnabled) {
        play.Logger.of("library.Zedis").debug(s"zrangeByScore $key $min $max")
      }
      j.zrangeByScore(key, min, max).asScala.toSet
    }

    def sdiff(key1: String, key2: String): Set[String] = {
      if (play.Logger.of("library.Zedis").isDebugEnabled) {
        play.Logger.of("library.Zedis").debug(s"sdiff $key1 $key2")
      }
      j.sdiff(key1, key2).asScala.toSet
    }

    def sdiff(key1: String, key2: String, key3: String): Set[String] = {
      if (play.Logger.of("library.Zedis").isDebugEnabled) {
        play.Logger.of("library.Zedis").debug(s"sdiff $key1 $key2 $key3")
      }
      j.sdiff(key1, key2, key3).asScala.toSet
    }

    def sdiff(key1: String, key2: String, key3: String, key4: String): Set[String] = {
      if (play.Logger.of("library.Zedis").isDebugEnabled) {
        play.Logger.of("library.Zedis").debug(s"sdiff $key1 $key2 $key3 $key4")
      }
      j.sdiff(key1, key2, key3, key4).asScala.toSet
    }

    def sdiff(key1: String, key2: String, key3: String, key4: String, key5: String): Set[String] = {
      if (play.Logger.of("library.Zedis").isDebugEnabled) {
        play.Logger.of("library.Zedis").debug(s"sdiff $key1 $key2 $key3 $key4 $key5")
      }
      j.sdiff(key1, key2, key3, key4, key5).asScala.toSet
    }

    def sdiffstore(newKey: String, key1: String, key2: String) = {
      if (play.Logger.of("library.Zedis").isDebugEnabled) {
        play.Logger.of("library.Zedis").debug(s"sdiff $newKey $key1 $key2")
      }
      j.sdiffstore(newKey, key1, key2)
    }

    def scard(key: String): Long = {
      if (play.Logger.of("library.Zedis").isDebugEnabled) {
        play.Logger.of("library.Zedis").debug(s"scard $key")
      }
      j.scard(key).longValue()
    }

    def keys(pattern: String): Set[String] = {
      if (play.Logger.of("library.Zedis").isDebugEnabled) {
        play.Logger.of("library.Zedis").debug(s"keys $pattern")
      }
      j.keys(pattern).asScala.toSet
    }

    def hexists(key: String, field: String): Boolean = {
      if (play.Logger.of("library.Zedis").isDebugEnabled) {
        play.Logger.of("library.Zedis").debug(s"hexists $key $field")
      }
      j.hexists(key, field).booleanValue
    }

    def srem(key: String, member: String): Long = {
      if (play.Logger.of("library.Zedis").isDebugEnabled) {
        play.Logger.of("library.Zedis").debug(s"srem $key $member")
      }
      j.srem(key, Seq(member).toSeq: _*).longValue()
    }

    def srem(key: String, members: Set[String]): Long = {
      if (play.Logger.of("library.Zedis").isDebugEnabled) {
        play.Logger.of("library.Zedis").debug(s"srem $key $members")
      }
      j.srem(key, members.toSeq: _*).longValue()
    }

    def zrevrangeByScore(key: String, max: String, min: String): Set[String] = {
      if (play.Logger.of("library.Zedis").isDebugEnabled) {
        play.Logger.of("library.Zedis").debug(s"zrevrangeByScore $key $max $min")
      }
      j.zrevrangeByScore(key, max, min).asScala.toSet
    }

  }

  def up(j: Jedis) = new Wrap(j)
}

object Dress extends Dress

class Pool(val underlying: JedisPool) {

  private val activeRedisDatabase: Option[Int] = current.configuration.getInt("redis.activeDatabase")

  def withClient[T](body: Dress.Wrap => T): T = {
    val jedis: Jedis = underlying.getResource
    if (play.Logger.of("library.Zedis.client").isDebugEnabled) {
      play.Logger.of("library.Zedis.client").debug("withClient " + jedis.hashCode())
      play.Logger.of("library.Zedis.client").debug("redis.activeDatabase " + activeRedisDatabase)
    }

    try {
      // Select the active Redis database if it was specified in the configuration
      // Note : you should not change the activeDatabase, unless you do testing
      activeRedisDatabase.map(jedis.select(_))

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

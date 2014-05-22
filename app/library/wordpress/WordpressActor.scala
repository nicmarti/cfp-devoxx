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

package library.wordpress

import play.libs.Akka
import akka.actor.{Actor, Props}
import models.{ScheduleConfiguration, Slot}

/**
 * An Akka actor for all Wordpress related tasks.
 *
 * Created by nicolas martignole on 21/05/2014.
 */
case class PublishToWordpress(id: String, confType: String)

case class PublishOneTalk(slot: Slot)

// Defines an actor (no failover strategy here)
object WordpressActor {
  val actor = Akka.system.actorOf(Props[WordpressActor])
}

class WordpressActor extends Actor {
  def receive = {
    case PublishToWordpress(id: String, confType: String) => doPublishScheduledConfiguration(id, confType)
    case PublishOneTalk(slot: Slot) => doPublishOneTalk(slot)
    case other => play.Logger.of("application.WordpressActor").error("Received an invalid actor message: " + other)

  }

  def doPublishScheduledConfiguration(id: String, confType: String) {
    play.Logger.info(s"Publishing $confType $id to Wordpress")
    ScheduleConfiguration.loadScheduledConfiguration(id).map {
      configuration =>
        for (slot <- configuration.slots if slot.proposal.isDefined) {
          WordpressActor.actor ! PublishOneTalk(slot)
        }
    }
  }

  def doPublishOneTalk(slot: Slot) {
    play.Logger.info(s"Publishing slot ${slot.id}")
    WordpressPublisher.doPublish(slot)
  }
}

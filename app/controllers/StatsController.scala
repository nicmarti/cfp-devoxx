/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 Fabien Vauchelles
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
package controllers

import library.Stopwords
import models.{ProposalType, Proposal}
import play.api.mvc._

/**
 * Stats
 * @author Fabien Vauchelles @fabienv
 */
object StatsController extends Controller {
    def index = Action {
        implicit request =>
            val stats = StatsBuilder.build

            Ok(views.html.Stats.index(stats))
    }
}


class Stats(
               var proposalsCount: Int,
               var speakersCount: Int,
               var avgProposalsBySpeakers: Double,
               var avgSizeOfTitles: Double,
               var avgWordOfTitles: Double,
               var avgSizeOfSummaries: Double,
               var avgWordOfSummaries: Double,
               var countProposalsByLang: Map[String, Int],
               var countProposalsByTalkType: List[(String, Int)],
               var countProposalsByAudienceLevel: List[(String, Int)],
               var countProposalsByTrack: List[(String, Int)],
               var countProposalsByDemoLevel: List[(String, Int)],
               var countProposalsByTalkTypeAndDemoLevel: List[(String, List[(String, Int)])],
               var top10proposalsCombinations: List[(List[(String, Int)], Int)],
               var topWords: List[(String, Int)]
               )


object StatsBuilder {
    def average[T](ts: Iterable[T])(implicit num: Numeric[T]) = num.toDouble(ts.sum) / ts.size


    def countTalkType(list: List[(String, ProposalType)]): List[(String, Int)] = list
        .groupBy(_._2)
        .mapValues(_.size)
        .map(p => (p._1.id, p._2))
        .toList
        .sortBy(p => (p._2, p._1))
        .reverse


    def build = {
        val proposals = Proposal.allProposalNotDeleted


        // Count speakers who have posted
        val countSpeakers = proposals
            .map(_.mainSpeaker)
            .distinct
            .size


        // Average proposals by speakers
        val countProposalsBySpeakers = proposals
            .map(_.allSpeakerUUIDs)
            .flatten
            .groupBy(p => p)
            .mapValues(_.size)
            .map(_._2)

        val avgProposalsBySpeakers = average(countProposalsBySpeakers)


        // Titles
        val sizeOfTitles = proposals
            .map(p => p.title.length.toDouble)

        val avgSizeOfTitles = average(sizeOfTitles)

        val wordsOfTitles = proposals
            .map(p => p.title.split(' ').size)

        val avgWordOfTitles = average(wordsOfTitles)


        // Summary
        val sizeOfSummaries = proposals
            .map(p => p.summary.length.toDouble)

        val avgSizeOfSummaries = average(sizeOfSummaries)

        val wordsOfSummaries = proposals
            .map(p => p.summary.split(' ').size)

        val avgWordOfSummaries = average(wordsOfSummaries)


        // Count proposals by language
        val countProposalsByLang = proposals
            .groupBy(_.lang)
            .mapValues(_.size)


        // Count proposals by talktype
        val countProposalsByTalkType = proposals
            .groupBy(_.talkType.id)
            .mapValues(_.size)
            .toList
            .sortBy(_._1)


        // Count proposals by audienceLevel
        val countProposalsByAudienceLevel = proposals
            .groupBy(p => p.audienceLevel + ".label")
            .mapValues(_.size)
            .toList
            .sortBy(_._1)


        // Count proposals by track
        val countProposalsByTrack = proposals
            .groupBy(_.track.label)
            .mapValues(_.size)
            .toList
            .sortBy(_._1)


        // Count proposals by demoLevel
        val countProposalsByDemoLevel = proposals
            .groupBy(p => if (p.demoLevel.isDefined) p.demoLevel.get + ".label" else "ndef.label")
            .mapValues(_.size)
            .toList
            .sortBy(_._1)


        // Count proposals by talktype & demoLevel
        val countProposalsByTalkTypeAndDemoLevel = proposals
            .groupBy(p => if (p.demoLevel.isDefined) p.demoLevel.get + ".label" else "ndef.label")
            .mapValues(proposalsByDemoLevel => proposalsByDemoLevel
                .groupBy(_.talkType.id)
                .mapValues(_.size)
                .toList
                .sortBy(_._1)
            )
            .toList
            .sortBy(_._1)


        // Top 10 proposal combinations
        val top10proposalsCombinations = proposals
            .map(p => p.allSpeakerUUIDs.map(id => (id, p.talkType)))
            .flatten
            .groupBy(_._1)
            .mapValues(countTalkType)
            .groupBy(_._2)
            .mapValues(_.size)
            .toList
            .sortBy(_._2)
            .reverse
            .take(10)


        // Top words
        val topWords = proposals
            .map(p => Stopwords.clean((p.summary).split(' ').toList))
            .flatten
            .groupBy(p => p)
            .mapValues(_.size)
            .toList
            .sortBy(_._2)
            .reverse
            .take(50)


        new Stats(
            proposals.size,
            countSpeakers,
            avgProposalsBySpeakers,
            avgSizeOfTitles,
            avgWordOfTitles,
            avgSizeOfSummaries,
            avgWordOfSummaries,
            countProposalsByLang,
            countProposalsByTalkType,
            countProposalsByAudienceLevel,
            countProposalsByTrack,
            countProposalsByDemoLevel,
            countProposalsByTalkTypeAndDemoLevel,
            top10proposalsCombinations,
            topWords
        )
    }
}

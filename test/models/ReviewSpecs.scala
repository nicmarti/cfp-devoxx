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
import org.specs2.control.Debug
import play.api.test.{FakeApplication, WithApplication, PlaySpecification}

/**
 * Review test for LUA.
 * Created by nicolas martignole on 10/07/2014.
 */
class ReviewSpecs extends PlaySpecification with Debug {

  // Use a different Redis Database than the PROD one
  val testRedis = Map("redis.host" -> "localhost", "redis.port" -> "6364", "redis.activeDatabase"->1)

  // To avoid Play Cache Exception during tests, check this
  // https://groups.google.com/forum/#!topic/play-framework/PBIfeiwl5rU
  val appWithTestRedis = () => FakeApplication(additionalConfiguration = testRedis)


   "Review" should {
     "return one review when a user votes" in new WithApplication(app = appWithTestRedis()) {

       // WARN : flush the DB
       Redis.pool.withClient{
         client=>
           client.flushDB()
       }

       // WHEN
       val proposalId="TEST"
       val reviewerUUID="TEST_UUID"
       val vote=5

       Review.voteForProposal(proposalId, reviewerUUID, vote)

       // THEN
       Review.allHistoryOfVotes(proposalId) mustNotEqual Nil
       Review.allProposalsWithNoVotes must be_==(Map.empty[String,Proposal])

     }

     "should have no more Proposal with no votes" in new WithApplication(app = appWithTestRedis()) {
       // WARN : flush the DB
       Redis.pool.withClient{
         client=>
           client.flushDB()
       }

       // WHEN
       val proposalId="TEST"
       val reviewerUUID="TEST_UUID"
       val vote=5

       Review.voteForProposal(proposalId, reviewerUUID, vote)

       // THEN
       Review.allProposalsWithNoVotes must be_==(Map.empty[String,Proposal])
     }

     "should return one submitted Proposal with no votes" in new WithApplication(app = appWithTestRedis()) {
       // WARN : flush the DB
       Redis.pool.withClient{
         client=>
           client.flushDB()
       }

       // GIVEN
      val proposal= Proposal(id="TEST",event="Test",lang="FR",title="Demo unit test"
                    , mainSpeaker="123"
                    , secondarySpeaker=None
                    , otherSpeakers=Nil
                    , talkType = ProposalType.UNKNOWN
                    , audienceLevel="test"
                    , summary="Created from test"
                    , privateMessage="Private message"
                    , state= ProposalState.SUBMITTED
                    ,sponsorTalk=false
                    ,track=Track.UNKNOWN
                    ,demoLevel="novice"
                    ,userGroup=false
                    ,wishlisted=None)


       // WHEN
       Proposal.save("123", proposal, ProposalState.SUBMITTED)


       // THEN
       Review.allProposalsWithNoVotes must haveKey("TEST")
     }
   }
}
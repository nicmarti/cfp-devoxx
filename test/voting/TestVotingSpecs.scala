package voting

import library.Redis
import models._
import org.apache.commons.lang3.RandomStringUtils
import org.junit.runner._
import org.specs2.matcher.MatchResult
import org.specs2.runner._
import play.api.libs.json._
import play.api.test._

/**
  * Tests for the Mobile V1 REST API, backward compatible with the first project created by John Mort.
  *
  * @see https://bitbucket.org/jonmort/devoxx-vote-api/src
  * @author Nicolas Martignole
  */
@RunWith(classOf[JUnitRunner])
class TestVotingSpecs extends PlaySpecification {
  // Use a different Redis Database than the PROD one
  val testRedis = Map("redis.host" -> "localhost",
    "redis.port" -> "6363",
    "redis.activeDatabase" -> 1,
    "actor.cronUpdater.active" -> false
  )



  val appWithTestRedis = () => FakeApplication(additionalConfiguration = testRedis)

  "MobileVotingV1" should {

    "returns a 404 if vote to an non-existent talk" in new WithApplication(app = appWithTestRedis()) {
      // GIVEN
      emptyRedis()

      val validVote = Json.obj(
        "talkId" -> "I-DONT-EXIST",
        "rating" -> 5,
        "user" -> 123456 // TODO le userID qui doit forcément etre un Int... c'est un peu dommage
      )

      // WHEN
      val response = route(
        FakeRequest(POST,
          "/api/voting/v1/vote"
        ).withJsonBody(validVote)
      ).get

      // THEN
      status(response) must be equalTo 404
    }

    "returns a 204 when there is no vote for a talk" in new WithApplication(app = appWithTestRedis()) {
      // GIVEN
      emptyRedis()

      val proposalId=createProposal()

      // WHEN
      val response = route(
        FakeRequest(GET,
          s"/api/voting/v1/talk/${proposalId}"
        )
      ).get

      status(response) must be equalTo 204
    }

    "accepts a valid vote" in new WithApplication(app = appWithTestRedis()) {
      // GIVEN
      emptyRedis()

      val proposalId=createProposal()

      val validVote = Json.obj(
        "talkId" -> s"${proposalId}",
        "rating" -> 5,
        "user" -> 123456 // TODO le userID qui doit forcément etre un Int... c'est un peu dommage
      )
      // WHEN
      val response = route(
        FakeRequest(POST,
          "/api/voting/v1/vote"
        ).withJsonBody(validVote)
      ).get

      // 201 Created
      status(response) must be equalTo 201
      contentType(response) must beSome.which(_ == "application/json")
      contentAsJson(response) must be equals validVote
    }

    "returns a bad request if the rating is greater than 5" in new WithApplication(app = appWithTestRedis()) {
      // GIVEN
      emptyRedis()
      val testProposalId=createProposal()

      val validVote = Json.obj(
        "talkId" -> s"${testProposalId}",
        "rating" -> 15,
        "user" -> 123456 // TODO le userID qui doit forcément etre un Int... c'est un peu dommage
      )
      // WHEN
      val response = route(
        FakeRequest(POST,
          "/api/voting/v1/vote"
        ).withJsonBody(validVote)
      ).get

      status(response) must be equalTo 400
    }


    "returns a bad request if the rating is lower than 1" in new WithApplication(app = appWithTestRedis()) {
      // GIVEN
      emptyRedis()
      val testProposalId=createProposal()

      val validVote = Json.obj(
        "talkId" -> s"${testProposalId}",
        "rating" -> 0,
        "user" -> 123456 // TODO le userID qui doit forcément etre un Int... c'est un peu dommage
      )
      // WHEN
      val response = route(
        FakeRequest(POST,
          "/api/voting/v1/vote"
        ).withJsonBody(validVote)
      ).get

      status(response) must be equalTo 400
    }

    "returns a 404 when we ask for all votes for a non-existent talk" in new WithApplication(app = appWithTestRedis()) {
      // GIVEN
      emptyRedis()

      // WHEN
      val response = route(
        FakeRequest(GET,
          "/api/voting/v1/talk/ANY_ID_I_DONT_CARE"
        )
      ).get

      status(response) must be equalTo 404
    }

    "returns a 200 when there is some votes for a talk" in new WithApplication(app = appWithTestRedis()) {
      // GIVEN
       emptyRedis()
      val testProposalId=createProposal()
      createVote(testProposalId,userId = 100, 5)
      createVote(testProposalId,userId = 200, 1)

      val validVote = Json.obj(
        "talkId" -> s"$testProposalId",
        "rating" -> 5,
        "user" -> 123456 // TODO le userID qui doit forcément etre un Int... c'est un peu dommage
      )

      // WHEN
      val response = route(
        FakeRequest(GET,
          s"/api/voting/v1/talk/${testProposalId}"
        )
      ).get

      // THEN

      val toReturn = contentAsJson(response)
      println(toReturn)


      status(response) must be equalTo 200
      contentType(response) must beSome.which(_ == "application/json")
      contentAsJson(response).\("sum") must beLike { case JsString(b) => b.must_==("6") }
      contentAsJson(response).\("avg") must beLike { case JsString(b) => b.must_==("3") }
      contentAsJson(response).\("count") must beLike { case JsString(b) => b.must_==("2") }
    }

  }

 private def emptyRedis()={
      Redis.pool.withClient {
        client =>
          client.select(1)
          client.flushDB()
      }
  }

  private def createProposal():String={
      val proposalId=RandomStringUtils.randomAlphanumeric(8)
      val uuidTest = "test_user"
      val proposal = Proposal.validateNewProposal(Some(proposalId), "fr", "test proposal", None, Nil,
        ConferenceDescriptor.ConferenceProposalTypes.CONF.id, "audience level", "summary", "private message",
        sponsorTalk = false, ConferenceDescriptor.ConferenceTracks.UNKNOWN.id, Option("beginner"), userGroup = None)
      Proposal.save(uuidTest, proposal, ProposalState.ACCEPTED)
      proposalId
  }

  private def createVote(talkId:String, userId:Int=1234, rating:Int=5){
    val newRating=Rating.createNew(talkId,userId,rating)
    Rating.saveNewRating(newRating)
  }
}

package library.search

import com.sksamuel.elastic4s.ElasticApi.RichFuture
import com.sksamuel.elastic4s.ElasticDsl.{BulkHandler, IndexHandler, bulk, indexInto}
import com.sksamuel.elastic4s.requests.bulk.BulkCompatibleRequest
import library.search.ElasticSearchPublisher.logger
import models.{Proposal, Speaker}

object ESIndexer {

  def indexSpeaker(s: Speaker) = {
    val client = ESClient.open()
    client.execute(
      indexInto(ESIndexUtils.speakerIndex)
        .fields(
          "uuid" -> s.uuid,
          "bio" -> s.bio,
          "company" -> s.company.orNull,
          "firstName" -> s.cleanFirstName,
          "name" -> s.cleanLastName,
          "qualifications" -> s.qualifications.orNull
        )
        .id(s.uuid)
    ).await
    client.close()
  }
  def indexAllSpeakers(allSpeakers: List[Speaker]) {
    val indexName = ESIndexUtils.speakerIndex
    logger.debug(s"Do index all speakers ${allSpeakers.size} to index $indexName")

    val requests: Iterable[BulkCompatibleRequest] = allSpeakers.map {
      speaker =>
        indexInto(indexName)
          .fields(
            "uuid" -> speaker.uuid
            , "bio" -> speaker.bio
            , "company" -> speaker.company.orNull
            , "firstName" -> speaker.cleanFirstName
            , "name" -> speaker.cleanLastName
            , "qualifications" -> speaker.qualifications.orNull
            , "email" -> speaker.email
          )
          .id(speaker.uuid)
    }

    val client = ESClient.open()

    client.execute(
      bulk(requests)
    ).await

    client.close()
  }


  def indexProposal(p: Proposal) = {
    val client = ESClient.open()
    client.execute(
      indexInto(ESIndexUtils.proposalIndex)
        .fields(
          "id" -> p.id
          , "title" -> p.title
          , "summary" -> p.summary
          , "mainSpeaker" -> p.mainSpeaker
          , "secondarySpeaker" -> p.secondarySpeaker.orNull
          , "otherSpeakers" -> p.otherSpeakers.map(os => os).orElse(null)
          , "talkType" -> p.talkType.id
          , "state" -> p.state.code
          , "tags" -> p.tags.map(sTags => sTags.filterNot(_.id == "0")).orNull
        )
        .id(p.id)
    ).await
    client.close()
  }


  def indexAllProposals(proposals: List[Proposal], cachedListOfSpeakers: List[Speaker]) {
    val indexName = ESIndexUtils.proposalIndex

    logger.debug(s"Do index accepted/approved/submitted ${proposals.size} to index $indexName")

    val requests: Iterable[BulkCompatibleRequest] = proposals.map {
      proposal =>
        val mainSpeaker: String = cachedListOfSpeakers.find(_.uuid == proposal.mainSpeaker).map(_.cleanName).getOrElse("")
        val secondarySpeaker: String = proposal.secondarySpeaker.map(sec => cachedListOfSpeakers.find(_.uuid == sec).map(_.cleanName).getOrElse("")).orNull
        val otherSpeakers: String = proposal.otherSpeakers.map(sec => cachedListOfSpeakers.find(_.uuid == sec).map(_.cleanName).getOrElse("")).mkString(", ")

        indexInto(indexName)
          .fields(
            "id" -> proposal.id
            , "title" -> proposal.title
            , "summary" -> proposal.summary
            , "mainSpeaker" -> mainSpeaker
            , "secondarySpeaker" -> secondarySpeaker
            , "otherSpeakers" -> otherSpeakers
            , "talkType" -> proposal.talkType.id
            , "state" -> proposal.state.code
            , "tags" -> proposal.tags.map(sTags => sTags.filterNot(_.id == "0")).orNull
          )
          .id(proposal.id)
    }

    val client = ESClient.open()

    client.execute(
      bulk(requests)
    ).await

    client.close()
  }


}

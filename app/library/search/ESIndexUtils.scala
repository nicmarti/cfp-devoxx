package library.search

import com.sksamuel.elastic4s.ElasticApi.{NotAnalyzed, RichFuture}
import com.sksamuel.elastic4s.ElasticDsl.{CreateIndexHandler, DeleteIndexHandler, createIndex, deleteIndex, properties}
import com.sksamuel.elastic4s.requests.mappings.{Analysis, KeywordField, TextField}
import library.search.ElasticSearchPublisher.{cfpLang, logger}

object ESIndexUtils {

  val proposalIndex:String = "proposals"
  val speakerIndex:String = "speakers"

  def createIndexForProposal()={

    logger.debug(s"createIndexForProposal indexName=$proposalIndex")
    val client = ESClient.open()
    import play.api.libs.concurrent.Execution.Implicits.defaultContext

    client.execute(
      deleteIndex(proposalIndex)
    ).await

    cfpLang match {
      case Some(lang) if lang.toLowerCase.startsWith("fr") =>
        client.execute(
          createIndex(proposalIndex).mapping(
            properties(
              KeywordField("id"),
              TextField("title", boost = Some(3.0), analysis = Analysis(analyzer = Some("french"))),
              TextField("summary", analysis = Analysis(analyzer = Some("french"))),
              TextField("mainSpeaker", boost = Some(2.0)),
              TextField("secondarySpeaker"),
              TextField("otherSpeakers"),
              KeywordField("talkType", index = Some("false")),
              KeywordField("state", index = Some("false")),
              KeywordField("tags")
            )
          )
        ).await
      case _ =>
        client.execute(
          createIndex(proposalIndex).mapping(
            properties(
              KeywordField("id"),
              TextField("title", boost = Some(3.0)),
              TextField("summary"),
              TextField("mainSpeaker", boost = Some(2.0)),
              TextField("secondarySpeaker"),
              TextField("otherSpeakers"),
              KeywordField("talkType", index = Some("false")),
              KeywordField("state", index = Some("false")),
              KeywordField("tags")
            )
          )
        ).await
    }

    client.close()
  }

  def createIndexForSpeaker()={
    logger.debug(s"createIndexForSpeaker indexName=$speakerIndex")
    val client = ESClient.open()
    import play.api.libs.concurrent.Execution.Implicits.defaultContext

    client.execute(
      deleteIndex(speakerIndex)
    ).await

    cfpLang match {
      case Some(lang) if lang.toLowerCase.startsWith("fr") =>
        client.execute(
          createIndex(speakerIndex).mapping(
              properties(
                KeywordField("uuid", index = Some("false")),
                TextField("bio", analysis = Analysis(analyzer = Some("french"))),
                TextField("company") boost 2.0,
                TextField("firstName"),
                TextField("name") boost 4.0,
                TextField("qualifications", analysis = Analysis(analyzer = Some("french")))
            )
          )
        ).await
      case _ =>
        client.execute(
          createIndex(speakerIndex).mapping(
            properties(
              KeywordField("uuid", index = Some("false")),
              TextField("bio"),
              TextField("company") boost 2.0,
              TextField("firstName"),
              TextField("name") boost 4.0,
              TextField("qualifications")
            )
          )
        ).await
    }

    client.close()
  }


  def createIndexForSchedule(indexName:String)={
    logger.debug(s"createIndexForSchedule indexName=$indexName")
    val client = ESClient.open()
    import play.api.libs.concurrent.Execution.Implicits.defaultContext

    client.execute(
      deleteIndex(indexName)
    ).await

    cfpLang match {
      case Some(lang) if lang.toLowerCase.startsWith("fr") =>
        client.execute(
          createIndex(indexName).mapping(
            properties(
              TextField("name", analysis = Analysis(analyzer = Some("french"))),
              TextField("title", boost=Some(2.0), analysis = Analysis(analyzer = Some("french"))),
              TextField("summary", analysis = Analysis(analyzer = Some("french"))),
              TextField("day",  index = Some("false")),
              TextField("from",  index = Some("false")),
              TextField("to",  index = Some("false")),
              TextField("room"),
              TextField("track.id",  index = Some("false")),
              TextField("talkType.id",  index = Some("false")),
              TextField("mainSpeaker", boost = Some(3.0)) ,
              TextField("secondarySpeaker", boost=Some(2.0)),
              TextField("otherSpeakers"),
              TextField("company")
            )
          )
        ).await
      case _ =>
        client.execute(
          createIndex(indexName).mapping(
            properties(
              TextField("name", analysis = Analysis(analyzer = Some("english"))),
              TextField("title"),
              TextField("summary"),
              TextField("day",  index = Some("false")),
              TextField("from",  index = Some("false")),
              TextField("to",  index = Some("false")),
              TextField("room"),
              TextField("track.id",  index = Some("false")),
              TextField("talkType.id",  index = Some("false")),
              TextField("mainSpeaker", boost = Some(3.0)) ,
              TextField("secondarySpeaker", boost=Some(2.0)),
              TextField("otherSpeakers"),
              TextField("company")
            )
          )
        ).await
    }
    client.close()
  }

}

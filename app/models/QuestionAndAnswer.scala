package models

import com.github.rjeschke.txtmark.Processor
import org.apache.commons.lang3.StringUtils
import play.api.libs.json._
import play.api.templates.HtmlFormat

case class QuestionAndAnswer(question: Option[String], answer: Option[String]) {

  implicit object QuestionAndAnswerFormat extends Format[QuestionAndAnswer] {
    def reads(json: JsValue) = JsSuccess(
      QuestionAndAnswer(
        (json \ "question").asOpt[String],
        (json \ "answer").asOpt[String]
      )
    )

    def writes(questionAndAnswer: QuestionAndAnswer): JsValue = JsObject(
      Seq(
        "question" -> questionAndAnswer.question.map(JsString).getOrElse(JsNull),
        "answer" -> questionAndAnswer.answer.map(JsString).getOrElse(JsNull)
      )
    )
  }

  lazy val questionAsHtml: String = {
    convertToEscapedHtml(question)
  }

  lazy val answerAsHtml: String = {
    convertToEscapedHtml(answer)
  }

  def convertToEscapedHtml(field: Option[String]): String = {
    val html = HtmlFormat.escape(field.getOrElse("")).body // escape HTML code and JS
    val processedMarkdownTest = Processor.process(StringUtils.trimToEmpty(html).trim()) // Then do markdown processing
    processedMarkdownTest
  }
}

object QuestionAndAnswer {
  implicit val questionAndAnswerFormat = Json.format[QuestionAndAnswer]
}

object QuestionAndAnswers {
  def empty: Option[Seq[QuestionAndAnswer]] = {
    Option.apply(Seq.empty)
  }
}

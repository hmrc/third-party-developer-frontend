/*
 * Copyright 2023 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.apiplatform.modules.submissions.domain.models

import cats.data.NonEmptyList

import play.api.libs.json.{Format, Json}

object AskWhen {
  import Submission.AnswersToQuestions

  type Context = Map[String, String]

  object Context {

    object Keys {
      val VAT_OR_ITSA             = "VAT_OR_ITSA"
      val IN_HOUSE_SOFTWARE       = "IN_HOUSE_SOFTWARE"       // Stored on Application
      val NEW_TERMS_OF_USE_UPLIFT = "NEW_TERMS_OF_USE_UPLIFT" // Application already in production, rather than a production credentials request
    }
  }

  def shouldAsk(context: Context, answersToQuestions: AnswersToQuestions)(askWhen: AskWhen): Boolean = {
    askWhen match {
      case AlwaysAsk                                 => true
      case AskWhenContext(contextKey, expectedValue) => context.get(contextKey).map(_.equalsIgnoreCase(expectedValue)).getOrElse(false)
      case AskWhenAnswer(questionId, expectedAnswer) => answersToQuestions.get(questionId).map(_ == expectedAnswer).getOrElse(false)
    }
  }
}

sealed trait AskWhen
case class AskWhenContext(contextKey: String, expectedValue: String)                 extends AskWhen
case class AskWhenAnswer(questionId: Question.Id, expectedValue: SingleChoiceAnswer) extends AskWhen
case object AlwaysAsk                                                                extends AskWhen

object AskWhenAnswer {

  def apply(question: SingleChoiceQuestion, expectedValue: String): AskWhen = {
    require(question.choices.find(qc => qc.value == expectedValue).isDefined)
    AskWhenAnswer(question.id, SingleChoiceAnswer(expectedValue))
  }
}

case class QuestionItem(question: Question, askWhen: AskWhen)

object QuestionItem {
  def apply(question: Question): QuestionItem                   = QuestionItem(question, AlwaysAsk)
  def apply(question: Question, askWhen: AskWhen): QuestionItem = new QuestionItem(question, askWhen)
}

object Questionnaire {
  case class Label(value: String) extends AnyVal
  case class Id(value: String)    extends AnyVal

  object Label {
    implicit val format: Format[Label] = Json.valueFormat[Label]
  }

  object Id {
    def random = Questionnaire.Id(java.util.UUID.randomUUID.toString)

    implicit val format: Format[Id] = Json.valueFormat[Id]
  }
}

case class Questionnaire(
    id: Questionnaire.Id,
    label: Questionnaire.Label,
    questions: NonEmptyList[QuestionItem]
  ) {
  def hasQuestion(qid: Question.Id): Boolean       = question(qid).isDefined
  def question(qid: Question.Id): Option[Question] = questions.find(_.question.id == qid).map(_.question)
}

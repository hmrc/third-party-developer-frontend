/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.modules.submissions.domain.models

import cats.data.NonEmptyList

sealed trait AskWhen
case class AskWhenContext(contextKey: String, expectedValue: String) extends AskWhen
case class AskWhenAnswer(questionId: QuestionId, expectedValue: SingleChoiceAnswer) extends AskWhen
case object AlwaysAsk extends AskWhen

object AskWhenAnswer {
  def apply(question: SingleChoiceQuestion, expectedValue: String): AskWhen = {
    require(question.choices.find(qc => qc.value == expectedValue).isDefined)
    AskWhenAnswer(question.id, SingleChoiceAnswer(expectedValue))
  }
}

case class QuestionItem(question: Question, askWhen: AskWhen)

case class Label(value: String) extends AnyVal

object QuestionItem {
  def apply(question: Question): QuestionItem = QuestionItem(question, AlwaysAsk)
  def apply(question: Question, askWhen: AskWhen): QuestionItem = new QuestionItem(question, askWhen)
}

case class QuestionnaireId(value: String) extends AnyVal

object QuestionnaireId {
  def random = QuestionnaireId(java.util.UUID.randomUUID.toString)
}

case class Questionnaire(
  id: QuestionnaireId,
  label: Label,
  questions: NonEmptyList[QuestionItem]
) {
  def hasQuestion(qid: QuestionId): Boolean = question(qid).isDefined
  def question(qid: QuestionId): Option[Question] = questions.find(_.question.id == qid).map(_.question)
}

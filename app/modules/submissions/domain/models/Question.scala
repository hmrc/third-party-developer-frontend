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

package modules.submissions.domain.models

import scala.collection.immutable.ListSet

case class Wording(value: String) extends AnyVal

case class QuestionId(value: String) extends AnyVal

object QuestionId {
  def random = QuestionId(java.util.UUID.randomUUID.toString)

  implicit val jsonFormatQuestionId = play.api.libs.json.Json.valueFormat[QuestionId]
}

sealed trait Question {
  def id: QuestionId
  def wording: Wording
  def statement: Statement

  def absenceText: Option[String]
  final def isOptional: Boolean = absenceText.isDefined
}

case class TextQuestion(id: QuestionId, wording: Wording, statement: Statement, absenceText: Option[String] = None) extends Question

case class AcknowledgementOnly(id: QuestionId, wording: Wording, statement: Statement) extends Question {
  val absenceText = None
}


case class PossibleAnswer(value: String) extends AnyVal
sealed trait ChoiceQuestion extends Question {
  def choices: ListSet[PossibleAnswer]
}

sealed trait SingleChoiceQuestion extends ChoiceQuestion

case class MultiChoiceQuestion(id: QuestionId, wording: Wording, statement: Statement, choices: ListSet[PossibleAnswer], absenceText: Option[String] = None) extends ChoiceQuestion

case class ChooseOneOfQuestion(id: QuestionId, wording: Wording, statement: Statement, choices: ListSet[PossibleAnswer], absenceText: Option[String] = None) extends SingleChoiceQuestion
case class YesNoQuestion(id: QuestionId, wording: Wording, statement: Statement, absenceText: Option[String] = None) extends SingleChoiceQuestion {
  lazy val choices = ListSet(PossibleAnswer("Yes"), PossibleAnswer("No"))
}


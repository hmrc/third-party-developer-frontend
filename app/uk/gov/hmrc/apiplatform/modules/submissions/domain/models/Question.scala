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

package uk.gov.hmrc.apiplatform.modules.submissions.domain.models

import scala.collection.immutable.ListSet
import scala.collection.immutable.ListMap

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

  def absence: Option[(String, Mark)]

  def absenceText: Option[String] = absence.map(_._1)
  def absenceMark: Option[Mark] = absence.map(_._2)

  final def isOptional: Boolean = absence.isDefined
}

case class TextQuestion(id: QuestionId, wording: Wording, statement: Statement, label: Option[TextQuestion.Label] = None, hintText: Option[TextQuestion.HintText] = None, absence: Option[(String, Mark)] = None) extends Question

object TextQuestion {
  import play.api.libs.json.Json

  case class Label(value: String) extends AnyVal
  case class HintText(value: String) extends AnyVal

  implicit val labelFormat = Json.valueFormat[Label]
  implicit val hintTextFormat = Json.valueFormat[HintText]
}

case class AcknowledgementOnly(id: QuestionId, wording: Wording, statement: Statement) extends Question {
  val absence = None
}

sealed trait Mark
case object Fail extends Mark
case object Warn extends Mark
case object Pass extends Mark

object Mark {
  import cats.Monoid

  implicit val markMonoid: Monoid[Mark] = new Monoid[Mark] {
    def empty: Mark = Pass
    def combine(x: Mark, y: Mark): Mark = (x,y) match {
      case (Fail, _)    => Fail
      case (_, Fail)    => Fail
      case (Warn, _)    => Warn
      case (_, Warn)    => Warn
      case (Pass, Pass) => Pass
    }
  }
}

case class PossibleAnswer(value: String) extends AnyVal

sealed trait ChoiceQuestion extends Question {
  def choices: ListSet[PossibleAnswer]
  def marking: ListMap[PossibleAnswer, Mark]
}

sealed trait SingleChoiceQuestion extends ChoiceQuestion

case class MultiChoiceQuestion(id: QuestionId, wording: Wording, statement: Statement, marking: ListMap[PossibleAnswer, Mark], absence: Option[(String, Mark)] = None) extends ChoiceQuestion {
  lazy val choices: ListSet[PossibleAnswer] = ListSet(marking.keys.toList : _*)
}

case class ChooseOneOfQuestion(id: QuestionId, wording: Wording, statement: Statement, marking: ListMap[PossibleAnswer, Mark], absence: Option[(String, Mark)] = None) extends SingleChoiceQuestion {
  lazy val choices: ListSet[PossibleAnswer] = ListSet(marking.keys.toList : _*)
}

case class YesNoQuestion(id: QuestionId, wording: Wording, statement: Statement,  yesMarking: Mark, noMarking: Mark, absence: Option[(String, Mark)] = None) extends SingleChoiceQuestion {
  val YES = PossibleAnswer("Yes")
  val NO = PossibleAnswer("No")

  lazy val marking: ListMap[PossibleAnswer, Mark] = ListMap(YES -> yesMarking, NO -> noMarking)
  lazy val choices = ListSet(YES, NO)
}

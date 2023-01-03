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

import scala.collection.immutable.ListSet
import scala.collection.immutable.ListMap
import play.api.libs.json.Json


sealed trait Question {
  def id: Question.Id
  def wording: Wording
  def statement: Option[Statement]
  def afterStatement: Option[Statement]

  def absence: Option[(String, Mark)]

  def absenceText: Option[String] = absence.map(_._1)
  def absenceMark: Option[Mark] = absence.map(_._2)

  final def isOptional: Boolean = absence.isDefined
}

trait LabelAndHints {
  self: Question =>

  def label: Option[Question.Label]
  def hintText: Option[NonBulletStatementFragment]
}

case class ErrorInfo(summary: String, message: Option[String])

object ErrorInfo {
  def apply(summary: String): ErrorInfo = new ErrorInfo(summary, None)
  def apply(summary: String, message: String): ErrorInfo =  if(summary == message) apply(summary) else new ErrorInfo(summary, Some(message))

  implicit val format = Json.format[ErrorInfo]
}

trait ErrorMessaging {
  self: Question =>
  
  def errorInfo: Option[ErrorInfo]
}
case class Wording(value: String) extends AnyVal

object Wording {
  implicit val format = Json.valueFormat[Wording]
}


object Question {
  case class Id(value: String) extends AnyVal

  object Id {
    def random = Id(java.util.UUID.randomUUID.toString)

    implicit val format = Json.valueFormat[Id]
  }

  case class Label(value: String) extends AnyVal

  object Label {
    implicit val format = Json.valueFormat[Label]
  }
}

case class TextQuestion(
  id: Question.Id,
  wording: Wording,
  statement: Option[Statement],
  afterStatement: Option[Statement] = None,
  label: Option[Question.Label] = None,
  hintText: Option[NonBulletStatementFragment] = None,
  validation: Option[TextValidation] = None,
  absence: Option[(String, Mark)] = None,
  errorInfo: Option[ErrorInfo] = None
) extends Question with LabelAndHints with ErrorMessaging

case class AcknowledgementOnly(
  id: Question.Id,
  wording: Wording,
  statement: Option[Statement]
) extends Question {
  val absence = None
  val afterStatement = None
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

case class PossibleAnswer(value: String) extends AnyVal {
  def htmlValue = value.replace(" ","-").filter(c => c.isLetterOrDigit || c == '-')
}

sealed trait ChoiceQuestion extends Question with LabelAndHints with ErrorMessaging {
  def choices: ListSet[PossibleAnswer]
  def marking: ListMap[PossibleAnswer, Mark]
}

sealed trait SingleChoiceQuestion extends ChoiceQuestion

case class MultiChoiceQuestion(
  id: Question.Id,
  wording: Wording,
  statement: Option[Statement],
  afterStatement: Option[Statement] = None,
  label: Option[Question.Label] = None,
  hintText: Option[NonBulletStatementFragment] = None,
  marking: ListMap[PossibleAnswer, Mark],
  absence: Option[(String, Mark)] = None,
  errorInfo: Option[ErrorInfo] = None
) extends ChoiceQuestion {
  lazy val choices: ListSet[PossibleAnswer] = ListSet(marking.keys.toList : _*)
}

case class ChooseOneOfQuestion(
  id: Question.Id,
  wording: Wording,
  statement: Option[Statement],
  afterStatement: Option[Statement] = None,
  label: Option[Question.Label] = None,
  hintText: Option[NonBulletStatementFragment] = None,
  marking: ListMap[PossibleAnswer, Mark],
  absence: Option[(String, Mark)] = None,
  errorInfo: Option[ErrorInfo] = None
) extends SingleChoiceQuestion {
  lazy val choices: ListSet[PossibleAnswer] = ListSet(marking.keys.toList : _*)
}

case class YesNoQuestion(
  id: Question.Id,
  wording: Wording,
  statement: Option[Statement],
  afterStatement: Option[Statement] = None,
  label: Option[Question.Label] = None,
  hintText: Option[NonBulletStatementFragment] = None,
  yesMarking: Mark, noMarking: Mark,
  absence: Option[(String, Mark)] = None,
  errorInfo: Option[ErrorInfo] = None
) extends SingleChoiceQuestion {
  
  val YES = PossibleAnswer("Yes")
  val NO = PossibleAnswer("No")

  lazy val marking: ListMap[PossibleAnswer, Mark] = ListMap(YES -> yesMarking, NO -> noMarking)
  lazy val choices = ListSet(YES, NO)
}

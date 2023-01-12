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

package uk.gov.hmrc.apiplatform.modules.submissions

import scala.collection.immutable.ListMap

import uk.gov.hmrc.apiplatform.modules.submissions.domain.models._

trait MakeOptional[T <: Question] {
  def makeOptional(text: String, mark: Mark): T
  def makeOptionalFail: T = makeOptional("Some text", Fail)
  def makeOptionalWarn: T = makeOptional("Some text", Warn)
  def makeOptionalPass: T = makeOptional("Some text", Pass)
}

trait QuestionBuilder {

  implicit class TextQuestionSyntax(question: TextQuestion) extends MakeOptional[TextQuestion] {
    def makeOptional(text: String, mark: Mark): TextQuestion = question.copy(absence = Some((text, mark)))
  }

  implicit class MultiChoiceQuestionSyntax(question: MultiChoiceQuestion) extends MakeOptional[MultiChoiceQuestion] {
    def makeOptional(text: String, mark: Mark): MultiChoiceQuestion = question.copy(absence = Some((text, mark)))
  }

  implicit class YesNoQuestionSyntax(question: YesNoQuestion) extends MakeOptional[YesNoQuestion] {
    def makeOptional(text: String, mark: Mark): YesNoQuestion = question.copy(absence = Some((text, mark)))
  }

  implicit class ChooseOneOfQuestionSyntax(question: ChooseOneOfQuestion) extends MakeOptional[ChooseOneOfQuestion] {
    def makeOptional(text: String, mark: Mark): ChooseOneOfQuestion = question.copy(absence = Some((text, mark)))
  }

  def acknowledgementOnly(counter: Int): AcknowledgementOnly =
    AcknowledgementOnly(
      Question.Id.random,
      Wording(s"Wording$counter"),
      None
    )

  def yesNoQuestion(counter: Int): YesNoQuestion = {
    YesNoQuestion(
      Question.Id.random,
      Wording(s"Wording$counter"),
      None,
      None,
      yesMarking = Pass,
      noMarking = Fail
    )
  }

  def chooseOneOfQuestion(counter: Int, choices: String*): ChooseOneOfQuestion = {
    ChooseOneOfQuestion(
      Question.Id.random,
      Wording(s"Wording$counter"),
      None,
      None,
      None,
      None,
      marking = choices.toList.map(c => (PossibleAnswer(c) -> Pass)).foldRight(ListMap.empty[PossibleAnswer, Mark])((pair, acc) => acc + pair)
    )
  }

  def multichoiceQuestion(counter: Int, choices: String*): MultiChoiceQuestion = {
    MultiChoiceQuestion(
      Question.Id.random,
      Wording(s"Wording$counter"),
      None,
      None,
      None,
      None,
      choices.toList.map(c => (PossibleAnswer(c) -> Pass)).foldRight(ListMap.empty[PossibleAnswer, Mark])((pair, acc) => acc + pair)
    )
  }

  def textQuestion(counter: Int): TextQuestion = {
    TextQuestion(
      Question.Id.random,
      Wording(s"Wording$counter"),
      None
    )
  }
}

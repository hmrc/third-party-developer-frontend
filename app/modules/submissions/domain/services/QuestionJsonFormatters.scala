/*
 * Copyright 2021 HM Revenue & Customs
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

package modules.questionnaires.domain.services

trait QuestionJsonFormatters extends StatementJsonFormatters {
  import modules.questionnaires.domain.models._
  import play.api.libs.json._
  import uk.gov.hmrc.play.json.Union

  implicit val jsonFormatWording = Json.valueFormat[Wording]
  implicit val jsonFormatLabel = Json.valueFormat[Label]


  implicit val jsonFormatPossibleAnswer = Json.valueFormat[PossibleAnswer]
  implicit val jsonFormatTextQuestion = Json.format[TextQuestion]
  implicit val jsonFormatYesNoQuestion = Json.format[YesNoQuestion]
  implicit val jsonFormatChooseOneOfQuestion = Json.format[ChooseOneOfQuestion]
  implicit val jsonFormatMultiChoiceQuestion = Json.format[MultiChoiceQuestion]
  implicit val jsonFormatSingleChoiceQuestion = Json.format[SingleChoiceQuestion]

  implicit val jsonFormatQuestion: Format[Question] = Union.from[Question]("questionType")
    .and[ChooseOneOfQuestion]("choose")
    .and[MultiChoiceQuestion]("multi")
    .and[YesNoQuestion]("yesNo")
    .and[SingleChoiceQuestion]("single")
    .and[TextQuestion]("text")
    .format
}

object QuestionJsonFormatters extends QuestionJsonFormatters
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

package modules.submissions.domain.services

import modules.services.MapJsonFormatters

trait QuestionJsonFormatters extends StatementJsonFormatters with MapJsonFormatters {
  import modules.submissions.domain.models._
  import play.api.libs.json._
  import uk.gov.hmrc.play.json.Union

  implicit val jsonFormatWording = Json.valueFormat[Wording]
  implicit val jsonFormatLabel = Json.valueFormat[Label]

  implicit val jsonFormatFailMarkAnswer = Json.format[Fail.type]
  implicit val jsonFormatWarnMarkAnswer = Json.format[Warn.type]
  implicit val jsonFormatPassMarkAnswer = Json.format[Pass.type]
  
  implicit val jsonFormatMarkAnswer = Union.from[Mark]("markAnswer")
    .and[Fail.type]("fail")
    .and[Warn.type]("warn")
    .and[Pass.type]("pass")
    .format

  implicit val keyReadsPossibleAnswer: KeyReads[PossibleAnswer] = key => JsSuccess(PossibleAnswer(key))
  implicit val keyWritesPossibleAnswer: KeyWrites[PossibleAnswer] = _.value
  implicit val jsonListMapKV = listMapReads[PossibleAnswer, Mark]

  implicit val jsonFormatPossibleAnswer = Json.valueFormat[PossibleAnswer]
  implicit val jsonFormatTextQuestion = Json.format[TextQuestion]
  implicit val jsonFormatYesNoQuestion = Json.format[YesNoQuestion]
  implicit val jsonFormatChooseOneOfQuestion = Json.format[ChooseOneOfQuestion]
  implicit val jsonFormatMultiChoiceQuestion = Json.format[MultiChoiceQuestion]
  implicit val jsonFormatAcknowledgementOnly = Json.format[AcknowledgementOnly]

  implicit val jsonFormatQuestion: Format[Question] = Union.from[Question]("questionType")
    .and[MultiChoiceQuestion]("multi")
    .and[YesNoQuestion]("yesNo")
    .and[ChooseOneOfQuestion]("choose")
    .and[TextQuestion]("text")
    .and[AcknowledgementOnly]("acknowledgement")
    .format
}

object QuestionJsonFormatters extends QuestionJsonFormatters


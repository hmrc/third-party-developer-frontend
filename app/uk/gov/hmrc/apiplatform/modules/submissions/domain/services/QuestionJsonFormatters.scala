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

package uk.gov.hmrc.apiplatform.modules.submissions.domain.services

import uk.gov.hmrc.apiplatform.modules.common.services.MapJsonFormatters

trait QuestionJsonFormatters extends StatementJsonFormatters with MapJsonFormatters {
  import uk.gov.hmrc.apiplatform.modules.submissions.domain.models._
  import play.api.libs.json._
  import uk.gov.hmrc.play.json.Union

  implicit val jsonFormatWording = Json.valueFormat[Wording]

  implicit val markWrites : Writes[Mark] = Writes {
    case Fail => JsString("fail")
    case Warn => JsString("warn")
    case Pass => JsString("pass")
  }
  
  implicit val markReads : Reads[Mark] = Reads {
    case JsString("fail") => JsSuccess(Fail)
    case JsString("warn") => JsSuccess(Warn)
    case JsString("pass") => JsSuccess(Pass)
    case _ => JsError("Failed to parse Mark value")
  }

  implicit val keyReadsQuestionId: KeyReads[Question.Id] = key => JsSuccess(Question.Id(key))
  implicit val keyWritesQuestionId: KeyWrites[Question.Id] = _.value

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

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

trait AnswersJsonFormatters {
  import uk.gov.hmrc.apiplatform.modules.submissions.domain.models._
  import play.api.libs.json._
  import uk.gov.hmrc.play.json.Union

  implicit val jfAcknowledgedAnswer = Json.format[AcknowledgedAnswer.type]
  implicit val jfNoAnswer = Json.format[NoAnswer.type]
  implicit val jfTextAnswer = Json.format[TextAnswer]
  implicit val jfSingleChoiceAnswer = Json.format[SingleChoiceAnswer]
  implicit val jfMultipleChoiceAnswer = Json.format[MultipleChoiceAnswer]

  implicit val jfActualAnswer: OFormat[ActualAnswer] = Union.from[ActualAnswer]("answerType")
    .and[MultipleChoiceAnswer]("multipleChoice")
    .and[SingleChoiceAnswer]("singleChoice")
    .and[TextAnswer]("text")
    .and[AcknowledgedAnswer.type]("acknowledged")
    .and[NoAnswer.type]("noAnswer")
    .format

}

object AnswersJsonFormatters extends AnswersJsonFormatters
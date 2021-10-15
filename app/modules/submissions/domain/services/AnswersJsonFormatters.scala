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


trait AnswersJsonFormatters {
  import modules.submissions.domain.models._
  import play.api.libs.json._
  import uk.gov.hmrc.play.json.Union

  implicit val jsonFormatSingleChoiceAnswer = Json.format[SingleChoiceAnswer]
  implicit val jsonFormatMultipleChoiceAnswer = Json.format[MultipleChoiceAnswer]
  implicit val jsonFormatTextAnswer = Json.format[TextAnswer]

  implicit val jsonFormatAnswerType: OFormat[ActualAnswer] = Union.from[ActualAnswer]("answer")
    .and[SingleChoiceAnswer]("singleChoiceAnswer")
    .and[MultipleChoiceAnswer]("multipleChoiceAnswer")
    .and[TextAnswer]("textAnswer")
    .format
}

object AnswersJsonFormatters extends AnswersJsonFormatters
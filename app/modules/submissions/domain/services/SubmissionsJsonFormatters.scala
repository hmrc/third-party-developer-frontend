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

package modules.submissions.domain.services

import modules.submissions.domain.models._
import play.api.libs.json._
import org.joda.time.DateTimeZone

trait SubmissionsJsonFormatters extends GroupOfQuestionnairesJsonFormatters {

  implicit val keyReadsQuestionnaireId: KeyReads[QuestionnaireId] = key => JsSuccess(QuestionnaireId(key))
  implicit val keyWritesQuestionnaireId: KeyWrites[QuestionnaireId] = _.value

  implicit val stateWrites : Writes[QuestionnaireState] = Writes {
    case NotStarted    => JsString("NotStarted")
    case InProgress    => JsString("InProgress")
    case NotApplicable => JsString("NotApplicable")
    case Completed     => JsString("Completed")
  }
  
  implicit val stateReads : Reads[QuestionnaireState] = Reads {
    case JsString("NotStarted") => JsSuccess(NotStarted)
    case JsString("InProgress") => JsSuccess(InProgress)
    case JsString("NotApplicable") => JsSuccess(NotApplicable)
    case JsString("Completed") => JsSuccess(Completed)
    case _ => JsError("Failed to parse QuestionnaireState value")
  }

  implicit val questionnaireProgressFormat = Json.format[QuestionnaireProgress]

  implicit val answersToQuestionsFormat: OFormat[Map[QuestionId, Option[ActualAnswer]]] = implicitly

  import JodaWrites.JodaDateTimeWrites
  implicit val utcReads = JodaReads.DefaultJodaDateTimeReads.map(dt => dt.withZone(DateTimeZone.UTC))
  implicit val submissionFormat = Json.format[Submission]
  implicit val extendedSubmissionFormat = Json.format[ExtendedSubmission]
}

object SubmissionsJsonFormatters extends SubmissionsJsonFormatters

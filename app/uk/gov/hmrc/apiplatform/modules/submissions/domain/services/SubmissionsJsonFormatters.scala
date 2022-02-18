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

package uk.gov.hmrc.apiplatform.modules.submissions.domain.services

import uk.gov.hmrc.apiplatform.modules.submissions.domain.models._
import play.api.libs.json._
import org.joda.time.DateTimeZone
import uk.gov.hmrc.play.json.Union

trait BaseSubmissionsJsonFormatters extends GroupOfQuestionnairesJsonFormatters {
  
  implicit val keyReadsQuestionnaireId: KeyReads[QuestionnaireId] = key => JsSuccess(QuestionnaireId(key))
  implicit val keyWritesQuestionnaireId: KeyWrites[QuestionnaireId] = _.value

  implicit val stateWrites : Writes[QuestionnaireState] = Writes {
    case QuestionnaireState.NotStarted    => JsString("NotStarted")
    case QuestionnaireState.InProgress    => JsString("InProgress")
    case QuestionnaireState.NotApplicable => JsString("NotApplicable")
    case QuestionnaireState.Completed     => JsString("Completed")
  }
  
  implicit val stateReads : Reads[QuestionnaireState] = Reads {
    case JsString("NotStarted") => JsSuccess(QuestionnaireState.NotStarted)
    case JsString("InProgress") => JsSuccess(QuestionnaireState.InProgress)
    case JsString("NotApplicable") => JsSuccess(QuestionnaireState.NotApplicable)
    case JsString("Completed") => JsSuccess(QuestionnaireState.Completed)
    case _ => JsError("Failed to parse QuestionnaireState value")
  }

  implicit val questionnaireProgressFormat = Json.format[QuestionnaireProgress]

  implicit val answersToQuestionsFormat: OFormat[Map[QuestionId, Option[ActualAnswer]]] = implicitly

  implicit val questionIdsOfInterestFormat = Json.format[QuestionIdsOfInterest]
}

trait SubmissionsJsonFormatters extends BaseSubmissionsJsonFormatters {
  import Submission.Status._
  
  import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
  implicit val dateFormat = ReactiveMongoFormats.dateTimeFormats

  implicit val RejectedStatusFormat = Json.format[Declined]
  implicit val AcceptedStatusFormat = Json.format[Granted]
  implicit val AcceptedWithWarningsStatusFormat = Json.format[GrantedWithWarnings]
  implicit val SubmittedStatusFormat = Json.format[Submitted]
  implicit val answeringStatusFormat = Json.format[Answering]
  implicit val CreatedStatusFormat = Json.format[Created]
  
  implicit val submissionStatus = Union.from[Submission.Status]("Submission.StatusType")
    .and[Declined]("declined")
    .and[Granted]("granted")
    .and[GrantedWithWarnings]("grantedWithWarnings")
    .and[Submitted]("submitted")
    .and[Answering]("answering")
    .and[Created]("created")
    .format

  implicit val submissionInstanceFormat = Json.format[Submission.Instance]
  implicit val submissionFormat = Json.format[Submission]
}

object SubmissionsJsonFormatters extends SubmissionsJsonFormatters

trait SubmissionsFrontendJsonFormatters extends BaseSubmissionsJsonFormatters {
  import JodaWrites.JodaDateTimeWrites
  import Submission.Status._

  implicit val utcReads = JodaReads.DefaultJodaDateTimeReads.map(dt => dt.withZone(DateTimeZone.UTC))

  implicit val rejectedStatusFormat = Json.format[Declined]
  implicit val acceptedStatusFormat = Json.format[Granted]
  implicit val acceptedWithWarningsStatusFormat = Json.format[GrantedWithWarnings]
  implicit val submittedStatusFormat = Json.format[Submitted]
  implicit val answeringStatusFormat = Json.format[Answering]
  implicit val createdStatusFormat = Json.format[Created]
  
  implicit val submissionStatus = Union.from[Submission.Status]("Submission.StatusType")
    .and[Declined]("declined")
    .and[Granted]("granted")
    .and[GrantedWithWarnings]("grantedWithWarnings")
    .and[Submitted]("submitted")
    .and[Answering]("answering")
    .and[Created]("created")
    .format

  implicit val submissionInstanceFormat = Json.format[Submission.Instance]
  implicit val submissionFormat = Json.format[Submission]
  implicit val extendedSubmissionFormat = Json.format[ExtendedSubmission]
  implicit val markedSubmissionFormat = Json.format[MarkedSubmission]
}

object SubmissionsFrontendJsonFormatters extends SubmissionsFrontendJsonFormatters

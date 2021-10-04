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

import domain.services.MapJsonFormatters
import scala.collection.immutable.ListMap
import modules.questionnaires.domain.models._
import play.api.libs.json._
import org.joda.time.DateTimeZone

trait SubmissionsJsonFormatters extends GroupOfQuestionnairesJsonFormatters with MapJsonFormatters {
  
  implicit val asStringQuestion: (QuestionId) => String = (q) => q.value
  implicit val asQuestionId: (String) => QuestionId = (s) => QuestionId(s)

  implicit val asStringQuestionnaire: (QuestionnaireId) => String = (q) => q.value
  implicit val asQuestionnaireId: (String) => QuestionnaireId = (s) => QuestionnaireId(s)
   
  implicit val listMapWrites: Writes[ListMap[QuestionId, ActualAnswer]] = listMapWrites[QuestionId, ActualAnswer]
  implicit val listMapReads: Reads[ListMap[QuestionId, ActualAnswer]] = listMapReads[QuestionId, ActualAnswer]

  implicit val outerMapWrites: Writes[Map[QuestionnaireId, ListMap[QuestionId, ActualAnswer]]] = mapWrites[QuestionnaireId, ListMap[QuestionId, ActualAnswer]]
  implicit val outerMapReads: Reads[Map[QuestionnaireId, ListMap[QuestionId, ActualAnswer]]] = mapReads[QuestionnaireId, ListMap[QuestionId, ActualAnswer]]
}

object SubmissionsJsonFormatters extends SubmissionsJsonFormatters {
  import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
  implicit val dateFormat = ReactiveMongoFormats.dateTimeFormats
  implicit val answerFormat = Json.format[AnswersToQuestionnaire]
  implicit val submissionFormat = Json.format[Submission]
}

object SubmissionsFrontendJsonFormatters extends SubmissionsJsonFormatters {
  import JodaWrites.JodaDateTimeWrites
  implicit val utcReads = JodaReads.DefaultJodaDateTimeReads.map(dt => dt.withZone(DateTimeZone.UTC))
  implicit val answerFormat = Json.format[AnswersToQuestionnaire]
  implicit val submissionFormat = Json.format[Submission]
}

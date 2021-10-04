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

 package modules.questionnaires.domain.models

import org.joda.time.DateTime
import java.util.UUID
import scala.collection.immutable.ListMap
import domain.models.applications.ApplicationId

sealed trait ActualAnswer
case class SingleChoiceAnswer(value: String) extends ActualAnswer
case class MultipleChoiceAnswer(values: Set[String]) extends ActualAnswer
case class TextAnswer(value: String) extends ActualAnswer

@Deprecated
case class AnswersToQuestionnaire(
  questionnaireId: QuestionnaireId, 
  answers: ListMap[QuestionId, ActualAnswer]
)

case class SubmissionId(value: String) extends AnyVal
object SubmissionId {
  implicit val format = play.api.libs.json.Json.valueFormat[SubmissionId]
  
  def random: SubmissionId = SubmissionId(UUID.randomUUID().toString())
}

import Submission.AnswerMapOfMaps

case class Submission(
  id: SubmissionId,
  applicationId: ApplicationId,
  startedOn: DateTime,
  groups: List[GroupOfQuestionnaires],
  questionnaireAnswers: AnswerMapOfMaps
) {
  def allQuestionnaireIds: List[QuestionnaireId] = groups.flatMap(_.links.map(_.id))

  def hasQuestionnaire(qid: QuestionnaireId): Boolean = allQuestionnaireIds.contains(qid)
}

object Submission {
  type AnswerMapOfMaps = Map[QuestionnaireId, ListMap[QuestionId, ActualAnswer]]
}
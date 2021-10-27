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

package modules.submissions.domain.models

import domain.models.applications.ApplicationId
import org.joda.time.DateTime
import java.util.UUID
import cats.data.NonEmptyList

sealed trait ActualAnswer
case class SingleChoiceAnswer(value: String) extends ActualAnswer
case class MultipleChoiceAnswer(values: Set[String]) extends ActualAnswer
case class TextAnswer(value: String) extends ActualAnswer

case class SubmissionId(value: String) extends AnyVal

object SubmissionId {
  implicit val format = play.api.libs.json.Json.valueFormat[SubmissionId]
  
  def random: SubmissionId = SubmissionId(UUID.randomUUID().toString())
}

sealed trait QuestionnaireState {
  override def toString(): String = {
    this match {
      case NotStarted => "Not Started"
      case InProgress => "In Progress"
      case NotApplicable => "Not Applicable"
      case Completed => "Completed"
    }
  }
}
case object NotStarted extends QuestionnaireState
case object InProgress extends QuestionnaireState
case object NotApplicable extends QuestionnaireState
case object Completed extends QuestionnaireState

case class QuestionnaireProgress(state: QuestionnaireState, questionsToAsk: List[QuestionId])

object Submission {
  type AnswersToQuestions = Map[QuestionId, ActualAnswer]
}

case class Submission(
  id: SubmissionId,
  applicationId: ApplicationId,
  startedOn: DateTime,
  groups: NonEmptyList[GroupOfQuestionnaires],
  answersToQuestions: Submission.AnswersToQuestions
) {
  def allQuestionnaires: NonEmptyList[Questionnaire] = groups.flatMap(g => g.links)

  def allQuestions: NonEmptyList[Question] = allQuestionnaires.flatMap(l => l.questions.map(_.question))

  def findQuestion(questionId: QuestionId): Option[Question] = allQuestions.find(q => q.id == questionId)

  def findQuestionnaireContaining(questionId: QuestionId): Option[Questionnaire] = 
    allQuestionnaires.find(qn => 
      qn.questions.exists(qi => 
        qi.question.id == questionId
      )
    )
}

case class ExtendedSubmission(
  submission: Submission,
  questionnaireProgress: Map[QuestionnaireId, QuestionnaireProgress]
)
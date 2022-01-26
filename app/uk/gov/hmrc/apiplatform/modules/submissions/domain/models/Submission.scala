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

package uk.gov.hmrc.apiplatform.modules.submissions.domain.models

import org.joda.time.DateTime
import java.util.UUID
import cats.data.NonEmptyList
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.UserId
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.ApplicationId

sealed trait QuestionnaireState

object QuestionnaireState {
  case object NotStarted extends QuestionnaireState
  case object InProgress extends QuestionnaireState
  case object NotApplicable extends QuestionnaireState
  case object Completed extends QuestionnaireState

  def describe(state: QuestionnaireState): String = state match {
    case NotStarted => "Not Started"
    case InProgress => "In Progress"
    case NotApplicable => "Not Applicable"
    case Completed => "Completed"
  }

  def isCompleted(state: QuestionnaireState): Boolean = state match {
    case NotStarted | InProgress => false
    case _ => true
  }
}

case class QuestionnaireProgress(state: QuestionnaireState, questionsToAsk: List[QuestionId])


case class QuestionIdsOfInterest(applicationNameId: QuestionId, privacyPolicyUrlId: QuestionId, termsAndConditionsUrlId: QuestionId, organisationUrlId: QuestionId)

object Submission {
  type AnswersToQuestions = Map[QuestionId, ActualAnswer]

  case class Id(value: String) extends AnyVal

  object Id {
    implicit val format = play.api.libs.json.Json.valueFormat[Id]
    
    def random: Id = Id(UUID.randomUUID().toString())
  }

  sealed trait Status {
    def isInProgress = this match {
      case _ : Submission.Status.Created => true
      case _ => false
    }
  }

  object Status {
    case class Rejected(
      timestamp: DateTime,
      name: String,
      reasons: String
    ) extends Status

    case class Accepted(
      timestamp: DateTime,
      name: String
    ) extends Status

    case class Submitted(
      timestamp: DateTime,
      userId: UserId
    ) extends Status

    case class Created(
      timestamp: DateTime,
      userId: UserId
    ) extends Status
  }

  case class Instance(
    index: Int,
    answersToQuestions: Submission.AnswersToQuestions,
    statusHistory: NonEmptyList[Submission.Status]
  ) {
    def isInProgress = this.statusHistory.head.isInProgress
  }
}

case class Submission(
  id: Submission.Id,
  applicationId: ApplicationId,
  startedOn: DateTime,
  groups: NonEmptyList[GroupOfQuestionnaires],
  questionIdsOfInterest: QuestionIdsOfInterest,
  instances: NonEmptyList[Submission.Instance]
) {
  lazy val allQuestionnaires: NonEmptyList[Questionnaire] = groups.flatMap(g => g.links)

  lazy val allQuestions: NonEmptyList[Question] = allQuestionnaires.flatMap(l => l.questions.map(_.question))

  def findQuestion(questionId: QuestionId): Option[Question] = allQuestions.find(q => q.id == questionId)

  def findQuestionnaireContaining(questionId: QuestionId): Option[Questionnaire] = 
    allQuestionnaires.find(qn => 
      qn.questions.exists(qi => 
        qi.question.id == questionId
      )
    )

  lazy val latestInstance = instances.head

  def isInProgress = latestInstance.isInProgress

  def setLatestAnswers(answers: Submission.AnswersToQuestions): Submission = {
    val newLatest = latestInstance.copy(answersToQuestions = answers)
    this.copy(instances = NonEmptyList.of(newLatest, this.instances.tail: _*))
  }
}


case class ExtendedSubmission(
  submission: Submission,
  questionnaireProgress: Map[QuestionnaireId, QuestionnaireProgress]
) {
  lazy val isCompleted = 
    questionnaireProgress.values
    .map(_.state)
    .forall(QuestionnaireState.isCompleted)
}

case class MarkedSubmission(
  submission: Submission,
  questionnaireProgress: Map[QuestionnaireId, QuestionnaireProgress],
  markedAnswers: Map[QuestionId, Mark]
) {
  lazy val isFail = markedAnswers.values.toList.contains(Fail) | markedAnswers.values.filter(_ == Warn).size >= 4
  lazy val isWarn = markedAnswers.values.toList.contains(Warn)
  lazy val isPass = !isWarn && !isFail
}

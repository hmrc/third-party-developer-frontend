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


case class QuestionIdsOfInterest(
    applicationNameId: QuestionId,
    privacyPolicyUrlId: QuestionId,
    termsAndConditionsUrlId: QuestionId,
    organisationUrlId: QuestionId,
    responsibleIndividualNameId: QuestionId,
    responsibleIndividualEmailId: QuestionId
)

object Submission {
  type AnswersToQuestions = Map[QuestionId, ActualAnswer]

  case class Id(value: String) extends AnyVal

  object Id {
    implicit val format = play.api.libs.json.Json.valueFormat[Id]
    
    def random: Id = Id(UUID.randomUUID().toString())
  }

  val create: (
      String,
      Submission.Id,
      ApplicationId,
      DateTime,
      NonEmptyList[GroupOfQuestionnaires],
      QuestionIdsOfInterest) => Submission = (requestedBy, id, applicationId, timestamp, groups, questionIdsOfInterest) => {

      val initialStatus = Submission.Status.Created(timestamp, requestedBy)
      val initialInstances = NonEmptyList.of(Submission.Instance(0, Map.empty, NonEmptyList.of(initialStatus)))
    Submission(id, applicationId, timestamp, groups, questionIdsOfInterest, initialInstances)
  }
  
  val addInstance: (Submission.AnswersToQuestions, Submission.Status) => Submission => Submission = (answers, status) => s => {
    val newInstance = Submission.Instance(s.latestInstance.index+1, answers, NonEmptyList.of(status))
    s.copy(instances = newInstance :: s.instances)
  }
  
  val changeLatestInstance: (Submission.Instance => Submission.Instance) => Submission => Submission = delta => s => {
    s.copy(instances = NonEmptyList(delta(s.instances.head), s.instances.tail))
  }

  val addStatusHistory: (Submission.Status) => Submission => Submission = newStatus => s => {
    require(Submission.Status.isLegalTransition(s.status, newStatus))
    changeLatestInstance(_.copy(statusHistory = newStatus :: s.latestInstance.statusHistory))(s)
  }

  val changeStatusHistory: (Submission.Status => Submission.Status) => Submission => Submission = delta => s => {
    val inStatus = s.latestInstance.statusHistory.head
    val outStatus = delta(inStatus)

    changeLatestInstance(
      _.copy(statusHistory = NonEmptyList(outStatus, s.latestInstance.statusHistory.tail))
    )(s)
  }

  val updateLatestAnswersTo: (Submission.AnswersToQuestions) => Submission => Submission = (newAnswers) => changeLatestInstance(_.copy(answersToQuestions = newAnswers))

  val decline: (DateTime, String, String) => Submission => Submission = (timestamp, name, reasons) => {
    val addDeclinedStatus = addStatusHistory(Status.Declined(timestamp, name, reasons))
    val addNewlyAnsweringInstance: Submission => Submission = (s) => addInstance(s.latestInstance.answersToQuestions, Status.Answering(timestamp, true))(s)
    
    addDeclinedStatus andThen addNewlyAnsweringInstance
  }

  val grant: (DateTime, String) => Submission => Submission = (timestamp, name) => addStatusHistory(Status.Granted(timestamp, name))

  val grantWithWarnings: (DateTime, String, String) => Submission => Submission = (timestamp, name, warnings) => {
    addStatusHistory(Status.GrantedWithWarnings(timestamp, name, warnings))
  }

  val submit: (DateTime, String) => Submission => Submission = (timestamp, requestedBy) => addStatusHistory(Status.Submitted(timestamp, requestedBy))

  
  sealed trait Status {
    def timestamp: DateTime
    
    def isOpenToAnswers = isCreated || isAnswering
    
    def canBeMarked = isAnsweredCompletely | isSubmitted | isDeclined | isGranted | isGrantedWithWarnings

    def isAnsweredCompletely = this match {
      case Submission.Status.Answering(_, completed) => completed
      case _                                         => false      
    }

    def isCreated = this match {
      case _ : Submission.Status.Created => true
      case _ => false      
    }

    def isAnswering = this match {
      case _ : Submission.Status.Answering => true
      case _ => false      
    }
    
    def isSubmitted = this match {
      case _ : Submission.Status.Submitted => true
      case _ => false      
    }

    def isGranted = this match {
      case _ : Submission.Status.Granted => true
      case _ => false      
    }

    def isGrantedWithWarnings = this match {
      case _ : Submission.Status.GrantedWithWarnings => true
      case _ => false          
    }

    def isDeclined = this match {
      case _ : Submission.Status.Declined => true
      case _ => false
    }
  }

  object Status {
    case class Declined(
      timestamp: DateTime,
      name: String,
      reasons: String
    ) extends Status
    
    case class Granted(
      timestamp: DateTime,
      name: String
    ) extends Status

    case class GrantedWithWarnings(
      timestamp: DateTime,
      name: String,
      warnings: String
    ) extends Status

    case class Submitted(
      timestamp: DateTime,
      requestedBy: String
    ) extends Status

    case class Answering(
      timestamp: DateTime,
      completed: Boolean
    ) extends Status

    case class Created(
      timestamp: DateTime,
      requestedBy: String
    ) extends Status

    def isLegalTransition(from: Submission.Status, to: Submission.Status): Boolean = (from, to) match {
      case (c: Created, a: Answering)               => true
      case (Answering(_, true), s: Submitted)       => true
      case (s: Submitted, d: Declined)              => true
      case (s: Submitted, g: Granted)               => true
      case (s: Submitted, w: GrantedWithWarnings)   => true
      case (w: GrantedWithWarnings, d: Declined)    => true   // ? Maybe
      case (w: GrantedWithWarnings, g: Granted)     => true   // ? Maybe
      case _                                        => false
    }
  }

  case class Instance(
    index: Int,
    answersToQuestions: Submission.AnswersToQuestions,
    statusHistory: NonEmptyList[Submission.Status]
  ) {
    lazy val status: Status = statusHistory.head
    lazy val isOpenToAnswers = status.isOpenToAnswers
    lazy val isAnsweredCompletely = status.isAnsweredCompletely

    lazy val isCreated = status.isCreated
    lazy val isAnswering = status.isAnswering
    lazy val isGranted = status.isGranted
    lazy val isGrantedWithWarnings = status.isGrantedWithWarnings
    lazy val isDeclined = status.isDeclined
    lazy val isSubmitted = status.isSubmitted
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

  lazy val status: Submission.Status = latestInstance.statusHistory.head
}


case class ExtendedSubmission(
  submission: Submission,
  questionnaireProgress: Map[QuestionnaireId, QuestionnaireProgress]
)

case class MarkedSubmission(
  submission: Submission,
  markedAnswers: Map[QuestionId, Mark]
) {
  lazy val isFail = markedAnswers.values.toList.contains(Fail) | markedAnswers.values.filter(_ == Warn).size >= 4
  lazy val isWarn = markedAnswers.values.toList.contains(Warn)
  lazy val isPass = !isWarn && !isFail
}

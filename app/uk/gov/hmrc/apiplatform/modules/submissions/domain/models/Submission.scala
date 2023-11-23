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

package uk.gov.hmrc.apiplatform.modules.submissions.domain.models

import java.time.LocalDateTime
import java.util.UUID
import cats.data.NonEmptyList
import play.api.libs.json.Format
import uk.gov.hmrc.apiplatform.modules.common.domain.models.ApplicationId

sealed trait QuestionnaireState

object QuestionnaireState {
  case object NotStarted    extends QuestionnaireState
  case object InProgress    extends QuestionnaireState
  case object NotApplicable extends QuestionnaireState
  case object Completed     extends QuestionnaireState

  def describe(state: QuestionnaireState): String = state match {
    case NotStarted    => "Not Started"
    case InProgress    => "In Progress"
    case NotApplicable => "Not Applicable"
    case Completed     => "Completed"
  }

  def isCompleted(state: QuestionnaireState): Boolean = state match {
    case NotStarted | InProgress => false
    case _                       => true
  }
}

case class QuestionnaireProgress(state: QuestionnaireState, questionsToAsk: List[Question.Id])

case class QuestionIdsOfInterest(
    applicationNameId: Question.Id,
    privacyPolicyId: Question.Id,
    privacyPolicyUrlId: Question.Id,
    termsAndConditionsId: Question.Id,
    termsAndConditionsUrlId: Question.Id,
    organisationUrlId: Question.Id,
    responsibleIndividualIsRequesterId: Question.Id,
    responsibleIndividualNameId: Question.Id,
    responsibleIndividualEmailId: Question.Id,
    identifyYourOrganisationId: Question.Id,
    serverLocationsId: Question.Id
  )

object Submission {
  type AnswersToQuestions = Map[Question.Id, ActualAnswer]

  case class Id(value: String) extends AnyVal {
    override def toString(): String = value
  }

  object Id {
    implicit val format: Format[Id] = play.api.libs.json.Json.valueFormat[Id]

    def random: Id = Id(UUID.randomUUID().toString())
  }

  val create: (
      String,
      Submission.Id,
      ApplicationId,
      LocalDateTime,
      NonEmptyList[GroupOfQuestionnaires],
      QuestionIdsOfInterest,
      AskWhen.Context
  ) => Submission = (requestedBy, id, applicationId, timestamp, groups, questionIdsOfInterest, context) => {
    val initialStatus    = Submission.Status.Created(timestamp, requestedBy)
    val initialInstances = NonEmptyList.of(Submission.Instance(0, Map.empty, NonEmptyList.of(initialStatus)))
    Submission(id, applicationId, timestamp, groups, questionIdsOfInterest, initialInstances, context)
  }

  val addInstance: (Submission.AnswersToQuestions, Submission.Status) => Submission => Submission = (answers, status) =>
    s => {
      val newInstance = Submission.Instance(s.latestInstance.index + 1, answers, NonEmptyList.of(status))
      s.copy(instances = newInstance :: s.instances)
    }

  val changeLatestInstance: (Submission.Instance => Submission.Instance) => Submission => Submission = delta =>
    s => {
      s.copy(instances = NonEmptyList(delta(s.instances.head), s.instances.tail))
    }

  val addStatusHistory: (Submission.Status) => Submission => Submission = newStatus =>
    s => {
      require(Submission.Status.isLegalTransition(s.status, newStatus))

      val currentHistory = s.latestInstance.statusHistory

      // Do not ADD if going from answering to answering - instead replace
      if ((s.status.isAnswering && newStatus.isAnswering)) {
        changeLatestInstance(_.copy(statusHistory = NonEmptyList(newStatus, currentHistory.tail)))(s)
      } else {
        changeLatestInstance(_.copy(statusHistory = newStatus :: currentHistory))(s)
      }
    }

  val updateLatestAnswersTo: (Submission.AnswersToQuestions) => Submission => Submission = (newAnswers) => changeLatestInstance(_.copy(answersToQuestions = newAnswers))

  val decline: (LocalDateTime, String, String) => Submission => Submission = (timestamp, name, reasons) => {
    val addDeclinedStatus                                   = addStatusHistory(Status.Declined(timestamp, name, reasons))
    val addNewlyAnsweringInstance: Submission => Submission = (s) => addInstance(s.latestInstance.answersToQuestions, Status.Answering(timestamp, true))(s)

    addDeclinedStatus andThen addNewlyAnsweringInstance
  }

  val grant: (LocalDateTime, String, Option[String], Option[String]) => Submission => Submission =
    (timestamp, name, comments, escalatedTo) => addStatusHistory(Status.Granted(timestamp, name, comments, escalatedTo))

  val grantWithWarnings: (LocalDateTime, String, String, Option[String]) => Submission => Submission = (timestamp, name, warnings, escalatedTo) => {
    addStatusHistory(Status.GrantedWithWarnings(timestamp, name, warnings, escalatedTo))
  }

  val fail: (LocalDateTime, String) => Submission => Submission = (timestamp, name) => addStatusHistory(Status.Failed(timestamp, name))

  val warnings: (LocalDateTime, String) => Submission => Submission = (timestamp, name) => addStatusHistory(Status.Warnings(timestamp, name))

  val pendingResponsibleIndividual: (LocalDateTime, String) => Submission => Submission = (timestamp, name) => addStatusHistory(Status.PendingResponsibleIndividual(timestamp, name))

  val submit: (LocalDateTime, String) => Submission => Submission = (timestamp, requestedBy) => addStatusHistory(Status.Submitted(timestamp, requestedBy))

  sealed trait Status {
    def timestamp: LocalDateTime

    def isOpenToAnswers: Boolean = isCreated || isAnswering

    def canBeMarked: Boolean = isAnsweredCompletely || isSubmitted || isDeclined || isGranted || isGrantedWithWarnings || isFailed || isWarnings || isPendingResponsibleIndividual

    def isAnsweredCompletely: Boolean = this match {
      case Submission.Status.Answering(_, completed) => completed
      case _                                         => false
    }

    def isReadOnly: Boolean = this match {
      case _: Submission.Status.Submitted                    => true
      case _: Submission.Status.Granted                      => true
      case _: Submission.Status.GrantedWithWarnings          => true
      case _: Submission.Status.Declined                     => true
      case _: Submission.Status.Failed                       => true
      case _: Submission.Status.Warnings                     => true
      case _: Submission.Status.PendingResponsibleIndividual => true
      case _                                                 => false
    }

    def isCreated: Boolean = this match {
      case _: Submission.Status.Created => true
      case _                            => false
    }

    def isAnswering: Boolean = this match {
      case _: Submission.Status.Answering => true
      case _                              => false
    }

    def isSubmitted: Boolean = this match {
      case _: Submission.Status.Submitted => true
      case _                              => false
    }

    def isGranted: Boolean = this match {
      case _: Submission.Status.Granted => true
      case _                            => false
    }

    def isGrantedWithWarnings: Boolean = this match {
      case _: Submission.Status.GrantedWithWarnings => true
      case _                                        => false
    }

    def isDeclined: Boolean = this match {
      case _: Submission.Status.Declined => true
      case _                             => false
    }

    def isFailed: Boolean = this match {
      case _: Submission.Status.Failed => true
      case _                           => false
    }

    def isWarnings: Boolean = this match {
      case _: Submission.Status.Warnings => true
      case _                             => false
    }

    def isPendingResponsibleIndividual: Boolean = this match {
      case _: Submission.Status.PendingResponsibleIndividual => true
      case _                                                 => false
    }
  }

  object Status {

    case class Declined(
        timestamp: LocalDateTime,
        name: String,
        reasons: String
      ) extends Status

    case class Granted(
        timestamp: LocalDateTime,
        name: String,
        comments: Option[String],
        escalatedTo: Option[String]
      ) extends Status

    case class GrantedWithWarnings(
        timestamp: LocalDateTime,
        name: String,
        warnings: String,
        escalatedTo: Option[String]
      ) extends Status

    case class Failed(
        timestamp: LocalDateTime,
        name: String
      ) extends Status

    case class Warnings(
        timestamp: LocalDateTime,
        name: String
      ) extends Status

    case class PendingResponsibleIndividual(
        timestamp: LocalDateTime,
        name: String
      ) extends Status

    case class Submitted(
        timestamp: LocalDateTime,
        requestedBy: String
      ) extends Status

    case class Answering(
        timestamp: LocalDateTime,
        completed: Boolean
      ) extends Status

    case class Created(
        timestamp: LocalDateTime,
        requestedBy: String
      ) extends Status

    def isLegalTransition(from: Submission.Status, to: Submission.Status): Boolean = (from, to) match {
      case (c: Created, a: Answering)                      => true
      case (Answering(_, true), s: Submitted)              => true
      case (a: Answering, b: Answering)                    => true
      case (s: Submitted, d: Declined)                     => true
      case (s: Submitted, g: Granted)                      => true
      case (s: Submitted, w: GrantedWithWarnings)          => true
      case (s: Submitted, f: Failed)                       => true
      case (s: Submitted, w: Warnings)                     => true
      case (s: Submitted, p: PendingResponsibleIndividual) => true
      case (p: PendingResponsibleIndividual, f: Failed)    => true
      case (p: PendingResponsibleIndividual, w: Warnings)  => true
      case (p: PendingResponsibleIndividual, g: Granted)   => true
      case (f: Failed, g: Granted)                         => true
      case (w: Warnings, g: Granted)                       => true
      case (w: GrantedWithWarnings, d: Declined)           => true // ? Maybe
      case (w: GrantedWithWarnings, g: Granted)            => true // ? Maybe
      case _                                               => false
    }
  }

  case class Instance(
      index: Int,
      answersToQuestions: Submission.AnswersToQuestions,
      statusHistory: NonEmptyList[Submission.Status]
    ) {
    lazy val status: Status = statusHistory.head

    lazy val isOpenToAnswers: Boolean = status.isOpenToAnswers
    lazy val isAnsweredCompletely: Boolean = status.isAnsweredCompletely

    lazy val isCreated: Boolean = status.isCreated
    lazy val isAnswering: Boolean = status.isAnswering
    lazy val isFailed: Boolean = status.isFailed
    lazy val isWarnings: Boolean = status.isWarnings
    lazy val isPendingResponsibleIndividual: Boolean = status.isPendingResponsibleIndividual
    lazy val isGranted: Boolean = status.isGranted
    lazy val isGrantedWithWarnings: Boolean = status.isGrantedWithWarnings
    lazy val isDeclined: Boolean = status.isDeclined
    lazy val isSubmitted: Boolean = status.isSubmitted
  }
}

case class Submission(
    id: Submission.Id,
    applicationId: ApplicationId,
    startedOn: LocalDateTime,
    groups: NonEmptyList[GroupOfQuestionnaires],
    questionIdsOfInterest: QuestionIdsOfInterest,
    instances: NonEmptyList[Submission.Instance],
    context: AskWhen.Context
  ) {
  lazy val allQuestionnaires: NonEmptyList[Questionnaire] = groups.flatMap(g => g.links)

  lazy val allQuestions: NonEmptyList[Question] = allQuestionnaires.flatMap(l => l.questions.map(_.question))

  def findQuestion(questionId: Question.Id): Option[Question] = allQuestions.find(q => q.id == questionId)

  def findQuestionnaireContaining(questionId: Question.Id): Option[Questionnaire] =
    allQuestionnaires.find(qn =>
      qn.questions.exists(qi =>
        qi.question.id == questionId
      )
    )

  lazy val latestInstance: Submission.Instance = instances.head

  lazy val status: Submission.Status = latestInstance.statusHistory.head
}

case class ExtendedSubmission(
    submission: Submission,
    questionnaireProgress: Map[Questionnaire.Id, QuestionnaireProgress]
  )

case class MarkedSubmission(
    submission: Submission,
    markedAnswers: Map[Question.Id, Mark]
  ) {
  lazy val isFail: Boolean = markedAnswers.values.toList.contains(Fail) | markedAnswers.values.filter(_ == Warn).size >= 4
  lazy val isWarn: Boolean = markedAnswers.values.toList.contains(Warn)
  lazy val isPass: Boolean = !isWarn && !isFail
}

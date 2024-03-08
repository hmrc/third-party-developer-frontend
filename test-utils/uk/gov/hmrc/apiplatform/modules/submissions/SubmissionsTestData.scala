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

package uk.gov.hmrc.apiplatform.modules.submissions

import java.time.Instant
import java.time.temporal.ChronoUnit.SECONDS
import scala.util.Random

import cats.data.NonEmptyList

import uk.gov.hmrc.apiplatform.modules.applications.submissions.domain.models.SubmissionId
import uk.gov.hmrc.apiplatform.modules.common.domain.models.ApplicationId
import uk.gov.hmrc.apiplatform.modules.common.services.ClockNow
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models.AskWhen.Context.Keys
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models._

trait StatusTestDataHelper extends FixedClock {

  implicit class StatusHistorySyntax(submission: Submission) {

    def hasCompletelyAnsweredWith(answers: Submission.AnswersToQuestions): Submission = {
      (
        Submission.addStatusHistory(Submission.Status.Answering(instant, true)) andThen
          Submission.updateLatestAnswersTo(answers)
      )(submission)
    }

    def hasCompletelyAnswered: Submission = {
      Submission.addStatusHistory(Submission.Status.Answering(instant, true))(submission)
    }

    def answeringWith(answers: Submission.AnswersToQuestions): Submission = {
      (
        Submission.addStatusHistory(Submission.Status.Answering(instant, false)) andThen
          Submission.updateLatestAnswersTo(answers)
      )(submission)
    }

    def answering: Submission = {
      Submission.addStatusHistory(Submission.Status.Answering(instant, false))(submission)
    }

    def submitted: Submission = {
      Submission.submit(instant, "bob@example.com")(submission)
    }
  }
}

trait ProgressTestDataHelper {

  implicit class ProgressSyntax(submission: Submission) {
    private val allQuestionnaireIds: NonEmptyList[Questionnaire.Id] = submission.allQuestionnaires.map(_.id)
    private val allQuestionIds                                      = submission.allQuestions.map(_.id)
    private def questionnaire(qId: Questionnaire.Id): Questionnaire = submission.allQuestionnaires.find(q => q.id == qId).get
    private def allQuestionIds(qId: Questionnaire.Id)               = questionnaire(qId).questions.map(_.question).map(_.id).toList

    private def incompleteQuestionnaireProgress(qId: Questionnaire.Id): QuestionnaireProgress    = QuestionnaireProgress(QuestionnaireState.InProgress, allQuestionIds(qId))
    private def completedQuestionnaireProgress(qId: Questionnaire.Id): QuestionnaireProgress     = QuestionnaireProgress(QuestionnaireState.Completed, allQuestionIds.toList)
    private def notStartedQuestionnaireProgress(qId: Questionnaire.Id): QuestionnaireProgress    = QuestionnaireProgress(QuestionnaireState.NotStarted, allQuestionIds.toList)
    private def notApplicableQuestionnaireProgress(qId: Questionnaire.Id): QuestionnaireProgress = QuestionnaireProgress(QuestionnaireState.NotApplicable, allQuestionIds.toList)

    def withIncompleteProgress(): ExtendedSubmission =
      ExtendedSubmission(submission, allQuestionnaireIds.map(i => (i -> incompleteQuestionnaireProgress(i))).toList.toMap)

    def withCompletedProgress(): ExtendedSubmission =
      ExtendedSubmission(submission, allQuestionnaireIds.map(i => (i -> completedQuestionnaireProgress(i))).toList.toMap)

    def withNotStartedProgress(): ExtendedSubmission =
      ExtendedSubmission(submission, allQuestionnaireIds.map(i => (i -> notStartedQuestionnaireProgress(i))).toList.toMap)

    def withNotApplicableProgress(): ExtendedSubmission =
      ExtendedSubmission(submission, allQuestionnaireIds.map(i => (i -> notApplicableQuestionnaireProgress(i))).toList.toMap)
  }
}

trait SubmissionsTestData extends QuestionBuilder with QuestionnaireTestData with ProgressTestDataHelper with StatusTestDataHelper {
  self: ClockNow =>

  val submissionId  = SubmissionId.random
  val applicationId = ApplicationId.random

  val standardContext: AskWhen.Context = Map(
    AskWhen.Context.Keys.IN_HOUSE_SOFTWARE -> "No",
    AskWhen.Context.Keys.VAT_OR_ITSA       -> "No"
  )

  val aSubmission = Submission.create("bob@example.com", submissionId, applicationId, instant, testGroups, testQuestionIdsOfInterest, standardContext)

  val altSubmissionId = SubmissionId.random
  require(altSubmissionId != submissionId)
  val altSubmission   = Submission.create("bob@example.com", altSubmissionId, applicationId, instant.plus(100, SECONDS), testGroups, testQuestionIdsOfInterest, standardContext)

  val completedSubmissionId = SubmissionId.random
  require(completedSubmissionId != submissionId)

  val completelyAnswerExtendedSubmission =
    aSubmission.copy(id = completedSubmissionId)
      .hasCompletelyAnsweredWith(answersToQuestions)
      .withCompletedProgress()

  val gatekeeperUserName = "gatekeeperUserName"
  val reasons            = "some reasons"

  val createdSubmission   = aSubmission
  val answeringSubmission = createdSubmission.answeringWith(answersToQuestions)
  val answeredSubmission  = createdSubmission.hasCompletelyAnsweredWith(answersToQuestions)
  val submittedSubmission = Submission.submit(instant, "bob@example.com")(answeredSubmission)
  val declinedSubmission  = Submission.decline(instant, gatekeeperUserName, reasons)(submittedSubmission)
  val grantedSubmission   = Submission.grant(instant, gatekeeperUserName, None, None)(submittedSubmission)

  def buildSubmissionWithQuestions(appId: ApplicationId = ApplicationId.random): Submission = {
    val subId = SubmissionId.random

    val question1               = yesNoQuestion(1)
    val questionRIRequester     = yesNoQuestion(2)
    val questionRIName          = textQuestion(3)
    val questionRIEmail         = textQuestion(4)
    val questionName            = textQuestion(5)
    val questionPrivacyUrl      = textQuestion(6)
    val questionTermsUrl        = textQuestion(7)
    val questionWeb             = textQuestion(8)
    val question2               = acknowledgementOnly(9)
    val question3               = multichoiceQuestion(10, "a1", "b", "c")
    val questionIdentifyOrg     = chooseOneOfQuestion(11, "a2", "b", "c")
    val questionPrivacy         = textQuestion(12)
    val questionTerms           = textQuestion(13)
    val questionServerLocations = multichoiceQuestion(14, "In the UK", "Outside the EEA with adequacy agreements")

    val questionnaire1 = Questionnaire(
      id = Questionnaire.Id.random,
      label = Questionnaire.Label("Questionnaire 1"),
      questions = NonEmptyList.of(
        QuestionItem(question1),
        QuestionItem(question2),
        QuestionItem(question3),
        QuestionItem(questionName),
        QuestionItem(questionPrivacy),
        QuestionItem(questionTerms),
        QuestionItem(questionWeb)
      )
    )

    val questionnaireGroups = NonEmptyList.of(
      GroupOfQuestionnaires(
        heading = "Group 1",
        links = NonEmptyList.of(
          questionnaire1
        )
      )
    )

    Submission.create(
      "bob@example.com",
      subId,
      appId,
      instant,
      questionnaireGroups,
      QuestionIdsOfInterest(
        questionName.id,
        questionPrivacy.id,
        questionPrivacyUrl.id,
        questionTerms.id,
        questionTermsUrl.id,
        questionWeb.id,
        questionRIRequester.id,
        questionRIName.id,
        questionRIEmail.id,
        questionIdentifyOrg.id,
        questionServerLocations.id
      ),
      standardContext
    )
  }

  private def buildAnsweredSubmission(fullyAnswered: Boolean)(submission: Submission): Submission = {

    def passAnswer(question: Question): ActualAnswer = {
      question match {
        case Question.TextQuestion(id, wording, statement, _, _, _, _, absence, _)                      => ActualAnswer.TextAnswer("some random text")
        case Question.ChooseOneOfQuestion(id, wording, statement, _, _, _, marking, absence, _)         => ActualAnswer.SingleChoiceAnswer(marking.filter {
            case (pa, Mark.Pass) => true; case _ => false
          }.head._1.value)
        case Question.MultiChoiceQuestion(id, wording, statement, _, _, _, marking, absence, _)         => ActualAnswer.MultipleChoiceAnswer(Set(marking.filter {
            case (pa, Mark.Pass) => true; case _ => false
          }.head._1.value))
        case Question.AcknowledgementOnly(id, wording, statement)                                       => ActualAnswer.NoAnswer
        case Question.YesNoQuestion(id, wording, statement, _, _, _, yesMarking, noMarking, absence, _) =>
          if (yesMarking == Mark.Pass) ActualAnswer.SingleChoiceAnswer("Yes") else ActualAnswer.SingleChoiceAnswer("No")
      }
    }

    val answerQuestions = submission.allQuestions.toList.drop(if (fullyAnswered) 0 else 1)
    val answers         = answerQuestions.map(q => (q.id -> passAnswer(q))).toMap

    if (fullyAnswered) {
      submission.hasCompletelyAnsweredWith(answers)
    } else {
      submission.answeringWith(answers)
    }
  }

  def buildPartiallyAnsweredSubmission(submission: Submission = buildSubmissionWithQuestions()): Submission =
    buildAnsweredSubmission(false)(submission)

  def buildFullyAnsweredSubmission(applicationId: ApplicationId = ApplicationId.random, submission: Submission = buildSubmissionWithQuestions(applicationId)): Submission =
    buildAnsweredSubmission(true)(submission)

  def allFirstQuestions(questionnaires: NonEmptyList[Questionnaire]): Map[Questionnaire.Id, Question.Id] =
    questionnaires.map { qn =>
      (qn.id, qn.questions.head.question.id)
    }
      .toList
      .toMap

  val simpleContext = Map(Keys.IN_HOUSE_SOFTWARE -> "Yes", Keys.VAT_OR_ITSA -> "No")
  val soldContext   = Map(Keys.IN_HOUSE_SOFTWARE -> "No", Keys.VAT_OR_ITSA -> "No")
  val vatContext    = Map(Keys.IN_HOUSE_SOFTWARE -> "Yes", Keys.VAT_OR_ITSA -> "Yes")
}

trait AnsweringQuestionsHelper {

  def answerForQuestion(desiredMark: Mark)(question: Question): Map[Question.Id, Option[ActualAnswer]] = {
    val answers: List[Option[ActualAnswer]] = question match {

      case Question.YesNoQuestion(id, _, _, _, _, _, yesMarking, noMarking, absence, _) =>
        (if (yesMarking == desiredMark) Some(ActualAnswer.SingleChoiceAnswer("Yes")) else None) ::
          (if (noMarking == desiredMark) Some(ActualAnswer.SingleChoiceAnswer("No")) else None) ::
          (absence.flatMap(a => if (a._2 == desiredMark) Some(ActualAnswer.NoAnswer) else None)) ::
          List.empty[Option[ActualAnswer]]

      case Question.ChooseOneOfQuestion(id, _, _, _, _, _, marking, absence, _) => {
        marking.map {
          case (pa, mark) => Some(ActualAnswer.SingleChoiceAnswer(pa.value))
          case _          => None
        }
          .toList ++
          List(absence.flatMap(a => if (a._2 == desiredMark) Some(ActualAnswer.NoAnswer) else None))
      }

      case Question.TextQuestion(id, _, _, _, _, _, _, absence, _) =>
        if (desiredMark == Mark.Pass)
          Some(ActualAnswer.TextAnswer(Random.nextString(Random.nextInt(25) + 1))) ::
            absence.flatMap(a => if (a._2 == desiredMark) Some(ActualAnswer.NoAnswer) else None) ::
            List.empty[Option[ActualAnswer]]
        else
          List(Some(ActualAnswer.NoAnswer)) // Cos we can't do anything else

      case Question.AcknowledgementOnly(id, _, _) => List(Some(ActualAnswer.NoAnswer))

      case Question.MultiChoiceQuestion(id, _, _, _, _, _, marking, absence, _) =>
        marking.map {
          case (pa, mark) if (mark == desiredMark) => Some(ActualAnswer.MultipleChoiceAnswer(Set(pa.value)))
          case _                                   => None
        }
          .toList ++
          List(absence.flatMap(a => if (a._2 == desiredMark) Some(ActualAnswer.NoAnswer) else None))
    }

    Map(question.id -> Random.shuffle(
      answers.collect {
        case Some(a) => a
      }
    ).headOption)
  }

  def answersForQuestionnaire(desiredMark: Mark)(questionnaire: Questionnaire): Map[Question.Id, ActualAnswer] = {
    questionnaire.questions
      .toList
      .map(qi => qi.question)
      .flatMap(x => answerForQuestion(desiredMark)(x))
      .collect {
        case (id, Some(a)) => id -> a
      }
      .toMap
  }

  def answersForGroups(desiredMark: Mark)(groups: NonEmptyList[GroupOfQuestionnaires]): Map[Question.Id, ActualAnswer] = {
    groups
      .flatMap(g => g.links)
      .toList
      .flatMap(qn => answersForQuestionnaire(desiredMark)(qn))
      .toMap
  }
}

trait MarkedSubmissionsTestData extends SubmissionsTestData with AnsweringQuestionsHelper with FixedClock {

  val markedAnswers: Map[Question.Id, Mark] = Map(
    (DevelopmentPractices.question1.id             -> Mark.Pass),
    (DevelopmentPractices.question2.id             -> Mark.Fail),
    (DevelopmentPractices.question3.id             -> Mark.Warn),
    (OrganisationDetails.question1.id              -> Mark.Pass),
    (OrganisationDetails.questionRI1.id            -> Mark.Pass),
    (OrganisationDetails.questionRI2.id            -> Mark.Pass),
    (CustomersAuthorisingYourSoftware.question3.id -> Mark.Pass),
    (CustomersAuthorisingYourSoftware.question4.id -> Mark.Pass),
    (CustomersAuthorisingYourSoftware.question6.id -> Mark.Fail)
  )

  val markedSubmission = MarkedSubmission(submittedSubmission, markedAnswers)

  def markAsPass(now: Instant = instant, requestedBy: String = "bob@example.com")(submission: Submission): MarkedSubmission = {
    val answers = answersForGroups(Mark.Pass)(submission.groups)
    val marks   = answers.map { case (q, a) => q -> Mark.Pass }

    MarkedSubmission(submission.hasCompletelyAnsweredWith(answers), marks)
  }
}

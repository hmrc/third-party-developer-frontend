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

package uk.gov.hmrc.apiplatform.modules.submissions

import uk.gov.hmrc.apiplatform.modules.submissions.domain.models._
import uk.gov.hmrc.time.DateTimeUtils
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models.AskWhen.Context.Keys
import cats.data.NonEmptyList
import org.joda.time.DateTime
import scala.util.Random
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.ApplicationId

trait StatusTestDataHelper {
  implicit class StatusHistorySyntax(submission: Submission) {
    def hasCompletelyAnsweredWith(answers: Submission.AnswersToQuestions): Submission = {
      (
        Submission.addStatusHistory(Submission.Status.Answering(DateTimeUtils.now, true)) andThen
        Submission.updateLatestAnswersTo(answers)
      )(submission)
    }

    def hasCompletelyAnswered: Submission = {
      Submission.addStatusHistory(Submission.Status.Answering(DateTimeUtils.now, true))(submission)
    }
    
    def answeringWith(answers: Submission.AnswersToQuestions): Submission = {
      (
        Submission.addStatusHistory(Submission.Status.Answering(DateTimeUtils.now, false)) andThen
        Submission.updateLatestAnswersTo(answers)
      )(submission)
    }

    def answering: Submission = {
      Submission.addStatusHistory(Submission.Status.Answering(DateTimeUtils.now, false))(submission)
    }
    
    def submitted: Submission = {
      Submission.submit(DateTimeUtils.now, "bob@example.com")(submission)
    }
  }
}

trait ProgressTestDataHelper {
  
    implicit class ProgressSyntax(submission: Submission) {
      private val allQuestionnaireIds: NonEmptyList[QuestionnaireId] = submission.allQuestionnaires.map(_.id)
      private val allQuestionIds = submission.allQuestions.map(_.id)
      private def questionnaire(qId: QuestionnaireId): Questionnaire = submission.allQuestionnaires.find(q => q.id == qId).get
      private def allQuestionIds(qId: QuestionnaireId) = questionnaire(qId).questions.map(_.question).map(_.id).toList

      private def incompleteQuestionnaireProgress(qId: QuestionnaireId): QuestionnaireProgress = QuestionnaireProgress(QuestionnaireState.InProgress, allQuestionIds(qId))
      private def completedQuestionnaireProgress(qId: QuestionnaireId): QuestionnaireProgress = QuestionnaireProgress(QuestionnaireState.Completed, allQuestionIds.toList)
      private def notStartedQuestionnaireProgress(qId: QuestionnaireId): QuestionnaireProgress = QuestionnaireProgress(QuestionnaireState.NotStarted, allQuestionIds.toList)
      private def notApplicableQuestionnaireProgress(qId: QuestionnaireId): QuestionnaireProgress = QuestionnaireProgress(QuestionnaireState.NotApplicable, allQuestionIds.toList)

      def withIncompleteProgress(): ExtendedSubmission =
        ExtendedSubmission(submission, allQuestionnaireIds.map(i => (i -> incompleteQuestionnaireProgress(i))).toList.toMap)
        
      def withCompletedProgresss(): ExtendedSubmission =
        ExtendedSubmission(submission, allQuestionnaireIds.map(i => (i -> completedQuestionnaireProgress(i))).toList.toMap)

      def withNotStartedProgresss(): ExtendedSubmission =
        ExtendedSubmission(submission, allQuestionnaireIds.map(i => (i -> notStartedQuestionnaireProgress(i))).toList.toMap)

      def withNotApplicableProgresss(): ExtendedSubmission =
        ExtendedSubmission(submission, allQuestionnaireIds.map(i => (i -> notApplicableQuestionnaireProgress(i))).toList.toMap)
    }
}
trait SubmissionsTestData extends QuestionBuilder with QuestionnaireTestData with ProgressTestDataHelper with StatusTestDataHelper {

  val submissionId = Submission.Id.random
  val applicationId = ApplicationId.random
  
  val now = DateTimeUtils.now

  val aSubmission = Submission.create("bob@example.com", submissionId, applicationId, now, testGroups, testQuestionIdsOfInterest)

  val altSubmissionId = Submission.Id.random
  require(altSubmissionId != submissionId)
  val altSubmission = Submission.create("bob@example.com", altSubmissionId, applicationId, now.plusSeconds(100), testGroups, testQuestionIdsOfInterest)

  val completedSubmissionId = Submission.Id.random
  require(completedSubmissionId != submissionId)

  val completelyAnswerExtendedSubmission = 
      aSubmission.copy(id = completedSubmissionId)
      .hasCompletelyAnsweredWith(answersToQuestions)
      .withCompletedProgresss

  val gatekeeperUserName = "gatekeeperUserName"
  val reasons = "some reasons"

  val createdSubmission = aSubmission
  val answeringSubmission = createdSubmission.answeringWith(answersToQuestions)
  val answeredSubmission = createdSubmission.hasCompletelyAnsweredWith(answersToQuestions)
  val submittedSubmission = Submission.submit(now, "bob@example.com")(answeredSubmission)
  val declinedSubmission = Submission.decline(now, gatekeeperUserName, reasons)(submittedSubmission)
  val grantedSubmission = Submission.grant(now, gatekeeperUserName)(submittedSubmission)

  def buildSubmissionWithQuestions(): Submission = {
    val subId = Submission.Id.random
    val appId = ApplicationId.random

    val question1 = yesNoQuestion(1)
    val questionRIName = textQuestion(2)
    val questionRIEmail = textQuestion(3)
    val questionName = textQuestion(4)
    val questionPrivacy = textQuestion(5)
    val questionTerms = textQuestion(6)
    val questionWeb = textQuestion(7)
    val question2 = acknowledgementOnly(8)
    val question3 = multichoiceQuestion(9, "a", "b", "c")
    val questionIdentifyOrg = chooseOneOfQuestion(10, "a", "b", "c")
    
    val questionnaire1 = Questionnaire(
        id = QuestionnaireId.random,
        label = Label("Questionnaire 1"),
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

    Submission.create("bob@example.com", subId, appId, DateTimeUtils.now, questionnaireGroups, QuestionIdsOfInterest(questionName.id, questionPrivacy.id, questionTerms.id, questionWeb.id, questionRIName.id, questionRIEmail.id, questionIdentifyOrg.id))
  }

  private def buildAnsweredSubmission(fullyAnswered: Boolean)(submission: Submission): Submission = {

    def passAnswer(question: Question): ActualAnswer = {
      question match {
        case TextQuestion(id, wording, statement, absence) => TextAnswer("some random text")
        case ChooseOneOfQuestion(id, wording, statement, marking, absence) => SingleChoiceAnswer(marking.filter { case (pa, Pass) => true; case _ => false }.head._1.value)
        case MultiChoiceQuestion(id, wording, statement, marking, absence) => MultipleChoiceAnswer(Set(marking.filter { case (pa, Pass) => true; case _ => false }.head._1.value))
        case AcknowledgementOnly(id, wording, statement) => NoAnswer
        case YesNoQuestion(id, wording, statement, yesMarking, noMarking, absence) => if(yesMarking == Pass) SingleChoiceAnswer("Yes") else SingleChoiceAnswer("No")
      }
    }
    
    val answerQuestions = submission.allQuestions.toList.drop(if(fullyAnswered) 0 else 1)
    val answers = answerQuestions.map(q => (q.id -> passAnswer(q))).toMap

    if(fullyAnswered) {
      submission.hasCompletelyAnsweredWith(answers)
    } else {
      submission.answeringWith(answers)
    }
  }

  def buildPartiallyAnsweredSubmission(submission: Submission = buildSubmissionWithQuestions()): Submission = 
    buildAnsweredSubmission(false)(submission)

  def buildFullyAnsweredSubmission(submission: Submission = buildSubmissionWithQuestions()): Submission =
    buildAnsweredSubmission(true)(submission)


  def allFirstQuestions(questionnaires: NonEmptyList[Questionnaire]): Map[QuestionnaireId, QuestionId] =
    questionnaires.map { qn =>
        (qn.id, qn.questions.head.question.id)
    }
    .toList
    .toMap
  
  val simpleContext = Map(Keys.IN_HOUSE_SOFTWARE -> "Yes", Keys.VAT_OR_ITSA -> "No")
  val soldContext = Map(Keys.IN_HOUSE_SOFTWARE -> "No", Keys.VAT_OR_ITSA -> "No")
  val vatContext = Map(Keys.IN_HOUSE_SOFTWARE -> "Yes", Keys.VAT_OR_ITSA -> "Yes")
}

trait AnsweringQuestionsHelper {
    def answerForQuestion(desiredMark: Mark)(question: Question): Map[QuestionId, Option[ActualAnswer]] = {
      val answers: List[Option[ActualAnswer]] = question match {

      case YesNoQuestion(id, _, _, yesMarking, noMarking, absence) =>
        (if(yesMarking == desiredMark) Some(SingleChoiceAnswer("Yes")) else None) ::
        (if(noMarking == desiredMark) Some(SingleChoiceAnswer("No")) else None) ::
        (absence.flatMap(a => if(a._2 == desiredMark) Some(NoAnswer) else None)) ::
        List.empty[Option[ActualAnswer]]

      case ChooseOneOfQuestion(id, _, _, marking, absence) => {
        marking.map {
          case (pa, mark) => Some(SingleChoiceAnswer(pa.value))
          case _ => None
        }
        .toList ++
        List(absence.flatMap(a => if(a._2 == desiredMark) Some(NoAnswer) else None))
      }

      case TextQuestion(id, _, _, absence) => 
        if(desiredMark == Pass)
          Some(TextAnswer(Random.nextString(Random.nextInt(25)+1))) ::
          absence.flatMap(a => if(a._2 == desiredMark) Some(NoAnswer) else None) ::
          List.empty[Option[ActualAnswer]]
        else
          List(Some(NoAnswer))  // Cos we can't do anything else

      case AcknowledgementOnly(id, _, _) => List(Some(NoAnswer))

      case MultiChoiceQuestion(id, _, _, marking, absence) => 
        marking.map {
          case (pa, mark) if(mark == desiredMark) => Some(MultipleChoiceAnswer(Set(pa.value)))
          case _ => None
        }
        .toList ++
        List(absence.flatMap(a => if(a._2 == desiredMark) Some(NoAnswer) else None))
    }

    Map(question.id -> Random.shuffle(
      answers.collect {
        case Some(a) => a
      }
    ).headOption)
  }

  def answersForQuestionnaire(desiredMark: Mark)(questionnaire: Questionnaire): Map[QuestionId, ActualAnswer] = {
    questionnaire.questions
    .toList
    .map(qi => qi.question)
    .flatMap(x => answerForQuestion(desiredMark)(x))
    .collect {
      case (id, Some(a)) => id -> a
    }
    .toMap
  }

  def answersForGroups(desiredMark: Mark)(groups: NonEmptyList[GroupOfQuestionnaires]): Map[QuestionId, ActualAnswer] = {
    groups
    .flatMap(g => g.links)
    .toList
    .flatMap(qn => answersForQuestionnaire(desiredMark)(qn))
    .toMap
  }
}
trait MarkedSubmissionsTestData extends SubmissionsTestData with AnsweringQuestionsHelper {
  val markedAnswers: Map[QuestionId, Mark] = Map(
    (DevelopmentPractices.question1.id -> Pass),
    (DevelopmentPractices.question2.id -> Fail),
    (DevelopmentPractices.question3.id -> Warn),
    (OrganisationDetails.question1.id -> Pass),
    (OrganisationDetails.questionRI1.id -> Pass),
    (OrganisationDetails.questionRI2.id -> Pass),
    (CustomersAuthorisingYourSoftware.question3.id -> Pass),
    (CustomersAuthorisingYourSoftware.question4.id -> Pass),
    (CustomersAuthorisingYourSoftware.question5.id -> Fail)
  )

  val markedSubmission = MarkedSubmission(submittedSubmission, markedAnswers)

  def markAsPass(now: DateTime = DateTimeUtils.now, requestedBy: String = "bob@example.com")(submission: Submission): MarkedSubmission = {
    val answers = answersForGroups(Pass)(submission.groups)
    val marks = answers.map { case (q,a) => q -> Pass }

    MarkedSubmission(submission.hasCompletelyAnsweredWith(answers), marks)
  }
}
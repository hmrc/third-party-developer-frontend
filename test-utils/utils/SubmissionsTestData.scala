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

package utils

import modules.submissions.domain.models._
import uk.gov.hmrc.time.DateTimeUtils
import cats.data.NonEmptyList
import domain.models.applications.ApplicationId

trait SubmissionsTestData {
  object DevelopmentPractices {
    val question1 = YesNoQuestion(
      QuestionId("653d2ee4-09cf-46a0-bc73-350a385ae860"),
      Wording("Do your development practices follow our guidance?"),
      Statement(
        CompoundFragment(
          StatementText("You must develop software following our"),
          StatementLink("development practices (opens in a new tab)", "http://www.google.com"),
          StatementText(".")
        )
      )
    )

    val question2 = YesNoQuestion(
      QuestionId("6139f57d-36ab-4338-85b3-a2079d6cf376"),
      Wording("Does your error handling meet our specification?"),
      Statement(
        CompoundFragment(
          StatementText("We will check for evidence that you comply with our"),
          StatementLink("error handling specification (opens in new tab)", "http://www.google.com"),
          StatementText(".")
        )
      )
    )
      
    val question3 = YesNoQuestion(
      QuestionId("3c5cd29d-bec2-463f-8593-cd5412fab1e5"),
      Wording("Does your software meet accessibility standards?"),
      Statement(
        CompoundFragment(
          StatementText("Web-based software must meet level AA of the"),
          StatementLink("Web Content Accessibility Guidelines (WCAG) (opens in new tab)", "http://www.google.com"),
          StatementText(". Desktop software should follow equivalent offline standards.")
        )
      )
    )

    val questionnaire = Questionnaire(
      id = QuestionnaireId("796336a5-f7b4-4dad-8003-a818e342cbb4"),
      label = Label("Development practices"),
      questions = NonEmptyList.of(
        QuestionItem(question1), 
        QuestionItem(question2), 
        QuestionItem(question3)
      )
    )
  }
    
  object BusinessDetails {
    val question1 = YesNoQuestion(
      QuestionId("62a12d00-e64a-4386-8418-dfb82e8ef676"),
      Wording("Do your development practices follow our guidance?"),
      Statement(
        CompoundFragment(
          StatementText("You must develop software following our"),
          StatementLink("development practices (opens in a new tab)", "http://www.google.com")
        )
      )
    )

    val questionnaire = Questionnaire(
      id = QuestionnaireId("ac69b129-524a-4d10-89a5-7bfa46ed95c7"),
      label = Label("Business details"),
      questions = NonEmptyList.of(
        QuestionItem(question1)
      )
    )
  }

  val activeQuestionnaireGroupings = 
    NonEmptyList.of(
      GroupOfQuestionnaires(
        heading = "Your processes",
        links = NonEmptyList.of(
          DevelopmentPractices.questionnaire
        )            
      ),
      GroupOfQuestionnaires(
        heading = "Your details",
        links = NonEmptyList.of(
          BusinessDetails.questionnaire
        )
      )
    )

  val questionnaire = DevelopmentPractices.questionnaire
  val questionnaireId = questionnaire.id
  val question = questionnaire.questions.head.question
  val questionId = question.id
  val question2Id = questionnaire.questions.tail.head.question.id
  val questionnaireAlt = BusinessDetails.questionnaire
  val questionnaireAltId = questionnaireAlt.id
  val questionAltId = questionnaireAlt.questions.head.question.id

  val submissionId = SubmissionId.random
  val applicationId = ApplicationId.random

  def firstQuestion(questionnaire: Questionnaire) = questionnaire.questions.head.question.id

  import AsIdsHelpers._
  val initialProgress = List(DevelopmentPractices.questionnaire, BusinessDetails.questionnaire).map(q => q.id -> QuestionnaireProgress(NotStarted, q.questions.asIds)).toMap

  val submission = Submission(submissionId, applicationId, DateTimeUtils.now, activeQuestionnaireGroupings, Map.empty)

  val extendedSubmission = ExtendedSubmission(submission, initialProgress)
  
  val altSubmissionId = SubmissionId.random
  require(altSubmissionId != submissionId)
  val altSubmission = Submission(altSubmissionId, applicationId, DateTimeUtils.now.plusMillis(100), activeQuestionnaireGroupings, Map.empty)

  val altExtendedSubmission = ExtendedSubmission(altSubmission, initialProgress)

  def allFirstQuestions(questionnaires: NonEmptyList[Questionnaire]): Map[QuestionnaireId, QuestionId] =
    questionnaires.map { qn =>
        (qn.id, qn.questions.head.question.id)
    }
    .toList
    .toMap
  
  object DeriveContext {
    object Keys {
      val VAT_OR_ITSA = "VAT_OR_ITSA"
      val IN_HOUSE_SOFTWARE = "IN_HOUSE_SOFTWARE" // Stored on Application
    }
  }

  val simpleContext = Map(DeriveContext.Keys.IN_HOUSE_SOFTWARE -> "Yes", DeriveContext.Keys.VAT_OR_ITSA -> "No")
  val soldContext = Map(DeriveContext.Keys.IN_HOUSE_SOFTWARE -> "No", DeriveContext.Keys.VAT_OR_ITSA -> "No")
  val vatContext = Map(DeriveContext.Keys.IN_HOUSE_SOFTWARE -> "Yes", DeriveContext.Keys.VAT_OR_ITSA -> "Yes")
}

object SubmissionsTestData extends SubmissionsTestData

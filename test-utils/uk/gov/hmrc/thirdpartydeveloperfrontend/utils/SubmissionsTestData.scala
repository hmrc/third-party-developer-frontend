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

package uk.gov.hmrc.thirdpartydeveloperfrontend.utils

import uk.gov.hmrc.apiplatform.modules.submissions.domain.models._
import uk.gov.hmrc.time.DateTimeUtils
import cats.data.NonEmptyList
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.ApplicationId
import scala.collection.immutable.ListMap

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
      ),
      yesMarking = Pass,
      noMarking = Warn
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
      ),
      yesMarking = Pass,
      noMarking = Fail
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
      ),
      yesMarking = Pass,
      noMarking = Warn
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
    
  object CustomersAuthorisingYourSoftware {
    val question1 = AcknowledgementOnly(
      QuestionId("95da25e8-af3a-4e05-a621-4a5f4ca788f6"),
      Wording("Customers authorising your software"),
      Statement(
        List(
          StatementText("Your customers will see the information you provide here when they authorise your software to interact with HMRC."),
          StatementText("Before you continue, you will need:"),
          StatementBullets(
            List(
              StatementText("the name of your software"),
              StatementText("the location of your servers which store customer data"),
              StatementText("a link to your privacy policy"),
              StatementText("a link to your terms and conditions")
            )
          )
        )
      )
    )

    val question2 = TextQuestion(
      QuestionId("4d5a41c8-8727-4d09-96c0-e2ce1bc222d3"),
      Wording("Confirm the name of your software"),
      Statement(
        List(
          StatementText("We show this name to your users when they authorise your software to interact with HMRC."),
          CompoundFragment(
            StatementText("It must comply with our "),
            StatementLink("naming guidelines (opens in a new tab)", "https://developer.service.hmrc.gov.uk/api-documentation/docs/using-the-hub/name-guidelines"),
            StatementText(".")            
          ),
          StatementText("Application name")
        )
      )
    )

    val question3 = MultiChoiceQuestion(
      QuestionId("57d706ad-c0b8-462b-a4f8-90e7aa58e57a"),
      Wording("Where are your servers that store customer information?"),
      Statement(
        StatementText("Select all that apply.")
      ),
      ListMap(
        (PossibleAnswer("In the UK") -> Pass),
        (PossibleAnswer("In the European Economic Area") -> Pass),
        (PossibleAnswer("Outside the European Economic Area") -> Warn)
      )
    )

    val question4 = TextQuestion(
      QuestionId("c0e4b068-23c9-4d51-a1fa-2513f50e428f"),
      Wording("Give us your privacy policy URL"),
      Statement(
        List(
          StatementText("Include the policy which covers the software you are requesting production credentials for."),
          StatementText("For example https://example.com/privacy-policy")
        )
      ),
      Some(("I don't have a privacy policy", Fail))
    )
    
    val question5 = TextQuestion(
      QuestionId("0a6d6973-c49a-49c3-93ff-de58daa1b90c"),
      Wording("Give us your terms and conditions URL"),
      Statement(
        List(
          StatementText("Your terms and conditions should cover the software you are requesting production credentials for."),
          StatementText("For example https://example.com/terms-conditions")
        )
      ),
      Some(("I don't have terms and conditions", Fail))
    )
    
    val questionnaire = Questionnaire(
      id = QuestionnaireId("3a7f3369-8e28-447c-bd47-efbabeb6d93f"),
      label = Label("Customers authorising your software"),
      questions = NonEmptyList.of(
        QuestionItem(question1),
        QuestionItem(question2),
        QuestionItem(question3, AskWhenContext(DeriveContext.Keys.IN_HOUSE_SOFTWARE, "No")),
        QuestionItem(question4),
        QuestionItem(question5)
      )
    )
  }

  object OrganisationDetails {
    val question1 = TextQuestion(
      QuestionId("b9dbf0a5-e72b-4c89-a735-26f0858ca6cc"),
      Wording("Give us your organisation's website URL"),
      Statement(
        List(
          StatementText("For example https://example.com")
        )
      ),
      Some(("My organisation doesn't have a website", Fail))
    )

    val questionnaire = Questionnaire(
      id = QuestionnaireId("ac69b129-524a-4d10-89a5-7bfa46ed95c7"),
      label = Label("Organisation details"),
      questions = NonEmptyList.of(
        QuestionItem(question1)
      )
    )
  }

  val activeQuestionnaireGroupings = 
    NonEmptyList.of(
      GroupOfQuestionnaires(
        heading = "About your processes",
        links = NonEmptyList.of(
          DevelopmentPractices.questionnaire
        )            
      ),
      GroupOfQuestionnaires(
        heading = "About your software",
        links = NonEmptyList.of(
          CustomersAuthorisingYourSoftware.questionnaire,
        )
      ),
      GroupOfQuestionnaires(
        heading = "About your organisation",
        links = NonEmptyList.of(
          OrganisationDetails.questionnaire
        )
      )
    )

  val questionnaire = DevelopmentPractices.questionnaire
  val questionnaireId = questionnaire.id
  val question = questionnaire.questions.head.question
  val questionId = question.id
  val question2Id = questionnaire.questions.tail.head.question.id
  val questionnaireAlt = OrganisationDetails.questionnaire
  val questionnaireAltId = questionnaireAlt.id
  val questionAltId = questionnaireAlt.questions.head.question.id

  val submissionId = Submission.Id.random
  val applicationId = ApplicationId.random

  def firstQuestion(questionnaire: Questionnaire) = questionnaire.questions.head.question.id

  import AsIdsHelpers._
  val initialProgress = List(DevelopmentPractices.questionnaire, CustomersAuthorisingYourSoftware.questionnaire, OrganisationDetails.questionnaire).map(q => q.id -> QuestionnaireProgress(QuestionnaireState.NotStarted, q.questions.asIds)).toMap
  val completedProgress = List(DevelopmentPractices.questionnaire, CustomersAuthorisingYourSoftware.questionnaire, OrganisationDetails.questionnaire).map(q => q.id -> QuestionnaireProgress(QuestionnaireState.Completed, q.questions.asIds)).toMap
  val notApplicableProgress = (
    List(OrganisationDetails.questionnaire).map(q => q.id -> QuestionnaireProgress(QuestionnaireState.NotStarted, q.questions.asIds)) ++ 
    List(CustomersAuthorisingYourSoftware.questionnaire).map(q => q.id -> QuestionnaireProgress(QuestionnaireState.NotStarted, q.questions.asIds)) ++ 
    List(DevelopmentPractices.questionnaire).map(q => q.id -> QuestionnaireProgress(QuestionnaireState.NotApplicable, q.questions.asIds))
  ).toMap

  val questionIdsOfInterest = QuestionIdsOfInterest(
    applicationNameId             = CustomersAuthorisingYourSoftware.question2.id,
    privacyPolicyUrlId            = CustomersAuthorisingYourSoftware.question4.id,
    termsAndConditionsUrlId       = CustomersAuthorisingYourSoftware.question5.id,
    organisationUrlId             = OrganisationDetails.question1.id
  )

  val initialStatus = Submission.Status.Created(DateTimeUtils.now, "user@example.com")
  val initialInstances = NonEmptyList.of(Submission.Instance(0, Map.empty, NonEmptyList.of(initialStatus)))
  val submission = Submission(submissionId, applicationId, DateTimeUtils.now, activeQuestionnaireGroupings, questionIdsOfInterest, initialInstances)

  val extendedSubmission = ExtendedSubmission(submission, initialProgress)
  
  val altSubmissionId = Submission.Id.random
  require(altSubmissionId != submissionId)
  val altSubmission = Submission(altSubmissionId, applicationId, DateTimeUtils.now.plusMillis(100), activeQuestionnaireGroupings, questionIdsOfInterest, initialInstances)

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

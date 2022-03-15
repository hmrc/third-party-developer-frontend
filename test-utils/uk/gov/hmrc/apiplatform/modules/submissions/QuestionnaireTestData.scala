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
import cats.data.NonEmptyList
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models.AskWhen.Context.Keys

import scala.collection.immutable.ListMap

trait QuestionnaireTestData {
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
    
  object OrganisationDetails {
    val questionRI1 = TextQuestion(
      QuestionId("36b7e670-83fc-4b31-8f85-4d3394908495"),
      Wording("What is the name of your responsible individual"),
      
      Statement(
        List(
          StatementText("The responsible individual:"),
          CompoundFragment(
            StatementText("ensures your software meets our "),
            StatementLink("terms of use", "/api-documentation/docs/terms-of-use")
          ),
          CompoundFragment(
            StatementText("understands the "),
            StatementLink("consequences of not meeting the terms of use", "/api-documentation/docs/terms-of-use")
          )
        )
      )
    )
    val questionRI2 = TextQuestion(
      QuestionId("fb9b8036-cc88-4f4e-ad84-c02caa4cebae"),
      Wording("What is the email address of your responsible individual"),
      Statement(
        List(
          StatementText("The responsible individual:"),
          CompoundFragment(
            StatementText("ensures your software meets our "),
            StatementLink("terms of use", "/api-documentation/docs/terms-of-use")
          ),
          CompoundFragment(
            StatementText("understands the "),
            StatementLink("consequences of not meeting the terms of use", "/api-documentation/docs/terms-of-use")
          )
        )
      )
    )

    val question1 = TextQuestion(
      QuestionId("b9dbf0a5-e72b-4c89-a735-26f0858ca6cc"),
      Wording("Give us your organisation's website URL"),
      Statement(
        List(
          StatementText("For example https://example.com")
        )
      ),
      absence = Some(("My organisation doesn't have a website", Fail))
    )

    val question2 = ChooseOneOfQuestion(
      QuestionId("cbdf264f-be39-4638-92ff-6ecd2259c662"),
      Wording("Identify your organisation"),
      Statement(
        List(
          StatementText("Provide evidence that you or your organisation is officially registered in the UK. Choose one option.")
        )
      ),
      ListMap(
        (PossibleAnswer("Unique Taxpayer Reference (UTR)") -> Pass),
        (PossibleAnswer("VAT registration number") -> Pass),
        (PossibleAnswer("Corporation Tax Unique Taxpayer Reference (UTR)") -> Pass),
        (PossibleAnswer("PAYE reference") -> Pass),
        (PossibleAnswer("My organisation is in the UK and doesn't have any of these") -> Pass),
        (PossibleAnswer("My organisation is outside the UK and doesn't have any of these") -> Warn)
      )
    )

    val question2a = TextQuestion(
      QuestionId("4e148791-1a07-4f28-8fe4-ba3e18cdc118"),
      Wording("What is your company registration number?"),
      Statement(
        List(
          StatementText("You can find your company registration number on any official documentation you receive from Companies House."),
          StatementText("It's 8 characters long or 2 letters followed by 6  numbers. Check and documents from Companies House.")
        )
      ),
      absence = Some(("My organisation doesn't have a company registration", Warn))
    )

    val question2b = TextQuestion(
      QuestionId("55da0b97-178c-45b5-a139-b61ad7b9ca84"),
      Wording("What is your Unique Taxpayer Reference (UTR)?"),
      Statement(List.empty)
    )
    val question2c = TextQuestion(
      QuestionId("dd12fd8b-907b-4ba1-95d3-ef6317f36199"),
      Wording("What is your VAT registration number?"),
      Statement(List.empty)
    )
    val question2d = TextQuestion(
      QuestionId("6be23951-ac69-47bf-aa56-86d3d690ee0b"),
      Wording("What is your Corporation Tax Unique Taxpayer Reference (UTR)?"),
      Statement(List.empty)
    )
    val question2e = TextQuestion(
      QuestionId("a143760e-72f3-423b-a6b4-558db37a3453"),
      Wording("What is your PAYE reference?"),
      Statement(List.empty)
    )
    
    val question3 = AcknowledgementOnly(
      QuestionId("a12f314e-bc12-4e0d-87ba-1326acb31008"),
      Wording("Provide evidence of your organisation's registration"),
      Statement(
        List(
          StatementText("You will need to provide evidence that your organisation is officially registered in a country outside of the UK."),
          StatementText("You will be asked for a digital copy of the official registration document.")
        )
      )
    )
      
    val questionnaire = Questionnaire(
      id = QuestionnaireId("ac69b129-524a-4d10-89a5-7bfa46ed95c7"),
      label = Label("Organisation details"),
      questions = NonEmptyList.of(
        QuestionItem(questionRI1),
        QuestionItem(questionRI2),
        QuestionItem(question1),
        QuestionItem(question2),
        QuestionItem(question2a, AskWhenAnswer(question2, "My organisation is in the UK and doesn't have any of these")),
        QuestionItem(question2b, AskWhenAnswer(question2, "Unique Taxpayer Reference (UTR)")),
        QuestionItem(question2c, AskWhenAnswer(question2, "VAT registration number")),
        QuestionItem(question2d, AskWhenAnswer(question2, "Corporation Tax Unique Taxpayer Reference (UTR)")),
        QuestionItem(question2e, AskWhenAnswer(question2, "PAYE reference")),
        QuestionItem(question3,  AskWhenAnswer(question2, "My organisation is outside the UK and doesn't have any of these"))
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

    val question4 = ChooseOneOfQuestion(
      QuestionId("b0ae9d71-e6a7-4cf6-abd4-7eb7ba992bc6"),
      Wording("Do you have a privacy policy URL for your software?"),
      Statement(
        List(
          StatementText("You need a privacy policy covering the software you request production credentials for.")
        )
      ),
      ListMap(
        (PossibleAnswer("Yes") -> Pass),
        (PossibleAnswer("No") -> Fail),
        (PossibleAnswer("The privacy policy is in desktop software") -> Pass)
      )
    )

    val question5 = TextQuestion(
      QuestionId("c0e4b068-23c9-4d51-a1fa-2513f50e428f"),
      Wording("What is your privacy policy URL?"),
      Statement(
        List(
          StatementText("For example https://example.com/privacy-policy")
        )
      )
    )

    val question6 = ChooseOneOfQuestion(
      QuestionId("ca6af382-4007-4228-a781-1446231578b9"),
      Wording("Do you have a terms and conditions URL for your software?"),
      Statement(
        List(
          StatementText("You need terms and conditions covering the software you request production credentials for.")
        )
      ),
      ListMap(
        (PossibleAnswer("Yes") -> Pass),
        (PossibleAnswer("No") -> Fail),
        (PossibleAnswer("The terms and conditions are in desktop software") -> Pass)
      )
    )

    val question7 = TextQuestion(
      QuestionId("0a6d6973-c49a-49c3-93ff-de58daa1b90c"),
      Wording("What is your terms and conditions URL?"),
      Statement(
        List(
          StatementText("For example https://example.com/terms-conditions")
        )
      )
    )

    val questionnaire = Questionnaire(
      id = QuestionnaireId("3a7f3369-8e28-447c-bd47-efbabeb6d93f"),
      label = Label("Customers authorising your software"),
      questions = NonEmptyList.of(
        QuestionItem(question1),
        QuestionItem(question2),
        QuestionItem(question3, AskWhenContext(Keys.IN_HOUSE_SOFTWARE, "No")),
        QuestionItem(question4),
        QuestionItem(question5, AskWhenAnswer(question4, "Yes")),
        QuestionItem(question6),
        QuestionItem(question7, AskWhenAnswer(question6, "Yes"))
      )
    )
  }

  val testGroups = 
    NonEmptyList.of(
      GroupOfQuestionnaires(
        heading = "Your processes",
        links = NonEmptyList.of(
          DevelopmentPractices.questionnaire
        )            
      ),
      GroupOfQuestionnaires(
        heading = "Your software",
        links = NonEmptyList.of(
          CustomersAuthorisingYourSoftware.questionnaire
        )            
      ),
      GroupOfQuestionnaires(
        heading = "Your details",
        links = NonEmptyList.of(
          OrganisationDetails.questionnaire
        )
      )
    )

  val testQuestionIdsOfInterest = QuestionIdsOfInterest(
    responsibleIndividualNameId   = OrganisationDetails.questionRI1.id,
    responsibleIndividualEmailId  = OrganisationDetails.questionRI2.id,
    applicationNameId             = CustomersAuthorisingYourSoftware.question2.id,
    privacyPolicyId               = CustomersAuthorisingYourSoftware.question4.id,
    privacyPolicyUrlId            = CustomersAuthorisingYourSoftware.question5.id,
    termsAndConditionsId          = CustomersAuthorisingYourSoftware.question6.id,
    termsAndConditionsUrlId       = CustomersAuthorisingYourSoftware.question7.id,
    organisationUrlId             = OrganisationDetails.question1.id,
    identifyYourOrganisationId    = OrganisationDetails.question2.id
  )

  val questionnaire = DevelopmentPractices.questionnaire
  val questionnaireId = questionnaire.id
  val question = questionnaire.questions.head.question
  val questionId = question.id
  val question2Id = questionnaire.questions.tail.head.question.id
  val questionnaireAlt = OrganisationDetails.questionnaire
  val questionnaireAltId = questionnaireAlt.id
  val questionAltId = questionnaireAlt.questions.head.question.id
  val optionalQuestion = OrganisationDetails.question1
  val optionalQuestionId = optionalQuestion.id

  val allQuestionnaires = testGroups.flatMap(_.links)

  val expectedAppName = "expectedAppName"

  val answersToQuestions: Submission.AnswersToQuestions = 
    Map(
      testQuestionIdsOfInterest.applicationNameId -> TextAnswer(expectedAppName), 
      testQuestionIdsOfInterest.responsibleIndividualEmailId -> TextAnswer("bob@example.com"),
      testQuestionIdsOfInterest.responsibleIndividualNameId -> TextAnswer("Bob Cratchett")
    )  

  val completeAnswersToQuestions = Map(
    (DevelopmentPractices.question1.id -> SingleChoiceAnswer("Yes")),
    (DevelopmentPractices.question2.id -> SingleChoiceAnswer("No")),
    (DevelopmentPractices.question3.id -> SingleChoiceAnswer("No")),
    (OrganisationDetails.questionRI1.id -> TextAnswer("Bob Cratchett")),
    (OrganisationDetails.questionRI2.id -> TextAnswer("bob@example.com")),
    (OrganisationDetails.question1.id -> TextAnswer("https://example.com")),
    (OrganisationDetails.question2.id -> SingleChoiceAnswer("VAT registration number")),
    (OrganisationDetails.question2c.id -> TextAnswer("123456789")),
    (CustomersAuthorisingYourSoftware.question1.id -> AcknowledgedAnswer),
    (CustomersAuthorisingYourSoftware.question2.id -> TextAnswer("name of software")),
    (CustomersAuthorisingYourSoftware.question3.id -> MultipleChoiceAnswer(Set("In the UK"))),
    (CustomersAuthorisingYourSoftware.question4.id -> SingleChoiceAnswer("Yes")),
    (CustomersAuthorisingYourSoftware.question5.id -> TextAnswer("https://example.com/privacy-policy")),
    (CustomersAuthorisingYourSoftware.question6.id -> SingleChoiceAnswer("Yes")),
    (CustomersAuthorisingYourSoftware.question7.id -> NoAnswer)
  )

  def firstQuestion(questionnaire: Questionnaire) = questionnaire.questions.head.question.id

}

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

import scala.collection.immutable.ListMap

import cats.data.NonEmptyList
import cats.implicits._

import uk.gov.hmrc.apiplatform.modules.submissions.domain.models.AskWhen.Context.Keys
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models._

trait QuestionnaireTestData {

  object DevelopmentPractices {

    val question1 = Question.YesNoQuestion(
      Question.Id("653d2ee4-09cf-46a0-bc73-350a385ae860"),
      Wording("Do your development practices follow our guidance?"),
      Statement(
        CompoundFragment(
          StatementText("You must develop software following our"),
          StatementLink("development practices (opens in a new tab)", "http://www.google.com"),
          StatementText(".")
        )
      ).some,
      yesMarking = Mark.Pass,
      noMarking = Mark.Warn
    )

    val question2 = Question.YesNoQuestion(
      Question.Id("6139f57d-36ab-4338-85b3-a2079d6cf376"),
      Wording("Does your error handling meet our specification?"),
      Statement(
        CompoundFragment(
          StatementText("We will check for evidence that you comply with our"),
          StatementLink("error handling specification (opens in new tab)", "http://www.google.com"),
          StatementText(".")
        )
      ).some,
      yesMarking = Mark.Pass,
      noMarking = Mark.Fail
    )

    val question3 = Question.YesNoQuestion(
      Question.Id("3c5cd29d-bec2-463f-8593-cd5412fab1e5"),
      Wording("Does your software meet accessibility standards?"),
      Statement(
        CompoundFragment(
          StatementText("Web-based software must meet level AA of the"),
          StatementLink("Web Content Accessibility Guidelines (WCAG) (opens in new tab)", "http://www.google.com"),
          StatementText(". Desktop software should follow equivalent offline standards.")
        )
      ).some,
      yesMarking = Mark.Pass,
      noMarking = Mark.Warn
    )

    val questionnaire = Questionnaire(
      id = Questionnaire.Id("796336a5-f7b4-4dad-8003-a818e342cbb4"),
      label = Questionnaire.Label("Development practices"),
      questions = NonEmptyList.of(
        QuestionItem(question1),
        QuestionItem(question2),
        QuestionItem(question3)
      )
    )
  }

  object OrganisationDetails {

    val questionRI1 = Question.YesNoQuestion(
      Question.Id("99d9362d-e365-4af1-aa46-88e95f9858f7"),
      Wording("Are you the individual responsible for the software in your organisation?"),
      statement = Statement(
        StatementText("As the responsible individual you:"),
        StatementBullets(
          CompoundFragment(
            StatementText("ensure your software conforms to the "),
            StatementLink("terms of use (opens in new tab)", "/api-documentation/docs/terms-of-use")
          ),
          CompoundFragment(
            StatementText("understand the "),
            StatementLink("consequences of not conforming to the terms of use (opens in new tab)", "/api-documentation/docs/terms-of-use")
          )
        )
      ).some,
      yesMarking = Mark.Pass,
      noMarking = Mark.Pass,
      errorInfo = ErrorInfo("Select Yes if you are the individual responsible for the software in your organisation").some
    )

    val questionRI2 = Question.TextQuestion(
      Question.Id("36b7e670-83fc-4b31-8f85-4d3394908495"),
      Wording("Who is responsible for the software in your organisation?"),
      statement = None,
      label = Question.Label("First and last name").some,
      errorInfo = ErrorInfo("Enter a first and last name", "First and last name cannot be blank").some
    )

    val questionRI3 = Question.TextQuestion(
      Question.Id("fb9b8036-cc88-4f4e-ad84-c02caa4cebae"),
      Wording("Give us the email address of the individual responsible for the software"),
      statement = None,
      afterStatement = Statement(
        StatementText("We will email a verification link to the responsible individual that expires in 10 working days."),
        StatementText("The responsible individual must verify before we can process your request for production credentials.")
      ).some,
      label = Question.Label("Email address").some,
      hintText = StatementText("Cannot be a shared mailbox").some,
      validation = TextValidation.Email.some,
      errorInfo = ErrorInfo("Enter an email address in the correct format, like yourname@example.com", "Email address cannot be blank").some
    )

    val question1 = Question.TextQuestion(
      Question.Id("b9dbf0a5-e72b-4c89-a735-26f0858ca6cc"),
      Wording("What is your organisation’s URL?"),
      statement = None,
      hintText = StatementText("For example https://example.com").some,
      absence = ("My organisation doesn't have a website", Mark.Fail).some,
      validation = TextValidation.Url.some,
      errorInfo = ErrorInfo("Enter a URL in the correct format, like https://example.com", "Enter a URL in the correct format, like https://example.com").some
    )

    val question2 = Question.ChooseOneOfQuestion(
      Question.Id("cbdf264f-be39-4638-92ff-6ecd2259c662"),
      Wording("Identify your organisation"),
      statement = Statement(
        StatementText("Provide evidence that you or your organisation is officially registered in the UK. Choose one option.")
      ).some,
      marking = ListMap(
        (PossibleAnswer("Unique Taxpayer Reference (UTR)")                                 -> Mark.Pass),
        (PossibleAnswer("VAT registration number")                                         -> Mark.Pass),
        (PossibleAnswer("Corporation Tax Unique Taxpayer Reference (UTR)")                 -> Mark.Pass),
        (PossibleAnswer("PAYE reference")                                                  -> Mark.Pass),
        (PossibleAnswer("My organisation is in the UK and doesn't have any of these")      -> Mark.Pass),
        (PossibleAnswer("My organisation is outside the UK and doesn't have any of these") -> Mark.Warn)
      ),
      errorInfo = ErrorInfo("Select a way to identify your organisation").some
    )

    val question2a = Question.TextQuestion(
      Question.Id("4e148791-1a07-4f28-8fe4-ba3e18cdc118"),
      Wording("What is your company registration number?"),
      statement = Statement(
        CompoundFragment(
          StatementText("You can "),
          StatementLink("search Companies House for your company registration number (opens in new tab)", "https://find-and-update.company-information.service.gov.uk/"),
          StatementText(".")
        )
      ).some,
      hintText = StatementText("It is 8 characters. For example, 01234567 or AC012345.").some,
      absence = Tuple2("My organisation doesn't have a company registration", Mark.Fail).some,
      errorInfo = ErrorInfo("Your company registration number cannot be blank", "Enter your company registration number, like 01234567").some
    )

    val question2b = Question.TextQuestion(
      Question.Id("55da0b97-178c-45b5-a139-b61ad7b9ca84"),
      Wording("What is your Self Assessment Unique Taxpayer Reference?"),
      statement = None,
      hintText =
        CompoundFragment(
          StatementText("This is 10 numbers, for example 1234567890. It will be on tax returns and other letters about Self Assessment. It may be called ‘reference’, ‘UTR’ or ‘official use’. You can "),
          StatementLink("find a lost UTR number (opens in new tab)", "https://www.gov.uk/find-lost-utr-number"),
          StatementText(".")
        ).some,
      errorInfo = ErrorInfo("Your Self Assessment Unique Taxpayer Reference cannot be blank", "Enter your Self Assessment Unique Taxpayer Reference, like 1234567890").some
    )

    val question2c = Question.TextQuestion(
      Question.Id("dd12fd8b-907b-4ba1-95d3-ef6317f36199"),
      Wording("What is your company’s VAT registration number?"),
      statement = None,
      hintText =
        StatementText("This is 9 numbers, sometimes with ‘GB’ at the start, for example 123456789 or GB123456789. You can find it on your company’s VAT registration certificate.").some,
      errorInfo = ErrorInfo("Your company's VAT registration number cannot be blank", "Enter your company's VAT registration number, like 123456789").some
    )

    val question2d = Question.TextQuestion(
      Question.Id("6be23951-ac69-47bf-aa56-86d3d690ee0b"),
      Wording("What is your Corporation Tax Unique Taxpayer Reference?"),
      statement = None,
      hintText =
        CompoundFragment(
          StatementText("This is 10 numbers, for example 1234567890. It will be on tax returns and other letters about Corporation Tax. It may be called ‘reference’, ‘UTR’ or ‘official use’. You can "),
          StatementLink("find a lost UTR number (opens in new tab)", "https://www.gov.uk/find-lost-utr-number"),
          StatementText(".")
        ).some,
      errorInfo = ErrorInfo("Your Corporation Tax Unique Taxpayer Reference cannot be blank", "Enter your Corporation Tax Unique Taxpayer Reference, like 1234567890").some
    )

    val question2e = Question.TextQuestion(
      Question.Id("a143760e-72f3-423b-a6b4-558db37a3453"),
      Wording("What is your company’s employer PAYE reference?"),
      statement = None,
      hintText = StatementText(
        "This is a 3 digit tax office number, a forward slash, and a tax office employer reference, like 123/AB456. It may be called ‘Employer PAYE reference’ or ‘PAYE reference’. It will be on your P60."
      ).some,
      errorInfo = ErrorInfo("Your company's employer PAYE reference number cannot be blank", "Enter your company's employer PAYE reference number, like 123/AB456").some
    )

    val question3 = Question.AcknowledgementOnly(
      Question.Id("a12f314e-bc12-4e0d-87ba-1326acb31008"),
      Wording("Provide evidence of your organisation’s registration"),
      statement = Statement(
        StatementText("You will need to provide evidence that your organisation is officially registered in a country outside of the UK."),
        StatementText("You will be asked for a digital copy of the official registration document.")
      ).some
    )

    val questionnaire = Questionnaire(
      id = Questionnaire.Id("ac69b129-524a-4d10-89a5-7bfa46ed95c7"),
      label = Questionnaire.Label("Organisation details"),
      questions = NonEmptyList.of(
        QuestionItem(questionRI1),
        QuestionItem(questionRI2, AskWhen.AskWhenAnswer(questionRI1, "No")),
        QuestionItem(questionRI3, AskWhen.AskWhenAnswer(questionRI1, "No")),
        QuestionItem(question1),
        QuestionItem(question2),
        QuestionItem(question2a, AskWhen.AskWhenAnswer(question2, "My organisation is in the UK and doesn't have any of these")),
        QuestionItem(question2b, AskWhen.AskWhenAnswer(question2, "Unique Taxpayer Reference (UTR)")),
        QuestionItem(question2c, AskWhen.AskWhenAnswer(question2, "VAT registration number")),
        QuestionItem(question2d, AskWhen.AskWhenAnswer(question2, "Corporation Tax Unique Taxpayer Reference (UTR)")),
        QuestionItem(question2e, AskWhen.AskWhenAnswer(question2, "PAYE reference")),
        QuestionItem(question3, AskWhen.AskWhenAnswer(question2, "My organisation is outside the UK and doesn't have any of these"))
      )
    )
  }

  object CustomersAuthorisingYourSoftware {

    val question1 = Question.AcknowledgementOnly(
      Question.Id("95da25e8-af3a-4e05-a621-4a5f4ca788f6"),
      Wording("Customers authorising your software"),
      Statement(
        StatementText("Your customers will see the information you provide here when they authorise your software to interact with HMRC."),
        StatementText("Before you continue, you will need:"),
        StatementBullets(
          StatementText("the name of your software"),
          StatementText("the location of your servers which store customer data"),
          StatementText("a link to your privacy policy"),
          StatementText("a link to your terms and conditions")
        )
      ).some
    )

    val question2 = Question.TextQuestion(
      Question.Id("4d5a41c8-8727-4d09-96c0-e2ce1bc222d3"),
      Wording("Confirm the name of your software"),
      Statement(
        StatementText("We show this name to your users when they authorise your software to interact with HMRC."),
        CompoundFragment(
          StatementText("It must comply with our "),
          StatementLink("naming guidelines (opens in a new tab)", "https://developer.service.hmrc.gov.uk/api-documentation/docs/using-the-hub/name-guidelines"),
          StatementText(".")
        ),
        StatementText("Application name")
      ).some
    )

    val question3 = Question.MultiChoiceQuestion(
      Question.Id("57d706ad-c0b8-462b-a4f8-90e7aa58e57a"),
      Wording("Where are your servers that store customer information?"),
      Statement(
        StatementText("Select all that apply.")
      ).some,
      marking = ListMap(
        (PossibleAnswer("In the UK")                          -> Mark.Pass),
        (PossibleAnswer("In the European Economic Area")      -> Mark.Pass),
        (PossibleAnswer("Outside the European Economic Area") -> Mark.Warn)
      )
    )

    val question4 = Question.ChooseOneOfQuestion(
      Question.Id("b0ae9d71-e6a7-4cf6-abd4-7eb7ba992bc6"),
      Wording("Do you have a privacy policy URL for your software?"),
      Statement(
        StatementText("You need a privacy policy covering the software you request production credentials for.")
      ).some,
      marking = ListMap(
        (PossibleAnswer("Yes")                                       -> Mark.Pass),
        (PossibleAnswer("No")                                        -> Mark.Fail),
        (PossibleAnswer("The privacy policy is in desktop software") -> Mark.Pass)
      )
    )

    val question5 = Question.TextQuestion(
      Question.Id("c0e4b068-23c9-4d51-a1fa-2513f50e428f"),
      Wording("What is your privacy policy URL?"),
      Statement(
        StatementText("For example https://example.com/privacy-policy")
      ).some
    )

    val question6 = Question.ChooseOneOfQuestion(
      Question.Id("ca6af382-4007-4228-a781-1446231578b9"),
      Wording("Do you have a terms and conditions URL for your software?"),
      Statement(
        StatementText("You need terms and conditions covering the software you request production credentials for.")
      ).some,
      marking = ListMap(
        (PossibleAnswer("Yes")                                              -> Mark.Pass),
        (PossibleAnswer("No")                                               -> Mark.Fail),
        (PossibleAnswer("The terms and conditions are in desktop software") -> Mark.Pass)
      )
    )

    val question7 = Question.TextQuestion(
      Question.Id("0a6d6973-c49a-49c3-93ff-de58daa1b90c"),
      Wording("What is your terms and conditions URL?"),
      Statement(
        StatementText("For example https://example.com/terms-conditions")
      ).some
    )

    val questionnaire = Questionnaire(
      id = Questionnaire.Id("3a7f3369-8e28-447c-bd47-efbabeb6d93f"),
      label = Questionnaire.Label("Customers authorising your software"),
      questions = NonEmptyList.of(
        QuestionItem(question1),
        QuestionItem(question2),
        QuestionItem(question3, AskWhen.AskWhenContext(Keys.IN_HOUSE_SOFTWARE, "No")),
        QuestionItem(question4),
        QuestionItem(question5, AskWhen.AskWhenAnswer(question4, "Yes")),
        QuestionItem(question6),
        QuestionItem(question7, AskWhen.AskWhenAnswer(question6, "Yes"))
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
    responsibleIndividualIsRequesterId = OrganisationDetails.questionRI1.id,
    responsibleIndividualNameId = OrganisationDetails.questionRI2.id,
    responsibleIndividualEmailId = OrganisationDetails.questionRI3.id,
    applicationNameId = CustomersAuthorisingYourSoftware.question2.id,
    privacyPolicyId = CustomersAuthorisingYourSoftware.question4.id,
    privacyPolicyUrlId = CustomersAuthorisingYourSoftware.question5.id,
    termsAndConditionsId = CustomersAuthorisingYourSoftware.question6.id,
    termsAndConditionsUrlId = CustomersAuthorisingYourSoftware.question7.id,
    organisationUrlId = OrganisationDetails.question1.id,
    identifyYourOrganisationId = OrganisationDetails.question2.id,
    serverLocationsId = CustomersAuthorisingYourSoftware.question3.id
  )

  val questionnaire      = DevelopmentPractices.questionnaire
  val questionnaireId    = questionnaire.id
  val question           = questionnaire.questions.head.question
  val questionId         = question.id
  val question2Id        = questionnaire.questions.tail.head.question.id
  val questionnaireAlt   = OrganisationDetails.questionnaire
  val questionnaireAltId = questionnaireAlt.id
  val questionAltId      = questionnaireAlt.questions.head.question.id
  val optionalQuestion   = OrganisationDetails.question1
  val optionalQuestionId = optionalQuestion.id

  val allQuestionnaires = testGroups.flatMap(_.links)

  val expectedAppName = "expectedAppName"

  val answersToQuestions: Submission.AnswersToQuestions =
    Map(
      testQuestionIdsOfInterest.applicationNameId                  -> ActualAnswer.TextAnswer(expectedAppName),
      testQuestionIdsOfInterest.responsibleIndividualIsRequesterId -> ActualAnswer.SingleChoiceAnswer("No"),
      testQuestionIdsOfInterest.responsibleIndividualEmailId       -> ActualAnswer.TextAnswer("bob@example.com"),
      testQuestionIdsOfInterest.responsibleIndividualNameId        -> ActualAnswer.TextAnswer("Bob Cratchett")
    )

  val completeAnswersToQuestions = Map(
    (DevelopmentPractices.question1.id             -> ActualAnswer.SingleChoiceAnswer("Yes")),
    (DevelopmentPractices.question2.id             -> ActualAnswer.SingleChoiceAnswer("No")),
    (DevelopmentPractices.question3.id             -> ActualAnswer.SingleChoiceAnswer("No")),
    (OrganisationDetails.questionRI1.id            -> ActualAnswer.TextAnswer("Bob Cratchett")),
    (OrganisationDetails.questionRI2.id            -> ActualAnswer.TextAnswer("bob@example.com")),
    (OrganisationDetails.question1.id              -> ActualAnswer.TextAnswer("https://example.com")),
    (OrganisationDetails.question2.id              -> ActualAnswer.SingleChoiceAnswer("VAT registration number")),
    (OrganisationDetails.question2c.id             -> ActualAnswer.TextAnswer("123456789")),
    (CustomersAuthorisingYourSoftware.question1.id -> ActualAnswer.AcknowledgedAnswer),
    (CustomersAuthorisingYourSoftware.question2.id -> ActualAnswer.TextAnswer("name of software")),
    (CustomersAuthorisingYourSoftware.question3.id -> ActualAnswer.MultipleChoiceAnswer(Set("In the UK"))),
    (CustomersAuthorisingYourSoftware.question4.id -> ActualAnswer.SingleChoiceAnswer("Yes")),
    (CustomersAuthorisingYourSoftware.question5.id -> ActualAnswer.TextAnswer("https://example.com/privacy-policy")),
    (CustomersAuthorisingYourSoftware.question6.id -> ActualAnswer.SingleChoiceAnswer("Yes")),
    (CustomersAuthorisingYourSoftware.question7.id -> ActualAnswer.NoAnswer)
  )

  def firstQuestion(questionnaire: Questionnaire) = questionnaire.questions.head.question.id

}

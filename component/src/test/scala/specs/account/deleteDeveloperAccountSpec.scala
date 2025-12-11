/*
 * Copyright 2025 HM Revenue & Customs
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

import ApplicationsSteps._
import CommonStepsSteps._
import LoginStepsSteps._
import org.scalatest.BeforeAndAfter

class deleteDeveloperAccountSpec extends Env with BeforeAndAfter {

  Feature("Developer requests their account to be deleted") {
    Scenario("TPSD sees account deletion link and clicks it") {
      Given("I am registered with")
      givenIAmRegisteredWith(Map(
        "Email address" -> "john.smith@example.com",
        "Password"      -> "StrongPassword1!",
        "First name"    -> "John",
        "Last name"     -> "Smith",
        "Mfa Setup"     -> ""
      ))

      And("And I have no application assigned to my email 'john.smith@example.com'")
      givenIHaveNoApplicationAssignedToMyEmail("john.smith@example.com")

      Given("I successfully log in with 'john.smith@example.com' and 'StrongPassword1!' skipping 2SV")
      givenISuccessfullyLogInWithAndSkipping2SV("john.smith@example.com", "StrongPassword1!") // auto-chosen (score=1.00, LoginStepsSteps.scala)

      When("I click on the 'John Smith' link")
      whenIClickOnTheLink("John Smith")

      Then("I am on the 'Manage profile' page")
      thenIAmOnThePage("Manage profile")

      And("I see text in fields")
      thenISeeTextInFields(
        "name" -> "John Smith"
      )

      When("I see a link to request account deletion")
      whenISeeALinkToRequestAccountDeletion()

      And("I click on the request account deletion link")
      whenIClickOnTheRequestAccountDeletionLink() // auto-chosen (score=1.00, ApplicationsSteps.scala)

      Then("I am on the Account deletion confirmation page")
      thenIAmOnThePage("Account deletion confirmation") // auto-chosen (score=0.88, CommonStepsSteps.scala)

      When("I select the confirmation option with id deleteAccountYes")
      whenISelectTheConfirmationOptionWithId("deleteAccountYes") // auto-chosen (score=0.91, ApplicationsSteps.scala)

      And("I click on the account deletion confirmation submit button")
      whenIClickOnTheAccountDeletionConfirmationSubmitButton() // auto-chosen (score=1.00, ApplicationsSteps.scala)

      Then("I am on the Account deletion request submitted page")
      thenIAmOnThePage("Account deletion request submitted") // auto-chosen (score=0.88, CommonStepsSteps.scala)

      Then("a deskpro ticket is generated with subject Delete Developer Account Request")
      thenADeskproTicketIsGeneratedWithSubject("Delete Developer Account Request") // auto-chosen (score=0.91, ApplicationsSteps.scala)

    }
  }
}

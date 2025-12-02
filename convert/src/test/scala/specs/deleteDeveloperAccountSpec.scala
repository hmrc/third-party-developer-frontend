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

import CommonStepsSteps._
import ApplicationsStepsSteps._
import org.scalatest.BeforeAndAfter

class deleteDeveloperAccountSpec extends Env with BeforeAndAfter {
 
  Feature("Developer requests their account to be deleted") {

    Scenario("TPSD sees account deletion link and clicks it") {
      When("I see a link to request account deletion")
        whenISeeALinkToRequestAccountDeletion()

      And("I click on the request account deletion link")
        whenIClickOnTheRequestAccountDeletionLink()  // auto-chosen (score=1.00, ApplicationsStepsSteps.scala)

      Then("I am on the Account deletion confirmation page")
        thenIAmOnThePage("Account deletion confirmation")  // auto-chosen (score=0.88, CommonStepsSteps.scala)

      When("I select the confirmation option with id deleteAccountYes")
        whenISelectTheConfirmationOptionWithId("deleteAccountYes")  // auto-chosen (score=0.91, ApplicationsStepsSteps.scala)

      And("I click on the account deletion confirmation submit button")
        whenIClickOnTheAccountDeletionConfirmationSubmitButton()  // auto-chosen (score=1.00, ApplicationsStepsSteps.scala)

      Then("I am on the Account deletion request submitted page")
        thenIAmOnThePage("Account deletion request submitted")  // auto-chosen (score=0.88, CommonStepsSteps.scala)

      Then("a deskpro ticket is generated with subject Delete Developer Account Request")
        thenADeskproTicketIsGeneratedWithSubject("Delete Developer Account Request")  // auto-chosen (score=0.91, ApplicationsStepsSteps.scala)

    }
  }
}

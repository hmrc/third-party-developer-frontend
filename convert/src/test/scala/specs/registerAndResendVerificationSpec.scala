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
import RegisterStepsSteps._

class registerAndResendVerificationSpec extends BaseSpec {

  Feature("Resend verification") {

    Scenario("Resend verification email successfully in the Developer Hub") {
      Given("I navigate to the Registration page")
        givenINavigateToThePage("Registration")  // auto-chosen (score=0.88, CommonStepsSteps.scala)

      And("I see:")
        thenISee(null)  // auto-chosen (score=1.00, CommonStepsSteps.scala)

      And("I enter valid information for all fields:")
        givenIEnterValidInformationForAllFields(null)  // auto-chosen (score=1.00, RegisterStepsSteps.scala)

      Then("I click on submit")
        whenIClickOnSubmit()  // auto-chosen (score=1.00, RegisterStepsSteps.scala)

      Then("I expect a resend call from john.smith@example.com")
        givenIExpectAResendCallFrom("john.smith@example.com")  // auto-chosen (score=1.00, RegisterStepsSteps.scala)

      Then("I am on the Email confirmation page")
        thenIAmOnThePage("Email confirmation")  // auto-chosen (score=0.88, CommonStepsSteps.scala)

      And("I see:")
        thenISee(null)  // auto-chosen (score=1.00, CommonStepsSteps.scala)

      When("I click on the I have not received the email link")
        // ⚠️ No step-def match found for: I click on the I have not received the email link

      Then("I am on the Resend confirmation page")
        thenIAmOnThePage("Resend confirmation")  // auto-chosen (score=0.88, CommonStepsSteps.scala)

      And("I see:")
        thenISee(null)  // auto-chosen (score=1.00, CommonStepsSteps.scala)

      Then("I click on the Resend link")
        // ⚠️ No step-def match found for: I click on the Resend link

      Then("I am on the Email confirmation page")
        thenIAmOnThePage("Email confirmation")  // auto-chosen (score=0.88, CommonStepsSteps.scala)

      And("I see:")
        thenISee(null)  // auto-chosen (score=1.00, CommonStepsSteps.scala)

    }
  }
}

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
import RegisterSteps._

class registerAndResendVerificationSpec extends Env {

  Feature("Resend verification") {
    Scenario("Resend verification email successfully in the Developer Hub") {
      Given("I navigate to the Registration page")
      givenINavigateToThePage("Registration") // auto-chosen (score=0.88, CommonStepsSteps.scala)

      And("I see:")
      thenISeeInOrder(
        "First name",
        "Last name",
        "Email address",
        "Create password",
        "Your password must be at least 12 characters and contain at least one number, lowercase letter, uppercase letter and special character",
        "Confirm password"
      ) // auto-chosen (score=1.00, CommonStepsSteps.scala)

      And("I enter valid information for all fields:")
      givenIEnterValidInformationForAllFields(
        "first name"       -> "John",
        "last name"        -> "Smith",
        "email address"    -> "john.smith@example.com",
        "password"         -> "A1@wwwwwwwww",
        "confirm password" -> "A1@wwwwwwwww"
      ) // auto-chosen (score=1.00, RegisterStepsSteps.scala)

      Then("I click on submit")
      whenIClickOnSubmit() // auto-chosen (score=1.00, RegisterStepsSteps.scala)

      Then("I expect a resend call from john.smith@example.com")
      givenIExpectAResendCallFrom("john.smith@example.com") // auto-chosen (score=1.00, RegisterStepsSteps.scala)

      Then("I am on the Email confirmation page")
      thenIAmOnThePage("Email confirmation") // auto-chosen (score=0.88, CommonStepsSteps.scala)

      And("I see:")
      thenISeeInOrder(
        "We have sent a confirmation email to john.smith@example.com.",
        "Click on the link in the email to verify your account.",
        "I have not received the email"
      ) // auto-chosen (score=1.00, CommonStepsSteps.scala)

      When("I click on the I have not received the email link")
      whenIClickOnTheLink("I have not received the email") // ⚠️ No step-def match found for: I click on the I have not received the email link

      Then("I am on the Resend confirmation page")
      thenIAmOnThePage("Resend confirmation") // auto-chosen (score=0.88, CommonStepsSteps.scala)

      And("I see:")
      thenISeeInOrder("Emails can take a few minutes to arrive. If you have not received it check your spam folder, or we can resend it.") // auto-chosen (score=1.00, CommonStepsSteps.scala)

      Then("I click on the Resend link")
      whenIClickOnTheLink("Resend") // ⚠️ No step-def match found for: I click on the Resend link

      Then("I am on the Email confirmation page")
      thenIAmOnThePage("Email confirmation") // auto-chosen (score=0.88, CommonStepsSteps.scala)

      And("I see:")
      thenISeeInOrder(
        "We have sent a confirmation email to john.smith@example.com.",
        "Click on the link in the email to verify your account.",
        "I have not received the email"
      ) // auto-chosen (score=1.00, CommonStepsSteps.scala)

    }
  }
}

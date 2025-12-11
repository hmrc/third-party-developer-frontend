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
import LoginStepsSteps._
import MfaStepsSteps._
import ApplicationsStepsSteps._

class mfaSmsSetupJourneysSpec extends Env {

  Feature("MFA Sms Setup") {

    Scenario("Signing with a valid credentials and no MFA mandated skipping mfa, select email preferences") {
      Given("I am registered with") 
        givenIAmRegisteredWith(Map(
          "Email address" -> "john.smith@example.com",
          "Password" -> "StrongPassword1!",
          "First name" -> "John",
          "Last name" -> "Smith",
          "Mfa Setup" -> "SMS"
      ))

      And("And I have no application assigned to my email 'john.smith@example.com'")
        givenIHaveNoApplicationAssignedToMyEmail("john.smith@example.com")

      Given("I navigate to the Sign in page")
        givenINavigateToThePage("Sign in")  // auto-chosen (score=0.88, CommonStepsSteps.scala)

      And("I enter all the fields:")
        givenIEnterAllTheFields(Map(
          "email address" -> "john.smith@example.com",
          "password" -> "StrongPassword1!"
        ))  // auto-chosen (score=1.00, CommonStepsSteps.scala)

      When("I click on the button with id submit")
        whenIClickOnTheButtonWithId("submit")  // auto-chosen (score=0.91, CommonStepsSteps.scala)

      Then("I am on the Recommend Mfa page")
        thenIAmOnThePage("Recommend Mfa")  // auto-chosen (score=0.88, CommonStepsSteps.scala)

      When("I click on the button with id skip")
        whenIClickOnTheButtonWithId("skip")  // auto-chosen (score=0.91, CommonStepsSteps.scala)

      Then("I am on the Recommend Mfa Skip Acknowledge page")
        thenIAmOnThePage("Recommend Mfa Skip Acknowledge")  // auto-chosen (score=0.88, CommonStepsSteps.scala)

      When("I click on the button with id submit")
        whenIClickOnTheButtonWithId("submit")  // auto-chosen (score=0.91, CommonStepsSteps.scala)

      Given("I am on the No Applications page")
        thenIAmOnThePage("No Applications")  // auto-chosen (score=0.88, CommonStepsSteps.scala)

      Then("My device session is not set")
        thenMyDeviceSessionIsNotSet()  // auto-chosen (score=1.00, MfaStepsSteps.scala)

      When("I click on the radio button with id get-emails")
        whenIClickOnTheButtonWithId("get-emails")  // auto-chosen (score=0.88, CommonStepsSteps.scala)

      And("I click on the button with id submit")
        whenIClickOnTheButtonWithId("submit")  // auto-chosen (score=0.91, CommonStepsSteps.scala)

      Then("I am on the Email preferences page")
        thenIAmOnThePage("Email preferences")  // auto-chosen (score=0.88, CommonStepsSteps.scala)

    }

    Scenario("Signing with a valid credentials and no MFA mandated or setup, select email preferences") {
      Given("I am registered with") 
        givenIAmRegisteredWith(Map(
          "Email address" -> "john.smith@example.com",
          "Password" -> "StrongPassword1!",
          "First name" -> "John",
          "Last name" -> "Smith",
          "Mfa Setup" -> "SMS"
      ))

      And("And I have no application assigned to my email 'john.smith@example.com'")
        givenIHaveNoApplicationAssignedToMyEmail("john.smith@example.com")

     Given("I navigate to the Sign in page")
        givenINavigateToThePage("Sign in")  // auto-chosen (score=0.88, CommonStepsSteps.scala)

      And("I enter all the fields:")
        givenIEnterAllTheFields(Map(
          "email address" -> "john.smith@example.com",
          "password" -> "StrongPassword1!"
        ))   // auto-chosen (score=1.00, CommonStepsSteps.scala)

      When("I click on the button with id submit")
        whenIClickOnTheButtonWithId("submit")  // auto-chosen (score=0.91, CommonStepsSteps.scala)

      Then("I am on the Recommend Mfa page")
        thenIAmOnThePage("Recommend Mfa")  // auto-chosen (score=0.88, CommonStepsSteps.scala)

      When("I click on the button with id submit")
        whenIClickOnTheButtonWithId("submit")  // auto-chosen (score=0.91, CommonStepsSteps.scala)

      Then("I am on the Select MFA page")
        thenIAmOnThePage("Select MFA")  // auto-chosen (score=0.88, CommonStepsSteps.scala)

      When("I click on the radio button with id sms-mfa")
        whenIClickOnTheButtonWithId("sms-mfa")  // auto-chosen (score=0.88, CommonStepsSteps.scala)

      And("I click on the button with id submit")
        whenIClickOnTheButtonWithId("submit")  // auto-chosen (score=0.91, CommonStepsSteps.scala)

      Then("I am on the Sms mobile number page")
        thenIAmOnThePage("Sms mobile number")  // auto-chosen (score=0.88, CommonStepsSteps.scala)

      When("I enter the mobile number then click continue")
        whenIEnterTheMobileNumberThenClickContinue()  // auto-chosen (score=1.00, MfaStepsSteps.scala)

      Then("I am on the Sms Access Code page")
        thenIAmOnThePage("Sms Access Code")  // auto-chosen (score=0.88, CommonStepsSteps.scala)

      When("I enter the correct Sms access code then click continue")
        whenIEnterTheCorrectSmsAccessCodeThenClickContinue()  // auto-chosen (score=1.00, MfaStepsSteps.scala)

      Then("I am on the Sms Setup Complete page")
        thenIAmOnThePage("Sms Setup Complete")  // auto-chosen (score=0.88, CommonStepsSteps.scala)

      And("I click on the button with id link")
        whenIClickOnTheButtonWithId("link")  // auto-chosen (score=0.91, CommonStepsSteps.scala)

      Given("john.smith@example.com session is uplifted to LoggedIn")
        givenSessionIsUpliftedToLoggedIn("john.smith@example.com")  // auto-chosen (score=1.00, LoginStepsSteps.scala)

      And("I am on the No Applications page")
        thenIAmOnThePage("No Applications")  // auto-chosen (score=0.88, CommonStepsSteps.scala)

      Then("My device session is not set")
        thenMyDeviceSessionIsNotSet()  // auto-chosen (score=1.00, MfaStepsSteps.scala)

      When("I click on the radio button with id get-emails")
        whenIClickOnTheButtonWithId("get-emails")  // auto-chosen (score=0.88, CommonStepsSteps.scala)

      And("I click on the button with id submit")
        whenIClickOnTheButtonWithId("submit")  // auto-chosen (score=0.91, CommonStepsSteps.scala)

      Then("I am on the Email preferences page")
        thenIAmOnThePage("Email preferences")  // auto-chosen (score=0.88, CommonStepsSteps.scala)
    }
  }
}

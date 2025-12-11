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
import MfaStepsSteps._

class smsMfaEnabledWithoutDeviceSessionSpec extends Env {

  Feature("Sms Enabled as MFA Method Journey User with No Device Session") {
    Scenario("Signing with a valid credentials and no MFA mandated but is setup, select email preferences") {
      Given("I have SMS enabled as MFA method, without a DeviceSession and registered with")
      givenIHaveSMSEnabledAsMFAMethodWithoutADeviceSessionAndRegisteredWith(Map(
        "Email address" -> "john.smith@example.com",
        "Password"      -> "StrongPassword1!",
        "First name"    -> "John",
        "Last name"     -> "Smith"
      ))

      And("I have no application assigned to my email 'john.smith@example.com'")
      givenIHaveNoApplicationAssignedToMyEmail("john.smith@example.com")

      Given("I navigate to the Sign in page")
      givenINavigateToThePage("Sign in") // auto-chosen (score=0.88, CommonStepsSteps.scala)

      And("I enter all the fields:")
      givenIEnterAllTheFields(Map(
        "email address" -> "john.smith@example.com",
        "password"      -> "StrongPassword1!"
      )) // auto-chosen (score=1.00, CommonStepsSteps.scala)

      When("I click on the button with id submit")
      whenIClickOnTheButtonWithId("submit") // auto-chosen (score=0.91, CommonStepsSteps.scala)

      Given("I am on the Sms Login Access Code page")
      thenIAmOnThePage("Sms Login Access Code") // auto-chosen (score=0.88, CommonStepsSteps.scala)

      Then("My device session is not set")
      thenMyDeviceSessionIsNotSet() // auto-chosen (score=1.00, MfaStepsSteps.scala)

      When("I enter the correct access code for SMS and click remember me for 7 days then click continue")
      whenIEnterTheCorrectAccessCodeForSMSAndClickRememberMeFor7DaysThenClickContinue() // auto-chosen (score=1.00, MfaStepsSteps.scala)

      Then("My device session is set")
      thenMyDeviceSessionIsSet() // auto-chosen (score=1.00, MfaStepsSteps.scala)

      And("I am on the Authenticator App Mfa Setup Reminder page")
      thenIAmOnThePage("Authenticator App Mfa Setup Reminder") // auto-chosen (score=0.88, CommonStepsSteps.scala)

      And("I click on the button with id link")
      whenIClickOnTheButtonWithId("link") // auto-chosen (score=0.91, CommonStepsSteps.scala)

      And("I am on the Authenticator App Setup Skipped page")
      thenIAmOnThePage("Authenticator App Setup Skipped") // auto-chosen (score=0.88, CommonStepsSteps.scala)

      And("I click on the button with id submit")
      whenIClickOnTheButtonWithId("submit") // auto-chosen (score=0.91, CommonStepsSteps.scala)

      Given("I am on the No Applications page")
      thenIAmOnThePage("No Applications") // auto-chosen (score=0.88, CommonStepsSteps.scala)

      When("I click on the radio button with id get-emails")
      whenIClickOnTheButtonWithId("get-emails") // auto-chosen (score=0.88, CommonStepsSteps.scala)

      And("I click on the button with id submit")
      whenIClickOnTheButtonWithId("submit") // auto-chosen (score=0.91, CommonStepsSteps.scala)

      Then("I am on the Email preferences page")
      thenIAmOnThePage("Email preferences") // auto-chosen (score=0.88, CommonStepsSteps.scala)

    }

    Scenario("Signing with a valid credentials and no MFA mandated but is setup, select email preferences part 2") {
      Given("I have SMS enabled as MFA method, without a DeviceSession and registered with")
      givenIHaveSMSEnabledAsMFAMethodWithoutADeviceSessionAndRegisteredWith(Map(
        "Email address" -> "john.smith@example.com",
        "Password"      -> "StrongPassword1!",
        "First name"    -> "John",
        "Last name"     -> "Smith"
      ))

      And("I have no application assigned to my email 'john.smith@example.com'")
      givenIHaveNoApplicationAssignedToMyEmail("john.smith@example.com")

      Given("I navigate to the Sign in page")
      givenINavigateToThePage("Sign in") // auto-chosen (score=0.88, CommonStepsSteps.scala)

      And("I enter all the fields:")
      givenIEnterAllTheFields(Map(
        "email address" -> "john.smith@example.com",
        "password"      -> "StrongPassword1!"
      )) //   // auto-chosen (score=1.00, CommonStepsSteps.scala)

      When("I click on the button with id submit")
      whenIClickOnTheButtonWithId("submit") // auto-chosen (score=0.91, CommonStepsSteps.scala)

      Given("I am on the Sms Login Access Code page")
      thenIAmOnThePage("Sms Login Access Code") // auto-chosen (score=0.88, CommonStepsSteps.scala)

      Then("My device session is not set")
      thenMyDeviceSessionIsNotSet() // auto-chosen (score=1.00, MfaStepsSteps.scala)

      When("I enter the correct access code SMS and do NOT click remember me for 7 days then click continue")
      whenIEnterTheCorrectAccessCodeSMSAndDoNOTClickRememberMeFor7DaysThenClickContinue() // auto-chosen (score=1.00, MfaStepsSteps.scala)

      And("I am on the Authenticator App Mfa Setup Reminder page")
      thenIAmOnThePage("Authenticator App Mfa Setup Reminder") // auto-chosen (score=0.88, CommonStepsSteps.scala)

      And("I click on the button with id link")
      whenIClickOnTheButtonWithId("link") // auto-chosen (score=0.91, CommonStepsSteps.scala)

      And("I am on the Authenticator App Setup Skipped page")
      thenIAmOnThePage("Authenticator App Setup Skipped") // auto-chosen (score=0.88, CommonStepsSteps.scala)

      And("I click on the button with id submit")
      whenIClickOnTheButtonWithId("submit") // auto-chosen (score=0.91, CommonStepsSteps.scala)

      Given("I am on the No Applications page")
      thenIAmOnThePage("No Applications") // auto-chosen (score=0.88, CommonStepsSteps.scala)

      Then("My device session is not set")
      thenMyDeviceSessionIsNotSet() // auto-chosen (score=1.00, MfaStepsSteps.scala)

      When("I click on the radio button with id get-emails")
      whenIClickOnTheButtonWithId("get-emails") // auto-chosen (score=0.88, CommonStepsSteps.scala)

      And("I click on the button with id submit")
      whenIClickOnTheButtonWithId("submit") // auto-chosen (score=0.91, CommonStepsSteps.scala)

      Then("I am on the Email preferences page")
      thenIAmOnThePage("Email preferences") // auto-chosen (score=0.88, CommonStepsSteps.scala)

    }
  }
}

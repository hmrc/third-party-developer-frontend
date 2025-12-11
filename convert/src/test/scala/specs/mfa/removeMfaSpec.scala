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
import MfaStepsSteps._
import ApplicationsStepsSteps._

class removeMfaSpec extends Env {

  Feature("Remove MFA. User with MFA enabled and Existing Device Session") {
    Scenario("Signing with a valid credentials and no MFA mandated or setup, remove MFA") {
      Given("I am mfaEnabled and with a DeviceSession registered with")
        givenIAmMfaEnabledAndWithADeviceSessionRegisteredWith(Map(
          "Email address" -> "john.smith@example.com",
          "Password" -> "StrongPassword1!",
          "First name" -> "John",
          "Last name" -> "Smith",
          "Mfa Setup" -> "AUTHENTICATOR_APP"
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

      And("I already have a device cookie")
        givenIAlreadyHaveADeviceCookie()  // auto-chosen (score=1.00, MfaStepsSteps.scala)

      When("I click on the button with id submit")
        whenIClickOnTheButtonWithId("submit")  // auto-chosen (score=0.91, CommonStepsSteps.scala)

      Given("I am on the No Applications page")
        thenIAmOnThePage("No Applications")  // auto-chosen (score=0.88, CommonStepsSteps.scala)

      Then("My device session is set")
        thenMyDeviceSessionIsSet()  // auto-chosen (score=1.00, MfaStepsSteps.scala)

      When("I click on the radio button with id get-emails")
        whenIClickOnTheButtonWithId("get-emails")  // auto-chosen (score=0.88, CommonStepsSteps.scala)

      And("I click on the button with id submit")
        whenIClickOnTheButtonWithId("submit")  // auto-chosen (score=0.91, CommonStepsSteps.scala)

      Then("I am on the Email preferences page")
        thenIAmOnThePage("Email preferences")  // auto-chosen (score=0.88, CommonStepsSteps.scala)

      Given("I navigate to the Security preferences page")
        givenINavigateToThePage("Security preferences")  // auto-chosen (score=0.88, CommonStepsSteps.scala)

      Then("I am on the Security preferences page")
        thenIAmOnThePage("Security preferences")  // auto-chosen (score=0.88, CommonStepsSteps.scala)

      And("I click on the button with id submit")
        whenIClickOnTheButtonWithId("submit")  // auto-chosen (score=0.91, CommonStepsSteps.scala)

      Then("I am on the Authenticator App Access Code page")
        thenIAmOnThePage("Authenticator App Access Code")  // auto-chosen (score=0.88, CommonStepsSteps.scala)

      When("I enter the correct access code during Auth App removal then click continue")
        whenIEnterTheCorrectAccessCodeDuringAuthAppRemovalThenClickContinue()  // auto-chosen (score=1.00, MfaStepsSteps.scala)

      Then("I am on the 2SV removal complete page")
        thenIAmOnThePage("2SV removal complete")  // auto-chosen (score=0.88, CommonStepsSteps.scala)

    }
  }
}

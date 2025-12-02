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

class mfaAuthAppSetupJourneysSpec extends BaseSpec {

  Feature("MFA Setup") {

    Scenario("Signing with a valid credentials and no MFA mandated or setup, select email preferences") {
      Given("I navigate to the Sign in page")
        givenINavigateToThePage("Sign in")  // auto-chosen (score=0.88, CommonStepsSteps.scala)

      And("I enter all the fields:")
        givenIEnterAllTheFields(null)  // auto-chosen (score=1.00, CommonStepsSteps.scala)

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
        whenIClickOnTheButton("get-emails")  // auto-chosen (score=0.88, CommonStepsSteps.scala)

      And("I click on the button with id submit")
        whenIClickOnTheButtonWithId("submit")  // auto-chosen (score=0.91, CommonStepsSteps.scala)

      Then("I am on the Email preferences page")
        thenIAmOnThePage("Email preferences")  // auto-chosen (score=0.88, CommonStepsSteps.scala)

    }

    Scenario("Signing with a valid credentials and no MFA mandated or setup, register for Authenticator App as new Mfa setup, select email preferences") {
      Given("I navigate to the Sign in page")
        givenINavigateToThePage("Sign in page")  // auto-chosen (score=0.88, CommonStepsSteps.scala)

      And("I enter all the fields:")
        givenIEnterAllTheFields(null)  // auto-chosen (score=1.00, CommonStepsSteps.scala)

      When("I click on the button with id submit")
        whenIClickOnTheButtonWithId("submit")  // auto-chosen (score=0.91, CommonStepsSteps.scala)

      Then("I am on the Recommend Mfa page")
        thenIAmOnThePage("Recommend Mfa")  // auto-chosen (score=0.88, CommonStepsSteps.scala)

      When("I click on the button with id submit")
        whenIClickOnTheButtonWithId("submit")  // auto-chosen (score=0.91, CommonStepsSteps.scala)

      Then("I am on the Select MFA page")
        thenIAmOnThePage("Select MFA")  // auto-chosen (score=0.88, CommonStepsSteps.scala)

      Then("I click on the radio button with id auth-app-mfa")
        whenIClickOnTheButton("auth-app-mfa")  // auto-chosen (score=0.88, CommonStepsSteps.scala)

      When("I click on the button with id submit")
        whenIClickOnTheButtonWithId("submit")  // auto-chosen (score=0.91, CommonStepsSteps.scala)

      Then("I am on the Authenticator App Start Page page")
        thenIAmOnThePage("Authenticator App Start Page")  // auto-chosen (score=0.88, CommonStepsSteps.scala)

      When("I click on the button with id submit")
        whenIClickOnTheButtonWithId("submit")  // auto-chosen (score=0.91, CommonStepsSteps.scala)

      Then("I am on the Setup 2SV QR page")
        thenIAmOnThePage("Setup 2SV QR")  // auto-chosen (score=0.88, CommonStepsSteps.scala)

      When("I click on the button with id submit")
        whenIClickOnTheButtonWithId("submit")  // auto-chosen (score=0.91, CommonStepsSteps.scala)

      Then("I am on the Authenticator App Access Code page")
        thenIAmOnThePage("Authenticator App Access Code")  // auto-chosen (score=0.88, CommonStepsSteps.scala)

      When("I enter the correct access code during 2SVSetup with mfaMandated false")
        whenIEnterTheCorrectAccessCodeDuring2SVSetupWithMfaMandated(false)  // auto-chosen (score=0.94, MfaStepsSteps.scala)

      Then("I am on the Create name for Authenticator App page")
        thenIAmOnThePage("Create name for Authenticator App")  // auto-chosen (score=0.88, CommonStepsSteps.scala)

      And("I enter an authenticator app name")
        thenIEnterAnAuthenticatorAppName()  // auto-chosen (score=1.00, MfaStepsSteps.scala)

      When("I click on the button with id submit")
        whenIClickOnTheButtonWithId("submit")  // auto-chosen (score=0.91, CommonStepsSteps.scala)

      Then("I am on the Authenticator App Setup Complete page")
        thenIAmOnThePage("Authenticator App Setup Complete")  // auto-chosen (score=0.88, CommonStepsSteps.scala)

      And("I click on the button with id link")
        whenIClickOnTheButtonWithId("link")  // auto-chosen (score=0.91, CommonStepsSteps.scala)

      Given("john.smith@example.com session is uplifted to LoggedIn")
        givenSessionIsUpliftedToLoggedIn("john.smith@example.com")  // auto-chosen (score=1.00, LoginStepsSteps.scala)

      And("I am on the Sms Mfa Setup Skipped page")
        thenIAmOnThePage("Sms Mfa Setup Skipped")  // auto-chosen (score=0.88, CommonStepsSteps.scala)

      And("I click on the button with id submit")
        whenIClickOnTheButtonWithId("submit")  // auto-chosen (score=0.91, CommonStepsSteps.scala)

      And("I am on the No Applications page")
        thenIAmOnThePage("No Applications")  // auto-chosen (score=0.88, CommonStepsSteps.scala)

      Then("My device session is not set")
        thenMyDeviceSessionIsNotSet()  // auto-chosen (score=1.00, MfaStepsSteps.scala)

      When("I click on the radio button with id get-emails")
        whenIClickOnTheButton("get-emails")  // auto-chosen (score=0.88, CommonStepsSteps.scala)

      And("I click on the button with id submit")
        whenIClickOnTheButtonWithId("submit")  // auto-chosen (score=0.91, CommonStepsSteps.scala)

      Then("I am on the Email preferences page")
        thenIAmOnThePage("Email preferences")  // auto-chosen (score=0.88, CommonStepsSteps.scala)

    }
  }
}

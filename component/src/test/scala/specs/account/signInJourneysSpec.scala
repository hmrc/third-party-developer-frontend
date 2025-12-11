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
import MfaStepsSteps._
import org.scalatest.BeforeAndAfter

class signInJourneysSpec extends Env with BeforeAndAfter {

  Feature("Sign in") {

    Scenario("Signing with a valid credentials and no MFA mandated or setup, select email preferences") {
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

      Given("I am on the No Applications page")
      thenIAmOnThePage("No Applications") // auto-chosen (score=0.88, CommonStepsSteps.scala)

      When("I click on the radio button with id get-emails")
      whenIClickOnTheButtonWithId("get-emails") // auto-chosen (score=0.88, CommonStepsSteps.scala)

      And("I click on the button with id submit")
      whenIClickOnTheButtonWithId("submit") // auto-chosen (score=0.91, CommonStepsSteps.scala)

      Then("I am on the Email preferences page")
      thenIAmOnThePage("Email preferences") // auto-chosen (score=0.88, CommonStepsSteps.scala)

    }

    Scenario("Signing with a valid credentials no mfa enabled and with a production admin app and the mandated date is in the past") {
      Given("I am registered with")
      givenIAmRegisteredWith(Map(
        "Email address" -> "john.smith@example.com", // UserIdData.one ???
        "Password"      -> "StrongPassword1!",
        "First name"    -> "John",
        "Last name"     -> "Smith",
        "Mfa Setup"     -> ""
      ))

      And("And I have no application assigned to my email 'john.smith@example.com'")
      givenIHaveNoApplicationAssignedToMyEmail("john.smith@example.com")

      Given("application with name My Admin Production App can be created")
      givenApplicationWithNameCanBeCreated("My Admin Production App") // auto-chosen (score=0.90, ApplicationsSteps.scala)

      Given("I navigate to the Sign in page")
      givenINavigateToThePage("Sign in") // auto-chosen (score=0.88, CommonStepsSteps.scala)

      And("I enter all the fields:")
      givenIEnterAllTheFields(
        Map(
          "email address" -> "john.smith@example.com",
          "password"      -> "StrongPassword1!"
        )
      ) // auto-chosen (score=1.00, CommonStepsSteps.scala)

      When("I click on the button with id submit")
      whenIClickOnTheButtonWithId("submit") // auto-chosen (score=0.91, CommonStepsSteps.scala)

      Then("I am on the Recommend Mfa page")
      thenIAmOnThePage("Recommend Mfa") // auto-chosen (score=0.88, CommonStepsSteps.scala)

      When("I click on the button with id submit")
      whenIClickOnTheButtonWithId("submit") // auto-chosen (score=0.91, CommonStepsSteps.scala)

      Then("I am on the Select MFA page")
      thenIAmOnThePage("Select MFA") // auto-chosen (score=0.88, CommonStepsSteps.scala)

      Then("I click on the radio button with id auth-app-mfa")
      whenIClickOnTheRadioButtonWithId("auth-app-mfa") // auto-chosen (score=0.88, CommonStepsSteps.scala)

      When("I click on the button with id submit")
      whenIClickOnTheButtonWithId("submit") // auto-chosen (score=0.91, CommonStepsSteps.scala)

      Then("I am on the Authenticator App Start Page page")
      thenIAmOnThePage("Authenticator App Start Page") // auto-chosen (score=0.88, CommonStepsSteps.scala)

      When("I click on the button with id submit")
      whenIClickOnTheButtonWithId("submit") // auto-chosen (score=0.91, CommonStepsSteps.scala)

      Then("I am on the Setup 2SV QR page")
      thenIAmOnThePage("Setup 2SV QR") // auto-chosen (score=0.88, CommonStepsSteps.scala)

      When("I click on the button with id submit")
      whenIClickOnTheButtonWithId("submit") // auto-chosen (score=0.91, CommonStepsSteps.scala)

      Then("I am on the Authenticator App Access Code page")
      thenIAmOnThePage("Authenticator App Access Code") // auto-chosen (score=0.88, CommonStepsSteps.scala)

      When("I enter the correct access code during 2SVSetup with mfaMandated false")
      whenIEnterTheCorrectAccessCodeDuring2SVSetupWithMfaMandated(false) // auto-chosen (score=0.94, MfaStepsSteps.scala)

      Then("I am on the Create name for Authenticator App page")
      thenIAmOnThePage("Create name for Authenticator App") // auto-chosen (score=0.88, CommonStepsSteps.scala)

      And("I enter an authenticator app name")
      thenIEnterAnAuthenticatorAppName() // auto-chosen (score=1.00, MfaStepsSteps.scala)

      When("I click on the button with id submit")
      whenIClickOnTheButtonWithId("submit") // auto-chosen (score=0.91, CommonStepsSteps.scala)

      Then("I am on the Authenticator App Setup Complete page")
      thenIAmOnThePage("Authenticator App Setup Complete") // auto-chosen (score=0.88, CommonStepsSteps.scala)

      Given("john.smith@example.com session is uplifted to LoggedIn")
      givenSessionIsUpliftedToLoggedIn("john.smith@example.com") // auto-chosen (score=1.00, LoginStepsSteps.scala)

      When("I click on the button with id link")
      whenIClickOnTheButtonWithId("link") // auto-chosen (score=0.91, CommonStepsSteps.scala)

      And("I am on the Sms Mfa Setup Skipped page")
      thenIAmOnThePage("Sms Mfa Setup Skipped") // auto-chosen (score=0.88, CommonStepsSteps.scala)

      And("I click on the button with id submit")
      whenIClickOnTheButtonWithId("submit") // auto-chosen (score=0.91, CommonStepsSteps.scala)

      Then("I am on the View all applications page")
      thenIAmOnThePage("View all applications") // auto-chosen (score=0.88, CommonStepsSteps.scala)

    }

    Scenario("Signing with a valid credentials and no MFA mandated or setup, start using our rest APIs") {
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

      Given("I successfully logged in with john.smith@example.com and StrongPassword1! skipping 2SV")
      givenISuccessfullyLogInWithAndSkipping2SV("john.smith@example.com", "StrongPassword1!") // auto-chosen (score=1.00, LoginStepsSteps.scala)

      Given("I am on the No Applications page")
      thenIAmOnThePage("No Applications") // auto-chosen (score=0.88, CommonStepsSteps.scala)

      When("I click on the radio button with id use-apis")
      whenIClickOnTheRadioButtonWithId("use-apis") // auto-chosen (score=0.88, CommonStepsSteps.scala)

      And("I click on the button with id submit")
      whenIClickOnTheButtonWithId("submit") // auto-chosen (score=0.91, CommonStepsSteps.scala)

      Then("I am on the Add an application to the sandbox empty nest page")
      thenIAmOnThePage("Add an application to the sandbox empty nest") // auto-chosen (score=1.00, CommonStepsSteps.scala)

    }

    Scenario("I have forgotten my password, and I want a link to reset it") {
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

      Given("I navigate to the Sign in page")
      givenINavigateToThePage("Sign in") // auto-chosen (score=0.88, CommonStepsSteps.scala)

      Given("I click on the button with id forgottenPassword")
      whenIClickOnTheButtonWithId("forgottenPassword") // auto-chosen (score=0.91, CommonStepsSteps.scala)

      And("I enter all the fields")
      givenIEnterAllTheFields(
        Map(
          "email address" -> "john.smith@example.com"
        )
      ) // auto-chosen (score=1.00, CommonStepsSteps.scala)

      When("I click on the button with id submit")
      whenIClickOnTheButtonWithId("submit") // auto-chosen (score=0.91, CommonStepsSteps.scala)

      Then("I am on the Password reset confirmation page")
      thenIAmOnThePage("Password reset confirmation") // auto-chosen (score=0.88, CommonStepsSteps.scala)

      Then("I should be sent an email with a link to reset for john.smith@example.com")
      thenIShouldBeSentAnEmailWithALinkToResetFor("john.smith@example.com") // auto-chosen (score=1.00, LoginStepsSteps.scala)
    }

    Scenario("I have a password reset link and I want to reset my password") {
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

      Given("I click on a valid password reset link for code 1234")
      givenIClickOnAValidPasswordResetLinkForCode("1234") // auto-chosen (score=0.94, LoginStepsSteps.scala)

      Then("I am on the Reset Password page with code 1234")
      thenIAmOnTheResetPasswordPageWithCode("1234") // auto-chosen (score=0.93, LoginStepsSteps.scala)

      When("I enter all the fields")
      givenIEnterAllTheFields(
        Map(
          "password"        -> "StrongNewPwd!2",
          "confirmpassword" -> "StrongNewPwd!2"
        )
      ) // auto-chosen (score=1.00, CommonStepsSteps.scala)

      And("I click on submit")
      whenIClickOnSubmit() // auto-chosen (score=1.00, RegisterStepsSteps.scala)

      Then("I am on the You have reset your password page")
      thenIAmOnThePage("You have reset your password") // auto-chosen (score=0.88, CommonStepsSteps.scala)
    }

    Scenario("I have an invalid password reset link and I want to reset my password") {
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

      Given("I click on an invalid password reset link for code 9876")
      givenIClickOnAnInvalidPasswordResetLinkForCode("9876") // auto-chosen (score=0.94, LoginStepsSteps.scala)

      Then("I am on the Reset password link no longer valid page")
      thenIAmOnThePage("Reset password link no longer valid") // auto-chosen (score=0.88, CommonStepsSteps.scala)

    }
  }
}

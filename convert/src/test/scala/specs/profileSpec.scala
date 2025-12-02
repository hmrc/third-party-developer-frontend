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
import ProfileStepsSteps._

class profileSpec extends BaseSpec {

  Feature("Developer views/updates profile") {

    Scenario("TPSD edits profile") {
      Given("I want to successfully change my profile")
        givenIWantToSuccessfullyChangeMyProfile()  // auto-chosen (score=1.00, ProfileStepsSteps.scala)

      When("I click on the button with id change")
        whenIClickOnTheButtonWithId("change")  // auto-chosen (score=0.91, CommonStepsSteps.scala)

      Then("I am on the Change profile details page")
        thenIAmOnThePage("Change profile details")  // auto-chosen (score=0.88, CommonStepsSteps.scala)

      When("I enter all the fields:")
        givenIEnterAllTheFields(null)  // auto-chosen (score=1.00, CommonStepsSteps.scala)

      And("I click on submit")
        whenIClickOnSubmit()  // auto-chosen (score=1.00, RegisterStepsSteps.scala)

      Then("I am on the Manage profile page")
        thenIAmOnThePage("Manage profile")  // auto-chosen (score=0.88, CommonStepsSteps.scala)

      And("the user-nav header contains a Joe Bloggs link")
        thenTheUserNavHeaderContainsALink("Joe Bloggs")  // auto-chosen (score=0.76, CommonStepsSteps.scala)

      And("The current page contains link Continue to your profile to Manage profile")
        thenTheCurrentPageContainsLinkTo("Continue to your profile", "Manage profile")  // auto-chosen (score=0.83, CommonStepsSteps.scala)

    }

    Scenario("TPSD edits password") {
      Given("I want to successfully change my password")
        givenIWantToSuccessfullyChangeMyPassword()  // auto-chosen (score=1.00, ProfileStepsSteps.scala)

      When("I click on the Change password link")
        // ⚠️ No step-def match found for: I click on the Change password link

      When("I enter all the fields:")
        givenIEnterAllTheFields(null)  // auto-chosen (score=1.00, CommonStepsSteps.scala)

      And("I click on submit")
        whenIClickOnSubmit()  // auto-chosen (score=1.00, RegisterStepsSteps.scala)

      Then("I am on the Edit password success page")
        thenIAmOnThePage("Edit password success")  // auto-chosen (score=0.88, CommonStepsSteps.scala)

    }
  }
}

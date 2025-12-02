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

class logoutSpec extends BaseSpec {

  Feature("Logout") {

    Scenario("TPDF should respond properly if logout fails") {
      Given("I navigate to the Sign in page")
        givenINavigateToThePage("Sign in")  // auto-chosen (score=0.88, CommonStepsSteps.scala)

      And("I successfully log in with john.smith@example.com and StrongPassword1! skipping 2SV")
        givenISuccessfullyLogInWithAndSkipping2SV("john.smith@example.com", "StrongPassword1!")  // auto-chosen (score=1.00, LoginStepsSteps.scala)

      And("I am on the No Applications page")
        thenIAmOnThePage("No Applications")  // auto-chosen (score=0.88, CommonStepsSteps.scala)

      When("I attempt to Sign out when the session expires")
        whenIAttemptToSignOutWhenTheSessionExpires()  // auto-chosen (score=1.00, LoginStepsSteps.scala)

      Then("I am not logged in")
        thenIAmNotLoggedIn()  // auto-chosen (score=1.00, LoginStepsSteps.scala)

    }
  }
}

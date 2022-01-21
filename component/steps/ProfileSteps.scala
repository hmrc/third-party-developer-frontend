/*
 * Copyright 2020 HM Revenue & Customs
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

package steps

import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.UpdateProfileRequest
import io.cucumber.scala.{EN, ScalaDsl}
import org.openqa.selenium.WebDriver
import pages._
import play.api.http.Status._
import stubs.{DeveloperStub, Stubs}
import org.scalatest.matchers.should.Matchers

class ProfileSteps extends ScalaDsl with EN with Matchers with NavigationSugar {
  implicit val webDriver: WebDriver = Env.driver

  Given( """^I want to successfully change my profile$""") { () =>
    // Pulling the user id from the developer in the test context defined in LoginSteps
    val userId = TestContext.developer.userId
    DeveloperStub.update(userId, UpdateProfileRequest("Joe", "Bloggs", None), OK)
  }

  Given( """^I want to successfully change my password""") { () =>
    Stubs.setupPostRequest("/change-password", NO_CONTENT)
  }
}

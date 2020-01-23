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

package unit.views

import config.ApplicationConfig
import domain.{Environment, LoggedInState}
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.OneServerPerSuite
import play.api.i18n.Messages.Implicits._
import play.api.test.FakeRequest
import uk.gov.hmrc.play.test.UnitSpec
import utils.CSRFTokenHelper._
import utils.SharedMetricsClearDown
import utils.ViewHelpers._

class AddApplicationSuccessSpec extends UnitSpec with OneServerPerSuite with SharedMetricsClearDown with MockitoSugar {

  val sandboxMessage = "You can now get your sandbox credentials for testing."
  val sandboxButton = "Manage API subscriptions"
  val appConfig = mock[ApplicationConfig]

  "Add application success page" should {

    def testPage(applicationName: String, environment: Environment): Document = {
      val applicationId = "application-id"
      val loggedIn = utils.DeveloperSession("", "", "", None, loggedInState = LoggedInState.LOGGED_IN)
      val request = FakeRequest().withCSRFToken
      val page = views.html.addApplicationSuccess.render(applicationName, applicationId, environment, request, loggedIn, applicationMessages, appConfig, navSection = "nav-section")
      val document = Jsoup.parse(page.body)
      elementExistsByText(document, "h1", s"You added $applicationName") shouldBe true
      document
    }

    "allow manage API subscriptions for sandbox application" in {
      val applicationName = "an application name"
      val document = testPage(applicationName, Environment.SANDBOX)
      elementExistsByText(document, "p", sandboxMessage) shouldBe true
      elementExistsByText(document, "a", sandboxButton) shouldBe true
    }
  }
}

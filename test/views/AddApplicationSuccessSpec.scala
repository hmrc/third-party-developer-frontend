/*
 * Copyright 2023 HM Revenue & Customs
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

package views

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import views.helper.CommonViewSpec
import views.html.AddApplicationSuccessView

import play.api.test.FakeRequest

import uk.gov.hmrc.apiplatform.modules.common.domain.models.{ApplicationId, Environment}
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.{LoggedInState, UserSession}
import uk.gov.hmrc.apiplatform.modules.tpd.test.data.UserTestData
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.DeveloperSessionBuilder
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.ViewHelpers._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithCSRFAddToken
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.ApplicationName
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.ApplicationNameFixtures

class AddApplicationSuccessSpec extends CommonViewSpec with ApplicationNameFixtures
    with WithCSRFAddToken
    with LocalUserIdTracker
    with DeveloperSessionBuilder
    with UserTestData {

  val addApplicationSuccess = app.injector.instanceOf[AddApplicationSuccessView]
  val sandboxMessage        = "You can now get your sandbox credentials for testing."
  val sandboxButton         = "Manage API subscriptions"

  "Add application success page" should {

    def testPage(applicationName: ApplicationName, environment: Environment): Document = {
      val applicationId = ApplicationId.random
      val loggedIn      = standardDeveloper.loggedIn
      val request       = FakeRequest().withCSRFToken
      val page          = addApplicationSuccess.render(applicationName, applicationId, environment, request, loggedIn, messagesProvider, appConfig, navSection = "nav-section")
      val document      = Jsoup.parse(page.body)
      elementExistsByText(document, "h1", s"You added $applicationName") shouldBe true
      document
    }

    "allow manage API subscriptions for sandbox application" in {
      val document        = testPage(appNameOne, Environment.SANDBOX)
      elementExistsByText(document, "p", sandboxMessage) shouldBe true
      elementExistsByText(document, "a", sandboxButton) shouldBe true
    }
  }
}

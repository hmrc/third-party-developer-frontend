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
import views.helper.CommonViewSpec
import views.html.UnsubscribeRequestSubmittedView

import play.api.test.FakeRequest

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.{ApplicationState, State}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{ApiVersionNbr, ApplicationId, ClientId, Environment}
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.{LoggedInState, UserSession}
import uk.gov.hmrc.apiplatform.modules.tpd.test.builders.UserBuilder
import uk.gov.hmrc.apiplatform.modules.tpd.test.data.SampleUserSession
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.{DeveloperSessionBuilder, _}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers.ApplicationViewModel
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.ViewHelpers._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils._

class UnsubscribeRequestSubmittedSpec extends CommonViewSpec with WithCSRFAddToken with CollaboratorTracker with LocalUserIdTracker with DeveloperSessionBuilder
    with SampleUserSession
    with SampleApplication with UserBuilder {
  "Unsubscribe request submitted page" should {
    "render with no errors" in {

      val request = FakeRequest().withCSRFToken

      val appId       = ApplicationId.random
      val apiName     = "Test API"
      val apiVersion  = ApiVersionNbr("1.0")
      val clientId    = ClientId("clientId123")
      val developer   = buildUser("email@example.com".toLaxEmail, "First Name", "Last Name").loggedIn
      val application = sampleApp

      val unsubscribeRequestSubmittedView = app.injector.instanceOf[UnsubscribeRequestSubmittedView]

      val page =
        unsubscribeRequestSubmittedView.render(
          ApplicationViewModel(application, false, false),
          apiName,
          apiVersion,
          request,
          developer,
          messagesProvider,
          appConfig,
          "subscriptions"
        )
      page.contentType should include("text/html")

      val document = Jsoup.parse(page.body)
      elementExistsByText(document, "h1", "Request submitted") shouldBe true
      elementExistsById(document, "success-request-unsubscribe-text") shouldBe true
    }
  }
}

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
import views.html.RequestDeleteApplicationCompleteView

import play.api.test.FakeRequest

import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models.Access
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.{ApplicationState, State}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{ApplicationId, ClientId, Environment}
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.{LoggedInState, UserSession}
import uk.gov.hmrc.apiplatform.modules.tpd.test.data.{SampleUserSession, UserTestData}
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.{DeveloperSessionBuilder, _}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.ViewHelpers.elementExistsByText
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithCSRFAddToken

class RequestDeleteApplicationCompleteSpec
    extends CommonViewSpec
    with WithCSRFAddToken
    with DeveloperSessionBuilder
    with UserTestData
    with SampleUserSession
    with SampleApplication
    with LocalUserIdTracker {

  val requestDeleteApplicationCompleteView: RequestDeleteApplicationCompleteView = app.injector.instanceOf[RequestDeleteApplicationCompleteView]

  "delete application complete page" should {
    "render with no errors" in {

      val request = FakeRequest().withCSRFToken

      val appId             = ApplicationId.random
      val clientId          = ClientId("clientId123")
      val loggedInDeveloper = standardDeveloper.loggedIn
      val application       = sampleApp

      val page = requestDeleteApplicationCompleteView.render(application, request, loggedInDeveloper, messagesProvider, appConfig)
      page.contentType should include("text/html")

      val document = Jsoup.parse(page.body)

      elementExistsByText(document, "h1", "Request submitted") shouldBe true
    }
  }

}

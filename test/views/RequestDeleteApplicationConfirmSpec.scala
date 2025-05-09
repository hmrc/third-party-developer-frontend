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

import java.time.Period

import org.jsoup.Jsoup
import views.helper.CommonViewSpec
import views.html.RequestDeleteApplicationConfirmView

import play.api.test.FakeRequest

import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models._
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.{ApplicationState, ApplicationWithCollaboratorsFixtures, RedirectUri, State}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{ApplicationId, ClientId, Environment}
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.{LoggedInState, UserSession}
import uk.gov.hmrc.apiplatform.modules.tpd.test.data.UserTestData
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.DeveloperSessionBuilder
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.DeleteApplicationForm
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.ViewHelpers.{elementExistsByText, elementIdentifiedByAttrWithValueContainsText}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{CollaboratorTracker, WithCSRFAddToken}

class RequestDeleteApplicationConfirmSpec extends CommonViewSpec with WithCSRFAddToken with LocalUserIdTracker with DeveloperSessionBuilder with UserTestData
    with CollaboratorTracker
    with ApplicationWithCollaboratorsFixtures
    with FixedClock {

  val requestDeleteApplicationConfirmView = app.injector.instanceOf[RequestDeleteApplicationConfirmView]

  "delete application confirm page" should {

    val request           = FakeRequest().withCSRFToken
    val appId             = ApplicationId.random
    val clientId          = ClientId("clientId123")
    val loggedInDeveloper = standardDeveloper.loggedIn
    val application       = standardApp

    "render with no errors" in {

      val page = requestDeleteApplicationConfirmView.render(application, DeleteApplicationForm.form, request, loggedInDeveloper, messagesProvider, appConfig)
      page.contentType should include("text/html")

      val document = Jsoup.parse(page.body)
      elementExistsByText(document, "h1", "Delete application") shouldBe true
      elementExistsByText(document, "h2", "Are you sure you want us to delete this application?") shouldBe true
      elementIdentifiedByAttrWithValueContainsText(document, "label", "for", "deleteConfirm", "Yes") shouldBe true
      elementIdentifiedByAttrWithValueContainsText(document, "label", "for", "no", "No") shouldBe true
    }

    "render with error when no radio button has been selected" in {

      val formWithErrors = DeleteApplicationForm.form.withError("confirmation", "Confirmation error message")

      val page = requestDeleteApplicationConfirmView.render(application, formWithErrors, request, loggedInDeveloper, messagesProvider, appConfig)
      page.contentType should include("text/html")

      val document = Jsoup.parse(page.body)
      document.body().toString.contains("Confirmation error message") shouldBe true
      elementExistsByText(document, "h1", "Delete application") shouldBe true
      elementExistsByText(document, "h2", "Are you sure you want us to delete this application?") shouldBe true
      elementIdentifiedByAttrWithValueContainsText(document, "label", "for", "deleteConfirm", "Yes") shouldBe true
      elementIdentifiedByAttrWithValueContainsText(document, "label", "for", "no", "No") shouldBe true
    }
  }
}

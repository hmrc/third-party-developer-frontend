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

package views.include

import org.jsoup.Jsoup
import views.helper.CommonViewSpec
import views.html.include.ChangeSubscriptionConfirmationView

import play.api.data.Form
import play.api.mvc.Call
import play.api.test.FakeRequest

import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models._
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.{ApplicationState, ApplicationWithCollaboratorsFixtures, RedirectUri, State}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{ApiContext, ApiVersionNbr, ApplicationId, ClientId, Environment}
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.{LoggedInState, UserSession}
import uk.gov.hmrc.apiplatform.modules.tpd.test.data.UserTestData
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.DeveloperSessionBuilder
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.ChangeSubscriptionConfirmationForm
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers.ApplicationViewModel
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.views.SubscriptionRedirect
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.ViewHelpers.elementExistsByText
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils._

class ChangeSubscriptionConfirmationSpec extends CommonViewSpec
    with WithCSRFAddToken
    with CollaboratorTracker
    with LocalUserIdTracker
    with DeveloperSessionBuilder
    with UserTestData
    with FixedClock
    with ApplicationWithCollaboratorsFixtures {

  val request         = FakeRequest().withCSRFToken
  val applicationId   = ApplicationId.random
  val clientId        = ClientId("clientId123")
  val applicationName = "Test Application"
  val apiName         = "Test API"
  val apiContext      = ApiContext("test")
  val apiVersion      = ApiVersionNbr("1.0")
  val callMock        = mock[Call]

  val loggedInDeveloper = standardDeveloper.loggedIn

  val application = standardApp

  def renderPage(form: Form[ChangeSubscriptionConfirmationForm], subscribed: Boolean) = {
    val changeSubscriptionConfirmationView = app.injector.instanceOf[ChangeSubscriptionConfirmationView]

    changeSubscriptionConfirmationView.render(
      ApplicationViewModel(application, hasSubscriptionsFields = false, hasPpnsFields = false),
      form,
      apiName,
      apiContext,
      apiVersion,
      subscribed,
      SubscriptionRedirect.API_SUBSCRIPTIONS_PAGE.toString,
      callMock,
      request,
      loggedInDeveloper,
      messagesProvider,
      appConfig,
      "details"
    )
  }

  "change subscription confirm page" when {
    "subscribing" should {
      val subscribed = false

      "render with no errors" in {
        val page = renderPage(ChangeSubscriptionConfirmationForm.form, subscribed)

        page.contentType should include("text/html")

        val document = Jsoup.parse(page.body)
        elementExistsByText(document, "h1", "Manage API subscriptions") shouldBe true
        elementExistsByText(document, "div", "For security reasons we must approve any API subscription changes. This takes up to 2 working days.") shouldBe true
        elementExistsByText(document, "h2", "Are you sure you want to request to subscribe to Test API 1.0?") shouldBe true
      }

      "render with error when no radio button has been selected" in {
        val formWithErrors = ChangeSubscriptionConfirmationForm.form.withError("subscribeConfirm", "Confirmation error message")

        val page = renderPage(formWithErrors, subscribed)

        page.contentType should include("text/html")

        val document = Jsoup.parse(page.body)
        document.body().toString.contains("Confirmation error message") shouldBe true

        elementExistsByText(document, "h1", "Manage API subscriptions") shouldBe true
        elementExistsByText(document, "div", "For security reasons we must approve any API subscription changes. This takes up to 2 working days.") shouldBe true
        elementExistsByText(document, "h2", "Are you sure you want to request to subscribe to Test API 1.0?") shouldBe true
      }
    }

    "unsubscribing" should {
      val subscribed = true

      "render with no errors" in {
        val page = renderPage(ChangeSubscriptionConfirmationForm.form, subscribed)

        page.contentType should include("text/html")

        val document = Jsoup.parse(page.body)
        elementExistsByText(document, "h1", "Manage API subscriptions") shouldBe true
        elementExistsByText(document, "div", "For security reasons we must approve any API subscription changes. This takes up to 2 working days.") shouldBe true
        elementExistsByText(document, "h2", "Are you sure you want to request to unsubscribe from Test API 1.0?") shouldBe true
      }

      "render with error when no radio button has been selected" in {
        val formWithErrors = ChangeSubscriptionConfirmationForm.form.withError("subscribeConfirm", "Confirmation error message")

        val page = renderPage(formWithErrors, subscribed)

        page.contentType should include("text/html")

        val document = Jsoup.parse(page.body)
        document.body().toString.contains("Confirmation error message") shouldBe true

        elementExistsByText(document, "h1", "Manage API subscriptions") shouldBe true
        elementExistsByText(document, "div", "For security reasons we must approve any API subscription changes. This takes up to 2 working days.") shouldBe true
        elementExistsByText(document, "h2", "Are you sure you want to request to unsubscribe from Test API 1.0?") shouldBe true
      }
    }
  }
}

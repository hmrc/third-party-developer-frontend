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

package unit.views.include

import config.ApplicationConfig
import controllers.ChangeSubscriptionConfirmationForm
import domain.{SubscriptionRedirect, _}
import org.jsoup.Jsoup
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.OneServerPerSuite
import play.api.data.Form
import play.api.i18n.Messages.Implicits._
import play.api.test.FakeRequest
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.time.DateTimeUtils
import utils.CSRFTokenHelper._
import utils.SharedMetricsClearDown
import utils.ViewHelpers.elementExistsByText

class ChangeSubscriptionConfirmationSpec extends UnitSpec with OneServerPerSuite with SharedMetricsClearDown with MockitoSugar {

  val appConfig = mock[ApplicationConfig]
  val request = FakeRequest().withCSRFToken

  val applicationId = "1234"
  val clientId = "clientId123"
  val applicationName = "Test Application"
  val apiName = "Test API"
  val apiContext = "test"
  val apiVersion = "1.0"

  val loggedInUser = utils.DeveloperSession("givenname.familyname@example.com", "Givenname", "Familyname", loggedInState = LoggedInState.LOGGED_IN)

  val application = Application(applicationId, clientId, applicationName, DateTimeUtils.now, DateTimeUtils.now, Environment.PRODUCTION, Some("Description 1"),
    Set(Collaborator(loggedInUser.email, Role.ADMINISTRATOR)), state = ApplicationState.production(loggedInUser.email, ""),
    access = Standard(redirectUris = Seq("https://red1", "https://red2"), termsAndConditionsUrl = Some("http://tnc-url.com")))


  def renderPage(form: Form[ChangeSubscriptionConfirmationForm], subscribed: Boolean) = {
    views.html.include.changeSubscriptionConfirmation.render(
      application,
      form,
      apiName,
      apiContext,
      apiVersion,
      subscribed,
      SubscriptionRedirect.API_SUBSCRIPTIONS_PAGE.toString,
      request,
      loggedInUser,
      applicationMessages,
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
        elementExistsByText(document, "p", "For security reasons we must approve any API subscription changes. This takes up to 2 working days.") shouldBe true
        elementExistsByText(document, "h2", "Are you sure you want to request to subscribe to Test API 1.0?") shouldBe true
      }

      "render with error when no radio button has been selected" in {
        val formWithErrors = ChangeSubscriptionConfirmationForm.form.withError("subscribeConfirm", "Confirmation error message")

        val page = renderPage(formWithErrors, subscribed)

        page.contentType should include("text/html")

        val document = Jsoup.parse(page.body)
        document.body().toString.contains("Confirmation error message") shouldBe true

        elementExistsByText(document, "h1", "Manage API subscriptions") shouldBe true
        elementExistsByText(document, "p", "For security reasons we must approve any API subscription changes. This takes up to 2 working days.") shouldBe true
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
        elementExistsByText(document, "p", "For security reasons we must approve any API subscription changes. This takes up to 2 working days.") shouldBe true
        elementExistsByText(document, "h2", "Are you sure you want to request to unsubscribe from Test API 1.0?") shouldBe true
      }

      "render with error when no radio button has been selected" in {
        val formWithErrors = ChangeSubscriptionConfirmationForm.form.withError("subscribeConfirm", "Confirmation error message")

        val page = renderPage(formWithErrors, subscribed)

        page.contentType should include("text/html")

        val document = Jsoup.parse(page.body)
        document.body().toString.contains("Confirmation error message") shouldBe true

        elementExistsByText(document, "h1", "Manage API subscriptions") shouldBe true
        elementExistsByText(document, "p", "For security reasons we must approve any API subscription changes. This takes up to 2 working days.") shouldBe true
        elementExistsByText(document, "h2", "Are you sure you want to request to unsubscribe from Test API 1.0?") shouldBe true
      }
    }
  }
}

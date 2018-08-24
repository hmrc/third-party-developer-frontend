/*
 * Copyright 2018 HM Revenue & Customs
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
import controllers.UnsubscribeConfirmationForm
import domain.SubscriptionRedirect
import domain._
import org.jsoup.Jsoup
import org.scalatestplus.play.OneServerPerSuite
import play.api.i18n.Messages.Implicits._
import play.api.test.FakeRequest
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.time.DateTimeUtils
import utils.CSRFTokenHelper._
import utils.ViewHelpers.elementExistsByText

class UnsubscribeConfirmationSpec extends UnitSpec with OneServerPerSuite {

  "unsubscribe confirm page" should {
      val request = FakeRequest().withCSRFToken

      val applicationId = "1234"
      val clientId = "clientId123"
      val applicationName = "Test Application"
      val apiName = "Test API"
      val apiContext = "test"
      val apiVersion = "1.0"

      val loggedInUser = Developer("givenname.familyname@example.com", "Givenname", "Familyname")

      val application = Application(applicationId, clientId, applicationName, DateTimeUtils.now, Environment.PRODUCTION, Some("Description 1"),
        Set(Collaborator(loggedInUser.email, Role.ADMINISTRATOR)), state = ApplicationState.production(loggedInUser.email, ""),
        access = Standard(redirectUris = Seq("https://red1", "https://red2"), termsAndConditionsUrl = Some("http://tnc-url.com")))

    "render with no errors" in {
      val page = views.html.include.unsubscribeConfirmation.render(
        application,
        UnsubscribeConfirmationForm.form,
        apiName,
        apiContext,
        apiVersion,
        SubscriptionRedirect.API_SUBSCRIPTIONS_PAGE.toString,
        request,
        loggedInUser,
        applicationMessages,
        ApplicationConfig,
        "subscriptions"
      )

      page.contentType should include("text/html")

      val document = Jsoup.parse(page.body)
      elementExistsByText(document, "h1", "Manage API subscriptions") shouldBe true
      elementExistsByText(document, "p", "For security reasons we must approve any API subscription changes. This takes up to 2 working days.") shouldBe true
      elementExistsByText(document, "h2", "Are you sure you want to request to unsubscribe from Test API 1.0?") shouldBe true
    }

    "render with error when no radio button has been selected" in {
      val formWithErrors = UnsubscribeConfirmationForm.form.withError("unsubscribeConfirm", "Confirmation error message")

      val page = views.html.include.unsubscribeConfirmation.render(
        application,
        formWithErrors,
        apiName,
        apiContext,
        apiVersion,
        SubscriptionRedirect.API_SUBSCRIPTIONS_PAGE.toString,
        request,
        loggedInUser,
        applicationMessages,
        ApplicationConfig,
        "subscriptions"
      )

      page.contentType should include("text/html")

      val document = Jsoup.parse(page.body)
      document.body().toString.contains("Confirmation error message") shouldBe true

      elementExistsByText(document, "h1", "Manage API subscriptions") shouldBe true
      elementExistsByText(document, "p", "For security reasons we must approve any API subscription changes. This takes up to 2 working days.") shouldBe true
      elementExistsByText(document, "h2", "Are you sure you want to request to unsubscribe from Test API 1.0?") shouldBe true
    }
  }
}

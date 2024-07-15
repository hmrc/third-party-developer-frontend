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
import views.html.ForgotPasswordView

import play.api.data.Form
import play.api.test.{FakeRequest, StubMessagesFactory}

import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.{DeveloperSession, LoggedInState}
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.{DeveloperBuilder, DeveloperSessionBuilder}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.ForgotPasswordForm
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.ViewHelpers._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{LocalUserIdTracker, WithCSRFAddToken}

class ForgotPasswordViewSpec extends CommonViewSpec
    with WithCSRFAddToken
    with LocalUserIdTracker
    with StubMessagesFactory {
  // with DeveloperSessionBuilder
  // with DeveloperBuilder {

  val forgotPasswordView: ForgotPasswordView = app.injector.instanceOf[ForgotPasswordView]

  // val loggedInDeveloper: DeveloperSession =buildDeveloperWithRandomId("admin@example.com".toLaxEmail, "firstName1", "lastName1").loggedIn

  "Forgot Password page" should {

    def renderPage(form: Form[ForgotPasswordForm]) = {
      val request = FakeRequest().withCSRFToken
      forgotPasswordView.render(form, request, stubMessages(), appConfig)
    }

    "render as expected" in {
      val document = Jsoup.parse(renderPage(form = ForgotPasswordForm.form).body)

      document.getElementById("reset-your-password-heading").text() shouldBe "Reset your password"

      document.getElementById("emailaddress-note-1").text() shouldBe "We have received a request to set up or change your password."
      document.getElementById("emailaddress-note-2").text() shouldBe "Give us your email address and we will send you a link to reset the password."
      document.getElementById("emailaddress-note-3").text() shouldBe "The link will expire in one hour."

      elementExistsById(document, "submit") shouldBe true
      document.getElementById("submit").text() shouldBe "Send password reset email"
    }

    "show an error when email address is invalid" in {
      val error       = "Email error"
      val invalidForm = ForgotPasswordForm.form.withError("emailaddress", error)
      val document    = Jsoup.parse(renderPage(form = invalidForm).body)
      elementExistsById(document, "data-field-error-emailaddress") shouldBe true
    }

  }
}

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

package uk.gov.hmrc.apiplatform.modules.mfa.views.authapp

import java.util.UUID

import org.jsoup.Jsoup
import views.helper.CommonViewSpec

import play.api.test.{FakeRequest, StubMessagesFactory}

import uk.gov.hmrc.apiplatform.modules.mfa.forms.MfaAccessCodeForm
import uk.gov.hmrc.apiplatform.modules.mfa.models.{MfaAction, MfaId}
import uk.gov.hmrc.apiplatform.modules.mfa.views.html.authapp.AuthAppAccessCodeView
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.{DeveloperSessionBuilder, DeveloperTestData}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.{DeveloperSession, LoggedInState}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{LocalUserIdTracker, WithCSRFAddToken}
import play.api.mvc.AnyContentAsEmpty

class AuthAppAccessCodeViewSpec extends CommonViewSpec
    with WithCSRFAddToken
    with DeveloperTestData
    with DeveloperSessionBuilder
    with LocalUserIdTracker
    with StubMessagesFactory {

  implicit val request: FakeRequest[AnyContentAsEmpty.type]      = FakeRequest()
  val authAppAccessCodeView = app.injector.instanceOf[AuthAppAccessCodeView]

  implicit val loggedIn: DeveloperSession = JoeBloggs.loggedIn

  "AuthAppAccessCodeView view" should {
    "render correctly when form is valid" in {

      val mainView =
        authAppAccessCodeView.apply(MfaAccessCodeForm.form, MfaId(UUID.randomUUID()), MfaAction.CREATE, None)(stubMessages(), FakeRequest().withCSRFToken, loggedIn, appConfig)
      val document = Jsoup.parse(mainView.body)
      document.getElementById("page-heading").text shouldBe "Enter your access code"
      document.getElementById("submit").text shouldBe "Continue"
      Option(document.getElementById("data-field-error-accessCode")) shouldBe None
    }

    "render correctly when form is invalid" in {

      val mainView = authAppAccessCodeView.apply(
        MfaAccessCodeForm.form.withError("accessCode", "You have entered an incorrect access code"),
        MfaId(UUID.randomUUID()),
        MfaAction.CREATE,
        None
      )(stubMessages(), FakeRequest().withCSRFToken, loggedIn, appConfig)
      val document = Jsoup.parse(mainView.body)
      document.getElementById("page-heading").text shouldBe "Enter your access code"
      document.getElementById("submit").text shouldBe "Continue"
      document.getElementById("data-field-error-accessCode").text() shouldBe "Error: You have entered an incorrect access code"
    }
  }
}

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

import play.api.mvc.AnyContentAsEmpty
import play.api.test.{FakeRequest, StubMessagesFactory}

import uk.gov.hmrc.apiplatform.modules.mfa.forms.MfaNameChangeForm
import uk.gov.hmrc.apiplatform.modules.mfa.views.html.authapp.NameChangeView
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.DeveloperSessionBuilder
import uk.gov.hmrc.apiplatform.modules.tpd.mfa.domain.models.MfaId
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.{DeveloperSession, LoggedInState}
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.apiplatform.modules.tpd.test.data.DeveloperTestData
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithCSRFAddToken

class NameChangeViewSpec extends CommonViewSpec with WithCSRFAddToken with DeveloperTestData with DeveloperSessionBuilder with LocalUserIdTracker with StubMessagesFactory {

  implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
  val nameChangeView                                        = app.injector.instanceOf[NameChangeView]
  implicit val loggedIn: DeveloperSession                   = JoeBloggs.loggedIn

  "NameChangeView view" should {
    "render correctly when form is valid" in {

      val mainView = nameChangeView.apply(MfaNameChangeForm.form, MfaId(UUID.randomUUID()))(stubMessages(), FakeRequest().withCSRFToken, loggedIn, appConfig)
      val document = Jsoup.parse(mainView.body)
      document.getElementById("page-heading").text shouldBe "Create a name for your authenticator app"
      document.getElementById("paragraph").text shouldBe "Use a name that will help you remember the app when you sign in."
      document.getElementById("name-label").text shouldBe "App Name"
      document.getElementById("submit").text shouldBe "Continue"
      Option(document.getElementById("data-field-error-accessCode")) shouldBe None
    }
    "render correctly when form is invalid" in {

      val mainView = nameChangeView.apply(MfaNameChangeForm.form.withError("name", "The name must be more than 3 characters in length"), MfaId(UUID.randomUUID()))(
        stubMessages(),
        FakeRequest().withCSRFToken,
        loggedIn,
        appConfig
      )
      val document = Jsoup.parse(mainView.body)
      document.getElementById("page-heading").text shouldBe "Create a name for your authenticator app"
      document.getElementById("paragraph").text shouldBe "Use a name that will help you remember the app when you sign in."
      document.getElementById("submit").text shouldBe "Continue"
      document.getElementById("data-field-error-name").text() shouldBe "Error: The name must be more than 3 characters in length"

    }

  }
}

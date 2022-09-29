/*
 * Copyright 2022 HM Revenue & Customs
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

import org.jsoup.Jsoup
import play.api.test.{FakeRequest, StubMessagesFactory}
import uk.gov.hmrc.apiplatform.modules.mfa.forms.MfaAccessCodeForm
import uk.gov.hmrc.apiplatform.modules.mfa.models.MfaId
import uk.gov.hmrc.apiplatform.modules.mfa.views.html.authapp.AccessCodeView
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.{DeveloperBuilder, DeveloperSessionBuilder}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.{DeveloperSession, LoggedInState}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{LocalUserIdTracker, WithCSRFAddToken}
import views.helper.CommonViewSpec

import java.util.UUID


class AccessCodeViewSpec extends CommonViewSpec with WithCSRFAddToken with DeveloperSessionBuilder with DeveloperBuilder with LocalUserIdTracker with StubMessagesFactory {
  implicit val request = FakeRequest()
  val accessCodeView = app.injector.instanceOf[AccessCodeView]
  implicit val loggedIn: DeveloperSession = buildDeveloperSession( loggedInState = LoggedInState.LOGGED_IN, buildDeveloper("developer@example.com", "Joe", "Bloggs"))

  "AccessCodeView view" should {
    "render correctly when form is valid" in {

      val mainView = accessCodeView.apply(MfaAccessCodeForm.form, MfaId(UUID.randomUUID()))(stubMessages(), FakeRequest().withCSRFToken, loggedIn, appConfig)
      val document = Jsoup.parse(mainView.body)
      document.getElementById("page-heading").text shouldBe "Enter your access code"
      document.getElementById("submit").text shouldBe "Continue"
      Option(document.getElementById("data-field-error-accessCode")) shouldBe None
    }
    "render correctly when form is invalid" in {

      val mainView = accessCodeView.apply(MfaAccessCodeForm.form.withError("accessCode" , "You have entered an incorrect access code"), MfaId(UUID.randomUUID()))(stubMessages(), FakeRequest().withCSRFToken, loggedIn, appConfig)
      val document = Jsoup.parse(mainView.body)
      document.getElementById("page-heading").text shouldBe "Enter your access code"
      document.getElementById("submit").text shouldBe "Continue"
      document.getElementById("data-field-error-accessCode").text() shouldBe "Error: You have entered an incorrect access code"


    }

  }
}

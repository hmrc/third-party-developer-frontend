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

import uk.gov.hmrc.apiplatform.modules.mfa.models.MfaId
import uk.gov.hmrc.apiplatform.modules.mfa.views.html.authapp.QrCodeView
import uk.gov.hmrc.apiplatform.modules.tpd.domain.models.{DeveloperSession, LoggedInState}
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.{DeveloperSessionBuilder, DeveloperTestData}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{LocalUserIdTracker, WithCSRFAddToken}

class QrCodeViewSpec extends CommonViewSpec with WithCSRFAddToken with DeveloperTestData with DeveloperSessionBuilder with LocalUserIdTracker with StubMessagesFactory {
  implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
  val qrCodeView                                            = app.injector.instanceOf[QrCodeView]
  implicit val loggedIn: DeveloperSession                   = JoeBloggs.loggedIn

  "QrCodeView view" should {
    "render correctly when form is valid" in {

      val mainView = qrCodeView.apply("secret", "qrcodeImg", MfaId(UUID.randomUUID()))(FakeRequest().withCSRFToken, loggedIn, appConfig, stubMessages())
      val document = Jsoup.parse(mainView.body)
      document.getElementById("page-heading").text shouldBe "Set up your authenticator app"
      document.getElementById("submit").text shouldBe "Continue"
    }

  }
}

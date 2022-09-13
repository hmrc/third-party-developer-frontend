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
import uk.gov.hmrc.apiplatform.modules.mfa.models.MfaId
import uk.gov.hmrc.apiplatform.modules.mfa.views.html.authapp.QrCodeView
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.{DeveloperBuilder, DeveloperSessionBuilder}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.{DeveloperSession, LoggedInState}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{LocalUserIdTracker, WithCSRFAddToken}
import views.helper.CommonViewSpec

import java.util.UUID


class QrCodeViewSpec extends CommonViewSpec with WithCSRFAddToken with DeveloperSessionBuilder with DeveloperBuilder with LocalUserIdTracker with StubMessagesFactory {
  implicit val request = FakeRequest()
  val qrCodeView = app.injector.instanceOf[QrCodeView]
  implicit val loggedIn: DeveloperSession = buildDeveloperSession( loggedInState = LoggedInState.LOGGED_IN, buildDeveloper("developer@example.com", "Joe", "Bloggs"))

  "QrCodeView view" should {
    "render correctly when form is valid" in {

      val mainView = qrCodeView.apply("secret", "qrcodeImg", MfaId(UUID.randomUUID()))( FakeRequest().withCSRFToken, loggedIn, appConfig, stubMessages())
      val document = Jsoup.parse(mainView.body)
      document.getElementById("page-heading").text shouldBe "Set up 2-step verification"
      document.getElementById("submit").text shouldBe "Continue"
    }


  }
}

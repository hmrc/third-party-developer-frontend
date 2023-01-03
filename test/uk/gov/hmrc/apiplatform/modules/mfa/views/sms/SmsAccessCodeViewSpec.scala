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

package uk.gov.hmrc.apiplatform.modules.mfa.views.sms

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import play.api.mvc.Flash
import play.api.test.{FakeRequest, StubMessagesFactory}
import uk.gov.hmrc.apiplatform.modules.mfa.forms.SmsAccessCodeForm
import uk.gov.hmrc.apiplatform.modules.mfa.models.{MfaAction, MfaId}
import uk.gov.hmrc.apiplatform.modules.mfa.views.html.sms.SmsAccessCodeView
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.{DeveloperBuilder, DeveloperSessionBuilder}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.{DeveloperSession, LoggedInState}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{LocalUserIdTracker, WithCSRFAddToken}
import views.helper.CommonViewSpec

import java.util.UUID

class SmsAccessCodeViewSpec extends CommonViewSpec
  with WithCSRFAddToken with DeveloperSessionBuilder
  with DeveloperBuilder with LocalUserIdTracker with StubMessagesFactory {

  implicit val flash = Flash(Map("mobileNumber" -> "0123456789"))
  implicit val request = FakeRequest()
  implicit val loggedIn: DeveloperSession = buildDeveloperSession(loggedInState = LoggedInState.LOGGED_IN,
    buildDeveloper("developer@example.com", "Joe", "Bloggs"))
  val smsAccessCodeView = app.injector.instanceOf[SmsAccessCodeView]

  trait Setup {
    val mobileNumber = "0123456789"

    def verifyPageElements(document: Document) = {
      document.getElementById("page-heading").text shouldBe "Enter the access code"
      document.getElementById("submit").text shouldBe "Continue"
    }
  }

  "SmsAccessCodeView" should {
    "render correctly when form is valid" in new Setup {
      val mainView = smsAccessCodeView.apply(SmsAccessCodeForm.form, MfaId(UUID.randomUUID()),
        MfaAction.CREATE, None)(flash, stubMessages(), FakeRequest().withCSRFToken, loggedIn, appConfig)
      val document = Jsoup.parse(mainView.body)

      verifyPageElements(document)
      Option(document.getElementById("data-field-error-accessCode")) shouldBe None
    }

    "render correctly when form is invalid" in new Setup {
      val mainView = smsAccessCodeView.apply(SmsAccessCodeForm.form.withError("accessCode",
        "You have entered an incorrect access code"),
        MfaId(UUID.randomUUID()), MfaAction.CREATE, None)(flash, stubMessages(), FakeRequest().withCSRFToken, loggedIn, appConfig)
      val document = Jsoup.parse(mainView.body)

      verifyPageElements(document)
      document.getElementById("data-field-error-accessCode").text() shouldBe "Error: You have entered an incorrect access code"
    }
  }
}

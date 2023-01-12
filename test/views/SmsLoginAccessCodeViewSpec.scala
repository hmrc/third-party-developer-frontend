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
import org.jsoup.nodes.Document
import org.scalatest.Assertion
import play.api.mvc.{AnyContentAsEmpty, Flash}
import play.api.test.{FakeRequest, StubMessagesFactory}
import play.twirl.api.Html
import uk.gov.hmrc.apiplatform.modules.mfa.forms.MfaAccessCodeForm
import uk.gov.hmrc.apiplatform.modules.mfa.models.{MfaId, MfaType}
import uk.gov.hmrc.apiplatform.modules.mfa.views.html.sms.SmsLoginAccessCodeView
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.{DeveloperBuilder, DeveloperSessionBuilder}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{LocalUserIdTracker, WithCSRFAddToken}
import views.helper.CommonViewSpec

import java.util.UUID

class SmsLoginAccessCodeViewSpec extends CommonViewSpec
    with WithCSRFAddToken with DeveloperSessionBuilder
    with DeveloperBuilder with LocalUserIdTracker with StubMessagesFactory {

  implicit val flash: Flash                                 = Flash(Map("mobileNumber" -> "0123456789"))
  implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

  val smsLoginAccessCodeView: SmsLoginAccessCodeView = app.injector.instanceOf[SmsLoginAccessCodeView]

  trait Setup {
    val mobileNumber = "0123456789"

    def verifyPageElements(document: Document): Assertion = {
      document.getElementById("page-heading").text shouldBe "Enter the access code"
      document.getElementById("paragraph-1").text shouldBe "We have sent a 6 digit access code to 0123456789"
      document.getElementById("paragraph-2").text shouldBe "It may take a few minutes to arrive"
      document.getElementById("paragraph-3").text shouldBe "If you have a UK phone number your 6-digit code will arrive from the phone number 60 551."
      document.getElementById("rememberMe-label").text shouldBe "Remember me for 7 days"
      document.getElementById("submit").text shouldBe "Continue"
      Option(document.getElementById("try-another-option")) shouldBe None
    }
  }

  "SmsLoginAccessCodeView" should {

    "render correctly when form is valid" in new Setup {
      val mainView: Html = smsLoginAccessCodeView.apply(MfaAccessCodeForm.form, MfaId(UUID.randomUUID()), MfaType.SMS, userHasMultipleMfa = false)(
        flash,
        stubMessages(),
        FakeRequest().withCSRFToken,
        appConfig
      )

      val document: Document = Jsoup.parse(mainView.body)

      verifyPageElements(document)
      Option(document.getElementById("data-field-error-accessCode")) shouldBe None
    }

    "render correctly when form is invalid" in new Setup {
      val mainView: Html = smsLoginAccessCodeView.apply(
        MfaAccessCodeForm.form.withError("accessCode", "You have entered an incorrect access code"),
        MfaId(UUID.randomUUID()),
        MfaType.SMS,
        userHasMultipleMfa = false
      )(flash, stubMessages(), FakeRequest().withCSRFToken, appConfig)

      val document: Document = Jsoup.parse(mainView.body)

      verifyPageElements(document)
      document.getElementById("data-field-error-accessCode").text() shouldBe "Error: You have entered an incorrect access code"
    }
  }
}

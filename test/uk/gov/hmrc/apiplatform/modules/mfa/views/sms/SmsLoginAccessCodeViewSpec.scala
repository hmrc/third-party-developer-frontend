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

import java.util.UUID

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import views.helper.CommonViewSpec

import play.api.mvc.{AnyContentAsEmpty, Flash}
import play.api.test.{FakeRequest, StubMessagesFactory}

import uk.gov.hmrc.apiplatform.modules.mfa.forms.MfaAccessCodeForm
import uk.gov.hmrc.apiplatform.modules.mfa.views.html.sms.SmsLoginAccessCodeView
import uk.gov.hmrc.apiplatform.modules.tpd.mfa.domain.models.{MfaId, MfaType}
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.{DeveloperBuilder, DeveloperSessionBuilder}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{LocalUserIdTracker, WithCSRFAddToken}

class SmsLoginAccessCodeViewSpec extends CommonViewSpec
    with WithCSRFAddToken with StubMessagesFactory {

  implicit val flash: Flash                                 = Flash(Map("mobileNumber" -> "0123456789"))
  implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
  val smsLoginAccessCodeView: SmsLoginAccessCodeView        = app.injector.instanceOf[SmsLoginAccessCodeView]

  trait Setup {
    val mobileNumber = "0123456789"

    def verifyPageElements(document: Document) = {
      document.getElementById("page-heading").text shouldBe "Enter the access code"
      document.getElementById("try-another-option").text shouldBe "Problems receiving this code? Try another option"
      document.getElementById("submit").text shouldBe "Continue"
    }
  }

  "SmsLoginAccessCodeView" should {
    "render correctly when form is valid" in new Setup {
      val mainView = smsLoginAccessCodeView.apply(MfaAccessCodeForm.form, MfaId(UUID.randomUUID()), MfaType.SMS, userHasMultipleMfa = true)(
        flash,
        stubMessages(),
        FakeRequest().withCSRFToken,
        appConfig
      )

      val document = Jsoup.parse(mainView.body)

      verifyPageElements(document)
      Option(document.getElementById("data-field-error-accessCode")) shouldBe None
    }

    "render correctly when form is invalid" in new Setup {
      val mainView = smsLoginAccessCodeView.apply(
        MfaAccessCodeForm.form
          .withError("accessCode", "You have entered an incorrect access code"),
        MfaId(UUID.randomUUID()),
        MfaType.SMS,
        userHasMultipleMfa = true
      )(flash, stubMessages(), FakeRequest().withCSRFToken, appConfig)

      val document = Jsoup.parse(mainView.body)

      verifyPageElements(document)
      document.getElementById("data-field-error-accessCode").text() shouldBe "Error: You have entered an incorrect access code"
    }
  }
}

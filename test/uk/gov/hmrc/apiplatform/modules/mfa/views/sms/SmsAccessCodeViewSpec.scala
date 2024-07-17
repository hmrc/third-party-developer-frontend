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
import org.scalatest.Assertion
import views.helper.CommonViewSpec

import play.api.mvc.{AnyContentAsEmpty, Flash}
import play.api.test.{FakeRequest, StubMessagesFactory}
import play.twirl.api.HtmlFormat

import uk.gov.hmrc.apiplatform.modules.mfa.forms.SmsAccessCodeForm
import uk.gov.hmrc.apiplatform.modules.mfa.views.html.sms.SmsAccessCodeView
import uk.gov.hmrc.apiplatform.modules.tpd.builder.DeveloperSessionBuilder
import uk.gov.hmrc.apiplatform.modules.tpd.mfa.domain.models.MfaId
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.{DeveloperSession, LoggedInState}
import uk.gov.hmrc.apiplatform.modules.tpd.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.DeveloperTestData
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.mfa.MfaAction
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithCSRFAddToken

class SmsAccessCodeViewSpec extends CommonViewSpec
    with WithCSRFAddToken with DeveloperTestData with DeveloperSessionBuilder with LocalUserIdTracker with StubMessagesFactory {

  implicit val flash: Flash                                 = Flash(Map("mobileNumber" -> "0123456789"))
  implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
  implicit val loggedIn: DeveloperSession                   = JoeBloggs.loggedIn
  val smsAccessCodeView: SmsAccessCodeView                  = app.injector.instanceOf[SmsAccessCodeView]

  trait Setup {
    val mobileNumber = "0123456789"

    def verifyPageElements(document: Document): Assertion = {
      document.getElementById("page-heading").text shouldBe "Enter the access code"
      document.getElementById("submit").text shouldBe "Continue"
    }
  }

  "SmsAccessCodeView" should {
    "render correctly when form is valid" in new Setup {
      val mainView: HtmlFormat.Appendable =
        smsAccessCodeView.apply(SmsAccessCodeForm.form, MfaId(UUID.randomUUID()), MfaAction.CREATE, None)(flash, stubMessages(), FakeRequest().withCSRFToken, loggedIn, appConfig)
      val document: Document              = Jsoup.parse(mainView.body)

      verifyPageElements(document)
      Option(document.getElementById("data-field-error-accessCode")) shouldBe None
    }

    "render correctly when form is invalid" in new Setup {
      val mainView: HtmlFormat.Appendable = smsAccessCodeView.apply(
        SmsAccessCodeForm.form.withError("accessCode", "You have entered an incorrect access code"),
        MfaId(UUID.randomUUID()),
        MfaAction.CREATE,
        None
      )(flash, stubMessages(), FakeRequest().withCSRFToken, loggedIn, appConfig)
      val document: Document              = Jsoup.parse(mainView.body)

      verifyPageElements(document)
      document.getElementById("data-field-error-accessCode").text() shouldBe "Error: You have entered an incorrect access code"
    }
  }
}

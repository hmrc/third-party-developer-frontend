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
import play.api.test.{FakeRequest, StubMessagesFactory}
import uk.gov.hmrc.apiplatform.modules.mfa.forms.MobileNumberForm
import uk.gov.hmrc.apiplatform.modules.mfa.views.html.sms.MobileNumberView
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.{DeveloperBuilder, DeveloperSessionBuilder}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.{DeveloperSession, LoggedInState}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{LocalUserIdTracker, WithCSRFAddToken}
import views.helper.CommonViewSpec

class MobileNumberViewSpec extends CommonViewSpec with WithCSRFAddToken with DeveloperSessionBuilder
    with DeveloperBuilder with LocalUserIdTracker with StubMessagesFactory {

  implicit val request = FakeRequest()

  implicit val loggedIn: DeveloperSession =
    buildDeveloperSession(loggedInState = LoggedInState.LOGGED_IN, buildDeveloper("developer@example.com", "Joe", "Bloggs"))
  val mobileNumberView                    = app.injector.instanceOf[MobileNumberView]

  trait Setup {

    def verifyPageElements(document: Document) = {
      document.getElementById("page-heading").text shouldBe "Enter a mobile phone number"
      document.getElementById("mobileNumber-hint").text shouldBe "We will send an access code to this phone number by text message."
      document.getElementById("paragraph").text shouldBe "Include your area code. The UK area code is +44, for example, +448081570192."
      document.getElementById("mobileNumber-label").text shouldBe "Phone number including area code"
      document.getElementById("submit").text shouldBe "Continue"
    }
  }

  "MobileNumberView" should {

    "render correctly when form is valid" in new Setup {
      val mainView           = mobileNumberView.apply(MobileNumberForm.form)(stubMessages(), FakeRequest().withCSRFToken, loggedIn, appConfig)
      val document: Document = Jsoup.parse(mainView.body)
      verifyPageElements(document)
      Option(document.getElementById("data-field-error-mobileNumber")) shouldBe None
    }

    "render correctly when form is invalid" in new Setup {
      val mainView = mobileNumberView.apply(
        MobileNumberForm.form.withError("mobileNumber", "It must be a valid mobile number")
      )(stubMessages(), FakeRequest().withCSRFToken, loggedIn, appConfig)
      val document = Jsoup.parse(mainView.body)
      verifyPageElements(document)
      document.getElementById("data-field-error-mobileNumber").text() shouldBe "Error: It must be a valid mobile number"
    }
  }
}

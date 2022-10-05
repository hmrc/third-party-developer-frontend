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

package uk.gov.hmrc.apiplatform.modules.mfa.views

import org.jsoup.Jsoup
import play.api.mvc.AnyContentAsEmpty
import play.api.test.{FakeRequest, StubMessagesFactory}
import uk.gov.hmrc.apiplatform.modules.mfa.forms.SelectMfaForm
import uk.gov.hmrc.apiplatform.modules.mfa.views.html.SelectMfaView
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.{DeveloperBuilder, DeveloperSessionBuilder}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.{DeveloperSession, LoggedInState}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{LocalUserIdTracker, WithCSRFAddToken}
import views.helper.CommonViewSpec

import scala.collection.JavaConverters._

class SelectMfaViewSpec extends CommonViewSpec
  with WithCSRFAddToken
  with DeveloperSessionBuilder
  with DeveloperBuilder
  with LocalUserIdTracker with StubMessagesFactory {

  implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

  implicit val loggedIn: DeveloperSession = buildDeveloperSession(
    loggedInState =
      LoggedInState.LOGGED_IN,
    buildDeveloper("developer@example.com", "Joe", "Bloggs")
  )
  val selectMfaViewView: SelectMfaView = app.injector.instanceOf[SelectMfaView]

  "SelectMfaView" should {
    "render correctly with Text Message selected as default" in {
      val mainView = selectMfaViewView.apply(SelectMfaForm.form)(FakeRequest().withCSRFToken, loggedIn, appConfig, stubMessages())
      val document = Jsoup.parse(mainView.body)

      document.getElementById("page-heading").text shouldBe "How do you want to get access codes?"
      document.getElementById("sms-mfa-label").text shouldBe "Text message"
      document.getElementById("auth-app-mfa-label").text shouldBe "Authenticator app for smartphone or tablet"
      document.getElementById("sms-item-hint").text shouldBe "Get codes sent to a mobile phone."
      document.getElementById("auth-app-item-hint").text shouldBe
        "Get codes generated by an authenticator app on your mobile device such as a smartphone or tablet."

      document.getElementById("sms-mfa").attributes().asList().asScala.map(_.getKey).contains("checked") shouldBe true
      document.getElementById("auth-app-mfa").attributes().asList().asScala.map(_.getKey).contains("checked") shouldBe false
      document.getElementById("submit").text shouldBe "Continue"
    }
  }
}
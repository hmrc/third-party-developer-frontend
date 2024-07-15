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
import views.helper.CommonViewSpec
import views.html.AddApplicationNameView

import play.api.data.Form
import play.api.test.FakeRequest

import uk.gov.hmrc.apiplatform.modules.common.domain.models.{ApplicationId, Environment}
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.{LoggedInState, UserSession}
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder._
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.AddApplicationNameForm
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.ViewHelpers._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{LocalUserIdTracker, WithCSRFAddToken}

class AddApplicationNameSpec extends CommonViewSpec
    with WithCSRFAddToken
    with LocalUserIdTracker
    with DeveloperSessionBuilder
    with DeveloperTestData {

  val addApplicationNameView = app.injector.instanceOf[AddApplicationNameView]
  val loggedInDeveloper      = adminDeveloper.loggedIn
  val subordinateEnvironment = Environment.SANDBOX
  val appId                  = ApplicationId.random
  val principalEnvironment   = Environment.PRODUCTION

  "Add application page in subordinate" should {

    def renderPage(form: Form[AddApplicationNameForm]) = {
      val request = FakeRequest().withCSRFToken
      addApplicationNameView.render(form, subordinateEnvironment, request, loggedInDeveloper, messagesProvider, appConfig)
    }

    "show an error when application name is invalid" in {
      val error               = "An error"
      val formWithInvalidName = AddApplicationNameForm.form.withError("applicationName", error)
      val document            = Jsoup.parse(renderPage(formWithInvalidName).body)
      elementExistsById(document, "data-field-error-applicationName") shouldBe true
    }
  }
  "Add application page in principal" should {

    def renderPage(form: Form[AddApplicationNameForm]) = {
      val request = FakeRequest().withCSRFToken
      addApplicationNameView.render(form, principalEnvironment, request, loggedInDeveloper, messagesProvider, appConfig)
    }

    "show an error when application name is invalid" in {
      val error               = "An error"
      val formWithInvalidName = AddApplicationNameForm.form.withError("applicationName", error)
      val document            = Jsoup.parse(renderPage(formWithInvalidName).body)
      elementExistsById(document, "data-field-error-applicationName") shouldBe true
    }
  }
}

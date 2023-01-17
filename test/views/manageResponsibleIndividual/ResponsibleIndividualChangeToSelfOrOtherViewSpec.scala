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

package views.manageResponsibleIndividual

import org.jsoup.Jsoup
import views.helper.CommonViewSpec
import views.html.manageResponsibleIndividual.ResponsibleIndividualChangeToSelfOrOtherView

import play.api.test.FakeRequest

import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.{DeveloperBuilder, DeveloperSessionBuilder}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.ResponsibleIndividualChangeToSelfOrOtherForm
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.LoggedInState
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.ViewHelpers.inputExistsWithValue
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{LocalUserIdTracker, TestApplications, WithCSRFAddToken}

class ResponsibleIndividualChangeToSelfOrOtherViewSpec extends CommonViewSpec with WithCSRFAddToken
    with DeveloperBuilder with LocalUserIdTracker with DeveloperSessionBuilder with TestApplications {

  "Responsible Individual Change To Self or Other View" should {
    val application = anApplication()
    val view        = app.injector.instanceOf[ResponsibleIndividualChangeToSelfOrOtherView]

    def renderPage() = {
      val request = FakeRequest().withCSRFToken
      val session = buildDeveloperSession(LoggedInState.LOGGED_IN, buildDeveloper("admin@example.com", "firstName1", "lastName1"))
      val form    = ResponsibleIndividualChangeToSelfOrOtherForm.form()
      view.render(application, form, request, session, messagesProvider.messages, appConfig)
    }

    "display correct radio button inputs" in {
      val document = Jsoup.parse(renderPage().body)
      inputExistsWithValue(document, "whoOther", "radio", "other")
      inputExistsWithValue(document, "whoSelf", "radio", "self")
    }
  }
}

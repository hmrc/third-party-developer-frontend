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
import views.html.manageResponsibleIndividual.ResponsibleIndividualChangeToSelfConfirmedView

import play.api.test.FakeRequest

import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.{LoggedInState, UserSession}
import uk.gov.hmrc.apiplatform.modules.tpd.test.data.UserTestData
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.DeveloperSessionBuilder
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{CollaboratorTracker, TestApplications, WithCSRFAddToken}

class ResponsibleIndividualChangeToSelfConfirmedViewSpec extends CommonViewSpec with WithCSRFAddToken
    with LocalUserIdTracker with CollaboratorTracker with UserTestData with DeveloperSessionBuilder with TestApplications {

  "Responsible Individual Change To Self Confirmed View" should {
    val application = anApplication()
    val view        = app.injector.instanceOf[ResponsibleIndividualChangeToSelfConfirmedView]

    def renderPage() = {
      val request = FakeRequest().withCSRFToken
      val session = adminDeveloper.loggedIn

      view.render(application, request, session, messagesProvider.messages, appConfig)
    }

    "display name of application correctly" in {
      val document = Jsoup.parse(renderPage().body)

      document.selectFirst("#appName").text() shouldBe application.name
    }
  }
}

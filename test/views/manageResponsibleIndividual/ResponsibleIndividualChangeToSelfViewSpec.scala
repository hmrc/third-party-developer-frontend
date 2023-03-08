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
import views.html.manageResponsibleIndividual.ResponsibleIndividualChangeToSelfView

import play.api.test.FakeRequest

import uk.gov.hmrc.thirdpartydeveloperfrontend.builder._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.LoggedInState
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.ViewHelpers.{elementExistsByText, linkExistsWithHref}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{LocalUserIdTracker, TestApplications, WithCSRFAddToken, CollaboratorTracker}

class ResponsibleIndividualChangeToSelfViewSpec extends CommonViewSpec with WithCSRFAddToken
    with LocalUserIdTracker with CollaboratorTracker with DeveloperSessionBuilder with TestApplications with DeveloperTestData {

  "Responsible Individual Change To Self View" should {
    val application = anApplication()
    val view        = app.injector.instanceOf[ResponsibleIndividualChangeToSelfView]

    def renderPage() = {
      val request = FakeRequest().withCSRFToken
      val session =  adminDeveloper.loggedIn
      view.render(application, request, session, messagesProvider.messages, appConfig)
    }

    "displays the correct explanatory heading" in {
      val document = Jsoup.parse(renderPage().body)
      elementExistsByText(document, "h1", "Become responsible for the application") shouldBe true
    }

    "links to terms of use" in {
      val document = Jsoup.parse(renderPage().body)
      linkExistsWithHref(document, "/api-documentation/docs/terms-of-use") shouldBe true
      linkExistsWithHref(document, "/api-documentation/docs/terms-of-use/not-meeting-terms-of-use") shouldBe true
    }

  }
}

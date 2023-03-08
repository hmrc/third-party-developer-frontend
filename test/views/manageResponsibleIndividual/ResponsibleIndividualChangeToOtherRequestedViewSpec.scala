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
import views.html.manageResponsibleIndividual.ResponsibleIndividualChangeToOtherRequestedView

import play.api.test.FakeRequest

import uk.gov.hmrc.thirdpartydeveloperfrontend.builder._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.LoggedInState
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.ViewHelpers.elementIdentifiedByIdContainsText
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{LocalUserIdTracker, TestApplications, WithCSRFAddToken, CollaboratorTracker}
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.DeveloperSessionTestData

class ResponsibleIndividualChangeToOtherRequestedViewSpec extends CommonViewSpec with WithCSRFAddToken
    with LocalUserIdTracker with DeveloperSessionBuilder with TestApplications with CollaboratorTracker with DeveloperTestData {

  "Responsible Individual Change To Other Requested View" should {
    val application = anApplication()
    val view        = app.injector.instanceOf[ResponsibleIndividualChangeToOtherRequestedView]
    val newRiName   = "Mr Responsible"

    def renderPage() = {
      val request = FakeRequest().withCSRFToken

      view.render(application, Some(newRiName), request, standardDeveloper.loggedIn, messagesProvider.messages, appConfig)
    }

    "RI and application name are displayed correctly" in {
      val document = Jsoup.parse(renderPage().body)

      elementIdentifiedByIdContainsText(document, "riMustAccept", s"$newRiName has 24 hours to accept or decline responsibility for ${application.name}.") shouldBe true
    }
  }
}

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
import views.html.manageResponsibleIndividual.ResponsibleIndividualDetailsView

import play.api.test.FakeRequest

import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models.Access
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.{ApplicationState, ApplicationWithCollaboratorsFixtures, RedirectUri, State}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{ApplicationId, ClientId, Environment}
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.{LoggedInState, UserSession}
import uk.gov.hmrc.apiplatform.modules.tpd.test.data.UserTestData
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.DeveloperSessionBuilder
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.ManageResponsibleIndividualController.{ResponsibleIndividualHistoryItem, ViewModel}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.ViewHelpers._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithCSRFAddToken

class ResponsibleIndividualDetailsViewSpec extends CommonViewSpec with WithCSRFAddToken with LocalUserIdTracker with UserTestData with DeveloperSessionBuilder
    with ApplicationWithCollaboratorsFixtures
    with FixedClock {

  val application = standardApp

  "responsible individual details view" should {
    val view          = app.injector.instanceOf[ResponsibleIndividualDetailsView]
    val environment   = "Production"
    val currentRiName = "Current RI"

    def renderPage(viewModel: ViewModel) = {
      val request = FakeRequest().withCSRFToken
      val session = adminDeveloper.loggedIn

      view.render(application, viewModel, request, session, messagesProvider.messages, appConfig)
    }

    "RI details and history are displayed correctly" in {
      val previousRis = List(
        ResponsibleIndividualHistoryItem("ri 1", "from 1", "to 1"),
        ResponsibleIndividualHistoryItem("ri 2", "from 2", "to 2")
      )
      val document    = Jsoup.parse(renderPage(ViewModel(environment, currentRiName, previousRis, true, List(), false)).body)

      elementBySelector(document, "#applicationName").map(_.text()) shouldBe Some(application.name.value)
      elementBySelector(document, "#environment").map(_.text()) shouldBe Some(environment)

      val oldRiNames = document.select(".riHistoryName")
      oldRiNames.size() shouldBe 2
      oldRiNames.get(0).text() shouldBe "ri 1"
      oldRiNames.get(1).text() shouldBe "ri 2"

      val oldRiFromDates = document.select(".riHistoryFrom")
      oldRiFromDates.size() shouldBe 2
      oldRiFromDates.get(0).text() shouldBe "from 1"
      oldRiFromDates.get(1).text() shouldBe "from 2"

      val oldRiToDates = document.select(".riHistoryTo")
      oldRiToDates.size() shouldBe 2
      oldRiToDates.get(0).text() shouldBe "to 1"
      oldRiToDates.get(1).text() shouldBe "to 2"
    }

  }
}

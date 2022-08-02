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

package views.manageResponsibleIndividual

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import play.api.data.Form
import play.api.test.FakeRequest
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.{DeveloperBuilder, DeveloperSessionBuilder}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.AddTeamMemberForm
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.ManageResponsibleIndividualController.{ResponsibleIndividualHistoryItem, ViewModel}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.{Application, ApplicationId, ApplicationState, ClientId, Collaborator, CollaboratorRole, Environment, Standard}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers.ApplicationViewModel
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.{Developer, LoggedInState, loggedInDeveloper}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.ViewHelpers.{elementBySelector, elementExistsById, elementExistsByText, linkExistsWithHref}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{LocalUserIdTracker, WithCSRFAddToken}
import views.helper.CommonViewSpec
import views.html.manageResponsibleIndividual.ResponsibleIndividualDetailsView
import views.html.manageTeamViews.ManageTeamView

import java.time.{LocalDateTime, ZoneOffset}

class ResponsibleIndividualDetailsViewSpec extends CommonViewSpec with WithCSRFAddToken with DeveloperBuilder with LocalUserIdTracker with DeveloperSessionBuilder {

  val application = Application(
      ApplicationId.random,
      ClientId("clientId123"),
      "App name 1",
      LocalDateTime.now(ZoneOffset.UTC),
      Some(LocalDateTime.now(ZoneOffset.UTC)),
      None,
      grantLength,
      Environment.PRODUCTION,
      Some("Description 1"),
      Set.empty,
      state = ApplicationState.production("user@example.com", ""),
      access = Standard(redirectUris = List("https://red1", "https://red2"), termsAndConditionsUrl = Some("http://tnc-url.com"))
    )


  "responsible individual details view" should {
    val view = app.injector.instanceOf[ResponsibleIndividualDetailsView]
    val environment = "Production"
    val currentRiName = "Current RI"

    def renderPage(viewModel: ViewModel) = {
      val request = FakeRequest().withCSRFToken
      val session = buildDeveloperSession(LoggedInState.LOGGED_IN, buildDeveloper("admin@example.com", "firstName1", "lastName1"))

      view.render(application, viewModel, request, session, messagesProvider.messages, appConfig)
    }

    "RI details and history are displayed correctly" in {
      val previousRis = List(
        ResponsibleIndividualHistoryItem("ri 1", "from 1", "to 1"),
        ResponsibleIndividualHistoryItem("ri 2", "from 2", "to 2")
      )
      val document = Jsoup.parse(renderPage(ViewModel(environment, currentRiName, previousRis, true, List())).body)

      elementBySelector(document, "#applicationName").map(_.text()) shouldBe Some(application.name)
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

    "Change button is shown for admins" in {
      val document = Jsoup.parse(renderPage(ViewModel(environment, currentRiName, List(), true, List())).body)

      elementExistsById(document, "changeResponsibleIndividual") shouldBe true
      elementExistsById(document, "changeRiText") shouldBe false
      elementExistsById(document, "adminList") shouldBe false
    }

    "Change button is not shown for non-admins, correct text shown if there is only 1 admin" in {
      val document = Jsoup.parse(renderPage(ViewModel(environment, currentRiName, List(), false, List("admin@example.com"))).body)

      elementExistsById(document, "changeResponsibleIndividual") shouldBe false
      elementBySelector(document, "#changeRiText").map(_.text()) shouldBe Some("Only admins can change the responsible individual. Speak to admin@example.com if you want to make a change.")
      elementExistsById(document, "adminList") shouldBe false
    }

    "Change button is not shown for non-admins, correct text shown if there is more than 1 admin" in {
      val document = Jsoup.parse(renderPage(ViewModel(environment, currentRiName, List(), false, List("admin1@example.com", "admin2@example.com"))).body)

      elementExistsById(document, "changeResponsibleIndividual") shouldBe false
      elementBySelector(document, "#changeRiText").map(_.text()) shouldBe Some("Only admins can change the responsible individual. If you want to make a change, speak to:")
      elementBySelector(document, "#adminList").map(_.text()) shouldBe Some("admin1@example.com admin2@example.com")
    }

  }
}

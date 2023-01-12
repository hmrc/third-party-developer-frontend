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

package uk.gov.hmrc.apiplatform.modules.dynamics.views

import org.jsoup.Jsoup
import play.api.test.{FakeRequest, StubMessagesFactory}
import uk.gov.hmrc.apiplatform.modules.dynamics.model.AddTicketForm
import uk.gov.hmrc.apiplatform.modules.dynamics.views.html.AddTicketView
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.{DeveloperBuilder, DeveloperSessionBuilder}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.{DeveloperSession, LoggedInState}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{LocalUserIdTracker, WithCSRFAddToken}
import views.helper.CommonViewSpec

class AddTicketViewSpec extends CommonViewSpec with DeveloperSessionBuilder with WithCSRFAddToken
    with DeveloperBuilder with LocalUserIdTracker with StubMessagesFactory {

  trait Setup {
    val addTicketView = app.injector.instanceOf[AddTicketView]

    implicit val request                    = FakeRequest().withCSRFToken
    implicit val loggedIn: DeveloperSession = buildDeveloperSession(LoggedInState.LOGGED_IN, buildDeveloper())
    implicit val messages                   = stubMessages()
  }

  "AddTicketView" should {

    "render correctly when form is valid" in new Setup {
      val mainView = addTicketView.apply(AddTicketForm.form)

      val document = Jsoup.parse(mainView.body)

      document.getElementById("page-heading").text shouldBe "MS Dynamics Add Ticket"
      Option(document.getElementById("data-field-error-ticketNumber")) shouldBe None
      Option(document.getElementById("data-field-error-title")) shouldBe None
      Option(document.getElementById("data-field-error-description")) shouldBe None
      document.getElementById("submit").text shouldBe "Continue"
    }

    "render correctly when form is invalid" in new Setup {
      val mainView = addTicketView.apply(AddTicketForm.form
        .withError("dynamics", "Failed API call")
        .withError("customerId", "Invalid Customer ID")
        .withError("title", "This field is required")
        .withError("description", "Required field"))

      val document = Jsoup.parse(mainView.body)

      document.getElementById("page-heading").text shouldBe "MS Dynamics Add Ticket"
      document.getElementById("data-field-error-dynamics").text() shouldBe "Error: Failed API call"
      document.getElementById("data-field-error-customerId").text() shouldBe "Error: Invalid Customer ID"
      document.getElementById("data-field-error-title").text() shouldBe "Error: This field is required"
      document.getElementById("data-field-error-description").text() shouldBe "Error: Required field"
    }
  }
}

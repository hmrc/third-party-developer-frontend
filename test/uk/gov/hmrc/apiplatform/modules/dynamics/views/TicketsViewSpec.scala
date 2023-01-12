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
import uk.gov.hmrc.apiplatform.modules.dynamics.connectors.Ticket
import uk.gov.hmrc.apiplatform.modules.dynamics.views.html.TicketsView
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.{DeveloperBuilder, DeveloperSessionBuilder}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.{DeveloperSession, LoggedInState}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.LocalUserIdTracker
import views.helper.CommonViewSpec

class TicketsViewSpec extends CommonViewSpec with DeveloperSessionBuilder with DeveloperBuilder with LocalUserIdTracker with StubMessagesFactory {

  trait Setup {
    val ticketsView = app.injector.instanceOf[TicketsView]

    val tickets = List(
      Ticket("CAS-1", "Title1", Some("Desc1"), 0, "id1"),
      Ticket("CAS-2", "Title2", None, 1, "id2")
    )

    implicit val request                    = FakeRequest()
    implicit val loggedIn: DeveloperSession = buildDeveloperSession(LoggedInState.LOGGED_IN, buildDeveloper())
    implicit val messages                   = stubMessages()
  }

  "TicketsView" should {
    "render correctly" in new Setup {
      val mainView = ticketsView.apply(tickets)

      val document = Jsoup.parse(mainView.body)

      document.getElementById("page-heading").text shouldBe "MS Dynamics Tickets"
      document.getElementsByTag("caption").text shouldBe "Latest 10 tickets"
      document.getElementsByTag("tr").size() shouldBe 3
      document.getElementsByTag("th").size() shouldBe 4
      document.getElementById("ticket-number-0").text shouldBe "CAS-1"
      document.getElementById("title-0").text shouldBe "Title1"
      document.getElementById("description-0").text shouldBe "Desc1"
      document.getElementById("status-0").text shouldBe "Active"
      document.getElementById("ticket-number-1").text shouldBe "CAS-2"
      document.getElementById("title-1").text shouldBe "Title2"
      document.getElementById("description-1").text shouldBe ""
      document.getElementById("status-1").text shouldBe "Resolved"
      document.getElementById("add-ticket").text shouldBe "Add Ticket"
    }
  }
}

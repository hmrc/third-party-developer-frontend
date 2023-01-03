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

package uk.gov.hmrc.apiplatform.modules.dynamics.controllers

import org.jsoup.Jsoup
import play.api.mvc.AnyContentAsFormUrlEncoded
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.apiplatform.modules.dynamics.connectors.{ThirdPartyDeveloperDynamicsConnector, Ticket}
import uk.gov.hmrc.apiplatform.modules.dynamics.views.html.{AddTicketView, TicketsView}
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.{DeveloperBuilder, DeveloperSessionBuilder}
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ErrorHandler
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.BaseControllerSpec
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.{LoggedInState, Session}
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service.SessionServiceMock
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithLoggedInSession._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{LocalUserIdTracker, WithCSRFAddToken}

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future._

class DynamicsControllerSpec extends BaseControllerSpec with DeveloperSessionBuilder with WithCSRFAddToken
  with DeveloperBuilder with LocalUserIdTracker {

  trait Setup extends SessionServiceMock {
    val ticketsView = app.injector.instanceOf[TicketsView]
    val addTicketView = app.injector.instanceOf[AddTicketView]
    val errorHandler = app.injector.instanceOf[ErrorHandler]

    val underTest: DynamicsController = new DynamicsController(
      mock[ThirdPartyDeveloperDynamicsConnector],
      ticketsView,
      addTicketView,
      sessionServiceMock,
      mcc,
      errorHandler,
      cookieSigner
    )

    val tickets = List(
      Ticket("CAS-1", "Title1", Some("Desc1"), 0, "id1"),
      Ticket("CAS-2", "Title2", None, 1, "id2")
    )
    val customerId = UUID.randomUUID().toString
    val title = "The Title"
    val description = "The description"
    
    val sessionId = UUID.randomUUID().toString
    val session = Session(sessionId, buildDeveloper(), LoggedInState.LOGGED_IN)
    when(sessionServiceMock.fetch(eqTo(sessionId))(*)).thenReturn(successful(Some(session)))
    when(sessionServiceMock.updateUserFlowSessions(sessionId)).thenReturn(successful(()))
    val request = FakeRequest().withLoggedIn(underTest, implicitly)(sessionId)
    
    def addTicketRequest(customerId: String, title: String, description: String): FakeRequest[AnyContentAsFormUrlEncoded] = {
      request
        .withCSRFToken
        .withFormUrlEncodedBody("customerId" -> customerId, "title" -> title, "description" -> description)
    }
  }
  
  "DynamicsController" when {

    "getTickets()" should {
      "show the tickets page" in new Setup {
        when(underTest.thirdPartyDeveloperDynamicsConnector.getTickets()(*)).thenReturn(successful(tickets))
        
        val result = underTest.tickets()(request)

        status(result) shouldBe OK
        val doc = Jsoup.parse(contentAsString(result))
        doc.getElementById("page-heading").text shouldBe "MS Dynamics Tickets"
      }

      "show an error message when tickets cannot be retrieved" in new Setup {
        when(underTest.thirdPartyDeveloperDynamicsConnector.getTickets()(*))
          .thenReturn(failed(UpstreamErrorResponse("error message", INTERNAL_SERVER_ERROR)))

        val result = underTest.tickets()(request)

        status(result) shouldBe INTERNAL_SERVER_ERROR
        val doc = Jsoup.parse(contentAsString(result))
        doc.getElementById("page-heading").text shouldBe "MS Dynamics Tickets"
        doc.getElementById("message").text shouldBe "Cannot get tickets"
      }
    }

    "addTicket()" should {
      "show the add ticket form" in new Setup {
        val result = underTest.addTicket()(request.withCSRFToken)

        status(result) shouldBe OK
        val doc = Jsoup.parse(contentAsString(result))
        doc.getElementById("page-heading").text shouldBe "MS Dynamics Add Ticket"
      }
    }

    "addTicketAction()" should {
      "redirect to the tickets page when ticket creation is successful" in new Setup {
        when(underTest.thirdPartyDeveloperDynamicsConnector.createTicket(eqTo(customerId), eqTo(title), eqTo(description))(*))
          .thenReturn(successful(Right(())))

        val result = underTest.addTicketAction()(addTicketRequest(customerId, title, description))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.DynamicsController.tickets.toString)
      }

      "show the add ticket form with errors when ticket creation fails" in new Setup {
        val errorMessage = "failed to create"
        when(underTest.thirdPartyDeveloperDynamicsConnector.createTicket(eqTo(customerId), eqTo(title), eqTo(description))(*))
          .thenReturn(successful(Left(errorMessage)))

        val result = underTest.addTicketAction()(addTicketRequest(customerId, title, description))

        status(result) shouldBe BAD_REQUEST
        val doc = Jsoup.parse(contentAsString(result))
        doc.getElementById("data-field-error-dynamics").text() shouldBe s"Error: $errorMessage"
      }

      "show the add ticket form with errors when the form is invalid" in new Setup {
        val result = underTest.addTicketAction()(addTicketRequest("", "", ""))

        status(result) shouldBe BAD_REQUEST
        val doc = Jsoup.parse(contentAsString(result))
        doc.getElementById("data-field-error-customerId").text() shouldBe "Error: This field is a UUID (e.g. 7e88b5e8-8924-ed11-9db2-0022481a611c)"
        doc.getElementById("data-field-error-title").text() shouldBe "Error: This field is required"
        doc.getElementById("data-field-error-description").text() shouldBe "Error: This field is required"
      }
    }
  }
}
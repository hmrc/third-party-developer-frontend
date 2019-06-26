/*
 * Copyright 2019 HM Revenue & Customs
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

package unit.controllers

import config.{ApplicationConfig, ErrorHandler}
import connectors.ThirdPartyDeveloperConnector
import controllers.DeleteApplication
import domain._
import org.joda.time.DateTime
import org.mockito.BDDMockito.given
import org.mockito.Matchers.{any, eq => mockEq}
import org.mockito.Mockito.verify
import org.scalatest.mockito.MockitoSugar
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider
import service.{ApplicationService, AuditService, SessionService}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import utils.WithCSRFAddToken
import utils.WithLoggedInSession._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future.successful


class DeleteApplicationSpec extends UnitSpec with WithCSRFAddToken with MockitoSugar with WithFakeApplication {
  implicit val materializer = fakeApplication.materializer

  trait Setup {
    val underTest = new DeleteApplication(
      mock[ThirdPartyDeveloperConnector],
      mock[AuditService],
      mock[ApplicationService],
      mock[SessionService],
      mock[ErrorHandler],
      mock[ApplicationConfig]
    )

    val appId = "1234"
    val clientId = "clientIdzzz"
    val appName: String = "Application Name"
    val loggedInUser = Developer("thirdpartydeveloper@example.com", "John", "Doe")
    val sessionId = "sessionId"
    val session = Session(sessionId, loggedInUser)
    val application = Application(appId, clientId, appName, DateTime.now.withTimeAtStartOfDay(), Environment.PRODUCTION, Some("Description 1"),
      Set(Collaborator(loggedInUser.email, Role.ADMINISTRATOR)), state = ApplicationState.production(loggedInUser.email, ""),
      access = Standard(redirectUris = Seq("https://red1", "https://red2"), termsAndConditionsUrl = Some("http://tnc-url.com")))

    given(underTest.sessionService.fetch(mockEq(sessionId))(any[HeaderCarrier])).willReturn(Some(session))
    given(underTest.applicationService.fetchByApplicationId(mockEq(application.id))(any[HeaderCarrier])).willReturn(successful(application))

    val sessionParams = Seq("csrfToken" -> fakeApplication.injector.instanceOf[TokenProvider].generateToken)
    val loggedInRequest = FakeRequest().withLoggedIn(underTest)(sessionId).withSession(sessionParams: _*)
  }

  "delete application page" should {
    "return delete application page" in new Setup {

      val result = await(addToken(underTest.deleteApplication(application.id, None))(loggedInRequest))

      status(result) shouldBe OK
      val body = bodyOf(result)

      body should include("Delete application")
      body should include("Request deletion")
    }
  }

  "delete application confirm page" should {
    "return delete application confirm page" in new Setup {

      val result = await(addToken(underTest.deleteApplicationConfirm(application.id, None))(loggedInRequest))

      status(result) shouldBe OK
      val body = bodyOf(result)

      body should include("Delete application")
      body should include("Are you sure you want us to delete this application?")
      body should include("Continue")
    }
  }

  "delete application action" should {
    "return delete application complete page when confirm selected" in new Setup {

      val requestWithFormBody = loggedInRequest.withFormUrlEncodedBody(("deleteConfirm", "Yes"))

      given(underTest.applicationService.requestApplicationDeletion(mockEq(loggedInUser), mockEq(application))(any[HeaderCarrier]))
        .willReturn(Future.successful(TicketCreated))

      val result = await(addToken(underTest.deleteApplicationAction(application.id))(requestWithFormBody))

      status(result) shouldBe OK
      val body = bodyOf(result)

      body should include("Delete application")
      body should include("Request submitted")
      verify(underTest.applicationService).requestApplicationDeletion(mockEq(loggedInUser), mockEq(application))(any[HeaderCarrier])
    }

    "redirect to 'Manage details' page when not-to-confirm selected" in new Setup {

      val requestWithFormBody = loggedInRequest.withFormUrlEncodedBody(("deleteConfirm", "No"))

      val result = await(addToken(underTest.deleteApplicationAction(application.id))(requestWithFormBody))

      status(result) shouldBe SEE_OTHER

      redirectLocation(result) shouldBe Some(s"/developer/applications/$appId/details")
    }
  }

}

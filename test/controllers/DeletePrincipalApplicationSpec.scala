/*
 * Copyright 2020 HM Revenue & Customs
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

package controllers

import domain._
import mocks.service._
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers.{any, eq => mockEq}
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verify
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider
import uk.gov.hmrc.http.HeaderCarrier
import utils.WithLoggedInSession._
import utils.{TestApplications, WithCSRFAddToken}
import views.html._

import scala.concurrent.Future
import scala.concurrent.Future.successful

class DeletePrincipalApplicationSpec extends BaseControllerSpec with WithCSRFAddToken with TestApplications with ErrorHandlerMock {

  trait Setup extends ApplicationServiceMock with SessionServiceMock {
    val deleteApplicationView = app.injector.instanceOf[DeleteApplicationView]
    val deletePrincipalApplicationConfirmView = app.injector.instanceOf[DeletePrincipalApplicationConfirmView]
    val deletePrincipalApplicationCompleteView = app.injector.instanceOf[DeletePrincipalApplicationCompleteView]
    val deleteSubordinateApplicationConfirmView = app.injector.instanceOf[DeleteSubordinateApplicationConfirmView]
    val deleteSubordinateApplicationCompleteView = app.injector.instanceOf[DeleteSubordinateApplicationCompleteView]

    val underTest = new DeleteApplication(
      applicationServiceMock,
      sessionServiceMock,
      mockErrorHandler,
      mcc,
      cookieSigner,
      deleteApplicationView,
      deletePrincipalApplicationConfirmView,
      deletePrincipalApplicationCompleteView,
      deleteSubordinateApplicationConfirmView,
      deleteSubordinateApplicationCompleteView
    )

    val appId = "1234"
    val clientId = "clientIdzzz"
    val appName: String = "Application Name"

    val developer = Developer("thirdpartydeveloper@example.com", "John", "Doe")
    val sessionId = "sessionId"
    val session = Session(sessionId, developer, LoggedInState.LOGGED_IN)

    val loggedInUser = DeveloperSession(session)
    
    implicit val hc = HeaderCarrier()

    val application = Application(appId, clientId, appName, DateTime.now.withTimeAtStartOfDay(), DateTime.now.withTimeAtStartOfDay(), None,
      Environment.PRODUCTION, Some("Description 1"), Set(Collaborator(loggedInUser.email, Role.ADMINISTRATOR)),
      state = ApplicationState.production(loggedInUser.email, ""),
      access = Standard(redirectUris = Seq("https://red1", "https://red2"), termsAndConditionsUrl = Some("http://tnc-url.com")))

    fetchSessionByIdReturns(sessionId, session)
    fetchByApplicationIdReturns(application.id, application)
    given(underTest.applicationService.apisWithSubscriptions(mockEq(application))(any[HeaderCarrier])).willReturn(successful(Seq.empty[APISubscriptionStatus]))

    val sessionParams = Seq("csrfToken" -> fakeApplication.injector.instanceOf[TokenProvider].generateToken)
    val loggedInRequest = FakeRequest().withLoggedIn(underTest,implicitly)(sessionId).withSession(sessionParams: _*)
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

      val result = await(addToken(underTest.deletePrincipalApplicationConfirm(application.id, None))(loggedInRequest))

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

      given(underTest.applicationService.requestPrincipalApplicationDeletion(mockEq(loggedInUser), mockEq(application))(any[HeaderCarrier]))
        .willReturn(Future.successful(TicketCreated))

      val result = await(addToken(underTest.deletePrincipalApplicationAction(application.id))(requestWithFormBody))

      status(result) shouldBe OK
      val body = bodyOf(result)

      body should include("Delete application")
      body should include("Request submitted")
      verify(underTest.applicationService).requestPrincipalApplicationDeletion(mockEq(loggedInUser), mockEq(application))(any[HeaderCarrier])
    }

    "redirect to 'Manage details' page when not-to-confirm selected" in new Setup {

      val requestWithFormBody = loggedInRequest.withFormUrlEncodedBody(("deleteConfirm", "No"))

      val result = await(addToken(underTest.deletePrincipalApplicationAction(application.id))(requestWithFormBody))

      status(result) shouldBe SEE_OTHER

      redirectLocation(result) shouldBe Some(s"/developer/applications/$appId/details")
    }
  }

  "return not found if non-approved app" when {
    trait UnapprovedApplicationSetup extends Setup {
      val nonApprovedApplication = aStandardNonApprovedApplication(loggedInUser.email)

      fetchByApplicationIdReturns(nonApprovedApplication)
      given(underTest.applicationService.apisWithSubscriptions(any())(any[HeaderCarrier])).willReturn(successful(Seq.empty[APISubscriptionStatus]))

      given(underTest.applicationService.requestPrincipalApplicationDeletion(any(), any())(any[HeaderCarrier]))
        .willReturn(Future.successful(TicketCreated))
    }

    "deleteApplication action is called" in new UnapprovedApplicationSetup {
      val result = await(addToken(underTest.deleteApplication(nonApprovedApplication.id, None))(loggedInRequest))
      status(result) shouldBe NOT_FOUND
    }

    "deletePrincipalApplicationConfirm action is called" in new UnapprovedApplicationSetup {
      val result = await(addToken(underTest.deletePrincipalApplicationConfirm(nonApprovedApplication.id, None))(loggedInRequest))
      status(result) shouldBe NOT_FOUND
    }

    "deletePrincipalApplicationAction action is called" in new UnapprovedApplicationSetup {
      val result = await(addToken(underTest.deletePrincipalApplicationAction(nonApprovedApplication.id))(loggedInRequest))
      status(result) shouldBe NOT_FOUND
    }

    "deleteSubordinateApplicationConfirm action is called" in new UnapprovedApplicationSetup {
      val result = await(addToken(underTest.deleteSubordinateApplicationConfirm(nonApprovedApplication.id))(loggedInRequest))
      status(result) shouldBe NOT_FOUND
    }
    
    "deleteSubordinateApplicationAction action is called" in new UnapprovedApplicationSetup {
      val result = await(addToken(underTest.deleteSubordinateApplicationAction(nonApprovedApplication.id))(loggedInRequest))
      status(result) shouldBe NOT_FOUND
    }
  }
}

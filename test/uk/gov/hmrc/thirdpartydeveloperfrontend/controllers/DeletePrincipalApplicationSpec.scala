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

package uk.gov.hmrc.thirdpartydeveloperfrontend.controllers

import java.time.{LocalDate, LocalDateTime}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import views.html._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.{ApplicationState, State}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.DeveloperBuilder
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.TicketCreated
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.{DeveloperSession, LoggedInState, Session}
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithLoggedInSession._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{LocalUserIdTracker, TestApplications, WithCSRFAddToken}

class DeletePrincipalApplicationSpec
    extends BaseControllerSpec
    with WithCSRFAddToken
    with TestApplications
    with ErrorHandlerMock
    with DeveloperBuilder
    with LocalUserIdTracker {

  trait Setup extends ApplicationServiceMock with ApplicationActionServiceMock with SessionServiceMock {
    val deleteApplicationView                    = app.injector.instanceOf[DeleteApplicationView]
    val deletePrincipalApplicationConfirmView    = app.injector.instanceOf[DeletePrincipalApplicationConfirmView]
    val deletePrincipalApplicationCompleteView   = app.injector.instanceOf[DeletePrincipalApplicationCompleteView]
    val deleteSubordinateApplicationConfirmView  = app.injector.instanceOf[DeleteSubordinateApplicationConfirmView]
    val deleteSubordinateApplicationCompleteView = app.injector.instanceOf[DeleteSubordinateApplicationCompleteView]

    val underTest = new DeleteApplication(
      mockErrorHandler,
      applicationServiceMock,
      applicationActionServiceMock,
      sessionServiceMock,
      mcc,
      cookieSigner,
      deleteApplicationView,
      deletePrincipalApplicationConfirmView,
      deletePrincipalApplicationCompleteView,
      deleteSubordinateApplicationConfirmView,
      deleteSubordinateApplicationCompleteView
    )

    val appId           = ApplicationId.random
    val clientId        = ClientId("clientIdzzz")
    val appName: String = "Application Name"

    val developer = buildDeveloper()
    val sessionId = "sessionId"
    val session   = Session(sessionId, developer, LoggedInState.LOGGED_IN)

    val loggedInDeveloper = DeveloperSession(session)

    implicit val hc: HeaderCarrier = HeaderCarrier()

    private val now: LocalDateTime = LocalDate.now.atStartOfDay()
    val application = Application(
      appId,
      clientId,
      appName,
      now,
      Some(now),
      None,
      grantLength,
      Environment.PRODUCTION,
      Some("Description 1"),
      Set(loggedInDeveloper.email.asAdministratorCollaborator),
      state = ApplicationState(State.PRODUCTION, Some(loggedInDeveloper.email.text), Some(loggedInDeveloper.displayedName), Some(""), now),
      access = Standard(redirectUris = List("https://red1", "https://red2"), termsAndConditionsUrl = Some("http://tnc-url.com"))
    )

    givenApplicationAction(application, loggedInDeveloper)
    fetchSessionByIdReturns(sessionId, session)
    updateUserFlowSessionsReturnsSuccessfully(sessionId)

    val sessionParams   = Seq("csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken)
    val loggedInRequest = FakeRequest().withLoggedIn(underTest, implicitly)(sessionId).withSession(sessionParams: _*)
  }

  "delete application page" should {
    "return delete application page" in new Setup {

      val result = addToken(underTest.deleteApplication(application.id, None))(loggedInRequest)

      status(result) shouldBe OK
      val body = contentAsString(result)

      body should include("Delete application")
      body should include("Request deletion")
    }
  }

  "delete application confirm page" should {
    "return delete application confirm page" in new Setup {

      val result = addToken(underTest.confirmRequestDeletePrincipalApplication(application.id, None))(loggedInRequest)

      status(result) shouldBe OK
      val body = contentAsString(result)

      body should include("Delete application")
      body should include("Are you sure you want us to delete this application?")
      body should include("Continue")
    }
  }

  "delete application action" should {
    "return delete application complete page when confirm selected" in new Setup {

      val requestWithFormBody = loggedInRequest.withFormUrlEncodedBody(("deleteConfirm", "Yes"))

      when(underTest.applicationService.requestPrincipalApplicationDeletion(eqTo(loggedInDeveloper), eqTo(application))(*))
        .thenReturn(Future.successful(TicketCreated))

      val result = addToken(underTest.requestDeletePrincipalApplicationAction(application.id))(requestWithFormBody)

      status(result) shouldBe OK
      val body = contentAsString(result)

      body should include("Request submitted")
      verify(underTest.applicationService).requestPrincipalApplicationDeletion(eqTo(loggedInDeveloper), eqTo(application))(*)
    }

    "redirect to 'Manage details' page when not-to-confirm selected" in new Setup {

      val requestWithFormBody = loggedInRequest.withFormUrlEncodedBody(("deleteConfirm", "No"))

      val result = addToken(underTest.requestDeletePrincipalApplicationAction(application.id))(requestWithFormBody)

      status(result) shouldBe SEE_OTHER

      redirectLocation(result) shouldBe Some(s"/developer/applications/${appId}/details")
    }
  }

  "return not found if non-approved app" when {
    trait UnapprovedApplicationSetup extends Setup {
      val nonApprovedApplication = aStandardNonApprovedApplication(loggedInDeveloper.email)

      givenApplicationAction(nonApprovedApplication, loggedInDeveloper)

      when(underTest.applicationService.requestPrincipalApplicationDeletion(*, *)(*))
        .thenReturn(Future.successful(TicketCreated))
    }

    "deleteApplication action is called" in new UnapprovedApplicationSetup {
      val result = addToken(underTest.deleteApplication(nonApprovedApplication.id, None))(loggedInRequest)
      status(result) shouldBe NOT_FOUND
    }

    "confirmRequestDeletePrincipalApplication action is called" in new UnapprovedApplicationSetup {
      val result = addToken(underTest.confirmRequestDeletePrincipalApplication(nonApprovedApplication.id, None))(loggedInRequest)
      status(result) shouldBe NOT_FOUND
    }

    "requestDeletePrincipalApplicationAction action is called" in new UnapprovedApplicationSetup {
      val result = addToken(underTest.requestDeletePrincipalApplicationAction(nonApprovedApplication.id))(loggedInRequest)
      status(result) shouldBe NOT_FOUND
    }

    "deleteSubordinateApplicationConfirm action is called" in new UnapprovedApplicationSetup {
      val result = addToken(underTest.deleteSubordinateApplicationConfirm(nonApprovedApplication.id))(loggedInRequest)
      status(result) shouldBe NOT_FOUND
    }

    "deleteSubordinateApplicationAction action is called" in new UnapprovedApplicationSetup {
      val result = addToken(underTest.deleteSubordinateApplicationAction(nonApprovedApplication.id))(loggedInRequest)
      status(result) shouldBe NOT_FOUND
    }
  }
}

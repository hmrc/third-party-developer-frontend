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

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import views.html._

import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.ApplicationWithCollaboratorsFixtures
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.TicketCreated
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithCSRFAddToken
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithLoggedInSession._

class DeleteApplicationSpec
    extends BaseControllerSpec
    with WithCSRFAddToken
    with ErrorHandlerMock
    with ApplicationWithCollaboratorsFixtures {

  trait Setup
      extends ApplicationServiceMock
      with ApplicationActionServiceMock {
    val deleteApplicationView                    = app.injector.instanceOf[DeleteApplicationView]
    val deletePrincipalApplicationConfirmView    = app.injector.instanceOf[DeletePrincipalApplicationConfirmView]
    val deletePrincipalApplicationCompleteView   = app.injector.instanceOf[DeletePrincipalApplicationCompleteView]
    val deleteSubordinateApplicationConfirmView  = app.injector.instanceOf[DeleteSubordinateApplicationConfirmView]
    val deleteSubordinateApplicationCompleteView = app.injector.instanceOf[DeleteSubordinateApplicationCompleteView]
    val deleteRestrictedApplicationConfirmView   = app.injector.instanceOf[DeleteRestrictedApplicationConfirmView]
    val deleteRestrictedApplicationCompleteView  = app.injector.instanceOf[DeleteRestrictedApplicationCompleteView]

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
      deleteSubordinateApplicationCompleteView,
      deleteRestrictedApplicationConfirmView,
      deleteRestrictedApplicationCompleteView
    )

    implicit val hc: HeaderCarrier = HeaderCarrier()

    val sessionId = adminSession.sessionId

    givenApplicationAction(standardApp, adminSession)
    fetchSessionByIdReturns(sessionId, adminSession)
    updateUserFlowSessionsReturnsSuccessfully(sessionId)

    val sessionParams   = Seq("csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken)
    val loggedInRequest = FakeRequest().withLoggedIn(underTest, implicitly)(sessionId).withSession(sessionParams: _*)
  }

  "delete application page" should {
    "return delete application page" in new Setup {

      val result = addToken(underTest.deleteApplication(standardApp.id, None))(loggedInRequest)

      status(result) shouldBe OK
      val body = contentAsString(result)

      body should include("Delete application")
      body should include("Request deletion")
    }
  }

  "delete application confirm page" should {
    "return delete application confirm page" in new Setup {

      val result = addToken(underTest.confirmRequestDeletePrincipalApplication(standardApp.id, None))(loggedInRequest)

      status(result) shouldBe OK
      val body = contentAsString(result)

      body should include("Delete application")
      body should include("Are you sure you want us to delete this application?")
      body should include("Continue")
    }
  }

  "delete restricted application confirm page" should {
    "return delete restricted application confirm page" in new Setup {

      val result = addToken(underTest.confirmRequestDeleteRestrictedApplication(standardApp.id, None))(loggedInRequest)

      status(result) shouldBe OK
      val body = contentAsString(result)

      body should include("Request deletion")
      body should include(s"Are you sure you want to delete ${standardApp.name}?")
      body should include("Submit request")
    }
  }

  "delete principle application action" should {
    "return delete application complete page when confirm selected" in new Setup {

      val requestWithFormBody = loggedInRequest.withFormUrlEncodedBody(("deleteConfirm", "Yes"))

      when(underTest.applicationService.requestPrincipalApplicationDeletion(eqTo(adminSession), eqTo(standardApp))(*))
        .thenReturn(Future.successful(TicketCreated))

      val result = addToken(underTest.requestDeletePrincipalApplicationAction(standardApp.id))(requestWithFormBody)

      status(result) shouldBe OK
      val body = contentAsString(result)

      body should include("Request submitted")
      verify(underTest.applicationService).requestPrincipalApplicationDeletion(eqTo(adminSession), eqTo(standardApp))(*)
    }

    "redirect to 'Manage details' page when not-to-confirm selected" in new Setup {

      val requestWithFormBody = loggedInRequest.withFormUrlEncodedBody(("deleteConfirm", "No"))

      val result = addToken(underTest.requestDeletePrincipalApplicationAction(standardApp.id))(requestWithFormBody)

      status(result) shouldBe SEE_OTHER

      redirectLocation(result) shouldBe Some(s"/developer/applications/${standardApp.id}/details")
    }
  }

  "delete restricted application action" should {
    "return delete restricted application complete page when confirm selected" in new Setup {

      val requestWithFormBody = loggedInRequest.withFormUrlEncodedBody(("deleteConfirm", "Yes"))

      when(underTest.applicationService.requestRestrictedApplicationDeletion(eqTo(adminSession), eqTo(standardApp))(*))
        .thenReturn(Future.successful(TicketCreated))

      val result = addToken(underTest.requestDeleteRestrictedApplicationAction(standardApp.id))(requestWithFormBody)

      status(result) shouldBe OK
      val body = contentAsString(result)

      body should include("Request submitted")
      verify(underTest.applicationService).requestRestrictedApplicationDeletion(eqTo(adminSession), eqTo(standardApp))(*)
    }

    "redirect to 'Manage details' page when not-to-confirm selected" in new Setup {

      val requestWithFormBody = loggedInRequest.withFormUrlEncodedBody(("deleteConfirm", "No"))

      val result = addToken(underTest.requestDeleteRestrictedApplicationAction(standardApp.id))(requestWithFormBody)

      status(result) shouldBe SEE_OTHER

      redirectLocation(result) shouldBe Some(s"/developer/applications/${standardApp.id}/details")
    }
  }

  trait UnapprovedApplicationSetup extends Setup {
    val nonApprovedApplication = standardApp.withState(appStateTesting)

    givenApplicationAction(nonApprovedApplication, adminSession)

    when(underTest.applicationService.requestPrincipalApplicationDeletion(*, *)(*))
      .thenReturn(Future.successful(TicketCreated))
  }

  "return not found if non-approved app" should {

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

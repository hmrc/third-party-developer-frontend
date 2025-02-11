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

import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future.successful

import org.mockito.ArgumentCaptor
import views.html.TermsOfUseView

import play.api.mvc.{AnyContentAsEmpty, AnyContentAsFormUrlEncoded, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models._
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.apiplatform.modules.tpd.core.domain.models.User
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.{LoggedInState, UserSession, UserSessionId}
import uk.gov.hmrc.apiplatform.modules.tpd.test.builders.UserBuilder
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.ApplicationUpdateSuccessful
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.TermsOfUseVersion
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service.{ApplicationActionServiceMock, ApplicationServiceMock, TermsOfUseVersionServiceMock}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithLoggedInSession._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{WithCSRFAddToken, _}

class TermsOfUseSpec
    extends BaseControllerSpec
    with WithCSRFAddToken
    with ApplicationWithCollaboratorsFixtures {

  trait Setup
      extends ApplicationServiceMock
      with ApplicationActionServiceMock
      with TermsOfUseVersionServiceMock
      with UserBuilder
      with CollaboratorTracker
      with LocalUserIdTracker {

    val termsOfUseView: TermsOfUseView = app.injector.instanceOf[TermsOfUseView]

    val underTest = new TermsOfUse(
      mockErrorHandler,
      applicationServiceMock,
      applicationActionServiceMock,
      sessionServiceMock,
      mcc,
      cookieSigner,
      termsOfUseView,
      termsOfUseVersionServiceMock,
      clock
    )

    val loggedInDeveloper: User              = buildTrackedUser()
    val sessionId                            = UserSessionId.random
    val userSession: UserSession             = UserSession(sessionId, LoggedInState.LOGGED_IN, loggedInDeveloper)
    val sessionParams: Seq[(String, String)] = Seq("csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken)

    val loggedOutRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withSession(sessionParams: _*)
    val loggedInRequest: FakeRequest[AnyContentAsEmpty.type]  = FakeRequest().withLoggedIn(underTest, implicitly)(sessionId).withSession(sessionParams: _*)

    val appId: ApplicationId = standardApp.id

    implicit val hc: HeaderCarrier = HeaderCarrier()

    def givenApplicationExists(
        userRole: Collaborator.Role = Collaborator.Roles.ADMINISTRATOR,
        environment: Environment = Environment.PRODUCTION,
        checkInformation: Option[CheckInformation] = None,
        access: Access = Access.Standard()
      ): ApplicationWithCollaborators = {

      val application =
        standardApp
          .withCollaborators(loggedInDeveloper.email.asCollaborator(userRole))
          .withEnvironment(environment)
          .withAccess(access)
          .modify(_.copy(checkInformation = checkInformation))

      givenApplicationAction(application, userSession)

      application
    }

    when(underTest.sessionService.fetch(eqTo(sessionId))(*)).thenReturn(successful(Some(userSession)))
    updateUserFlowSessionsReturnsSuccessfully(sessionId)
  }

  "termsOfUsePartial" should {
    "render the partial" in new Setup {
      returnLatestTermsOfUseVersion

      when(underTest.appConfig.thirdPartyDeveloperFrontendUrl).thenReturn("http://tpdf")

      val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
      val result: Future[Result]                       = underTest.termsOfUsePartial()(request)

      status(result) shouldBe OK
    }
  }

  "termsOfUse" should {

    "render the page for an administrator on a standard production app when the ToU have not been agreed" in new Setup {
      val checkInformation: CheckInformation = CheckInformation(termsOfUseAgreements = List.empty)
      returnTermsOfUseVersionForApplication
      givenApplicationExists(checkInformation = Some(checkInformation))
      val result: Future[Result]             = addToken(underTest.termsOfUse(appId))(loggedInRequest)
      status(result) shouldBe OK
      contentAsString(result) should include("Agree to our terms of use")
      contentAsString(result) should not include "Terms of use accepted on"
    }

    "render the page for an administrator on a standard production app when the ToU have been agreed" in new Setup {
      val email: LaxEmailAddress    = "email@exmaple.com".toLaxEmail
      val expectedTimeStamp: String = DateTimeFormatter.ofPattern("dd MMMM yyyy").withZone(ZoneOffset.UTC).format(instant)
      val version                   = "1.0"

      val checkInformation: CheckInformation = CheckInformation(termsOfUseAgreements = List(TermsOfUseAgreement(email, instant, version)))
      returnTermsOfUseVersionForApplication
      givenApplicationExists(checkInformation = Some(checkInformation))
      val result: Future[Result]             = addToken(underTest.termsOfUse(appId))(loggedInRequest)
      status(result) shouldBe OK
      contentAsString(result) should include("Terms of use")
      contentAsString(result) should include(s"Terms of use accepted on $expectedTimeStamp by ${email.text}.")
    }

    "return a bad request for a sandbox app" in new Setup {
      givenApplicationExists(environment = Environment.SANDBOX)
      val result: Future[Result] = addToken(underTest.termsOfUse(appId))(loggedInRequest)
      status(result) shouldBe BAD_REQUEST
    }

    "return the ROPC page for a ROPC app" in new Setup {
      givenApplicationExists(access = Access.Ropc())
      val result: Future[Result] = addToken(underTest.termsOfUse(appId))(loggedInRequest)
      status(result) shouldBe BAD_REQUEST // FORBIDDEN
    }

    "return the privileged page for a privileged app" in new Setup {
      givenApplicationExists(access = Access.Privileged())
      val result: Future[Result] = addToken(underTest.termsOfUse(appId))(loggedInRequest)
      status(result) shouldBe BAD_REQUEST // FORBIDDEN
    }
  }

  "agreeTermsOfUse" should {

    "record the terms of use agreement for an administrator on a standard production app" in new Setup {
      val application: ApplicationWithCollaborators = givenApplicationExists()
      returnLatestTermsOfUseVersion
      val captor: ArgumentCaptor[CheckInformation]  = ArgumentCaptor.forClass(classOf[CheckInformation])
      when(underTest.applicationService.updateCheckInformation(eqTo(application), captor.capture())(*)).thenReturn(Future.successful(ApplicationUpdateSuccessful))

      val request: FakeRequest[AnyContentAsFormUrlEncoded] = loggedInRequest.withFormUrlEncodedBody("termsOfUseAgreed" -> "true")
      val result: Future[Result]                           = addToken(underTest.agreeTermsOfUse(appId))(request)
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/applications/${application.id}/details")

      val termsOfUseAgreement: TermsOfUseAgreement = captor.getValue.termsOfUseAgreements.head
      termsOfUseAgreement.emailAddress shouldBe loggedInDeveloper.email
      termsOfUseAgreement.version shouldBe TermsOfUseVersion.latest.toString
    }

    "return a bad request if termsOfUseAgreed is not set in the request" in new Setup {
      returnTermsOfUseVersionForApplication
      givenApplicationExists()
      val result: Future[Result] = addToken(underTest.agreeTermsOfUse(appId))(loggedInRequest)
      status(result) shouldBe BAD_REQUEST
      verify(underTest.applicationService, never).updateCheckInformation(*, *)(*)
    }

    "return a bad request if the app already has terms of use agreed" in new Setup {
      val checkInformation: CheckInformation =
        CheckInformation(termsOfUseAgreements = List(TermsOfUseAgreement("bob@example.com".toLaxEmail, instant, "1.0")))
      givenApplicationExists(checkInformation = Some(checkInformation))

      val request: FakeRequest[AnyContentAsFormUrlEncoded] = loggedInRequest.withFormUrlEncodedBody("termsOfUseAgreed" -> "true")
      val result: Future[Result]                           = addToken(underTest.agreeTermsOfUse(appId))(request)
      status(result) shouldBe BAD_REQUEST
      verify(underTest.applicationService, never).updateCheckInformation(*, *)(*)
    }

    "return a bad request for a ROPC app" in new Setup {
      givenApplicationExists(access = Access.Ropc())
      val request: FakeRequest[AnyContentAsFormUrlEncoded] = loggedInRequest.withFormUrlEncodedBody("termsOfUseAgreed" -> "true")
      val result: Future[Result]                           = addToken(underTest.agreeTermsOfUse(appId))(request)
      status(result) shouldBe BAD_REQUEST // FORBIDDEN
    }

    "return a bad request for a Privileged app" in new Setup {
      givenApplicationExists(access = Access.Privileged())
      val request: FakeRequest[AnyContentAsFormUrlEncoded] = loggedInRequest.withFormUrlEncodedBody("termsOfUseAgreed" -> "true")
      val result: Future[Result]                           = addToken(underTest.agreeTermsOfUse(appId))(request)
      status(result) shouldBe BAD_REQUEST // FORBIDDEN
    }
  }
}

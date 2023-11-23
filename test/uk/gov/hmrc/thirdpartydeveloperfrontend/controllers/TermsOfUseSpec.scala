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

import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZoneOffset}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future.successful

import org.mockito.ArgumentCaptor
import views.html.TermsOfUseView

import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.Collaborator
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.DeveloperBuilder
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.ApplicationUpdateSuccessful
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.{DeveloperSession, LoggedInState, Session}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.{TermsOfUseVersion, applications}
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service.{ApplicationActionServiceMock, ApplicationServiceMock, SessionServiceMock, TermsOfUseVersionServiceMock}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithLoggedInSession._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{LocalUserIdTracker, WithCSRFAddToken}

class TermsOfUseSpec
    extends BaseControllerSpec
    with WithCSRFAddToken
    with DeveloperBuilder
    with LocalUserIdTracker {

  trait Setup extends ApplicationServiceMock with SessionServiceMock with ApplicationActionServiceMock with TermsOfUseVersionServiceMock {

    val termsOfUseView = app.injector.instanceOf[TermsOfUseView]

    val underTest = new TermsOfUse(
      mockErrorHandler,
      applicationServiceMock,
      applicationActionServiceMock,
      sessionServiceMock,
      mcc,
      cookieSigner,
      termsOfUseView,
      termsOfUseVersionServiceMock
    )

    val loggedInDeveloper = buildDeveloper()
    val sessionId         = "sessionId"
    val session           = Session(sessionId, loggedInDeveloper, LoggedInState.LOGGED_IN)
    val sessionParams     = Seq("csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken)
    val developerSession  = DeveloperSession(session)

    val loggedOutRequest = FakeRequest().withSession(sessionParams: _*)
    val loggedInRequest  = FakeRequest().withLoggedIn(underTest, implicitly)(sessionId).withSession(sessionParams: _*)

    val appId = ApplicationId.random

    implicit val hc: HeaderCarrier = HeaderCarrier()

    def givenApplicationExists(
        userRole: Collaborator.Role = Collaborator.Roles.ADMINISTRATOR,
        environment: Environment = Environment.PRODUCTION,
        checkInformation: Option[CheckInformation] = None,
        access: Access = Standard()
      ) = {
      val application = Application(
        appId,
        ClientId("clientId"),
        "appName",
        LocalDateTime.now(ZoneOffset.UTC),
        Some(LocalDateTime.now(ZoneOffset.UTC)),
        None,
        grantLength,
        environment,
        collaborators = Set(loggedInDeveloper.email.asCollaborator(userRole)),
        access = access,
        state = ApplicationState.production("dont-care", "dont-care", "dont-care"),
        checkInformation = checkInformation
      )

      givenApplicationAction(application, developerSession)

      application
    }

    when(underTest.sessionService.fetch(eqTo(sessionId))(*)).thenReturn(successful(Some(session)))
    updateUserFlowSessionsReturnsSuccessfully(sessionId)
  }

  "termsOfUsePartial" should {
    "render the partial" in new Setup {
      returnLatestTermsOfUseVersion

      when(underTest.appConfig.thirdPartyDeveloperFrontendUrl).thenReturn("http://tpdf")

      val request = FakeRequest()
      val result  = underTest.termsOfUsePartial()(request)

      status(result) shouldBe OK
    }
  }

  "termsOfUse" should {

    "render the page for an administrator on a standard production app when the ToU have not been agreed" in new Setup {
      val checkInformation = CheckInformation(termsOfUseAgreements = List.empty)
      returnTermsOfUseVersionForApplication
      givenApplicationExists(checkInformation = Some(checkInformation))
      val result           = addToken(underTest.termsOfUse(appId))(loggedInRequest)
      status(result) shouldBe OK
      contentAsString(result) should include("Agree to our terms of use")
      contentAsString(result) should not include "Terms of use accepted on"
    }

    "render the page for an administrator on a standard production app when the ToU have been agreed" in new Setup {
      val email             = "email@exmaple.com".toLaxEmail
      val timeStamp         = LocalDateTime.now(ZoneOffset.UTC)
      val expectedTimeStamp = DateTimeFormatter.ofPattern("dd MMMM yyyy").format(timeStamp)
      val version           = "1.0"

      val checkInformation = CheckInformation(termsOfUseAgreements = List(TermsOfUseAgreement(email, timeStamp, version)))
      returnTermsOfUseVersionForApplication
      givenApplicationExists(checkInformation = Some(checkInformation))
      val result           = addToken(underTest.termsOfUse(appId))(loggedInRequest)
      status(result) shouldBe OK
      contentAsString(result) should include("Terms of use")
      contentAsString(result) should include(s"Terms of use accepted on $expectedTimeStamp by ${email.text}.")
    }

    "return a bad request for a sandbox app" in new Setup {
      givenApplicationExists(environment = Environment.SANDBOX)
      val result = addToken(underTest.termsOfUse(appId))(loggedInRequest)
      status(result) shouldBe BAD_REQUEST
    }

    "return the ROPC page for a ROPC app" in new Setup {
      givenApplicationExists(access = ROPC())
      val result = addToken(underTest.termsOfUse(appId))(loggedInRequest)
      status(result) shouldBe BAD_REQUEST // FORBIDDEN
    }

    "return the privileged page for a privileged app" in new Setup {
      givenApplicationExists(access = Privileged())
      val result = addToken(underTest.termsOfUse(appId))(loggedInRequest)
      status(result) shouldBe BAD_REQUEST // FORBIDDEN
    }
  }

  "agreeTermsOfUse" should {

    "record the terms of use agreement for an administrator on a standard production app" in new Setup {
      val application                              = givenApplicationExists()
      returnLatestTermsOfUseVersion
      val captor: ArgumentCaptor[CheckInformation] = ArgumentCaptor.forClass(classOf[CheckInformation])
      when(underTest.applicationService.updateCheckInformation(eqTo(application), captor.capture())(*)).thenReturn(Future.successful(ApplicationUpdateSuccessful))

      val request = loggedInRequest.withFormUrlEncodedBody("termsOfUseAgreed" -> "true")
      val result  = addToken(underTest.agreeTermsOfUse(appId))(request)
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/applications/${application.id}/details")

      val termsOfUseAgreement = captor.getValue.termsOfUseAgreements.head
      termsOfUseAgreement.emailAddress shouldBe loggedInDeveloper.email
      termsOfUseAgreement.version shouldBe TermsOfUseVersion.latest.toString
    }

    "return a bad request if termsOfUseAgreed is not set in the request" in new Setup {
      returnTermsOfUseVersionForApplication
      givenApplicationExists()
      val result = addToken(underTest.agreeTermsOfUse(appId))(loggedInRequest)
      status(result) shouldBe BAD_REQUEST
      verify(underTest.applicationService, never).updateCheckInformation(*, *)(*)
    }

    "return a bad request if the app already has terms of use agreed" in new Setup {
      val checkInformation = CheckInformation(termsOfUseAgreements = List(applications.TermsOfUseAgreement("bob@example.com".toLaxEmail, LocalDateTime.now(ZoneOffset.UTC), "1.0")))
      givenApplicationExists(checkInformation = Some(checkInformation))

      val request = loggedInRequest.withFormUrlEncodedBody("termsOfUseAgreed" -> "true")
      val result  = addToken(underTest.agreeTermsOfUse(appId))(request)
      status(result) shouldBe BAD_REQUEST
      verify(underTest.applicationService, never).updateCheckInformation(*, *)(*)
    }

    "return a bad request for a ROPC app" in new Setup {
      givenApplicationExists(access = ROPC())
      val request = loggedInRequest.withFormUrlEncodedBody("termsOfUseAgreed" -> "true")
      val result  = addToken(underTest.agreeTermsOfUse(appId))(request)
      status(result) shouldBe BAD_REQUEST // FORBIDDEN
    }

    "return a bad request for a Privileged app" in new Setup {
      givenApplicationExists(access = Privileged())
      val request = loggedInRequest.withFormUrlEncodedBody("termsOfUseAgreed" -> "true")
      val result  = addToken(underTest.agreeTermsOfUse(appId))(request)
      status(result) shouldBe BAD_REQUEST // FORBIDDEN
    }
  }
}

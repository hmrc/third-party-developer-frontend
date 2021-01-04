/*
 * Copyright 2021 HM Revenue & Customs
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

import builder.DeveloperBuilder
import domain.ApplicationUpdateSuccessful
import domain.models.applications
import domain.models.applications._
import domain.models.applications.Environment.{PRODUCTION, SANDBOX}
import domain.models.applications.Role.ADMINISTRATOR
import domain.models.developers.{LoggedInState, Session}
import mocks.service.{ApplicationActionServiceMock, ApplicationServiceMock, SessionServiceMock}
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.mockito.ArgumentCaptor
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.time.DateTimeUtils
import utils.WithCSRFAddToken
import utils.WithLoggedInSession._
import views.html.TermsOfUseView

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future.successful
import domain.models.developers.DeveloperSession

class TermsOfUseSpec extends BaseControllerSpec with WithCSRFAddToken {

  trait Setup extends ApplicationServiceMock with SessionServiceMock with ApplicationActionServiceMock with DeveloperBuilder {
    val termsOfUseView = app.injector.instanceOf[TermsOfUseView]

    val underTest = new TermsOfUse(
      mockErrorHandler,
      applicationServiceMock,
      applicationActionServiceMock,
      sessionServiceMock,
      mcc,
      cookieSigner,
      termsOfUseView
    )

    val loggedInUser = buildDeveloper()
    val sessionId = "sessionId"
    val session = Session(sessionId, loggedInUser, LoggedInState.LOGGED_IN)
    val sessionParams = Seq("csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken)
    val developerSession = DeveloperSession(session)

    val loggedOutRequest = FakeRequest().withSession(sessionParams: _*)
    val loggedInRequest = FakeRequest().withLoggedIn(underTest, implicitly)(sessionId).withSession(sessionParams: _*)

    val appId = ApplicationId("1234")

    implicit val hc = HeaderCarrier()

    def givenApplicationExists(
        userRole: Role = ADMINISTRATOR,
        environment: Environment = PRODUCTION,
        checkInformation: Option[CheckInformation] = None,
        access: Access = Standard()
    ) = {
      val application = Application(
        appId,
        ClientId("clientId"),
        "appName",
        DateTimeUtils.now,
        DateTimeUtils.now,
        None,
        environment,
        collaborators = Set(Collaborator(loggedInUser.email, userRole)),
        access = access,
        state = ApplicationState.production("dont-care", "dont-care"),
        checkInformation = checkInformation
      )

      givenApplicationAction(application, developerSession)

      application
    }

    when(underTest.sessionService.fetch(eqTo(sessionId))(any[HeaderCarrier])).thenReturn(successful(Some(session)))
    updateUserFlowSessionsReturnsSuccessfully(sessionId)
  }

  "termsOfUsePartial" should {
    "render the partial with correct version, date and links to the support page" in new Setup {

      val version = "1.1"
      val date = DateTime.parse("2018-06-25")

      when(underTest.appConfig.thirdPartyDeveloperFrontendUrl).thenReturn("http://tpdf")
      when(underTest.appConfig.currentTermsOfUseVersion).thenReturn(version)
      when(underTest.appConfig.currentTermsOfUseDate).thenReturn(date)

      val request = FakeRequest()
      val result = underTest.termsOfUsePartial()(request)

      status(result) shouldBe OK
      contentAsString(result) should include(s"Version $version issued 25 June 2018")
      contentAsString(result) should include("http://tpdf/developer/support")
    }
  }

  "termsOfUse" should {

    "render the page for an administrator on a standard production app when the ToU have not been agreed" in new Setup {
      val checkInformation = CheckInformation(termsOfUseAgreements = Seq.empty)
      givenApplicationExists(checkInformation = Some(checkInformation))
      val result = addToken(underTest.termsOfUse(appId))(loggedInRequest)
      status(result) shouldBe OK
      contentAsString(result) should include("Agree to our terms of use")
      contentAsString(result) should not include "Terms of use accepted on"
    }

    "render the page for an administrator on a standard production app when the ToU have been agreed" in new Setup {
      val email = "email@exmaple.com"
      val timeStamp = DateTimeUtils.now
      val expectedTimeStamp = DateTimeFormat.forPattern("dd MMMM yyyy").print(timeStamp)
      val version = "1.0"

      val checkInformation = CheckInformation(termsOfUseAgreements = Seq(TermsOfUseAgreement(email, timeStamp, version)))
      givenApplicationExists(checkInformation = Some(checkInformation))
      val result = addToken(underTest.termsOfUse(appId))(loggedInRequest)
      status(result) shouldBe OK
      contentAsString(result) should include("Terms of use")
      contentAsString(result) should include(s"Terms of use accepted on $expectedTimeStamp by $email.")
    }

    "return a bad request for a sandbox app" in new Setup {
      givenApplicationExists(environment = SANDBOX)
      val result = addToken(underTest.termsOfUse(appId))(loggedInRequest)
      status(result) shouldBe BAD_REQUEST
    }

    "return the ROPC page for a ROPC app" in new Setup {
      givenApplicationExists(access = ROPC())
      val result = addToken(underTest.termsOfUse(appId))(loggedInRequest)
      status(result) shouldBe BAD_REQUEST //FORBIDDEN
    }

    "return the privileged page for a privileged app" in new Setup {
      givenApplicationExists(access = Privileged())
      val result = addToken(underTest.termsOfUse(appId))(loggedInRequest)
      status(result) shouldBe BAD_REQUEST //FORBIDDEN
    }
  }

  "agreeTermsOfUse" should {

    "record the terms of use agreement for an administrator on a standard production app" in new Setup {

      val version = "1.1"

      when(underTest.appConfig.currentTermsOfUseVersion).thenReturn(version)

      val application = givenApplicationExists()
      val captor: ArgumentCaptor[CheckInformation] = ArgumentCaptor.forClass(classOf[CheckInformation])
      when(underTest.applicationService.updateCheckInformation(eqTo(application), captor.capture())(*)).thenReturn(Future.successful(ApplicationUpdateSuccessful))

      val request = loggedInRequest.withFormUrlEncodedBody("termsOfUseAgreed" -> "true")
      val result = addToken(underTest.agreeTermsOfUse(appId))(request)
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/applications/1234/details")

      val termsOfUseAgreement = captor.getValue.termsOfUseAgreements.head
      termsOfUseAgreement.emailAddress shouldBe loggedInUser.email
      termsOfUseAgreement.version shouldBe version
    }

    "return a bad request if termsOfUseAgreed is not set in the request" in new Setup {
      givenApplicationExists()
      val result = addToken(underTest.agreeTermsOfUse(appId))(loggedInRequest)
      status(result) shouldBe BAD_REQUEST
      verify(underTest.applicationService, never).updateCheckInformation(*, *)(*)
    }

    "return a bad request if the app already has terms of use agreed" in new Setup {
      val checkInformation = CheckInformation(termsOfUseAgreements = Seq(applications.TermsOfUseAgreement("bob@example.com", DateTimeUtils.now, "1.0")))
      givenApplicationExists(checkInformation = Some(checkInformation))

      val request = loggedInRequest.withFormUrlEncodedBody("termsOfUseAgreed" -> "true")
      val result = addToken(underTest.agreeTermsOfUse(appId))(request)
      status(result) shouldBe BAD_REQUEST
      verify(underTest.applicationService, never).updateCheckInformation(*, *)(*)
    }

    "return a bad request for a ROPC app" in new Setup {
      givenApplicationExists(access = ROPC())
      val request = loggedInRequest.withFormUrlEncodedBody("termsOfUseAgreed" -> "true")
      val result = addToken(underTest.agreeTermsOfUse(appId))(request)
      status(result) shouldBe BAD_REQUEST //FORBIDDEN
    }

    "return a bad request for a Privileged app" in new Setup {
      givenApplicationExists(access = Privileged())
      val request = loggedInRequest.withFormUrlEncodedBody("termsOfUseAgreed" -> "true")
      val result = addToken(underTest.agreeTermsOfUse(appId))(request)
      status(result) shouldBe BAD_REQUEST //FORBIDDEN
    }
  }
}

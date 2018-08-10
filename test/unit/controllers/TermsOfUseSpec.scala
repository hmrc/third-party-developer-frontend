/*
 * Copyright 2018 HM Revenue & Customs
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

import config.ApplicationConfig
import controllers.TermsOfUse
import domain.Environment._
import domain.Role._
import domain._
import org.mockito.ArgumentCaptor
import org.mockito.BDDMockito.given
import org.mockito.Matchers.{any, eq => mockEq}
import org.scalatest.mockito.MockitoSugar
import org.mockito.Mockito.{verify, when, never}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider
import service.{ApplicationService, SessionService}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import uk.gov.hmrc.time.DateTimeUtils
import utils.WithCSRFAddToken
import utils.WithLoggedInSession._
import org.joda.time.DateTime

import scala.concurrent.Future

class TermsOfUseSpec extends UnitSpec with MockitoSugar with WithFakeApplication with WithCSRFAddToken {

  implicit val materializer = fakeApplication.materializer

  trait Setup {
    val mockAppConfig = mock[ApplicationConfig]
    val mockSessionService = mock[SessionService]
    val mockApplicationService = mock[ApplicationService]

    val underTest = new TermsOfUse {
      override val sessionService = mockSessionService
      override val applicationService = mockApplicationService
      override val appConfig = mockAppConfig
    }

    val loggedInUser = Developer("thirdpartydeveloper@example.com", "John", "Doe")
    val sessionId = "sessionId"
    val session = Session(sessionId, loggedInUser)
    val sessionParams = Seq("csrfToken" -> fakeApplication.injector.instanceOf[TokenProvider].generateToken)

    val loggedOutRequest = FakeRequest().withSession(sessionParams: _*)
    val loggedInRequest = FakeRequest().withLoggedIn(underTest)(sessionId).withSession(sessionParams: _*)

    val appId = "1234"

    def givenTheApplicationExists(userRole: Role.Value = ADMINISTRATOR,
                                  environment: Environment = PRODUCTION,
                                  state: ApplicationState = ApplicationState.testing,
                                  checkInformation: Option[CheckInformation] = None,
                                  access: Access = Standard()) = {
      val application = Application(appId, "clientId", "appName", DateTimeUtils.now, environment,
        collaborators = Set(Collaborator(loggedInUser.email, userRole)), access = access, state = state, checkInformation = checkInformation)
      given(mockApplicationService.fetchByApplicationId(mockEq(application.id))(any[HeaderCarrier])).willReturn(application)
      application
    }

    given(underTest.sessionService.fetch(mockEq(sessionId))(any[HeaderCarrier])).willReturn(Some(session))
  }

  "termsOfUsePartial" should {
    "render the partial with correct version, date and links to the support page" in new Setup {

      val version = "1.1"
      val date = DateTime.parse("2018-06-25")

      when(mockAppConfig.thirdPartyDeveloperFrontendUrl).thenReturn("http://tpdf")
      when(mockAppConfig.currentTermsOfUseVersion).thenReturn(version)
      when(mockAppConfig.currentTermsOfUseDate).thenReturn(date)

      val request = FakeRequest()
      val result = await(underTest.termsOfUsePartial()(request))

      status(result) shouldBe OK
      bodyOf(result) should include(s"Version $version issued 25 June 2018")
      bodyOf(result) should include("http://tpdf/developer/support")
    }
  }

  "termsOfUse" should {

    "render the page for an administrator on a standard production app" in new Setup {
      givenTheApplicationExists()
      val result = await(addToken(underTest.termsOfUse(appId))(loggedInRequest))
      status(result) shouldBe OK
      bodyOf(result) should include("Agree to our terms of use")
    }

    "return a bad request for a sandbox app" in new Setup {
      givenTheApplicationExists(environment = SANDBOX)
      val result = await(addToken(underTest.termsOfUse(appId))(loggedInRequest))
      status(result) shouldBe BAD_REQUEST
    }

    "return the ROPC page for a ROPC app" in new Setup {
      givenTheApplicationExists(access = ROPC())
      val result = await(addToken(underTest.termsOfUse(appId))(loggedInRequest))
      status(result) shouldBe OK
      bodyOf(result) should include("This application is a ROPC application")
    }

    "return the privileged page for a privileged app" in new Setup {
      givenTheApplicationExists(access = Privileged())
      val result = await(addToken(underTest.termsOfUse(appId))(loggedInRequest))
      status(result) shouldBe OK
      bodyOf(result) should include("This application is a privileged application")
    }
  }

  "agreeTermsOfUse" should {

    "record the terms of use agreement for an administrator on a standard production app" in new Setup {

      val  version = "1.1"

      when(mockAppConfig.currentTermsOfUseVersion).thenReturn(version)

      givenTheApplicationExists()
      val captor = ArgumentCaptor.forClass(classOf[CheckInformation])
      given(mockApplicationService.updateCheckInformation(mockEq(appId), captor.capture())(any())).willReturn(Future.successful(ApplicationUpdateSuccessful))

      val request = loggedInRequest.withFormUrlEncodedBody("termsOfUseAgreed" -> "true")
      val result = await(addToken(underTest.agreeTermsOfUse(appId))(request))
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/applications/1234/details")

      val termsOfUseAgreement = captor.getValue.termsOfUseAgreements.head
      termsOfUseAgreement.emailAddress shouldBe loggedInUser.email
      termsOfUseAgreement.version shouldBe version
    }

    "return a bad request if termsOfUseAgreed is not set in the request" in new Setup {
      givenTheApplicationExists()
      val result = await(addToken(underTest.agreeTermsOfUse(appId))(loggedInRequest))
      status(result) shouldBe BAD_REQUEST
      verify(mockApplicationService, never()).updateCheckInformation(any(), any())(any())
    }

    "return a bad request if the app already has terms of use agreed" in new Setup {
      val checkInformation = CheckInformation(termsOfUseAgreements = Seq(TermsOfUseAgreement("bob@example.com", DateTimeUtils.now, "1.0")))
      givenTheApplicationExists(checkInformation = Some(checkInformation))

      val request = loggedInRequest.withFormUrlEncodedBody("termsOfUseAgreed" -> "true")
      val result = await(addToken(underTest.agreeTermsOfUse(appId))(request))
      status(result) shouldBe BAD_REQUEST
      verify(mockApplicationService, never()).updateCheckInformation(any(), any())(any())
    }

    "return the ROPC page for a ROPC app" in new Setup {
      givenTheApplicationExists(access = ROPC())
      val request = loggedInRequest.withFormUrlEncodedBody("termsOfUseAgreed" -> "true")
      val result = await(addToken(underTest.agreeTermsOfUse(appId))(request))
      status(result) shouldBe OK
      bodyOf(result) should include("This application is a ROPC application")
    }

    "return the privileged page for a ROPC app" in new Setup {
      givenTheApplicationExists(access = Privileged())
      val request = loggedInRequest.withFormUrlEncodedBody("termsOfUseAgreed" -> "true")
      val result = await(addToken(underTest.agreeTermsOfUse(appId))(request))
      status(result) shouldBe OK
      bodyOf(result) should include("This application is a privileged application")
    }
  }
}

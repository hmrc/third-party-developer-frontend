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

import domain.Environment._
import domain.Role._
import domain._
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.{any, eq => mockEq}
import org.mockito.BDDMockito.given
import org.mockito.Mockito.{never, verify, when}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider
import service.{ApplicationService, SessionService}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.time.DateTimeUtils
import utils.WithCSRFAddToken
import utils.WithLoggedInSession._

import scala.concurrent.Future

class TermsOfUseSpec extends BaseControllerSpec with WithCSRFAddToken {

  trait Setup {
    val underTest = new TermsOfUse(
      mockErrorHandler,
      mock[SessionService],
      mock[ApplicationService],
      messagesApi
      )

    val loggedInUser = Developer("thirdpartydeveloper@example.com", "John", "Doe")
    val sessionId = "sessionId"
    val session = Session(sessionId, loggedInUser, LoggedInState.LOGGED_IN)
    val sessionParams = Seq("csrfToken" -> fakeApplication.injector.instanceOf[TokenProvider].generateToken)

    val loggedOutRequest = FakeRequest().withSession(sessionParams: _*)
    val loggedInRequest = FakeRequest().withLoggedIn(underTest, implicitly)(sessionId).withSession(sessionParams: _*)

    val appId = "1234"

    def givenTheApplicationExists(userRole: Role = ADMINISTRATOR,
                                  environment: Environment = PRODUCTION,
                                  state: ApplicationState = ApplicationState.testing,
                                  checkInformation: Option[CheckInformation] = None,
                                  access: Access = Standard()) = {
      val application = Application(appId, "clientId", "appName", DateTimeUtils.now, DateTimeUtils.now, environment,
        collaborators = Set(Collaborator(loggedInUser.email, userRole)), access = access, state = state, checkInformation = checkInformation)
      given(underTest.applicationService.fetchByApplicationId(mockEq(application.id))(any[HeaderCarrier])).willReturn(application)
      given(underTest.applicationService.apisWithSubscriptions(mockEq(application))(any[HeaderCarrier])).willReturn(Seq.empty[APISubscriptionStatus])
      application
    }

    given(underTest.sessionService.fetch(mockEq(sessionId))(any[HeaderCarrier])).willReturn(Some(session))
  }

  "termsOfUsePartial" should {
    "render the partial with correct version, date and links to the support page" in new Setup {

      val version = "1.1"
      val date = DateTime.parse("2018-06-25")

      when(underTest.appConfig.thirdPartyDeveloperFrontendUrl).thenReturn("http://tpdf")
      when(underTest.appConfig.currentTermsOfUseVersion).thenReturn(version)
      when(underTest.appConfig.currentTermsOfUseDate).thenReturn(date)

      val request = FakeRequest()
      val result = await(underTest.termsOfUsePartial()(request))

      status(result) shouldBe OK
      bodyOf(result) should include(s"Version $version issued 25 June 2018")
      bodyOf(result) should include("http://tpdf/developer/support")
    }
  }

  "termsOfUse" should {

    "render the page for an administrator on a standard production app when the ToU have not been agreed" in new Setup {
      val checkInformation = CheckInformation(termsOfUseAgreements = Seq.empty)
      givenTheApplicationExists(checkInformation = Some(checkInformation))
      val result = await(addToken(underTest.termsOfUse(appId))(loggedInRequest))
      status(result) shouldBe OK
      bodyOf(result) should include("Agree to our terms of use")
      bodyOf(result) should not include "Terms of use accepted on"
    }

    "render the page for an administrator on a standard production app when the ToU have been agreed" in new Setup {
      val email = "email@exmaple.com"
      val timeStamp = DateTimeUtils.now
      val expectedTimeStamp = DateTimeFormat.forPattern("dd MMMM yyyy").print(timeStamp)
      val version = "1.0"

      val checkInformation = CheckInformation(termsOfUseAgreements = Seq(TermsOfUseAgreement(email, timeStamp, version)))
      givenTheApplicationExists(checkInformation = Some(checkInformation))
      val result = await(addToken(underTest.termsOfUse(appId))(loggedInRequest))
      status(result) shouldBe OK
      bodyOf(result) should include("Terms of use")
      bodyOf(result) should include(s"Terms of use accepted on $expectedTimeStamp by $email.")
    }

    "return a bad request for a sandbox app" in new Setup {
      givenTheApplicationExists(environment = SANDBOX)
      val result = await(addToken(underTest.termsOfUse(appId))(loggedInRequest))
      status(result) shouldBe BAD_REQUEST
    }

    "return the ROPC page for a ROPC app" in new Setup {
      givenTheApplicationExists(access = ROPC())
      val result = await(addToken(underTest.termsOfUse(appId))(loggedInRequest))
      status(result) shouldBe BAD_REQUEST //FORBIDDEN
    }

    "return the privileged page for a privileged app" in new Setup {
      givenTheApplicationExists(access = Privileged())
      val result = await(addToken(underTest.termsOfUse(appId))(loggedInRequest))
      status(result) shouldBe BAD_REQUEST //FORBIDDEN
    }
  }

  "agreeTermsOfUse" should {

    "record the terms of use agreement for an administrator on a standard production app" in new Setup {

      val  version = "1.1"

      when(underTest.appConfig.currentTermsOfUseVersion).thenReturn(version)

      givenTheApplicationExists()
      val captor: ArgumentCaptor[CheckInformation] = ArgumentCaptor.forClass(classOf[CheckInformation])
      given(underTest.applicationService.updateCheckInformation(mockEq(appId), captor.capture())(any())).willReturn(Future.successful(ApplicationUpdateSuccessful))

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
      verify(underTest.applicationService, never()).updateCheckInformation(any(), any())(any())
    }

    "return a bad request if the app already has terms of use agreed" in new Setup {
      val checkInformation = CheckInformation(termsOfUseAgreements = Seq(TermsOfUseAgreement("bob@example.com", DateTimeUtils.now, "1.0")))
      givenTheApplicationExists(checkInformation = Some(checkInformation))

      val request = loggedInRequest.withFormUrlEncodedBody("termsOfUseAgreed" -> "true")
      val result = await(addToken(underTest.agreeTermsOfUse(appId))(request))
      status(result) shouldBe BAD_REQUEST
      verify(underTest.applicationService, never()).updateCheckInformation(any(), any())(any())
    }

    "return a bad request for a ROPC app" in new Setup {
      givenTheApplicationExists(access = ROPC())
      val request = loggedInRequest.withFormUrlEncodedBody("termsOfUseAgreed" -> "true")
      val result = await(addToken(underTest.agreeTermsOfUse(appId))(request))
      status(result) shouldBe BAD_REQUEST //FORBIDDEN
    }

    "return a bad request for a Privileged app" in new Setup {
      givenTheApplicationExists(access = Privileged())
      val request = loggedInRequest.withFormUrlEncodedBody("termsOfUseAgreed" -> "true")
      val result = await(addToken(underTest.agreeTermsOfUse(appId))(request))
      status(result) shouldBe BAD_REQUEST //FORBIDDEN
    }
  }
}

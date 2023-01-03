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

package uk.gov.hmrc.apiplatform.modules.mfa.controllers.profile

import play.api.http.Status
import play.api.mvc.{AnyContentAsFormUrlEncoded, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.apiplatform.modules.mfa.MfaViewsValidator
import uk.gov.hmrc.apiplatform.modules.mfa.connectors.ThirdPartyDeveloperMfaConnector
import uk.gov.hmrc.apiplatform.modules.mfa.connectors.ThirdPartyDeveloperMfaConnector.{RegisterAuthAppResponse, RegisterSmsResponse}
import uk.gov.hmrc.apiplatform.modules.mfa.service.MfaService
import uk.gov.hmrc.apiplatform.modules.mfa.views.html.{RemoveMfaCompletedView, SecurityPreferencesView, SelectMfaView}
import uk.gov.hmrc.apiplatform.modules.mfa.views.html.authapp.{AuthAppAccessCodeView, AuthAppSetupCompletedView, AuthAppSetupReminderView, AuthAppSetupSkippedView, AuthAppStartView, NameChangeView, QrCodeView}
import uk.gov.hmrc.apiplatform.modules.mfa.views.html.sms.{MobileNumberView, SmsAccessCodeView, SmsSetupCompletedView, SmsSetupReminderView, SmsSetupSkippedView}
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.{DeveloperBuilder, MfaDetailBuilder}
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ErrorHandler
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ThirdPartyDeveloperConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.BaseControllerSpec
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.{LoggedInState, Session}
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service.SessionServiceMock
import uk.gov.hmrc.thirdpartydeveloperfrontend.qr.{OtpAuthUri, QRCode}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithLoggedInSession.AuthFakeRequest
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{LocalUserIdTracker, WithCSRFAddToken}

import java.net.URI
import scala.concurrent.Future
import scala.concurrent.Future.successful
import scala.concurrent.ExecutionContext.Implicits.global

class MfaControllerBaseSpec extends BaseControllerSpec
  with WithCSRFAddToken
  with DeveloperBuilder
  with LocalUserIdTracker
  with MfaDetailBuilder
  with MfaViewsValidator {

  trait Setup extends SessionServiceMock {
    val secret = "ABCDEFGH"
    val issuer = "HMRC Developer Hub"
    val sessionId = "sessionId"
    val authAppMfaId = verifiedAuthenticatorAppMfaDetail.id
    val smsMfaId = verifiedSmsMfaDetail.id
    val loggedInDeveloper = buildDeveloper()
    val otpUri = new URI("OTPURI")
    val correctCode = "123123"
    val mobileNumber = "07774567891"

    def loggedInState: LoggedInState

    val securityPreferencesView = app.injector.instanceOf[SecurityPreferencesView]
    val authAppStartView = app.injector.instanceOf[AuthAppStartView]
    val accessCodeView = app.injector.instanceOf[AuthAppAccessCodeView]
    val qrCodeView = app.injector.instanceOf[QrCodeView]
    val authAppSetupCompletedView = app.injector.instanceOf[AuthAppSetupCompletedView]
    val nameChangeView = app.injector.instanceOf[NameChangeView]
    val mobileNumberView = app.injector.instanceOf[MobileNumberView]
    val smsAccessCodeView = app.injector.instanceOf[SmsAccessCodeView]
    val smsSetupCompletedView = app.injector.instanceOf[SmsSetupCompletedView]
    val smsSetupSkippedView = app.injector.instanceOf[SmsSetupSkippedView]
    val smsSetupReminderView = app.injector.instanceOf[SmsSetupReminderView]
    val authAppSkippedView = app.injector.instanceOf[AuthAppSetupSkippedView]
    val authAppSetupReminderView = app.injector.instanceOf[AuthAppSetupReminderView]
    val selectMfaView = app.injector.instanceOf[SelectMfaView]
    val errorHandler = app.injector.instanceOf[ErrorHandler]
    val removeMfaCompletedView = app.injector.instanceOf[RemoveMfaCompletedView]

    val underTest: MfaController = new MfaController(
      mock[ThirdPartyDeveloperConnector],
      mock[ThirdPartyDeveloperMfaConnector],
      mock[OtpAuthUri],
      mock[MfaService],
      sessionServiceMock,
      mcc,
      errorHandler,
      cookieSigner,
      securityPreferencesView,
      authAppStartView,
      accessCodeView,
      qrCodeView,
      authAppSetupCompletedView,
      nameChangeView,
      mobileNumberView,
      smsAccessCodeView,
      smsSetupCompletedView: SmsSetupCompletedView,
      smsSetupSkippedView: SmsSetupSkippedView,
      smsSetupReminderView: SmsSetupReminderView,
      authAppSkippedView: AuthAppSetupSkippedView,
      authAppSetupReminderView: AuthAppSetupReminderView,
      selectMfaView: SelectMfaView,
      removeMfaCompletedView: RemoveMfaCompletedView
    ) {
      override val qrCode: QRCode = mock[QRCode]
    }

    fetchSessionByIdReturns(sessionId, Session(sessionId, loggedInDeveloper, loggedInState))
    updateUserFlowSessionsReturnsSuccessfully(sessionId)

    val registerSmsResponse: RegisterSmsResponse = RegisterSmsResponse(mfaId = smsMfaId, mobileNumber = verifiedSmsMfaDetail.mobileNumber)

    def validateRedirectToLoginPage(result: Future[Result]) = {
      status(result) shouldBe Status.SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/login")
    }

    def createRequestWithInvalidSession(formFieldMap: Map[String, String] = Map.empty) = {
      val invalidSessionId = "notASessionId"
      when(underTest.sessionService.fetch(eqTo(invalidSessionId))(*))
        .thenReturn(Future.successful(None))

      val request = FakeRequest()
        .withLoggedIn(underTest, implicitly)(invalidSessionId)
        .withCSRFToken

      if (formFieldMap.isEmpty) request else request.withFormUrlEncodedBody(formFieldMap.toSeq: _*)
    }

    def createRequest() = {
      FakeRequest().withLoggedIn(underTest, implicitly)(sessionId).withCSRFToken
    }

    def authAppAccessCodeRequest(code: String): FakeRequest[AnyContentAsFormUrlEncoded] = {
      createRequest().withFormUrlEncodedBody("accessCode" -> code)
    }

    def nameChangeRequest(name: String): FakeRequest[AnyContentAsFormUrlEncoded] = {
      createRequest().withFormUrlEncodedBody("name" -> name)
    }

    def selectMfaRequest(mfaType: String): FakeRequest[AnyContentAsFormUrlEncoded] = {
      createRequest().withFormUrlEncodedBody("mfaType" -> mfaType)
    }

    def mobileNumberRequest(): FakeRequest[AnyContentAsFormUrlEncoded] = {
      createRequest().withFormUrlEncodedBody("mobileNumber" -> mobileNumber)
    }

    def smsAccessCodeRequest(code: String): FakeRequest[AnyContentAsFormUrlEncoded] = {
      createRequest().withFormUrlEncodedBody("accessCode" -> code, "mobileNumber" -> mobileNumber)
    }
  }

  trait SetupUnprotectedAccount extends Setup {
    when(underTest.thirdPartyDeveloperConnector.fetchDeveloper(eqTo(loggedInDeveloper.userId))(*))
      .thenReturn(successful(Some(buildDeveloper(emailAddress = loggedInDeveloper.email, organisation = None))))
  }

  trait SetupAuthAppSecurityPreferences extends Setup {
    when(underTest.thirdPartyDeveloperConnector.fetchDeveloper(eqTo(loggedInDeveloper.userId))(*))
      .thenReturn(successful(Some(
        buildDeveloper(emailAddress = loggedInDeveloper.email, organisation = None, mfaDetails = List(verifiedAuthenticatorAppMfaDetail))
      )))
  }

  trait SetupSmsSecurityPreferences extends Setup {
    when(underTest.thirdPartyDeveloperConnector.fetchDeveloper(eqTo(loggedInDeveloper.userId))(*))
      .thenReturn(successful(Some(
        buildDeveloper(emailAddress = loggedInDeveloper.email, organisation = None, mfaDetails = List(verifiedSmsMfaDetail))
      )))
  }

  trait SetupWithUnverifiedSmsSecurityPreferences extends Setup {
    when(underTest.thirdPartyDeveloperConnector.fetchDeveloper(eqTo(loggedInDeveloper.userId))(*))
      .thenReturn(successful(Some(
        buildDeveloper(emailAddress = loggedInDeveloper.email, organisation = None, mfaDetails = List(verifiedSmsMfaDetail.copy(verified = false)))
      )))
  }

  trait SetupSmsAndAuthAppSecurityPreferences extends Setup {
    when(underTest.thirdPartyDeveloperConnector.fetchDeveloper(eqTo(loggedInDeveloper.userId))(*))
      .thenReturn(successful(Some(
        buildDeveloper(emailAddress = loggedInDeveloper.email, organisation = None, mfaDetails = List(verifiedSmsMfaDetail, verifiedAuthenticatorAppMfaDetail))
      )))
  }

  trait SetupSuccessfulStart2SV extends Setup {
    val registerAuthAppResponse = RegisterAuthAppResponse(authAppMfaId, secret)

    when(underTest.otpAuthUri.apply(secret.toLowerCase(), issuer, loggedInDeveloper.email)).thenReturn(otpUri)
    when(underTest.qrCode.generateDataImageBase64(otpUri.toString)).thenReturn(qrImage)
    when(underTest.thirdPartyDeveloperMfaConnector.createMfaAuthApp(eqTo(loggedInDeveloper.userId))(*))
      .thenReturn(successful(registerAuthAppResponse))
  }

  trait PartLogged extends Setup {
    override def loggedInState: LoggedInState = LoggedInState.PART_LOGGED_IN_ENABLING_MFA
  }

  trait LoggedIn extends Setup {
    override def loggedInState: LoggedInState = LoggedInState.LOGGED_IN
  }
}

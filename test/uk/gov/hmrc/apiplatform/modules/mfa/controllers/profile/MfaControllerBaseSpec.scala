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

import java.net.URI
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future.successful

import play.api.http.Status
import play.api.mvc.{AnyContentAsFormUrlEncoded, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._

import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.apiplatform.modules.mfa.MfaViewsValidator
import uk.gov.hmrc.apiplatform.modules.mfa.connectors.ThirdPartyDeveloperMfaConnector
import uk.gov.hmrc.apiplatform.modules.mfa.service.MfaService
import uk.gov.hmrc.apiplatform.modules.mfa.views.html.authapp._
import uk.gov.hmrc.apiplatform.modules.mfa.views.html.sms.{MobileNumberView, SmsAccessCodeView, SmsSetupCompletedView, SmsSetupReminderView, SmsSetupSkippedView}
import uk.gov.hmrc.apiplatform.modules.mfa.views.html.{RemoveMfaCompletedView, SecurityPreferencesView, SelectMfaView}
import uk.gov.hmrc.apiplatform.modules.tpd.mfa.dto._
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.{LoggedInState, UserSession, UserSessionId}
import uk.gov.hmrc.apiplatform.modules.tpd.test.builders.{MfaDetailBuilder, UserBuilder}
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ErrorHandler
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.{ThirdPartyDeveloperConnector, ThirdPartyDeveloperSessionConnector}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.BaseControllerSpec
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service.SessionServiceMock
import uk.gov.hmrc.thirdpartydeveloperfrontend.qr.{OtpAuthUri, QRCode}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithCSRFAddToken
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithLoggedInSession.AuthFakeRequest

class MfaControllerBaseSpec extends BaseControllerSpec
    with WithCSRFAddToken
    with MfaDetailBuilder
    with MfaViewsValidator {

  trait Setup
      extends UserBuilder
      with LocalUserIdTracker
      with FixedClock
      with SessionServiceMock {

    val secret            = "ABCDEFGH"
    val issuer            = "HMRC Developer Hub"
    val sessionId         = UserSessionId.random
    val authAppMfaId      = verifiedAuthenticatorAppMfaDetail.id
    val smsMfaId          = verifiedSmsMfaDetail.id
    val loggedInDeveloper = buildTrackedUser()
    val otpUri            = new URI("OTPURI")
    val correctCode       = "123123"
    val mobileNumber      = "07774567891"

    def loggedInState: LoggedInState

    val securityPreferencesView   = app.injector.instanceOf[SecurityPreferencesView]
    val authAppStartView          = app.injector.instanceOf[AuthAppStartView]
    val accessCodeView            = app.injector.instanceOf[AuthAppAccessCodeView]
    val qrCodeView                = app.injector.instanceOf[QrCodeView]
    val authAppSetupCompletedView = app.injector.instanceOf[AuthAppSetupCompletedView]
    val nameChangeView            = app.injector.instanceOf[NameChangeView]
    val mobileNumberView          = app.injector.instanceOf[MobileNumberView]
    val smsAccessCodeView         = app.injector.instanceOf[SmsAccessCodeView]
    val smsSetupCompletedView     = app.injector.instanceOf[SmsSetupCompletedView]
    val smsSetupSkippedView       = app.injector.instanceOf[SmsSetupSkippedView]
    val smsSetupReminderView      = app.injector.instanceOf[SmsSetupReminderView]
    val authAppSkippedView        = app.injector.instanceOf[AuthAppSetupSkippedView]
    val authAppSetupReminderView  = app.injector.instanceOf[AuthAppSetupReminderView]
    val selectMfaView             = app.injector.instanceOf[SelectMfaView]
    val errorHandler              = app.injector.instanceOf[ErrorHandler]
    val removeMfaCompletedView    = app.injector.instanceOf[RemoveMfaCompletedView]

    val underTest: MfaController = new MfaController(
      mock[ThirdPartyDeveloperConnector],
      mock[ThirdPartyDeveloperSessionConnector],
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

    fetchSessionByIdReturns(sessionId, UserSession(sessionId, loggedInState, loggedInDeveloper))
    updateUserFlowSessionsReturnsSuccessfully(sessionId)

    val registerSmsResponse: RegisterSmsResponse = RegisterSmsResponse(mfaId = smsMfaId, mobileNumber = verifiedSmsMfaDetail.mobileNumber)

    def validateRedirectToLoginPage(result: Future[Result]) = {
      status(result) shouldBe Status.SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/login")
    }

    def createRequestWithInvalidSession(formFieldMap: Map[String, String] = Map.empty) = {
      val notPresentSessionId = UserSessionId.random
      when(underTest.sessionService.fetch(eqTo(notPresentSessionId))(*))
        .thenReturn(Future.successful(None))

      val request = FakeRequest()
        .withLoggedIn(underTest, implicitly)(notPresentSessionId)
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
      .thenReturn(successful(Some(buildTrackedUser(emailAddress = loggedInDeveloper.email, organisation = None))))
  }

  trait SetupAuthAppSecurityPreferences extends Setup {
    when(underTest.thirdPartyDeveloperConnector.fetchDeveloper(eqTo(loggedInDeveloper.userId))(*))
      .thenReturn(successful(Some(
        buildTrackedUser(emailAddress = loggedInDeveloper.email, organisation = None, mfaDetails = List(verifiedAuthenticatorAppMfaDetail))
      )))
  }

  trait SetupSmsSecurityPreferences extends Setup {
    when(underTest.thirdPartyDeveloperConnector.fetchDeveloper(eqTo(loggedInDeveloper.userId))(*))
      .thenReturn(successful(Some(
        buildTrackedUser(emailAddress = loggedInDeveloper.email, organisation = None, mfaDetails = List(verifiedSmsMfaDetail))
      )))
  }

  trait SetupWithUnverifiedSmsSecurityPreferences extends Setup {
    when(underTest.thirdPartyDeveloperConnector.fetchDeveloper(eqTo(loggedInDeveloper.userId))(*))
      .thenReturn(successful(Some(
        buildTrackedUser(emailAddress = loggedInDeveloper.email, organisation = None, mfaDetails = List(verifiedSmsMfaDetail.copy(verified = false)))
      )))
  }

  trait SetupSmsAndAuthAppSecurityPreferences extends Setup {
    when(underTest.thirdPartyDeveloperConnector.fetchDeveloper(eqTo(loggedInDeveloper.userId))(*))
      .thenReturn(successful(Some(
        buildTrackedUser(emailAddress = loggedInDeveloper.email, organisation = None, mfaDetails = List(verifiedSmsMfaDetail, verifiedAuthenticatorAppMfaDetail))
      )))
  }

  trait SetupSuccessfulStart2SV extends Setup {
    val registerAuthAppResponse = RegisterAuthAppResponse(secret, authAppMfaId)

    when(underTest.otpAuthUri.apply(secret.toLowerCase(), issuer, loggedInDeveloper.email.text)).thenReturn(otpUri)
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

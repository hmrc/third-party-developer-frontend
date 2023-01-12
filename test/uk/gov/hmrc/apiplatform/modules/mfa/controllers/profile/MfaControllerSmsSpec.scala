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

import scala.concurrent.Future
import scala.concurrent.Future.successful

import org.jsoup.Jsoup

import play.api.http.Status
import play.api.test.Helpers._

import uk.gov.hmrc.apiplatform.modules.mfa.models.MfaAction
import uk.gov.hmrc.apiplatform.modules.mfa.service.MfaResponse
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.UserId

class MfaControllerSmsSpec extends MfaControllerBaseSpec {

  "setupSms" should {
    "return 200 and show the mobile number view when user is Logged in" in new SetupAuthAppSecurityPreferences with LoggedIn {
      shouldReturnOK(addToken(underTest.setupSms())(mobileNumberRequest()), validateMobileNumberView)
    }

    "return 200 and show the mobile number view when user is Part Logged in" in new SetupAuthAppSecurityPreferences with PartLogged {
      shouldReturnOK(addToken(underTest.setupSms())(mobileNumberRequest()), validateMobileNumberView)
    }

    "redirect to the login page when user is not logged in" in new SetupSuccessfulStart2SV with LoggedIn {
      private val result = addToken(underTest.setupSms)(createRequestWithInvalidSession(Map("mobileNumber" -> "07771234567")))
      validateRedirectToLoginPage(result)
    }
  }

  "setupSmsAction" should {
    "redirect to access code page when user is Logged in and form is valid and call to connector is successful" in
      new SetupAuthAppSecurityPreferences with LoggedIn {
        when(underTest.thirdPartyDeveloperMfaConnector.createMfaSms(*[UserId], eqTo(mobileNumber))(*))
          .thenReturn(Future.successful(registerSmsResponse))

        private val result = underTest.setupSmsAction()(mobileNumberRequest())

        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some(s"/developer/profile/security-preferences/sms/access-code?mfaId=${smsMfaId.value.toString}&mfaAction=CREATE")

        verify(underTest.thirdPartyDeveloperMfaConnector).createMfaSms(*[UserId], eqTo(mobileNumber))(*)
      }

    "redirect to access code page when user is Part Logged in and form is valid and call to connector is successful" in
      new SetupAuthAppSecurityPreferences with PartLogged {
        when(underTest.thirdPartyDeveloperMfaConnector.createMfaSms(*[UserId], eqTo(mobileNumber))(*))
          .thenReturn(Future.successful(registerSmsResponse))

        private val result = underTest.setupSmsAction()(mobileNumberRequest())

        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some(s"/developer/profile/security-preferences/sms/access-code?mfaId=${smsMfaId.value.toString}&mfaAction=CREATE")

        verify(underTest.thirdPartyDeveloperMfaConnector).createMfaSms(*[UserId], eqTo(mobileNumber))(*)
      }

    "redirect to login page when user is not logged in" in new SetupAuthAppSecurityPreferences with LoggedIn {
      private val result = addToken(underTest.setupSmsAction)(createRequestWithInvalidSession(Map("mobileNumber" -> "07771234567")))
      validateRedirectToLoginPage(result)
      verifyZeroInteractions(underTest.thirdPartyDeveloperMfaConnector)
    }

    "return Bad Request when user is logged in and mobile number is invalid on the form" in new SetupSuccessfulStart2SV with LoggedIn {
      val request = createRequest().withFormUrlEncodedBody("mobileNumber" -> "INVALID_NUMBER")

      val result = addToken(underTest.setupSmsAction())(request)

      status(result) shouldBe BAD_REQUEST
      val doc = Jsoup.parse(contentAsString(result))
      validateMobileNumberView(doc)
      doc.getElementById("data-field-error-mobileNumber").text() shouldBe "Error: It must be a valid mobile number"

      verifyZeroInteractions(underTest.thirdPartyDeveloperMfaConnector)
    }
  }

  "smsSetupReminderPage" should {
    "return sms setup reminder view when user is logged in with only Authenticator App MFA setup" in new SetupSuccessfulStart2SV with LoggedIn {
      when(underTest.thirdPartyDeveloperConnector.fetchDeveloper(eqTo(loggedInDeveloper.userId))(*))
        .thenReturn(successful(Some(loggedInDeveloper)))

      val result = addToken(underTest.smsSetupReminderPage())(createRequest())
      shouldReturnOK(result, validateSmsSetupReminderView)
    }

    "return sms setup reminder view when user is part logged in with only Authenticator App MFA setup" in new SetupSuccessfulStart2SV with PartLogged {
      when(underTest.thirdPartyDeveloperConnector.fetchDeveloper(eqTo(loggedInDeveloper.userId))(*))
        .thenReturn(successful(Some(loggedInDeveloper)))

      val result = addToken(underTest.smsSetupReminderPage())(createRequest())
      shouldReturnOK(result, validateSmsSetupReminderView)
    }

    "redirect to login page when user is not logged in" in new SetupSuccessfulStart2SV with PartLogged {
      val result = addToken(underTest.smsSetupReminderPage())(createRequestWithInvalidSession())
      validateRedirectToLoginPage(result)
    }
  }

  "smsSetupSkippedPage" should {
    "return sms setup skipped view when user is logged in" in new SetupSuccessfulStart2SV with LoggedIn {
      when(underTest.thirdPartyDeveloperConnector.fetchDeveloper(eqTo(loggedInDeveloper.userId))(*))
        .thenReturn(successful(Some(loggedInDeveloper)))

      val result = addToken(underTest.smsSetupCompletedPage())(createRequest())
      shouldReturnOK(result, validateSmsCompletedPage)
    }

    "return sms setup skipped view when user is part logged in" in new SetupSuccessfulStart2SV with PartLogged {
      when(underTest.thirdPartyDeveloperConnector.fetchDeveloper(eqTo(loggedInDeveloper.userId))(*))
        .thenReturn(successful(Some(loggedInDeveloper)))

      val result = addToken(underTest.smsSetupCompletedPage())(createRequest())
      shouldReturnOK(result, validateSmsCompletedPage)
    }

    "redirect to login page when user is not logged in" in new SetupSuccessfulStart2SV with PartLogged {
      val result = addToken(underTest.smsSetupCompletedPage())(createRequestWithInvalidSession())
      validateRedirectToLoginPage(result)
    }
  }

  "smsAccessCodePage" should {
    "return sms access code view when user is logged in" in new SetupSuccessfulStart2SV with LoggedIn {
      val result = addToken(underTest.smsAccessCodePage(authAppMfaId, MfaAction.CREATE, None))(smsAccessCodeRequest(correctCode))
      shouldReturnOK(result, validateSmsAccessCodeView)
    }

    "return sms access code view when user is part logged in" in new SetupSuccessfulStart2SV with PartLogged {
      val result = addToken(underTest.smsAccessCodePage(authAppMfaId, MfaAction.CREATE, None))(smsAccessCodeRequest(correctCode))
      shouldReturnOK(result, validateSmsAccessCodeView)
    }

    "redirect to the login page when user is not logged in" in new SetupAuthAppSecurityPreferences with LoggedIn {
      private val result = addToken(underTest.smsAccessCodePage(authAppMfaId, MfaAction.CREATE, None))(
        createRequestWithInvalidSession(Map("accessCode" -> "code", "mobileNumber" -> mobileNumber))
      )
      validateRedirectToLoginPage(result)
    }
  }

  "smsAccessCodeAction for CREATE" should {
    "redirect to sms setup completed page when user is Logged in and form is valid and call to connector returns true" in
      new SetupAuthAppSecurityPreferences with LoggedIn {
        when(underTest.thirdPartyDeveloperMfaConnector.verifyMfa(*[UserId], eqTo(smsMfaId), eqTo(correctCode))(*))
          .thenReturn(Future.successful(true))

        private val result = underTest.smsAccessCodeAction(smsMfaId, MfaAction.CREATE, None)(smsAccessCodeRequest(correctCode))

        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some(s"/developer/profile/security-preferences/sms/setup/complete")

        verify(underTest.thirdPartyDeveloperMfaConnector).verifyMfa(*[UserId], eqTo(smsMfaId), eqTo(correctCode))(*)
      }

    "redirect to sms setup completed page when user is Part Logged in and form is valid and call to connector returns true" in
      new SetupAuthAppSecurityPreferences with PartLogged {
        when(underTest.thirdPartyDeveloperMfaConnector.verifyMfa(*[UserId], eqTo(smsMfaId), eqTo(correctCode))(*))
          .thenReturn(Future.successful(true))

        private val result = underTest.smsAccessCodeAction(smsMfaId, MfaAction.CREATE, None)(smsAccessCodeRequest(correctCode))

        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some(s"/developer/profile/security-preferences/sms/setup/complete")

        verify(underTest.thirdPartyDeveloperMfaConnector).verifyMfa(*[UserId], eqTo(smsMfaId), eqTo(correctCode))(*)
      }

    "redirect to login page when user is not logged in" in new SetupAuthAppSecurityPreferences with LoggedIn {
      private val result = addToken(underTest.smsAccessCodeAction(authAppMfaId, MfaAction.CREATE, None))(
        createRequestWithInvalidSession(Map("mobileNumber" -> "07771234567", "accessCode" -> correctCode))
      )
      validateRedirectToLoginPage(result)
      verifyZeroInteractions(underTest.thirdPartyDeveloperMfaConnector)
    }

    "return Bad Request when user is logged in and access code is invalid on the form" in new SetupSuccessfulStart2SV with LoggedIn {
      val request = createRequest().withFormUrlEncodedBody("mobileNumber" -> mobileNumber, "accessCode" -> "INVALID")

      val result = addToken(underTest.smsAccessCodeAction(smsMfaId, MfaAction.CREATE, None))(request)

      status(result) shouldBe BAD_REQUEST
      val doc = Jsoup.parse(contentAsString(result))
      validateSmsAccessCodeView(doc)
      doc.getElementById("data-field-error-accessCode").text() shouldBe "Error: You have entered an invalid access code"

      verifyZeroInteractions(underTest.thirdPartyDeveloperMfaConnector)
    }

    "return error page when user is Logged in and form is valid and call to connector returns false" in
      new SetupAuthAppSecurityPreferences with LoggedIn {
        when(underTest.thirdPartyDeveloperMfaConnector.verifyMfa(*[UserId], eqTo(smsMfaId), eqTo(correctCode))(*))
          .thenReturn(Future.successful(false))

        private val result = underTest.smsAccessCodeAction(smsMfaId, MfaAction.CREATE, None)(smsAccessCodeRequest(correctCode))

        validateErrorTemplateView(result, "Unable to verify SMS access code")
        verify(underTest.thirdPartyDeveloperMfaConnector).verifyMfa(*[UserId], eqTo(smsMfaId), eqTo(correctCode))(*)
      }
  }

  "smsAccessCodeAction for REMOVE" should {
    "redirect to remove mfa completed page when user is Logged in and call to connector returns true" in
      new SetupAuthAppSecurityPreferences with LoggedIn {

        when(underTest.mfaService.removeMfaById(*[UserId], eqTo(smsMfaId), eqTo(correctCode), eqTo(smsMfaId))(*))
          .thenReturn(Future.successful(MfaResponse(true)))

        private val result = underTest.smsAccessCodeAction(smsMfaId, MfaAction.REMOVE, Some(smsMfaId))(smsAccessCodeRequest(correctCode))

        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some(s"/developer/profile/security-preferences/remove-mfa/complete")

        verify(underTest.thirdPartyDeveloperMfaConnector, times(0)).verifyMfa(*[UserId], eqTo(smsMfaId), eqTo(correctCode))(*)
        verify(underTest.mfaService).removeMfaById(*[UserId], eqTo(smsMfaId), eqTo(correctCode), eqTo(smsMfaId))(*)
      }

    "redirect to login page when user is not logged in" in new SetupAuthAppSecurityPreferences with LoggedIn {
      private val result = addToken(underTest.smsAccessCodeAction(authAppMfaId, MfaAction.REMOVE, Some(authAppMfaId)))(
        createRequestWithInvalidSession(Map("mobileNumber" -> "07771234567", "accessCode" -> correctCode))
      )
      validateRedirectToLoginPage(result)
      verifyZeroInteractions(underTest.thirdPartyDeveloperMfaConnector)
    }

    "return Bad Request when user is logged in and access code is invalid on the form" in new SetupSuccessfulStart2SV with LoggedIn {
      val request = createRequest().withFormUrlEncodedBody("mobileNumber" -> mobileNumber, "accessCode" -> "INVALID")
      val result  = addToken(underTest.smsAccessCodeAction(smsMfaId, MfaAction.REMOVE, Some(smsMfaId)))(request)

      status(result) shouldBe BAD_REQUEST
      val doc = Jsoup.parse(contentAsString(result))
      validateSmsAccessCodeView(doc)
      doc.getElementById("data-field-error-accessCode").text() shouldBe "Error: You have entered an invalid access code"

      verifyZeroInteractions(underTest.thirdPartyDeveloperMfaConnector)
    }

    "return error page when user is Logged in and form is valid and call to connector returns false" in
      new SetupAuthAppSecurityPreferences with LoggedIn {
        when(underTest.mfaService.removeMfaById(*[UserId], eqTo(smsMfaId), eqTo(correctCode), eqTo(smsMfaId))(*))
          .thenReturn(Future.successful(MfaResponse(false)))

        private val result = underTest.smsAccessCodeAction(smsMfaId, MfaAction.REMOVE, Some(smsMfaId))(smsAccessCodeRequest(correctCode))

        validateErrorTemplateView(result, "Unable to verify access code")
        verify(underTest.thirdPartyDeveloperMfaConnector, times(0)).verifyMfa(*[UserId], eqTo(smsMfaId), eqTo(correctCode))(*)
        verify(underTest.mfaService).removeMfaById(*[UserId], eqTo(smsMfaId), eqTo(correctCode), eqTo(smsMfaId))(*)
      }

    "return error page when user is Logged in and form is valid and mfaIdForRemoval is None" in
      new SetupAuthAppSecurityPreferences with LoggedIn {
        private val result = underTest.smsAccessCodeAction(smsMfaId, MfaAction.REMOVE, None)(smsAccessCodeRequest(correctCode))

        validateErrorTemplateView(result, "Unable to find Mfa to remove")

        verify(underTest.thirdPartyDeveloperMfaConnector, times(0)).verifyMfa(*[UserId], eqTo(smsMfaId), eqTo(correctCode))(*)
        verify(underTest.mfaService, times(0)).removeMfaById(*[UserId], eqTo(smsMfaId), eqTo(correctCode), eqTo(smsMfaId))(*)
      }
  }

  "smsSetupCompletedPage" should {
    "return sms setup complete view when user is logged in and fetchDeveloper returns a developer" in new SetupSuccessfulStart2SV with LoggedIn {
      when(underTest.thirdPartyDeveloperConnector.fetchDeveloper(eqTo(loggedInDeveloper.userId))(*))
        .thenReturn(successful(Some(loggedInDeveloper)))

      val result = addToken(underTest.smsSetupCompletedPage())(createRequest())
      shouldReturnOK(result, validateSmsCompletedPage)
    }

    "return error template view when user is logged in and fetchDeveloper returns None" in new SetupSuccessfulStart2SV with LoggedIn {
      when(underTest.thirdPartyDeveloperConnector.fetchDeveloper(eqTo(loggedInDeveloper.userId))(*))
        .thenReturn(successful(None))

      val result = addToken(underTest.smsSetupCompletedPage())(createRequest())
      validateErrorTemplateView(result, "Unable to obtain User information")
    }

    "return sms setup complete view when user is part logged in and fetchDeveloper returns a developer" in new SetupSuccessfulStart2SV with PartLogged {
      when(underTest.thirdPartyDeveloperConnector.fetchDeveloper(eqTo(loggedInDeveloper.userId))(*))
        .thenReturn(successful(Some(loggedInDeveloper)))

      val result = addToken(underTest.smsSetupCompletedPage())(createRequest())
      shouldReturnOK(result, validateSmsCompletedPage)
    }

    "redirect to the login page when user is not logged in" in new SetupSuccessfulStart2SV with LoggedIn {
      private val result = addToken(underTest.smsSetupCompletedPage())(createRequestWithInvalidSession())
      validateRedirectToLoginPage(result)
    }
  }
}

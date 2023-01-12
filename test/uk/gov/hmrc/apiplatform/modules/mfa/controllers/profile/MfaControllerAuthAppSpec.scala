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

import org.jsoup.Jsoup
import play.api.http.Status
import play.api.test.Helpers._
import uk.gov.hmrc.apiplatform.modules.mfa.models.{MfaAction, MfaId}
import uk.gov.hmrc.apiplatform.modules.mfa.service.MfaResponse
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.UserId

import scala.concurrent.Future
import scala.concurrent.Future.successful

class MfaControllerAuthAppSpec extends MfaControllerBaseSpec {

  "authAppStart is called it" should {
    "return 200 and show the Auth App Start page when user is logged in" in new SetupAuthAppSecurityPreferences with LoggedIn {
      shouldReturnOK(underTest.authAppStart()(createRequest()), validateAuthAppStartPage)
    }

    "return 200 and show the Auth App Start page when user is part logged in" in new SetupAuthAppSecurityPreferences with PartLogged {
      shouldReturnOK(underTest.authAppStart()(createRequest()), validateAuthAppStartPage)
    }

    "redirect to the login page when user is not logged in" in new SetupAuthAppSecurityPreferences with LoggedIn {
      private val result = addToken(underTest.authAppStart())(createRequestWithInvalidSession())
      validateRedirectToLoginPage(result)
    }
  }

  "setupAuthApp" should {
    "return qrCodeView with secret from third party developer when user is logged in" in new SetupSuccessfulStart2SV with LoggedIn {
      shouldReturnOK(underTest.setupAuthApp()(createRequest()), validateQrCodePage)
    }

    "return qrCodeView with secret from third party developer when user is part logged in" in new SetupSuccessfulStart2SV with PartLogged {
      shouldReturnOK(underTest.setupAuthApp()(createRequest()), validateQrCodePage)
    }

    "redirect to the login page when user is not logged in" in new SetupSuccessfulStart2SV with LoggedIn {
      private val result = addToken(underTest.setupAuthApp())(createRequestWithInvalidSession())
      validateRedirectToLoginPage(result)
    }
  }

  "getAccessCodePage" should {
    "return access code view when user is logged in" in new SetupSuccessfulStart2SV with LoggedIn {
      val result = addToken(underTest.authAppAccessCodePage(authAppMfaId, MfaAction.CREATE, None))(authAppAccessCodeRequest(correctCode))
      shouldReturnOK(result, validateAuthAppAccessCodePage)
    }

    "return access code view when user is part logged in" in new SetupSuccessfulStart2SV with PartLogged {
      val result = addToken(underTest.authAppAccessCodePage(authAppMfaId, MfaAction.CREATE, None))(authAppAccessCodeRequest(correctCode))
      shouldReturnOK(result, validateAuthAppAccessCodePage)
    }

    "redirect to the login page when user is not logged in" in new SetupSuccessfulStart2SV with LoggedIn {
      private val result = addToken(underTest.authAppAccessCodePage(authAppMfaId, MfaAction.CREATE, None))(
        createRequestWithInvalidSession(Map("accessCode" -> correctCode))
      )
      validateRedirectToLoginPage(result)
    }
  }

  "authAppAccessCodeAction For CREATE" should {
    "return change name view when user is logged in and enable mfa successful" in new SetupSuccessfulStart2SV with LoggedIn {
      when(underTest.mfaService.enableMfa(*[UserId], *[MfaId], *)(*)).thenReturn(Future.successful(MfaResponse(true)))

      val result = addToken(underTest.authAppAccessCodeAction(authAppMfaId, MfaAction.CREATE, None))(authAppAccessCodeRequest(correctCode))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/profile/security-preferences/auth-app/name?mfaId=${authAppMfaId.value.toString}")
    }

    "return change name view when user is part logged in and enable mfa successful" in new SetupSuccessfulStart2SV with PartLogged {
      when(underTest.mfaService.enableMfa(*[UserId], *[MfaId], *)(*)).thenReturn(Future.successful(MfaResponse(true)))

      val result = addToken(underTest.authAppAccessCodeAction(authAppMfaId, MfaAction.CREATE, None))(authAppAccessCodeRequest(correctCode))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/profile/security-preferences/auth-app/name?mfaId=${authAppMfaId.value.toString}")
    }

    "return access code view with errors when user is logged in and enable mfa fails" in new SetupSuccessfulStart2SV with LoggedIn {
      when(underTest.mfaService.enableMfa(*[UserId], *[MfaId], *)(*)).thenReturn(Future.successful(MfaResponse(false)))

      val result = addToken(underTest.authAppAccessCodeAction(authAppMfaId, MfaAction.CREATE, None))(authAppAccessCodeRequest(correctCode))

      status(result) shouldBe BAD_REQUEST
      val doc = Jsoup.parse(contentAsString(result))
      validateAuthAppAccessCodePage(doc)
      doc.getElementById("data-field-error-accessCode").text() shouldBe "Error: You have entered an incorrect access code"
    }

    "return access code view with errors when user is logged in and submitted form is invalid" in new SetupSuccessfulStart2SV with LoggedIn {
      when(underTest.mfaService.enableMfa(*[UserId], *[MfaId], *)(*)).thenReturn(Future.successful(MfaResponse(false)))

      val result = addToken(underTest.authAppAccessCodeAction(authAppMfaId, MfaAction.CREATE, None))(authAppAccessCodeRequest("INVALID_CODE"))

      status(result) shouldBe BAD_REQUEST
      val doc = Jsoup.parse(contentAsString(result))
      validateAuthAppAccessCodePage(doc)
      doc.getElementById("data-field-error-accessCode").text() shouldBe "Error: You have entered an invalid access code"
    }

    "redirect to the login page when user is not logged in" in new SetupSuccessfulStart2SV with LoggedIn {
      private val result = addToken(underTest.authAppAccessCodeAction(authAppMfaId, MfaAction.CREATE, None))(
        createRequestWithInvalidSession(Map("accessCode" -> correctCode))
      )
      validateRedirectToLoginPage(result)
    }
  }

  "autAppAccessCodeAction for REMOVE" should {
    "redirect to remove mfa completed page when user is Logged in and call to connector returns true" in
      new SetupAuthAppSecurityPreferences with LoggedIn {

        when(underTest.mfaService.removeMfaById(*[UserId], eqTo(authAppMfaId), eqTo(correctCode), eqTo(authAppMfaId))(*))
          .thenReturn(Future.successful(MfaResponse(true)))

        private val result = underTest.authAppAccessCodeAction(authAppMfaId, MfaAction.REMOVE, Some(authAppMfaId))(authAppAccessCodeRequest(correctCode))

        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some(s"/developer/profile/security-preferences/remove-mfa/complete")

        verify(underTest.thirdPartyDeveloperMfaConnector, times(0)).verifyMfa(*[UserId], eqTo(authAppMfaId), eqTo(correctCode))(*)
        verify(underTest.mfaService).removeMfaById(*[UserId], eqTo(authAppMfaId), eqTo(correctCode), eqTo(authAppMfaId))(*)
      }

    "redirect to login page when user is not logged in" in new SetupAuthAppSecurityPreferences with LoggedIn {
      private val result = addToken(underTest.authAppAccessCodeAction(authAppMfaId, MfaAction.REMOVE, None))(
        createRequestWithInvalidSession(Map("accessCode" -> correctCode))
      )
      validateRedirectToLoginPage(result)
      verifyZeroInteractions(underTest.thirdPartyDeveloperMfaConnector)
    }

    "return Bad Request when user is logged in and access code is invalid on the form" in new SetupSuccessfulStart2SV with LoggedIn {
      val request = createRequest().withFormUrlEncodedBody("accessCode" -> "INVALID")
      val result  = addToken(underTest.authAppAccessCodeAction(authAppMfaId, MfaAction.REMOVE, None))(request)

      status(result) shouldBe BAD_REQUEST
      val doc = Jsoup.parse(contentAsString(result))
      validateAuthAppAccessCodePage(doc)
      doc.getElementById("data-field-error-accessCode").text() shouldBe "Error: You have entered an invalid access code"

      verifyZeroInteractions(underTest.thirdPartyDeveloperMfaConnector)
    }

    "return error page when user is Logged in and form is valid and call to connector returns false" in
      new SetupAuthAppSecurityPreferences with LoggedIn {
        when(underTest.mfaService.removeMfaById(*[UserId], eqTo(authAppMfaId), eqTo(correctCode), eqTo(authAppMfaId))(*))
          .thenReturn(Future.successful(MfaResponse(false)))

        private val result = underTest.authAppAccessCodeAction(authAppMfaId, MfaAction.REMOVE, Some(authAppMfaId))(authAppAccessCodeRequest(correctCode))

        validateErrorTemplateView(result, "Unable to verify access code")
        verify(underTest.thirdPartyDeveloperMfaConnector, times(0)).verifyMfa(*[UserId], eqTo(authAppMfaId), eqTo(correctCode))(*)
        verify(underTest.mfaService).removeMfaById(*[UserId], eqTo(authAppMfaId), eqTo(correctCode), eqTo(authAppMfaId))(*)
      }

    "return error page when user is Logged in and form is valid and call to connector returns true and mfaIdForRemoval is None" in
      new SetupAuthAppSecurityPreferences with LoggedIn {
        private val result = underTest.authAppAccessCodeAction(authAppMfaId, MfaAction.REMOVE, None)(authAppAccessCodeRequest(correctCode))

        validateErrorTemplateView(result, "Unable to find Mfa to remove")

        verify(underTest.thirdPartyDeveloperMfaConnector, times(0)).verifyMfa(*[UserId], eqTo(authAppMfaId), eqTo(correctCode))(*)
        verify(underTest.mfaService, times(0)).removeMfaById(*[UserId], eqTo(authAppMfaId), eqTo(correctCode), eqTo(authAppMfaId))(*)
      }
  }

  "getNameChangePage" should {
    "return name change view when user is logged in" in new SetupSuccessfulStart2SV with LoggedIn {
      val result = addToken(underTest.nameChangePage(authAppMfaId))(nameChangeRequest("app name"))
      shouldReturnOK(result, validateNameChangePage)
    }

    "return name change view when user is part logged in" in new SetupSuccessfulStart2SV with PartLogged {
      val result = addToken(underTest.nameChangePage(authAppMfaId))(nameChangeRequest("app name"))
      shouldReturnOK(result, validateNameChangePage)
    }

    "redirect to the login page when user is not logged in" in new SetupSuccessfulStart2SV with LoggedIn {
      private val result = addToken(underTest.nameChangePage(authAppMfaId))(createRequestWithInvalidSession())
      validateRedirectToLoginPage(result)
    }
  }

  "nameChangeAction" should {
    val updatedName = "updated name"
    "return auth app completed view when user is logged in and call to backend is successful" in new SetupSuccessfulStart2SV with LoggedIn {
      when(underTest.thirdPartyDeveloperMfaConnector.changeName(*[UserId], *[MfaId], *)(*)).thenReturn(Future.successful(true))

      val result = addToken(underTest.nameChangeAction(authAppMfaId))(nameChangeRequest(updatedName))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/profile/security-preferences/auth-app/setup/complete")
    }

    "return auth app completed view when user is part logged in and call to backend is successful" in new SetupSuccessfulStart2SV with PartLogged {
      when(underTest.thirdPartyDeveloperMfaConnector.changeName(*[UserId], *[MfaId], *)(*)).thenReturn(Future.successful(true))

      val result = addToken(underTest.nameChangeAction(authAppMfaId))(nameChangeRequest(updatedName))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/profile/security-preferences/auth-app/setup/complete")
    }

    "return error page when user is logged in and connector returns false" in new SetupSuccessfulStart2SV with LoggedIn {
      when(underTest.thirdPartyDeveloperMfaConnector.changeName(*[UserId], *[MfaId], *)(*)).thenReturn(Future.successful(false))

      val result = addToken(underTest.nameChangeAction(authAppMfaId))(nameChangeRequest(updatedName))

      validateErrorTemplateView(result, "Failed to change MFA name")
    }

    "return name change view with errors when user is logged in and form is invalid" in new SetupSuccessfulStart2SV with LoggedIn {
      val result = addToken(underTest.nameChangeAction(authAppMfaId))(nameChangeRequest("a"))

      status(result) shouldBe BAD_REQUEST
      val doc = Jsoup.parse(contentAsString(result))
      validateNameChangePage(doc)
      doc.getElementById("data-field-error-name").text() shouldBe "Error: The name must be more than 3 characters in length"

      verifyZeroInteractions(underTest.thirdPartyDeveloperMfaConnector)
    }

    "redirect to login page when user is not logged in" in new SetupSuccessfulStart2SV with LoggedIn {
      private val result = addToken(underTest.nameChangeAction(authAppMfaId))(
        createRequestWithInvalidSession(Map("name" -> "some-name"))
      )
      validateRedirectToLoginPage(result)
    }
  }

  "authAppSetupCompletedPage" should {
    "return auth app setup complete view when user is logged in and fetchDeveloper returns a developer" in new SetupSuccessfulStart2SV with LoggedIn {
      when(underTest.thirdPartyDeveloperConnector.fetchDeveloper(eqTo(loggedInDeveloper.userId))(*))
        .thenReturn(successful(Some(loggedInDeveloper)))

      val result = addToken(underTest.authAppSetupCompletedPage())(createRequest())
      shouldReturnOK(result, validateAuthAppCompletedPage)
    }

    "return InternalServerError when user is logged in and fetchDeveloper returns None" in new SetupSuccessfulStart2SV with LoggedIn {
      when(underTest.thirdPartyDeveloperConnector.fetchDeveloper(eqTo(loggedInDeveloper.userId))(*))
        .thenReturn(successful(None))

      val result = addToken(underTest.authAppSetupCompletedPage())(createRequest())
      validateErrorTemplateView(result, "Unable to obtain user information")
    }

    "return auth app setup complete view when user is part logged in" in new SetupSuccessfulStart2SV with PartLogged {
      when(underTest.thirdPartyDeveloperConnector.fetchDeveloper(eqTo(loggedInDeveloper.userId))(*))
        .thenReturn(successful(Some(loggedInDeveloper)))

      val result = addToken(underTest.authAppSetupCompletedPage())(createRequest())
      shouldReturnOK(result, validateAuthAppCompletedPage)
    }

    "redirect to the login page when user is not logged in" in new SetupSuccessfulStart2SV with LoggedIn {
      private val result = addToken(underTest.authAppSetupCompletedPage())(createRequestWithInvalidSession())
      validateRedirectToLoginPage(result)
    }
  }
}

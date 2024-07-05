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
import play.api.test.FakeRequest
import play.api.test.Helpers._

import uk.gov.hmrc.apiplatform.modules.common.domain.models.UserId
import uk.gov.hmrc.apiplatform.modules.mfa.domain.models.{MfaAction, MfaId, MfaType}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithLoggedInSession._

class MfaControllerSpec extends MfaControllerBaseSpec {

  "MfaController" when {
    "selectMfaPage" should {
      "return 200 and show the Select MFA page when user is Logged in" in new SetupAuthAppSecurityPreferences with LoggedIn {
        shouldReturnOK(addToken(underTest.selectMfaPage(None, MfaAction.CREATE))(selectMfaRequest("SMS")), validateSelectMfaPage)
      }

      "return 200 and show the Select MFA page when user is Part Logged in" in new SetupAuthAppSecurityPreferences with PartLogged {
        shouldReturnOK(addToken(underTest.selectMfaPage(None, MfaAction.CREATE))(selectMfaRequest("SMS")), validateSelectMfaPage)
      }

      "redirect to the login page when user is not logged in" in new SetupAuthAppSecurityPreferences with LoggedIn {
        private val result = addToken(underTest.selectMfaPage(None, MfaAction.CREATE))(createRequestWithInvalidSession(Map("mfaType" -> "SMS")))
        validateRedirectToLoginPage(result)
      }
    }

    "selectMfaAction for CREATE" should {
      "redirect to setup sms page when user is logged in and mfaType is SMS" in new SetupSuccessfulStart2SV with LoggedIn {
        val result = addToken(underTest.selectMfaAction(None, MfaAction.CREATE))(selectMfaRequest("SMS"))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(s"/developer/profile/security-preferences/sms/setup")
      }

      "redirect to setup sms page when user is part logged in and mfaType is SMS" in new SetupSuccessfulStart2SV with PartLogged {
        val result = addToken(underTest.selectMfaAction(None, MfaAction.CREATE))(selectMfaRequest("SMS"))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(s"/developer/profile/security-preferences/sms/setup")
      }

      "redirect to setup Auth App page when user is logged in and mfaType is AUTHENTICATOR_APP" in new SetupSuccessfulStart2SV with LoggedIn {
        val result = addToken(underTest.selectMfaAction(None, MfaAction.CREATE))(selectMfaRequest("AUTHENTICATOR_APP"))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(s"/developer/profile/security-preferences/auth-app/start")
      }

      "redirect to setup Auth App page when user is part logged in and mfaType is AUTHENTICATOR_APP" in new SetupSuccessfulStart2SV with PartLogged {
        val result = addToken(underTest.selectMfaAction(None, MfaAction.CREATE))(selectMfaRequest("AUTHENTICATOR_APP"))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(s"/developer/profile/security-preferences/auth-app/start")
      }

      "return errors when user is logged in and mfaType is invalid on the form" in new SetupSuccessfulStart2SV with LoggedIn {
        val result = addToken(underTest.selectMfaAction(None, MfaAction.CREATE))(selectMfaRequest("INVALID_MFA_TYPE"))

        status(result) shouldBe BAD_REQUEST
        val doc = Jsoup.parse(contentAsString(result))
        validateSelectMfaPage(doc)
        doc.getElementById("data-field-error-mfaType").text() shouldBe "Error: It must be a valid MFA Type"
      }

      "redirect to the login page when user is not logged in" in new SetupSuccessfulStart2SV with LoggedIn {
        private val result = addToken(underTest.selectMfaAction(None, MfaAction.CREATE))(createRequestWithInvalidSession(Map("mfaType" -> "SMS")))
        validateRedirectToLoginPage(result)
      }
    }

    "selectMfaAction for REMOVE" should {
      "redirect to Auth App access code page when mfa to be deleted is Auth App and mfa authentication method is Auth App" in
        new SetupSmsAndAuthAppSecurityPreferences with LoggedIn {

          val result = addToken(underTest.selectMfaAction(Some(authAppMfaId), MfaAction.REMOVE))(selectMfaRequest(MfaType.AUTHENTICATOR_APP.toString))

          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe
            Some(s"/developer/profile/security-preferences/auth-app/access-code?mfaId=${authAppMfaId.value.toString}&" +
              s"mfaAction=${MfaAction.REMOVE.toString}&mfaIdForRemoval=${authAppMfaId.value.toString}")
        }

      "redirect to Sms access code page when mfa to be deleted is Auth App and mfa authentication method is Sms" in
        new SetupSmsAndAuthAppSecurityPreferences with LoggedIn {
          when(underTest.thirdPartyDeveloperMfaConnector.sendSms(*[UserId], *[MfaId])(*)).thenReturn(Future.successful(true))

          val result = addToken(underTest.selectMfaAction(Some(authAppMfaId), MfaAction.REMOVE))(selectMfaRequest(MfaType.SMS.toString))

          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe
            Some(s"/developer/profile/security-preferences/sms/access-code?mfaId=${smsMfaId.value.toString}&" +
              s"mfaAction=${MfaAction.REMOVE.toString}&mfaIdForRemoval=${authAppMfaId.value.toString}")
        }

      "redirect to Sms access code page when mfa to be deleted is Sms and mfa authentication method is Sms" in
        new SetupSmsAndAuthAppSecurityPreferences with LoggedIn {
          when(underTest.thirdPartyDeveloperMfaConnector.sendSms(*[UserId], *[MfaId])(*)).thenReturn(Future.successful(true))

          val result = addToken(underTest.selectMfaAction(Some(smsMfaId), MfaAction.REMOVE))(selectMfaRequest(MfaType.SMS.toString))

          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe
            Some(s"/developer/profile/security-preferences/sms/access-code?mfaId=${smsMfaId.value.toString}&" +
              s"mfaAction=${MfaAction.REMOVE.toString}&mfaIdForRemoval=${smsMfaId.value.toString}")
        }

      "redirect to Auth App access code page when mfa to be deleted is Sms and mfa authentication method is Auth App" in
        new SetupSmsAndAuthAppSecurityPreferences with LoggedIn {
          val result = addToken(underTest.selectMfaAction(Some(smsMfaId), MfaAction.REMOVE))(selectMfaRequest(MfaType.AUTHENTICATOR_APP.toString))

          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe
            Some(s"/developer/profile/security-preferences/auth-app/access-code?mfaId=${authAppMfaId.value.toString}&" +
              s"mfaAction=${MfaAction.REMOVE.toString}&mfaIdForRemoval=${smsMfaId.value.toString}")
        }

      "return error when send sms fails and mfa to be deleted is Sms and mfa authentication method is Sms" in
        new SetupSmsAndAuthAppSecurityPreferences with LoggedIn {
          when(underTest.thirdPartyDeveloperMfaConnector.sendSms(*[UserId], *[MfaId])(*)).thenReturn(Future.successful(false))

          val result = addToken(underTest.selectMfaAction(Some(smsMfaId), MfaAction.REMOVE))(selectMfaRequest(MfaType.SMS.toString))

          status(result) shouldBe INTERNAL_SERVER_ERROR
          validateErrorTemplateView(result, "Failed to send SMS")
        }

      "return error when fetch user returns none and mfa to be deleted is Sms and mfa authentication method is Sms" in new LoggedIn {
        when(underTest.thirdPartyDeveloperConnector.fetchDeveloper(eqTo(loggedInDeveloper.userId))(*)).thenReturn(successful(None))

        val result = addToken(underTest.selectMfaAction(Some(smsMfaId), MfaAction.REMOVE))(selectMfaRequest(MfaType.SMS.toString))

        status(result) shouldBe INTERNAL_SERVER_ERROR
        validateErrorTemplateView(result, "Unable to obtain user information")
      }
    }

    "securityPreferences" should {
      "return 200 and show the Security Preferences page when user is Logged in" in new SetupAuthAppSecurityPreferences with LoggedIn {
        private val request = FakeRequest().withLoggedIn(underTest, implicitly)(sessionId)
        shouldReturnOK(underTest.securityPreferences()(request), validateSecurityPreferences)
      }

      "return 200 and show the Security Preferences page when user is Part Logged in" in new SetupAuthAppSecurityPreferences with PartLogged {
        private val request = FakeRequest().withLoggedIn(underTest, implicitly)(sessionId)
        shouldReturnOK(underTest.securityPreferences()(request), validateSecurityPreferences)
      }

      "redirect to the login page when user is not logged in" in new SetupAuthAppSecurityPreferences with LoggedIn {
        private val result = addToken(underTest.securityPreferences())(createRequestWithInvalidSession())
        validateRedirectToLoginPage(result)
      }

      "redirect to the login page when third party developer returns None for User " in new LoggedIn {
        when(underTest.thirdPartyDeveloperConnector.fetchDeveloper(eqTo(loggedInDeveloper.userId))(*))
          .thenReturn(successful(None))

        private val request = FakeRequest().withLoggedIn(underTest, implicitly)(sessionId)
        val result          = underTest.securityPreferences()(request)

        validateErrorTemplateView(result, "Unable to obtain User information")
      }
    }

    "removeMfa" should {
      "redirect to auth app code access page when a logged in user removes auth app" in new SetupAuthAppSecurityPreferences with LoggedIn {
        val result = addToken(underTest.removeMfa(authAppMfaId, MfaType.AUTHENTICATOR_APP))(createRequest())

        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some(
          s"/developer/profile/security-preferences/auth-app/access-code?mfaId=${authAppMfaId.value.toString}&" +
            s"mfaAction=${MfaAction.REMOVE.toString}&mfaIdForRemoval=${authAppMfaId.value.toString}"
        )
      }

      "redirect to sms access code page when a logged in user removes sms" in new SetupSmsSecurityPreferences with LoggedIn {
        when(underTest.thirdPartyDeveloperMfaConnector.sendSms(*[UserId], *[MfaId])(*)).thenReturn(Future.successful(true))

        val result = addToken(underTest.removeMfa(smsMfaId, MfaType.SMS))(createRequest())

        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some(
          s"/developer/profile/security-preferences/sms/access-code?mfaId=${smsMfaId.value.toString}&" +
            s"mfaAction=${MfaAction.REMOVE.toString}&mfaIdForRemoval=${smsMfaId.value.toString}"
        )
      }

      "return error page when send sms fails" in new SetupSmsSecurityPreferences with LoggedIn {
        when(underTest.thirdPartyDeveloperMfaConnector.sendSms(*[UserId], *[MfaId])(*)).thenReturn(Future.successful(false))

        val result = addToken(underTest.removeMfa(smsMfaId, MfaType.SMS))(createRequest())

        validateErrorTemplateView(result, "Failed to send SMS")
      }

      "redirect to the login page when user is not logged in" in new SetupSuccessfulStart2SV with LoggedIn {
        private val result = addToken(underTest.removeMfa(authAppMfaId, MfaType.AUTHENTICATOR_APP))(createRequestWithInvalidSession())
        validateRedirectToLoginPage(result)
      }

      "redirect to the login page when user is part logged in" in new SetupSuccessfulStart2SV with PartLogged {
        private val result = addToken(underTest.removeMfa(authAppMfaId, MfaType.AUTHENTICATOR_APP))(authAppAccessCodeRequest(correctCode))
        validateRedirectToLoginPage(result)
      }

      "redirect to select mfa page when both mfa methods are setup and user wants to remove Auth App" in
        new SetupSmsAndAuthAppSecurityPreferences with LoggedIn {
          val result = addToken(underTest.removeMfa(authAppMfaId, MfaType.AUTHENTICATOR_APP))(createRequest())

          status(result) shouldBe Status.SEE_OTHER
          redirectLocation(result) shouldBe Some(
            s"/developer/profile/security-preferences/select-mfa?mfaId=${authAppMfaId.value.toString}&mfaAction=${MfaAction.REMOVE.toString}"
          )
        }

      "redirect to select mfa page when both mfa methods are setup and user wants to remove Sms" in
        new SetupSmsAndAuthAppSecurityPreferences with LoggedIn {
          val result = addToken(underTest.removeMfa(smsMfaId, MfaType.SMS))(createRequest())

          status(result) shouldBe Status.SEE_OTHER
          redirectLocation(result) shouldBe Some(
            s"/developer/profile/security-preferences/select-mfa?mfaId=${smsMfaId.value.toString}&mfaAction=${MfaAction.REMOVE.toString}"
          )
        }

      "return error page when there is no valid mfa setup found" in new SetupWithUnverifiedSmsSecurityPreferences with LoggedIn {
        val result = addToken(underTest.removeMfa(smsMfaId, MfaType.SMS))(createRequest())

        validateErrorTemplateView(result, "MFA setup not valid")
      }

      "return error page when fetch developer returns None for User " in new LoggedIn {
        when(underTest.thirdPartyDeveloperConnector.fetchDeveloper(eqTo(loggedInDeveloper.userId))(*))
          .thenReturn(successful(None))

        val result = addToken(underTest.removeMfa(smsMfaId, MfaType.SMS))(createRequest())

        validateErrorTemplateView(result, "Unable to obtain User information")
      }
    }
  }
}

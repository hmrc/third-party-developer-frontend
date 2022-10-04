/*
 * Copyright 2022 HM Revenue & Customs
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
import org.jsoup.nodes.Document
import org.scalatest.Assertion
import play.api.http.Status
import play.api.mvc.{AnyContentAsEmpty, AnyContentAsFormUrlEncoded, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider
import uk.gov.hmrc.apiplatform.modules.mfa.connectors.ThirdPartyDeveloperMfaConnector
import uk.gov.hmrc.apiplatform.modules.mfa.connectors.ThirdPartyDeveloperMfaConnector.RegisterAuthAppResponse
import uk.gov.hmrc.apiplatform.modules.mfa.models.MfaId
import uk.gov.hmrc.apiplatform.modules.mfa.service.{MFAResponse, MFAService}
import uk.gov.hmrc.apiplatform.modules.mfa.views.html.{SecurityPreferencesView, SelectMfaView}
import uk.gov.hmrc.apiplatform.modules.mfa.views.html.authapp.{AuthAppAccessCodeView, AuthAppSetupCompletedView, AuthAppStartView, NameChangeView, QrCodeView}
import uk.gov.hmrc.apiplatform.modules.mfa.views.html.sms.{MobileNumberView, SmsAccessCodeView, SmsSetupCompletedView}
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.{DeveloperBuilder, MfaDetailBuilder}
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ErrorHandler
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ThirdPartyDeveloperConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.BaseControllerSpec
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.{LoggedInState, Session, UserId}
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service.SessionServiceMock
import uk.gov.hmrc.thirdpartydeveloperfrontend.qr.{OtpAuthUri, QRCode}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithLoggedInSession._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{LocalUserIdTracker, WithCSRFAddToken}

import java.net.URI
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future.successful

class MfaControllerSpec extends BaseControllerSpec with WithCSRFAddToken with DeveloperBuilder with LocalUserIdTracker with MfaDetailBuilder {

  val qrImage = "qrImage"

  trait Setup extends SessionServiceMock {
    val secret = "ABCDEFGH"
    val issuer = "HMRC Developer Hub"
    val sessionId = "sessionId"
    val mfaId = verifiedAuthenticatorAppMfaDetail.id
    val loggedInDeveloper = buildDeveloper()
    val otpUri = new URI("OTPURI")
    val correctCode = "123123"

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
    val selectMfaView = app.injector.instanceOf[SelectMfaView]

    val underTest: MfaController = new MfaController(
      mock[ThirdPartyDeveloperConnector],
      mock[ThirdPartyDeveloperMfaConnector],
      mock[OtpAuthUri],
      mock[MFAService],
      sessionServiceMock,
      mcc,
      mock[ErrorHandler],
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
      selectMfaView: SelectMfaView
    ) {
      override val qrCode: QRCode = mock[QRCode]
    }

    fetchSessionByIdReturns(sessionId, Session(sessionId, loggedInDeveloper, loggedInState))
    updateUserFlowSessionsReturnsSuccessfully(sessionId)

    def accessCodeRequest(code: String): FakeRequest[AnyContentAsFormUrlEncoded] = {
      FakeRequest()
        .withLoggedIn(underTest, implicitly)(sessionId)
        .withSession("csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken)
        .withFormUrlEncodedBody("accessCode" -> code)
    }

    def nameChangeRequest(name: String): FakeRequest[AnyContentAsFormUrlEncoded] = {
      FakeRequest()
        .withLoggedIn(underTest, implicitly)(sessionId)
        .withSession("csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken)
        .withFormUrlEncodedBody("name" -> name)
    }

    def selectMfaRequest(mfaType: String): FakeRequest[AnyContentAsFormUrlEncoded] = {
      FakeRequest()
        .withLoggedIn(underTest, implicitly)(sessionId)
        .withSession("csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken)
        .withFormUrlEncodedBody("mfaType" -> mfaType)
    }
  }

  trait SetupUnprotectedAccount extends Setup {
    when(underTest.thirdPartyDeveloperConnector.fetchDeveloper(eqTo(loggedInDeveloper.userId))(*))
      .thenReturn(successful(Some(buildDeveloper(emailAddress = loggedInDeveloper.email, organisation = None))))
  }

  trait SetupSecurityPreferences extends Setup {
    when(underTest.thirdPartyDeveloperConnector.fetchDeveloper(eqTo(loggedInDeveloper.userId))(*))
      .thenReturn(successful(Some(
        buildDeveloper(emailAddress = loggedInDeveloper.email, organisation = None, mfaDetails = List(verifiedAuthenticatorAppMfaDetail))))
      )
  }

  trait SetupSuccessfulStart2SV extends Setup {
    val registerAuthAppResponse = RegisterAuthAppResponse(secret, mfaId)

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

  "MfaController" when {

    "selectMfaPage()" should {
      "return 200 and show the Select MFA page when user is Logged in" in new SetupSecurityPreferences with LoggedIn {
        shouldReturnOK(addToken(underTest.selectMfaPage())(selectMfaRequest("SMS")), validateSelectMfaPage)
      }

      "return 200 and show the Select MFA page when user is Part Logged in" in new SetupSecurityPreferences with PartLogged {
        shouldReturnOK(addToken(underTest.selectMfaPage())(selectMfaRequest("SMS")), validateSelectMfaPage)
      }

      "redirect to the login page when user is not logged in" in new SetupSecurityPreferences with LoggedIn {
        val invalidSessionId = "notASessionId"
        when(underTest.sessionService.fetch(eqTo(invalidSessionId))(*))
          .thenReturn(Future.successful(None))

        private val request =
          FakeRequest()
            .withLoggedIn(underTest, implicitly)(invalidSessionId)
            .withSession("csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken)
            .withFormUrlEncodedBody("mfaType" -> "SMS")

        private val result = addToken(underTest.selectMfaPage())(request)

        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some("/developer/login")
      }
    }

    "selectMfaAction()" should {

      "redirect to setup sms page when user is logged in and mfaType is SMS" in new SetupSuccessfulStart2SV with LoggedIn {
        val result = addToken(underTest.selectMfaAction())(selectMfaRequest("SMS"))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(s"/developer/profile/security-preferences/sms/setup")
      }

      "redirect to setup sms page when user is part logged in and mfaType is SMS" in new SetupSuccessfulStart2SV with PartLogged {
        val result = addToken(underTest.selectMfaAction())(selectMfaRequest("SMS"))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(s"/developer/profile/security-preferences/sms/setup")
      }

      "redirect to setup Auth App page when user is logged in and mfaType is AUTHENTICATOR_APP" in new SetupSuccessfulStart2SV with LoggedIn {
        val result = addToken(underTest.selectMfaAction())(selectMfaRequest("AUTHENTICATOR_APP"))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(s"/developer/profile/security-preferences/auth-app/start")
      }

      "redirect to setup Auth App page when user is part logged in and mfaType is AUTHENTICATOR_APP" in new SetupSuccessfulStart2SV with PartLogged {
        val result = addToken(underTest.selectMfaAction())(selectMfaRequest("AUTHENTICATOR_APP"))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(s"/developer/profile/security-preferences/auth-app/start")
      }

      "return errors when user is logged in and mfaType is invalid on the form" in new SetupSuccessfulStart2SV with LoggedIn {
        val result = addToken(underTest.selectMfaAction())(selectMfaRequest("INVALID_MFA_TYPE"))

        status(result) shouldBe BAD_REQUEST
        val doc = Jsoup.parse(contentAsString(result))
        validateSelectMfaPage(doc)
        doc.getElementById("data-field-error-mfaType").text() shouldBe "Error: It must be a valid MFA Type"
      }
//
//      "return access code view with errors when user is logged in and submitted form is invalid" in new SetupSuccessfulStart2SV with LoggedIn {
//        when(underTest.mfaService.enableMfa(*[UserId], *[MfaId], *)(*)).thenReturn(Future.successful(MFAResponse(false)))
//
//        val result = addToken(underTest.enableAuthApp(mfaId))(accessCodeRequest("INVALID_CODE"))
//
//        status(result) shouldBe BAD_REQUEST
//        val doc = Jsoup.parse(contentAsString(result))
//        validateAccessCodePage(doc)
//        doc.getElementById("data-field-error-accessCode").text() shouldBe "Error: You have entered an invalid access code"
//
//      }
//
//      "redirect to the login page when user is not logged in" in new SetupSuccessfulStart2SV with LoggedIn {
//        val invalidSessionId = "notASessionId"
//
//        when(underTest.sessionService.fetch(eqTo(invalidSessionId))(*))
//          .thenReturn(Future.successful(None))
//
//        private val request = FakeRequest()
//          .withLoggedIn(underTest, implicitly)(invalidSessionId)
//          .withSession("csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken)
//          .withFormUrlEncodedBody("accessCode" -> correctCode)
//
//        private val result: Future[Result] = addToken(underTest.enableAuthApp(mfaId))(request)
//
//        status(result) shouldBe Status.SEE_OTHER
//        redirectLocation(result) shouldBe Some("/developer/login")
//      }
    }

      "securityPreferences()" should {
      "return 200 and show the Security Preferences page when user is Logged in" in new SetupSecurityPreferences with LoggedIn {
        private val request = FakeRequest().withLoggedIn(underTest, implicitly)(sessionId)
        shouldReturnOK(underTest.securityPreferences()(request), validateSecurityPreferences)
      }

      "return 200 and show the Security Preferences page when user is Part Logged in" in new SetupSecurityPreferences with PartLogged {
        private val request = FakeRequest().withLoggedIn(underTest, implicitly)(sessionId)
        shouldReturnOK(underTest.securityPreferences()(request), validateSecurityPreferences)
      }

      "redirect to the login page when user is not logged in" in new SetupSecurityPreferences with LoggedIn {

        val invalidSessionId = "notASessionId"

        when(underTest.sessionService.fetch(eqTo(invalidSessionId))(*))
          .thenReturn(Future.successful(None))

        private val request = FakeRequest().withLoggedIn(underTest, implicitly)(invalidSessionId)

        private val result = underTest.securityPreferences()(request)

        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some("/developer/login")
      }

      "redirect to the login page when third party developer returns None for User " in new LoggedIn {

        when(underTest.thirdPartyDeveloperConnector.fetchDeveloper(eqTo(loggedInDeveloper.userId))(*))
          .thenReturn(successful(None))

        private val request = FakeRequest().withLoggedIn(underTest, implicitly)(sessionId)


        val result = underTest.securityPreferences()(request)
        status(result) shouldBe Status.INTERNAL_SERVER_ERROR

      }
    }

    "authAppStart() is called it" should {
      "return 200 and show the Auth App Start page when user is logged in" in new SetupSecurityPreferences with LoggedIn {
        private val request = FakeRequest().withLoggedIn(underTest, implicitly)(sessionId)
        shouldReturnOK(underTest.authAppStart()(request), validateAuthAppStartPage)
      }

      "return 200 and show the Auth App Start page when user is part logged in" in new SetupSecurityPreferences with PartLogged {
        private val request = FakeRequest().withLoggedIn(underTest, implicitly)(sessionId)
        shouldReturnOK(underTest.authAppStart()(request), validateAuthAppStartPage)
      }


      "redirect to the login page when user is not logged in" in new SetupSecurityPreferences with LoggedIn {

        val invalidSessionId = "notASessionId"

        when(underTest.sessionService.fetch(eqTo(invalidSessionId))(*))
          .thenReturn(Future.successful(None))

        private val request = FakeRequest().withLoggedIn(underTest, implicitly)(invalidSessionId)

        private val result = underTest.authAppStart()(request)

        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some("/developer/login")
      }
    }

    "setupAuthApp()" should {
      "return qrCodeView with secret from third party developer when user is logged in" in new SetupSuccessfulStart2SV with LoggedIn {
        private val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withLoggedIn(underTest, implicitly)(sessionId)
        shouldReturnOK(underTest.setupAuthApp()(request), validateQrCodePage)
      }

      "return qrCodeView with secret from third party developer when user is part logged in" in new SetupSuccessfulStart2SV with PartLogged {
        private val request = FakeRequest().withLoggedIn(underTest, implicitly)(sessionId)
        shouldReturnOK(underTest.setupAuthApp()(request), validateQrCodePage)
      }

      "redirect to the login page when user is not logged in" in new SetupSuccessfulStart2SV with LoggedIn {
        val invalidSessionId = "notASessionId"

        when(underTest.sessionService.fetch(eqTo(invalidSessionId))(*))
          .thenReturn(Future.successful(None))

        private val request = FakeRequest().withLoggedIn(underTest, implicitly)(invalidSessionId)

        private val result: Future[Result] = underTest.setupAuthApp()(request)

        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some("/developer/login")
      }
    }

    "getAccessCodePage()" should {
      "return access code view when user is logged in" in new SetupSuccessfulStart2SV with LoggedIn {
        val result = addToken(underTest.authAppAccessCodePage(mfaId))(accessCodeRequest(correctCode))
        shouldReturnOK(result, validateAccessCodePage)
      }

      "return access code view when user is part logged in" in new SetupSuccessfulStart2SV with PartLogged {
        val result = addToken(underTest.authAppAccessCodePage(mfaId))(accessCodeRequest(correctCode))
        shouldReturnOK(result, validateAccessCodePage)
      }

      "redirect to the login page when user is not logged in" in new SetupSuccessfulStart2SV with LoggedIn {
        val invalidSessionId = "notASessionId"

        when(underTest.sessionService.fetch(eqTo(invalidSessionId))(*))
          .thenReturn(Future.successful(None))

        private val request = FakeRequest()
          .withLoggedIn(underTest, implicitly)(invalidSessionId)
          .withSession("csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken)
          .withFormUrlEncodedBody("accessCode" -> correctCode)

        private val result: Future[Result] = addToken(underTest.authAppAccessCodePage(mfaId))(request)

        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some("/developer/login")
      }
    }

    "enableAuthApp()" should {
      "return change name view when user is logged in and enable mfa successful" in new SetupSuccessfulStart2SV with LoggedIn {
        when(underTest.mfaService.enableMfa(*[UserId], *[MfaId], *)(*)).thenReturn(Future.successful(MFAResponse(true)))

        val result = addToken(underTest.enableAuthApp(mfaId))(accessCodeRequest(correctCode))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(s"/developer/profile/security-preferences/auth-app/name?mfaId=${mfaId.value.toString}")
      }

      "return change name view when user is part logged in and enable mfa successful" in new SetupSuccessfulStart2SV with PartLogged {
        when(underTest.mfaService.enableMfa(*[UserId], *[MfaId], *)(*)).thenReturn(Future.successful(MFAResponse(true)))

        val result = addToken(underTest.enableAuthApp(mfaId))(accessCodeRequest(correctCode))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(s"/developer/profile/security-preferences/auth-app/name?mfaId=${mfaId.value.toString}")
      }

      "return access code view with errors when user is logged in and enable mfa fails" in new SetupSuccessfulStart2SV with LoggedIn {
        when(underTest.mfaService.enableMfa(*[UserId], *[MfaId], *)(*)).thenReturn(Future.successful(MFAResponse(false)))

        val result = addToken(underTest.enableAuthApp(mfaId))(accessCodeRequest(correctCode))

        status(result) shouldBe BAD_REQUEST
        val doc = Jsoup.parse(contentAsString(result))
        validateAccessCodePage(doc)
        doc.getElementById("data-field-error-accessCode").text() shouldBe "Error: You have entered an incorrect access code"

      }

      "return access code view with errors when user is logged in and submitted form is invalid" in new SetupSuccessfulStart2SV with LoggedIn {
        when(underTest.mfaService.enableMfa(*[UserId], *[MfaId], *)(*)).thenReturn(Future.successful(MFAResponse(false)))

        val result = addToken(underTest.enableAuthApp(mfaId))(accessCodeRequest("INVALID_CODE"))

        status(result) shouldBe BAD_REQUEST
        val doc = Jsoup.parse(contentAsString(result))
        validateAccessCodePage(doc)
        doc.getElementById("data-field-error-accessCode").text() shouldBe "Error: You have entered an invalid access code"

      }

      "redirect to the login page when user is not logged in" in new SetupSuccessfulStart2SV with LoggedIn {
        val invalidSessionId = "notASessionId"

        when(underTest.sessionService.fetch(eqTo(invalidSessionId))(*))
          .thenReturn(Future.successful(None))

        private val request = FakeRequest()
          .withLoggedIn(underTest, implicitly)(invalidSessionId)
          .withSession("csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken)
          .withFormUrlEncodedBody("accessCode" -> correctCode)

        private val result: Future[Result] = addToken(underTest.enableAuthApp(mfaId))(request)

        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some("/developer/login")
      }
    }

    "getNameChangePage()" should {

      "return name change view when user is logged in" in new SetupSuccessfulStart2SV with LoggedIn {
        val result = addToken(underTest.nameChangePage(mfaId))(nameChangeRequest("app name"))
        shouldReturnOK(result, validateNameChangePage)
      }

      "return name change view when user is part logged in" in new SetupSuccessfulStart2SV with PartLogged {
        val result = addToken(underTest.nameChangePage(mfaId))(nameChangeRequest("app name"))
        shouldReturnOK(result, validateNameChangePage)
      }

      "redirect to the login page when user is not logged in" in new SetupSuccessfulStart2SV with LoggedIn {
        val invalidSessionId = "notASessionId"

        when(underTest.sessionService.fetch(eqTo(invalidSessionId))(*))
          .thenReturn(Future.successful(None))
        val request = FakeRequest()
          .withSession("csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken)
          .withLoggedIn(underTest, implicitly)(invalidSessionId)

        private val result: Future[Result] = addToken(underTest.nameChangePage(mfaId))(request)

        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some("/developer/login")
      }
    }

    "nameChangeAction()" should {
      val updatedName = "updated name"
      "return auth app completed view when user is logged in and call to backend is successful" in new SetupSuccessfulStart2SV with LoggedIn {
        when(underTest.thirdPartyDeveloperMfaConnector.changeName(*[UserId], *[MfaId], *)(*)).thenReturn(Future.successful(true))

        val result = addToken(underTest.nameChangeAction(mfaId))(nameChangeRequest(updatedName))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(s"/developer/profile/security-preferences/auth-app/setup/complete")
      }


      "return auth app completed view when user is part logged in and call to backend is successful" in new SetupSuccessfulStart2SV with PartLogged {
        when(underTest.thirdPartyDeveloperMfaConnector.changeName(*[UserId], *[MfaId], *)(*)).thenReturn(Future.successful(true))

        val result = addToken(underTest.nameChangeAction(mfaId))(nameChangeRequest(updatedName))

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(s"/developer/profile/security-preferences/auth-app/setup/complete")
      }

      "return error page when user is logged in and call to backend fails" in new SetupSuccessfulStart2SV with LoggedIn {
        when(underTest.thirdPartyDeveloperMfaConnector.changeName(*[UserId], *[MfaId], *)(*)).thenReturn(Future.successful(false))

        val result = addToken(underTest.nameChangeAction(mfaId))(nameChangeRequest(updatedName))

        status(result) shouldBe INTERNAL_SERVER_ERROR
      }

      "return name change view with errors when user is logged in and form is invalid" in new SetupSuccessfulStart2SV with LoggedIn {
        val result = addToken(underTest.nameChangeAction(mfaId))(nameChangeRequest("a"))

        status(result) shouldBe BAD_REQUEST
        val doc = Jsoup.parse(contentAsString(result))
        validateNameChangePage(doc)
        doc.getElementById("data-field-error-name").text() shouldBe "Error: The name must be more than 3 characters in length"

        verifyZeroInteractions(underTest.thirdPartyDeveloperMfaConnector)
      }
    }

    "authAppSetupCompletedPage()" should {
      "return auth app setup complete view when user is logged in and fetchDeveloper returns a devveloper" in new SetupSuccessfulStart2SV with LoggedIn {
        private val request = FakeRequest().withLoggedIn(underTest, implicitly)(sessionId)
        when(underTest.thirdPartyDeveloperConnector.fetchDeveloper(eqTo(loggedInDeveloper.userId))(*))
          .thenReturn(successful(Some(loggedInDeveloper)))
        val result = addToken(underTest.authAppSetupCompletedPage())(request)
        shouldReturnOK(result, validateAuthAppCompletedPage)
      }

      "return InternalServerError when user is logged in and fetchDeveloper returns None" in new SetupSuccessfulStart2SV with LoggedIn {
        private val request = FakeRequest().withLoggedIn(underTest, implicitly)(sessionId)
        when(underTest.thirdPartyDeveloperConnector.fetchDeveloper(eqTo(loggedInDeveloper.userId))(*))
          .thenReturn(successful(None))
        val result = addToken(underTest.authAppSetupCompletedPage())(request)
        status(result) shouldBe Status.INTERNAL_SERVER_ERROR
      }

      "return auth app setup complete view when user is part logged in" in new SetupSuccessfulStart2SV with PartLogged {
        private val request = FakeRequest().withLoggedIn(underTest, implicitly)(sessionId)

        when(underTest.thirdPartyDeveloperConnector.fetchDeveloper(eqTo(loggedInDeveloper.userId))(*))
          .thenReturn(successful(Some(loggedInDeveloper)))

        val result = addToken(underTest.authAppSetupCompletedPage())(request)
        shouldReturnOK(result, validateAuthAppCompletedPage)
      }

      "redirect to the login page when user is not logged in" in new SetupSuccessfulStart2SV with LoggedIn {
        val invalidSessionId = "notASessionId"

        when(underTest.sessionService.fetch(eqTo(invalidSessionId))(*))
          .thenReturn(Future.successful(None))

        private val request = FakeRequest().withLoggedIn(underTest, implicitly)(invalidSessionId)

        private val result: Future[Result] = addToken(underTest.authAppSetupCompletedPage())(request)

        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some("/developer/login")
      }
    }

  }

  def shouldReturnOK(result: Future[Result], f: Document => Assertion) = {
    status(result) shouldBe 200
    val doc = Jsoup.parse(contentAsString(result))
    f(doc)
  }

  def validateSelectMfaPage(dom: Document) = {
    dom.getElementById("page-heading").text shouldBe "How do you want to get access codes?"
  }

  def validateSecurityPreferences(dom: Document) = {
    dom.getElementById("page-heading").text shouldBe "Your security preferences"
  }

  def validateAuthAppStartPage(dom: Document) = {
    dom.getElementById("page-heading").text shouldBe "You need an authenticator app on your device"
  }

  def validateQrCodePage(dom: Document): Assertion = {
    dom.getElementById("page-heading").text shouldBe "Set up your authenticator app"
    dom.getElementById("secret").html() shouldBe "abcd efgh"
    dom.getElementById("qrCode").attr("src") shouldBe qrImage
  }

  def validateAccessCodePage(dom: Document) = {
    dom.getElementById("page-heading").text shouldBe "Enter your access code"
  }

  def validateNameChangePage(dom: Document) = {
    dom.getElementById("page-heading").text shouldBe "Create a name for your authenticator app"
    dom.getElementById("paragraph").text shouldBe "Use a name that will help you remember the app when you sign in."
    dom.getElementById("name-label").text shouldBe "App Name"
    dom.getElementById("submit").text shouldBe "Continue"
  }

  def validateAuthAppCompletedPage(dom: Document) = {
    dom.getElementById("page-heading").text shouldBe "You can now get access codes by authenticator app"
  }

}

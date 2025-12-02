/*
 * Copyright 2025 HM Revenue & Customs
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

import org.openqa.selenium.By
import play.api.http.Status._
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.tpd.core.domain.models.User
import uk.gov.hmrc.apiplatform.modules.tpd.core.dto._
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.{LoggedInState, UserSessionId}
import uk.gov.hmrc.selenium.webdriver.Driver
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.UserSession
import uk.gov.hmrc.apiplatform.modules.tpd.session.dto.UserAuthenticationResponse
import uk.gov.hmrc.apiplatform.modules.tpd.session.dto.SessionCreateWithDeviceRequest
import play.api.libs.json.Json

object LoginStepsSteps extends NavigationSugar with ComponentTestDeveloperBuilder {

  var developer: User = _
  var sessionIdForloggedInDeveloper: UserSessionId = UserSessionId.random
  var sessionIdForMfaMandatingUser: UserSessionId  = UserSessionId.random
  private val mobileNumber = "+440126"

  // ^I successfully log in with '(.*)' and '(.*)' skipping 2SV$
  def givenISuccessfullyLogInWithAndSkipping2SV(email: String, password: String): Unit = {
    goOn(SignInPage.default)
    SignInPage.default.signInWith(email, password)
    on(RecommendMfaPage)
    RecommendMfaPage.skip2SVReminder()
    on(RecommendMfaSkipAcknowledgePage)
    RecommendMfaSkipAcknowledgePage.confirmSkip2SV()
  }

  // ^I am registered with$
  def givenIAmRegisteredWith(result: Map[String,String]) = {
        val password  = result("Password")
        val developer = buildUser(emailAddress = result("Email address").toLaxEmail, firstName = result("First name"), lastName = result("Last name"))

        val mfaSetup = result("Mfa Setup")

        val authAppMfaId = authenticatorAppMfaDetails.id
        val smsMfaId     = smsMfaDetails.id
        mfaSetup match {
          case "AUTHENTICATOR_APP" =>
            MfaStub.setupGettingMfaSecret(developer, authAppMfaId)
            MfaStub.setupVerificationOfAccessCode(developer, authAppMfaId)
            MfaStub.stubMfaAuthAppNameChange(developer, authAppMfaId, "SomeAuthApp")

          case "SMS" =>
            MfaStub.setupSmsAccessCode(developer, smsMfaId, mobileNumber)
            MfaStub.setupVerificationOfAccessCode(developer, smsMfaId)

          case _ =>
            MfaStub.setupGettingMfaSecret(developer, authAppMfaId)
            MfaStub.setupVerificationOfAccessCode(developer, authAppMfaId)
            MfaStub.stubMfaAuthAppNameChange(developer, authAppMfaId, "SomeAuthApp")
        }
        TestContext.developer = developer

        DeveloperStub.findUserIdByEmailAddress(developer.email)
        Stubs.setupPostRequest("/check-password", NO_CONTENT)
        Stubs.setupPostRequest("/authenticate", UNAUTHORIZED)

        TestContext.sessionIdForloggedInDeveloper = setupLoggedOrPartLoggedInDeveloper(developer, password, LoggedInState.LOGGED_IN)
        TestContext.sessionIdForMfaMandatingUser = setupLoggedOrPartLoggedInDeveloper(developer, password, LoggedInState.PART_LOGGED_IN_ENABLING_MFA)

        DeveloperStub.setupGettingDeveloperByUserId(developer)
  }

  // ^'(.*)' session is uplifted to LoggedIn$
  def givenSessionIsUpliftedToLoggedIn(email: String) = {
        if (email.toLaxEmail != TestContext.developer.email) {
          throw new IllegalArgumentException(s"Can only know how to uplift ${TestContext.developer.email}'s session")
        }
        MfaStub.setupMfaMandated()
  }

  // ^I fill in the login form with$
  def givenIFillInTheLoginFormWith(form: Map[String, String]): Unit = {
    Form.populate(form)
  }

  // ^I am logged in as '(.+)'$
  def thenIAmLoggedInAs(userFullName: String) = {
        val authCookie = Driver.instance.manage().getCookieNamed("PLAY2AUTH_SESS_ID")
        authCookie should not be null
        AnyWebPageWithUserLinks.userLink(userFullName) shouldBe ("defined")
  }

  // ^I am not logged in$
  def thenIAmNotLoggedIn(): Unit = {
    val authCookie = Driver.instance.manage().getCookieNamed("PLAY2AUTH_SESS_ID")
        authCookie shouldBe null
  }

  // ^I attempt to Sign out when the session expires
  def whenIAttemptToSignOutWhenTheSessionExpires(): Unit = {
    val sessionId = UserSessionId.random
        Stubs.setupDeleteRequest(s"/session/$sessionId", NOT_FOUND)
        try {
          val link = Driver.instance.findElement(By.linkText("Sign out"))
          link.click()
        } catch {
          case _: NoSuchElementException =>
            val menu = Driver.instance.findElement(By.linkText("Menu"))
            menu.click()

            val link2 = Driver.instance.findElement(By.linkText("Sign out"))
            link2.click()
        }
  }

  // ^I should be sent an email with a link to reset for '(.*)'$
  def thenIShouldBeSentAnEmailWithALinkToResetFor(email: String) = {
    DeveloperStub.verifyResetPassword(EmailIdentifier(email.toLaxEmail))
  }

  // ^I click on a valid password reset link for code '(.*)'$
  def givenIClickOnAValidPasswordResetLinkForCode(resetPwdCode: String) = {
        val email = "bob@example.com"
        DeveloperStub.stubResetPasswordJourney(email.toLaxEmail, resetPwdCode)

        Driver.instance.manage().deleteAllCookies()
        go(new WebLink() { val url = s"http://localhost:${EnvConfig.port}/developer/reset-password-link?code='$resetPwdCode'" })
  }

  // ^I click on an invalid password reset link for code '(.*)'$
  def givenIClickOnAnInvalidPasswordResetLinkForCode(invalidResetPwdCode: String) = {
        DeveloperStub.stubResetPasswordJourneyFail()

        Driver.instance.manage().deleteAllCookies()
        go(new WebLink() { val url = s"http://localhost:${EnvConfig.port}/developer/reset-password-link?code='$invalidResetPwdCode'" })
  }

  // ^I am on the 'Reset Password' page with code '(.*)'$
  def thenIAmOnTheResetPasswordPageWithCode(resetPwdCode: String) = {
        eventually {
          withClue(s"Fail to be on page: 'Reset Password'")(on(ResetPasswordPage(resetPwdCode)))
        }
  }

  def setupLoggedOrPartLoggedInDeveloper(developer: User, password: String, loggedInState: LoggedInState): UserSessionId = {
    val sessionId = UserSessionId.random

    val session                    = UserSession(sessionId, loggedInState, developer)
    val userAuthenticationResponse = UserAuthenticationResponse(accessCodeRequired = false, mfaEnabled = false, session = Some(session))

    val mfaMandatedForUser = loggedInState == LoggedInState.PART_LOGGED_IN_ENABLING_MFA

    Stubs.setupEncryptedPostRequest(
      "/authenticate",
      SessionCreateWithDeviceRequest(developer.email, password, Some(mfaMandatedForUser), None),
      OK,
      Json.toJson(userAuthenticationResponse).toString()
    )

    Stubs.setupRequest(s"/session/$sessionId", OK, Json.toJson(session).toString())
    Stubs.setupDeleteRequest(s"/session/$sessionId", OK)

    sessionId
  }

}

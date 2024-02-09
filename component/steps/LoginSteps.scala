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

package steps

import io.cucumber.datatable.DataTable
import io.cucumber.scala.Implicits._
import io.cucumber.scala.{EN, ScalaDsl}
import matchers.CustomMatchers
import org.openqa.selenium.By
import org.scalatest.matchers.should.Matchers
import pages._
import stubs.{DeveloperStub, MfaStub, Stubs}
import utils.{BrowserDriver, ComponentTestDeveloperBuilder}

import play.api.http.Status._
import play.api.libs.json.{Format, Json}

import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.{LoginRequest, PasswordResetRequest, UserAuthenticationResponse}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.{Developer, LoggedInState, Session}

case class MfaSecret(secret: String)

object MfaSecret {
  implicit val format: Format[MfaSecret] = Json.format[MfaSecret]
}

object TestContext {
  var developer: Developer = _

  var sessionIdForloggedInDeveloper: String = ""
  var sessionIdForMfaMandatingUser: String  = ""
}

class LoginSteps extends ScalaDsl with EN with Matchers with NavigationSugar with CustomMatchers with ComponentTestDeveloperBuilder with BrowserDriver {

  private val mobileNumber = "+447890123456"

  Given("""^I successfully log in with '(.*)' and '(.*)' skipping 2SV$""") { (email: String, password: String) =>
    goOn(SignInPage.default)
    SignInPage.default.signInWith(email, password)
    on(RecommendMfaPage)
    RecommendMfaPage.skip2SVReminder()
    on(RecommendMfaSkipAcknowledgePage)
    RecommendMfaSkipAcknowledgePage.confirmSkip2SV()
  }

  Given("""^I am registered with$""") { data: DataTable =>
    val result: Map[String, String] = data.asScalaRawMaps[String, String].head

    val password  = result("Password")
    val developer = buildDeveloper(emailAddress = result("Email address").toLaxEmail, firstName = result("First name"), lastName = result("Last name"))

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

  Given("""^'(.*)' session is uplifted to LoggedIn$""") { email: String =>
    if (email.toLaxEmail != TestContext.developer.email) {
      throw new IllegalArgumentException(s"Can only know how to uplift ${TestContext.developer.email}'s session")
    }
    MfaStub.setupMfaMandated()
  }

  Given("""^I fill in the login form with$""") { (data: DataTable) =>
    val form = data.asScalaRawMaps[String, String].head
    Form.populate(form)
  }

  Then("""^I am logged in as '(.+)'$""") { userFullName: String =>
    val authCookie = driver.manage().getCookieNamed("PLAY2AUTH_SESS_ID")
    authCookie should not be null
    AnyWebPageWithUserLinks.userLink(userFullName) shouldBe ("defined")
  }

  Then("""^I am not logged in$""") { () =>
    val authCookie = driver.manage().getCookieNamed("PLAY2AUTH_SESS_ID")
    authCookie shouldBe null
  }

  When("""^I attempt to Sign out when the session expires""") {
    val sessionId = "sessionId"
    Stubs.setupDeleteRequest(s"/session/$sessionId", NOT_FOUND)
    try {
      val link = driver.findElement(By.linkText("Sign out"))
      link.click()
    } catch {
      case _: NoSuchElementException =>
        val menu = driver.findElement(By.linkText("Menu"))
        menu.click()

        val link2 = driver.findElement(By.linkText("Sign out"))
        link2.click()
    }
  }

  Then("""^I should be sent an email with a link to reset for '(.*)'$""") { email: String =>
    DeveloperStub.verifyResetPassword(PasswordResetRequest(email.toLaxEmail))
  }

  Given("""^I click on a valid password reset link for code '(.*)'$""") { resetPwdCode: String =>
    val email = "bob@example.com"
    DeveloperStub.stubResetPasswordJourney(email.toLaxEmail, resetPwdCode)

    driver.manage().deleteAllCookies()
    go(new WebLink() { val url = s"http://localhost:${EnvConfig.port}/developer/reset-password-link?code='$resetPwdCode'" })
  }

  Given("""^I click on an invalid password reset link for code '(.*)'$""") { invalidResetPwdCode: String =>
    DeveloperStub.stubResetPasswordJourneyFail()

    driver.manage().deleteAllCookies()
    go(new WebLink() { val url = s"http://localhost:${EnvConfig.port}/developer/reset-password-link?code='$invalidResetPwdCode'" })
  }

  Then("""^I am on the 'Reset Password' page with code '(.*)'$""") { resetPwdCode: String =>
    eventually {
      withClue(s"Fail to be on page: 'Reset Password'")(on(ResetPasswordPage(resetPwdCode)))
    }
  }

  def setupLoggedOrPartLoggedInDeveloper(developer: Developer, password: String, loggedInState: LoggedInState): String = {
    val sessionId = "sessionId_" + loggedInState.toString

    val session                    = Session(sessionId, developer, loggedInState)
    val userAuthenticationResponse = UserAuthenticationResponse(accessCodeRequired = false, mfaEnabled = false, session = Some(session))

    val mfaMandatedForUser = loggedInState == LoggedInState.PART_LOGGED_IN_ENABLING_MFA

    Stubs.setupEncryptedPostRequest("/authenticate", LoginRequest(developer.email, password, mfaMandatedForUser, None), OK, Json.toJson(userAuthenticationResponse).toString())

    Stubs.setupRequest(s"/session/$sessionId", OK, Json.toJson(session).toString())
    Stubs.setupDeleteRequest(s"/session/$sessionId", OK)

    sessionId
  }
}

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

package steps

import io.cucumber.datatable.DataTable
import io.cucumber.scala.Implicits._
import io.cucumber.scala.{EN, ScalaDsl}
import matchers.CustomMatchers
import org.openqa.selenium.WebDriver
import org.scalatest.matchers.should.Matchers
import pages._
import play.api.http.Status._
import play.api.libs.json.Json
import stubs.{DeveloperStub, Stubs, MfaStub}
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.DeveloperBuilder
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.{LoginRequest, UserAuthenticationResponse}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.{Developer, LoggedInState, Session}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{GlobalUserIdTracker, UserIdTracker}


class MfaSteps extends ScalaDsl with EN with Matchers with NavigationSugar with PageSugar with CustomMatchers with DeveloperBuilder with UserIdTracker {
  def idOf(email: String) = GlobalUserIdTracker.idOf(email)

  implicit val webDriver: WebDriver = Env.driver

  private val accessCode = "123456"

  Given("""^I am registered as well with$""") { data: DataTable =>
    val result: Map[String,String] = data.asScalaRawMaps[String, String].head

    val password = result("Password")
    val developer = buildDeveloper(emailAddress = result("Email address"), firstName = result("First name"), lastName = result("Last name"))

    DeveloperStub.findUserIdByEmailAddress(developer.email)
    Stubs.setupPostRequest("/check-password", NO_CONTENT)
    Stubs.setupPostRequest("/authenticate", UNAUTHORIZED)

    TestContext.developer = developer

    TestContext.sessionIdForloggedInDeveloper = setupLoggedOrPartLoggedInDeveloper(developer, password, LoggedInState.LOGGED_IN)
    TestContext.sessionIdForMfaMandatingUser = setupLoggedOrPartLoggedInDeveloper(developer, password, LoggedInState.PART_LOGGED_IN_ENABLING_MFA)

    DeveloperStub.setupGettingDeveloperByEmail(developer)

    MfaStub.setupGettingMfaSecret(developer)

    MfaStub.setupVerificationOfAccessCode(developer)

    DeveloperStub.setUpGetCombinedApis()

    MfaStub.setupEnablingMfa(developer)
  }

  Then("""My device session is not set$""") { () =>
    val authCookie = webDriver.manage().getCookieNamed("DEVICE_SESS_ID")
    authCookie shouldBe null
  }

  When("""^I enter the correct access code during 2SVSetup$""") {
    stubs.MfaStub.stubAuthenticateTotpSuccess()
    Setup2svEnterAccessCodePage.enterAccessCode(accessCode)
    Setup2svEnterAccessCodePage.clickContinue()
  }

  When("""^I enter the correct access code and click remember me for 7 days then click continue$""") {
    Login2svEnterAccessCodePage.enterAccessCode(accessCode, true)
    Login2svEnterAccessCodePage.clickContinue()
  }

  Then("""My device session is set$""") { () =>
    val authCookie = webDriver.manage().getCookieNamed("DEVICE_SESS_ID")
    authCookie should not be null
  }

  When("""^I enter the correct access code and do NOT click remember me for 7 days then click continue$""") {
    Login2svEnterAccessCodePage.enterAccessCode(accessCode)
    Login2svEnterAccessCodePage.clickContinue()
  }

  def setupLoggedOrPartLoggedInDeveloper(developer: Developer, password: String, loggedInState: LoggedInState): String = {
    val sessionId = "sessionId_" + loggedInState.toString

    val session = Session(sessionId, developer, loggedInState)
    val userAuthenticationResponse = UserAuthenticationResponse(accessCodeRequired = false, mfaEnabled= false, session = Some(session))

    val mfaMandatedForUser = loggedInState == LoggedInState.PART_LOGGED_IN_ENABLING_MFA

    Stubs.setupEncryptedPostRequest("/authenticate", LoginRequest(developer.email, password, mfaMandatedForUser, None),
      OK, Json.toJson(userAuthenticationResponse).toString())

    Stubs.setupRequest(s"/session/$sessionId", OK, Json.toJson(session).toString())
    Stubs.setupDeleteRequest(s"/session/$sessionId", OK)

    sessionId
  }

}

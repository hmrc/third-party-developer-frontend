/*
 * Copyright 2018 HM Revenue & Customs
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

package unit.controllers

import config.{ApplicationConfig, ErrorHandler}
import connectors.ThirdPartyDeveloperConnector
import controllers._
import domain._
import org.joda.time.DateTimeZone
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.mockito.BDDMockito.given
import org.mockito.Matchers.{any, eq => mockEq}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider
import service.{ApplicationService, AuditService, SessionService}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import uk.gov.hmrc.time.DateTimeUtils
import utils.ViewHelpers._
import utils.WithCSRFAddToken
import utils.WithLoggedInSession._

import scala.concurrent.Future._

class ManageApplicationsSpec
  extends UnitSpec with MockitoSugar with WithFakeApplication with ScalaFutures with SubscriptionTestHelperSugar with WithCSRFAddToken {

  implicit val materializer = fakeApplication.materializer
  val appId = "1234"
  val clientId = "clientId123"
  val loggedInUser = Developer("thirdpartydeveloper@example.com", "John", "Doe")
  val sessionId = "sessionId"
  val session = Session(sessionId, loggedInUser)
  val application = Application(appId, clientId, "App name 1", DateTimeUtils.now, Environment.PRODUCTION, Some("Description 1"),
    Set(Collaborator(loggedInUser.email, Role.ADMINISTRATOR)), state = ApplicationState.production(loggedInUser.email, ""),
    access = Standard(redirectUris = Seq("https://red1", "https://red2"), termsAndConditionsUrl = Some("http://tnc-url.com")))

  val tokens = ApplicationTokens(EnvironmentToken("clientId", Seq(aClientSecret("secret"), aClientSecret("secret2")), "token"))

  trait Setup {
    val underTest = new ManageApplications(
      mock[ApplicationService],
      mock[ThirdPartyDeveloperConnector],
      mock[SessionService],
      mock[AuditService],
      mock[ErrorHandler],
      mock[ApplicationConfig]
    )

    val hc = HeaderCarrier()

    given(underTest.sessionService.fetch(mockEq(sessionId))(any[HeaderCarrier])).willReturn(Some(session))
    given(underTest.applicationService.update(any[UpdateApplicationRequest])(any[HeaderCarrier])).willReturn(successful(ApplicationUpdateSuccessful))
    given(underTest.applicationService.fetchByApplicationId(mockEq(application.id))(any[HeaderCarrier])).willReturn(successful(application))

    val sessionParams = Seq("csrfToken" -> fakeApplication.injector.instanceOf[TokenProvider].generateToken)
    val loggedOutRequest = FakeRequest().withSession(sessionParams: _*)
    val loggedInRequest = FakeRequest().withLoggedIn(underTest)(sessionId).withSession(sessionParams: _*)
  }

  "manageApps" should {

    "return the manage Applications page with the user logged in" in new Setup {

      given(underTest.applicationService.fetchByTeamMemberEmail(mockEq(loggedInUser.email))(any[HeaderCarrier]))
        .willReturn(successful(List(application)))

      val result = await(underTest.manageApps()(loggedInRequest))

      status(result) shouldBe OK
      bodyOf(result) should include("Manage applications")
      bodyOf(result) should include(loggedInUser.displayedName)
      bodyOf(result) should include("Sign out")
      bodyOf(result) should include("App name 1")
      bodyOf(result) should not include "Sign in"
    }

    "show the link to agree terms of use for an app that has not has the terms of use agreed" in new Setup {

      given(underTest.applicationService.fetchByTeamMemberEmail(mockEq(loggedInUser.email))(any[HeaderCarrier]))
        .willReturn(successful(List(application)))

      val result = await(underTest.manageApps()(loggedInRequest))

      status(result) shouldBe OK
      val dom = Jsoup.parse(bodyOf(result))
      termsOfUseWarningExists(dom) shouldBe true
      termsOfUseColumnExists(dom) shouldBe true
      elementIdentifiedByAttrWithValueContainsText(
        dom, "a", "href", s"/developer/applications/${application.id}/details/terms-of-use", "Read and agree") shouldBe true
    }

    "show the needs admin rights indication for a developer on an app that has not has the terms of use agreed" in new Setup {

      val appWithDeveloperRights = application.copy(id = "56768", collaborators = Set(Collaborator(loggedInUser.email, Role.DEVELOPER)))

      given(underTest.applicationService.fetchByTeamMemberEmail(mockEq(loggedInUser.email))(any[HeaderCarrier]))
        .willReturn(successful(List(application, appWithDeveloperRights)))

      val result = await(underTest.manageApps()(loggedInRequest))

      status(result) shouldBe OK
      val dom = Jsoup.parse(bodyOf(result))
      termsOfUseWarningExists(dom) shouldBe true
      termsOfUseColumnExists(dom) shouldBe true
      termsOfUseIndicatorExistsWithText(dom, appWithDeveloperRights.id, "Need admin rights") shouldBe true
    }

    "show the terms of use agreed indication for an app that has has the terms of use agreed" in new Setup {

      val appWithTermsOfUseAgreed =
        application.copy(
          id = "56768", checkInformation = Some(CheckInformation(termsOfUseAgreements = Seq(TermsOfUseAgreement("bob@example.com", DateTimeUtils.now, "1.0")))))

      given(underTest.applicationService.fetchByTeamMemberEmail(mockEq(loggedInUser.email))(any[HeaderCarrier]))
        .willReturn(successful(List(application, appWithTermsOfUseAgreed)))

      val result = await(underTest.manageApps()(loggedInRequest))

      status(result) shouldBe OK
      val dom = Jsoup.parse(bodyOf(result))
      termsOfUseWarningExists(dom) shouldBe true
      termsOfUseColumnExists(dom) shouldBe true
      termsOfUseIndicatorExistsWithText(dom, appWithTermsOfUseAgreed.id, "Agreed") shouldBe true
    }

    "show the terms of use not applicable indication for a sandbox app" in new Setup {

      val sandboxApp = application.copy(id = "56768", deployedTo = Environment.SANDBOX)

      given(underTest.applicationService.fetchByTeamMemberEmail(mockEq(loggedInUser.email))(any[HeaderCarrier]))
        .willReturn(successful(List(application, sandboxApp)))

      val result = await(underTest.manageApps()(loggedInRequest))

      status(result) shouldBe OK
      val dom = Jsoup.parse(bodyOf(result))
      termsOfUseWarningExists(dom) shouldBe true
      termsOfUseColumnExists(dom) shouldBe true
      termsOfUseIndicatorExistsWithText(dom, sandboxApp.id, "Not applicable") shouldBe true
    }

    "not show the terms of use warning and column when there are no apps requiring terms of use agreement" in new Setup {

      val appWithTermsOfUseAgreed = application.copy(
        id = "56768", checkInformation = Some(CheckInformation(termsOfUseAgreements = Seq(TermsOfUseAgreement("bob@example.com", DateTimeUtils.now, "1.0")))))

      given(underTest.applicationService.fetchByTeamMemberEmail(mockEq(loggedInUser.email))(any[HeaderCarrier]))
        .willReturn(successful(List(appWithTermsOfUseAgreed)))

      val result = await(underTest.manageApps()(loggedInRequest))

      status(result) shouldBe OK
      val dom = Jsoup.parse(bodyOf(result))
      termsOfUseWarningExists(dom) shouldBe false
      termsOfUseColumnExists(dom) shouldBe false
    }


    "return to the login page when the user is not logged in" in new Setup {

      val request = FakeRequest()

      val result = await(underTest.manageApps()(request))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/login")
    }
  }

  "addApplication" should {
    "contain a google analytics event (via the data-journey attribute) when adding an application is successful" in new Setup {
      given(underTest.applicationService.createForUser(any[CreateApplicationRequest])(any[HeaderCarrier]))
        .willReturn(successful(ApplicationCreatedResponse(application.id)))

      val request = loggedInRequest
        .withFormUrlEncodedBody(
          ("applicationName", "Application Name"),
          ("environment", "PRODUCTION"),
          ("description", ""))

      val result = await(underTest.addApplicationAction()(request))
      val dom = Jsoup.parse(bodyOf(result))
      val element = dom.getElementsByAttribute("data-journey").first

      element.attr("data-journey") shouldEqual "application:added"
    }
    "show the application check page button when the environment specified is PRODUCTION" in new Setup {
      given(underTest.applicationService.createForUser(any[CreateApplicationRequest])(any[HeaderCarrier]))
        .willReturn(successful(ApplicationCreatedResponse(application.id)))

      val request = loggedInRequest
        .withFormUrlEncodedBody(
          ("applicationName", "Application Name"),
          ("environment", "PRODUCTION"),
          ("description", ""))

      val result = await(underTest.addApplicationAction()(request))
      val dom = Jsoup.parse(bodyOf(result))
      val element = Option(dom.getElementById("start"))

      element shouldBe defined
    }

    "show the manage subscriptions button when the environment specified is SANDBOX" in new Setup {
      given(underTest.applicationService.createForUser(any[CreateApplicationRequest])(any[HeaderCarrier]))
        .willReturn(successful(ApplicationCreatedResponse(application.id)))

      val request = loggedInRequest
        .withFormUrlEncodedBody(
          ("applicationName", "Application Name"),
          ("environment", "SANDBOX"),
          ("description", ""))

      val result = await(underTest.addApplicationAction()(request))
      val dom = Jsoup.parse(bodyOf(result))
      val element = Option(dom.getElementById("manage-api-subscriptions"))
      elementIdentifiedByAttrWithValueContainsText(
        dom, "a", "href", s"/developer/applications/${application.id}/subscriptions", "Manage API subscriptions") shouldBe true
      element shouldBe defined
    }
  }

  private def aClientSecret(secret: String) = ClientSecret(secret, secret, DateTimeUtils.now.withZone(DateTimeZone.getDefault))

  private def termsOfUseWarningExists(doc: Document) = {
    elementExistsByText(doc, "strong", "You must agree to the terms of use on all production applications.")
  }

  private def termsOfUseColumnExists(doc: Document) = elementExistsByText(doc, "th", "Terms of use")

  private def termsOfUseIndicatorExistsWithText(doc: Document, id: String, text: String) = {
    elementIdentifiedByAttrWithValueContainsText(doc, "td", "id", s"terms-of-use-$id", text)
  }
}

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

package controllers

import domain.ApplicationUpdateSuccessful
import domain.models.applications.Application
import domain.models.developers.{Developer, DeveloperSession, LoggedInState, Session}
import domain.models.flows.IpAllowlistFlow
import mocks.service._
import org.scalatest.Assertion
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import service.IpAllowlistService
import uk.gov.hmrc.http.HeaderCarrier
import utils.WithLoggedInSession._
import utils.{TestApplications, WithCSRFAddToken}
import views.html.ipAllowlist._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future.successful

class IpAllowlistSpec extends BaseControllerSpec with ApplicationActionServiceMock with TestApplications with WithCSRFAddToken {

  trait Setup extends ApplicationServiceMock with SessionServiceMock {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    val mockIpAllowlistService: IpAllowlistService = mock[IpAllowlistService]

    val underTest = new IpAllowlist(
      mockErrorHandler,
      applicationServiceMock,
      applicationActionServiceMock,
      sessionServiceMock,
      mcc,
      cookieSigner,
      mockIpAllowlistService,
      app.injector.instanceOf[IpAllowlistView],
      app.injector.instanceOf[EditIpAllowlistView],
      app.injector.instanceOf[AddCidrBlockView],
      app.injector.instanceOf[ReviewIpAllowlistView],
      app.injector.instanceOf[ChangeIpAllowlistSuccessView],
      app.injector.instanceOf[StartIpAllowlistView],
      app.injector.instanceOf[AllowedIpsView],
      app.injector.instanceOf[SettingUpAllowlistView],
      app.injector.instanceOf[RemoveIpAllowlistView],
      app.injector.instanceOf[RemoveIpAllowlistSuccessView],
      app.injector.instanceOf[RemoveCidrBlockView]
    )

    val sessionId = "sessionId"
    val loggedInRequest = FakeRequest().withLoggedIn(underTest, implicitly)(sessionId)

    val admin: Developer = Developer("admin@example.com", "Joe", "Bloggs")
    val developer: Developer = Developer("developer@example.com", "John", "Doe")

    val anApplicationWithoutIpWhitelist: Application = anApplication(adminEmail = admin.email, developerEmail = developer.email)
    val anApplicationWithIpWhitelist: Application = anApplicationWithoutIpWhitelist.copy(ipWhitelist = Set("1.1.1.0/24"))

    def givenTheUserIsLoggedInAs(user: Developer): DeveloperSession = {
      val session = Session(sessionId, user, LoggedInState.LOGGED_IN)
      fetchSessionByIdReturns(sessionId, session)
      updateUserFlowSessionsReturnsSuccessfully(sessionId)
      DeveloperSession(session)
    }

    def verifyIpAllowlistSurveyIsPresent(body: String): Assertion = {
      body should include("Take our survey and answer questions about the IP allow list service.")
    }
  }

  "viewIpAllowlist" should {
    "return the start page when the app does not have an active allowlist" in new Setup {
      givenApplicationAction(anApplicationWithoutIpWhitelist, givenTheUserIsLoggedInAs(admin))
      when(mockIpAllowlistService.discardIpAllowlistFlow(sessionId)).thenReturn(successful(true))

      val result: Future[Result] = underTest.viewIpAllowlist(anApplicationWithoutIpWhitelist.id)(loggedInRequest)

      status(result) shouldBe OK
      val body: String = contentAsString(result)
      body should include("IP allow list")
      body should include("Before you start")
      verifyIpAllowlistSurveyIsPresent(body)
    }

    "return the allowlist when the app has an active allowlist" in new Setup {
      givenApplicationAction(anApplicationWithIpWhitelist, givenTheUserIsLoggedInAs(admin))
      when(mockIpAllowlistService.discardIpAllowlistFlow(sessionId)).thenReturn(successful(true))

      val result: Future[Result] = underTest.viewIpAllowlist(anApplicationWithIpWhitelist.id)(loggedInRequest)

      status(result) shouldBe OK
      val body: String = contentAsString(result)
      body should include("IP allow list")
      body should include("API requests can only be made from these IP addresses.")
      verifyIpAllowlistSurveyIsPresent(body)
    }

    "return the start page and tell the user to contact an admin when a developer accesses a prod app that does not have an active allowlist" in new Setup {
      givenApplicationAction(anApplicationWithoutIpWhitelist, givenTheUserIsLoggedInAs(developer))
      when(mockIpAllowlistService.discardIpAllowlistFlow(sessionId)).thenReturn(successful(true))

      val result: Future[Result] = underTest.viewIpAllowlist(anApplicationWithoutIpWhitelist.id)(loggedInRequest)

      status(result) shouldBe OK
      val body: String = contentAsString(result)
      body should include("An IP allow list is a security feature that lets you control which IP addresses are allowed to make API requests to HMRC")
      body should include("You cannot set up the IP allow list because you are not an administrator")
      body should include("The administrator <a href=\"mailto:admin@example.com\">admin@example.com</a> has access.")
      verifyIpAllowlistSurveyIsPresent(body)
    }

    "return the allowlist and tell the user to contact an admin when a developer accesses a prod app that has an active allowlist" in new Setup {
      givenApplicationAction(anApplicationWithIpWhitelist, givenTheUserIsLoggedInAs(developer))
      when(mockIpAllowlistService.discardIpAllowlistFlow(sessionId)).thenReturn(successful(true))

      val result: Future[Result] = underTest.viewIpAllowlist(anApplicationWithIpWhitelist.id)(loggedInRequest)

      status(result) shouldBe OK
      val body: String = contentAsString(result)
      body should include("API requests can only be made from these IP addresses.")
      body should include("You cannot edit the IP allow list because you are not an administrator")
      body should include("The administrator <a href=\"mailto:admin@example.com\">admin@example.com</a> has access.")
      verifyIpAllowlistSurveyIsPresent(body)
    }
  }

  "allowedIps" should {
    "return the allowed IP addresses page" in new Setup {
      givenApplicationAction(anApplicationWithoutIpWhitelist, givenTheUserIsLoggedInAs(admin))

      val result: Future[Result] = underTest.allowedIps(anApplicationWithoutIpWhitelist.id)(loggedInRequest)

      status(result) shouldBe OK
      val body: String = contentAsString(result)
      body should include("Allowed IP addresses")
      body should include("We allow IP address ranges represented in CIDR notation")
      verifyIpAllowlistSurveyIsPresent(body)
    }

    "return 403 when a developer tries to access a production app" in new Setup {
      givenApplicationAction(anApplicationWithoutIpWhitelist, givenTheUserIsLoggedInAs(developer))

      val result: Future[Result] = underTest.allowedIps(anApplicationWithoutIpWhitelist.id)(loggedInRequest)

      status(result) shouldBe FORBIDDEN
    }
  }

  "settingUpAllowlist" should {
    "return the setting up allowlist page" in new Setup {
      givenApplicationAction(anApplicationWithoutIpWhitelist, givenTheUserIsLoggedInAs(admin))

      val result: Future[Result] = underTest.settingUpAllowlist(anApplicationWithoutIpWhitelist.id)(loggedInRequest)

      status(result) shouldBe OK
      val body: String = contentAsString(result)
      body should include("Setting up IP allow list")
      body should include("Decide which IP addresses are allowed to make API requests to HMRC")
      verifyIpAllowlistSurveyIsPresent(body)
    }

    "return 403 when a developer tries to access a production app" in new Setup {
      givenApplicationAction(anApplicationWithoutIpWhitelist, givenTheUserIsLoggedInAs(developer))

      val result: Future[Result] = underTest.settingUpAllowlist(anApplicationWithoutIpWhitelist.id)(loggedInRequest)

      status(result) shouldBe FORBIDDEN
    }
  }

  "editIpAllowlist" should {
    "return the edit IP allowlist page when the app does not have an active allowlist" in new Setup {
      givenApplicationAction(anApplicationWithoutIpWhitelist, givenTheUserIsLoggedInAs(admin))
      when(mockIpAllowlistService.getIpAllowlistFlow(anApplicationWithoutIpWhitelist, sessionId))
        .thenReturn(successful(IpAllowlistFlow(sessionId, Set("2.2.2.0/24"))))

      val result: Future[Result] = underTest.editIpAllowlist(anApplicationWithoutIpWhitelist.id)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
      val body: String = contentAsString(result)
      body should include("Continue setting up your IP allow list")
      verifyIpAllowlistSurveyIsPresent(body)
    }

    "return the edit IP allowlist page when the app has an active allowlist" in new Setup {
      givenApplicationAction(anApplicationWithIpWhitelist, givenTheUserIsLoggedInAs(admin))
      when(mockIpAllowlistService.getIpAllowlistFlow(anApplicationWithIpWhitelist, sessionId))
        .thenReturn(successful(IpAllowlistFlow(sessionId, Set("2.2.2.0/24"))))

      val result: Future[Result] = underTest.editIpAllowlist(anApplicationWithIpWhitelist.id)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
      val body: String = contentAsString(result)
      body should include("Edit your IP allow list")
      verifyIpAllowlistSurveyIsPresent(body)
    }

    "return 403 when a developer tries to access a production app" in new Setup {
      givenApplicationAction(anApplicationWithoutIpWhitelist, givenTheUserIsLoggedInAs(developer))

      val result: Future[Result] = underTest.settingUpAllowlist(anApplicationWithoutIpWhitelist.id)(loggedInRequest.withCSRFToken)

      status(result) shouldBe FORBIDDEN
    }
  }

  "editIpAllowlistAction" should {
    "redirect to the add CIDR block page when the user responds yes" in new Setup {
      givenApplicationAction(anApplicationWithoutIpWhitelist, givenTheUserIsLoggedInAs(admin))

      val result: Future[Result] = underTest.editIpAllowlistAction(anApplicationWithoutIpWhitelist.id)(loggedInRequest.withCSRFToken
        .withFormUrlEncodedBody("confirm" -> "Yes"))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/applications/${anApplicationWithoutIpWhitelist.id.value}/ip-allowlist/add")
    }

    "redirect to the review IP allowlist page when the user responds no" in new Setup {
      givenApplicationAction(anApplicationWithoutIpWhitelist, givenTheUserIsLoggedInAs(admin))

      val result: Future[Result] = underTest.editIpAllowlistAction(anApplicationWithoutIpWhitelist.id)(loggedInRequest.withCSRFToken
        .withFormUrlEncodedBody("confirm" -> "No"))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/applications/${anApplicationWithoutIpWhitelist.id.value}/ip-allowlist/activate")
    }

    "return validation error when the user does not select an option" in new Setup {
      givenApplicationAction(anApplicationWithoutIpWhitelist, givenTheUserIsLoggedInAs(admin))
      when(mockIpAllowlistService.getIpAllowlistFlow(anApplicationWithoutIpWhitelist, sessionId)).thenReturn(successful(IpAllowlistFlow(sessionId, Set())))

      val result: Future[Result] = underTest.editIpAllowlistAction(anApplicationWithoutIpWhitelist.id)(loggedInRequest.withCSRFToken
        .withFormUrlEncodedBody("confirm" -> ""))

      status(result) shouldBe BAD_REQUEST
      val body: String = contentAsString(result)
      body should include("Tell us if you want to add another IP address")
      verifyIpAllowlistSurveyIsPresent(body)
    }

    "return 403 when a developer tries to access a production app" in new Setup {
      givenApplicationAction(anApplicationWithoutIpWhitelist, givenTheUserIsLoggedInAs(developer))

      val result: Future[Result] = underTest.editIpAllowlistAction(anApplicationWithoutIpWhitelist.id)(loggedInRequest.withCSRFToken
        .withFormUrlEncodedBody("confirm" -> "Yes"))

      status(result) shouldBe FORBIDDEN
    }
  }

  "addCidrBlock" should {
    "return the add cidr block page" in new Setup {
      givenApplicationAction(anApplicationWithoutIpWhitelist, givenTheUserIsLoggedInAs(admin))
      when(mockIpAllowlistService.getIpAllowlistFlow(anApplicationWithoutIpWhitelist, sessionId))
        .thenReturn(successful(IpAllowlistFlow(sessionId, Set("2.2.2.0/24"))))

      val result: Future[Result] = underTest.addCidrBlock(anApplicationWithoutIpWhitelist.id)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
      val body: String = contentAsString(result)
      body should include("Add an IP address to your IP allow list")
      verifyIpAllowlistSurveyIsPresent(body)
    }

    "return 403 when a developer tries to access a production app" in new Setup {
      givenApplicationAction(anApplicationWithoutIpWhitelist, givenTheUserIsLoggedInAs(developer))

      val result: Future[Result] = underTest.addCidrBlock(anApplicationWithoutIpWhitelist.id)(loggedInRequest.withCSRFToken)

      status(result) shouldBe FORBIDDEN
    }
  }

  "addCidrBlockAction" should {
    val newCidrBlock = "2.2.2.0/24"

    "add the CIDR block and redirect to the edit page" in new Setup {
      givenApplicationAction(anApplicationWithoutIpWhitelist, givenTheUserIsLoggedInAs(admin))
      when(mockIpAllowlistService.addCidrBlock(newCidrBlock, anApplicationWithoutIpWhitelist, sessionId))
        .thenReturn(successful(IpAllowlistFlow(sessionId, Set(newCidrBlock))))

      val result: Future[Result] = underTest.addCidrBlockAction(anApplicationWithoutIpWhitelist.id)(loggedInRequest.withCSRFToken
        .withFormUrlEncodedBody("ipAddress" -> newCidrBlock))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/applications/${anApplicationWithoutIpWhitelist.id.value}/ip-allowlist/change")
      verify(mockIpAllowlistService).addCidrBlock(newCidrBlock, anApplicationWithoutIpWhitelist, sessionId)
    }

    "return validation error when the CIDR block is invalid" in new Setup {
      givenApplicationAction(anApplicationWithoutIpWhitelist, givenTheUserIsLoggedInAs(admin))
      when(mockIpAllowlistService.getIpAllowlistFlow(anApplicationWithoutIpWhitelist, sessionId)).thenReturn(successful(IpAllowlistFlow(sessionId, Set())))

      val result: Future[Result] = underTest.addCidrBlockAction(anApplicationWithoutIpWhitelist.id)(loggedInRequest.withCSRFToken
        .withFormUrlEncodedBody("ipAddress" -> "invalid CIDR block"))

      status(result) shouldBe BAD_REQUEST
      val body: String = contentAsString(result)
      body should include("Enter a CIDR notation in the correct format, for example, 1.1.1.0/24")
      verifyIpAllowlistSurveyIsPresent(body)
    }

    "return validation error when the CIDR block is in a private range" in new Setup {
      givenApplicationAction(anApplicationWithoutIpWhitelist, givenTheUserIsLoggedInAs(admin))
      when(mockIpAllowlistService.getIpAllowlistFlow(anApplicationWithoutIpWhitelist, sessionId)).thenReturn(successful(IpAllowlistFlow(sessionId, Set())))

      val result: Future[Result] = underTest.addCidrBlockAction(anApplicationWithoutIpWhitelist.id)(loggedInRequest.withCSRFToken
        .withFormUrlEncodedBody("ipAddress" -> "192.168.1.0/24"))

      status(result) shouldBe BAD_REQUEST
      val body: String = contentAsString(result)
      body should include("You must use a public network range")
      verifyIpAllowlistSurveyIsPresent(body)
    }

    "return validation error when the CIDR block is using a wider range than it is allowed" in new Setup {
      givenApplicationAction(anApplicationWithoutIpWhitelist, givenTheUserIsLoggedInAs(admin))
      when(mockIpAllowlistService.getIpAllowlistFlow(anApplicationWithoutIpWhitelist, sessionId)).thenReturn(successful(IpAllowlistFlow(sessionId, Set())))

      val result: Future[Result] = underTest.addCidrBlockAction(anApplicationWithoutIpWhitelist.id)(loggedInRequest.withCSRFToken
        .withFormUrlEncodedBody("ipAddress" -> "2.2.0.0/16"))

      status(result) shouldBe BAD_REQUEST
      val body: String = contentAsString(result)
      body should include("Enter a CIDR notation with the correct netmask range. We accept netmask ranges between 24 and 32")
      verifyIpAllowlistSurveyIsPresent(body)
    }

    "return 403 when a developer tries to access a production app" in new Setup {
      givenApplicationAction(anApplicationWithoutIpWhitelist, givenTheUserIsLoggedInAs(developer))

      val result: Future[Result] = underTest.addCidrBlockAction(anApplicationWithoutIpWhitelist.id)(loggedInRequest.withCSRFToken
        .withFormUrlEncodedBody("ipAddress" -> "2.2.2.0/24"))

      status(result) shouldBe FORBIDDEN
    }
  }

  "removeCidrBlock" should {
    val cidrBlockToRemove = "2.2.2.0/24"

    "return the remove cidr block page" in new Setup {
      givenApplicationAction(anApplicationWithoutIpWhitelist, givenTheUserIsLoggedInAs(admin))

      val result: Future[Result] = underTest.removeCidrBlock(anApplicationWithoutIpWhitelist.id, cidrBlockToRemove)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
      val body: String = contentAsString(result)
      body should include("Are you sure you want to remove this IP address from your IP allow list?")
      verifyIpAllowlistSurveyIsPresent(body)
    }

    "return 403 when a developer tries to access a production app" in new Setup {
      givenApplicationAction(anApplicationWithoutIpWhitelist, givenTheUserIsLoggedInAs(developer))

      val result: Future[Result] = underTest.removeCidrBlock(anApplicationWithoutIpWhitelist.id, cidrBlockToRemove)(loggedInRequest.withCSRFToken)

      status(result) shouldBe FORBIDDEN
    }
  }

  "removeCidrBlockAction" should {
    val cidrBlockToRemove = "2.2.2.0/24"

    "remove the CIDR block and redirect to the setup page when the IP allowlist is empty" in new Setup {
      givenApplicationAction(anApplicationWithoutIpWhitelist, givenTheUserIsLoggedInAs(admin))
      when(mockIpAllowlistService.removeCidrBlock(cidrBlockToRemove, sessionId)).thenReturn(successful(IpAllowlistFlow(sessionId, Set())))

      val result: Future[Result] = underTest.removeCidrBlockAction(anApplicationWithoutIpWhitelist.id, cidrBlockToRemove)(loggedInRequest)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/applications/${anApplicationWithoutIpWhitelist.id.value}/ip-allowlist/setup")
      verify(mockIpAllowlistService).removeCidrBlock(cidrBlockToRemove, sessionId)
    }

    "remove the CIDR block and redirect to the edit IP allowlist page when the IP allowlist is not empty" in new Setup {
      givenApplicationAction(anApplicationWithoutIpWhitelist, givenTheUserIsLoggedInAs(admin))
      when(mockIpAllowlistService.removeCidrBlock(cidrBlockToRemove, sessionId)).thenReturn(successful(IpAllowlistFlow(sessionId, Set("1.1.1.0/24"))))

      val result: Future[Result] = underTest.removeCidrBlockAction(anApplicationWithoutIpWhitelist.id, cidrBlockToRemove)(loggedInRequest)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/applications/${anApplicationWithoutIpWhitelist.id.value}/ip-allowlist/change")
      verify(mockIpAllowlistService).removeCidrBlock(cidrBlockToRemove, sessionId)
    }

    "return 403 when a developer tries to access a production app" in new Setup {
      givenApplicationAction(anApplicationWithoutIpWhitelist, givenTheUserIsLoggedInAs(developer))

      val result: Future[Result] = underTest.removeCidrBlockAction(anApplicationWithoutIpWhitelist.id, cidrBlockToRemove)(loggedInRequest)

      status(result) shouldBe FORBIDDEN
    }
  }

  "reviewIpAllowlist" should {
    "return the review IP allowlist page when the app does not have an active allowlist" in new Setup {
      givenApplicationAction(anApplicationWithoutIpWhitelist, givenTheUserIsLoggedInAs(admin))
      when(mockIpAllowlistService.getIpAllowlistFlow(anApplicationWithoutIpWhitelist, sessionId))
        .thenReturn(successful(IpAllowlistFlow(sessionId, Set("2.2.2.0/24"))))

      val result: Future[Result] = underTest.reviewIpAllowlist(anApplicationWithoutIpWhitelist.id)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
      val body: String = contentAsString(result)
      body should include("Review IP allow list")
      body should include("Check your IP allow list before you make it active")
      verifyIpAllowlistSurveyIsPresent(body)
    }

    "return the review IP allowlist page when the app has an active allowlist" in new Setup {
      givenApplicationAction(anApplicationWithIpWhitelist, givenTheUserIsLoggedInAs(admin))
      when(mockIpAllowlistService.getIpAllowlistFlow(anApplicationWithIpWhitelist, sessionId))
        .thenReturn(successful(IpAllowlistFlow(sessionId, Set("2.2.2.0/24"))))

      val result: Future[Result] = underTest.reviewIpAllowlist(anApplicationWithIpWhitelist.id)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
      val body: String = contentAsString(result)
      body should include("Review IP allow list")
      body should include("Review updates to your IP allow list before you make your changes active")
      verifyIpAllowlistSurveyIsPresent(body)
    }

    "return 403 when a developer tries to access a production app" in new Setup {
      givenApplicationAction(anApplicationWithoutIpWhitelist, givenTheUserIsLoggedInAs(developer))

      val result: Future[Result] = underTest.reviewIpAllowlist(anApplicationWithoutIpWhitelist.id)(loggedInRequest.withCSRFToken)

      status(result) shouldBe FORBIDDEN
    }
  }

  "activateIpAllowlist" should {
    "activate the IP allowlist and return the success page" in new Setup {
      givenApplicationAction(anApplicationWithoutIpWhitelist, givenTheUserIsLoggedInAs(admin))
      when(mockIpAllowlistService.getIpAllowlistFlow(anApplicationWithoutIpWhitelist, sessionId))
        .thenReturn(successful(IpAllowlistFlow(sessionId, Set("2.2.2.0/24"))))
      when(mockIpAllowlistService.activateIpAllowlist(eqTo(anApplicationWithoutIpWhitelist), eqTo(sessionId))(*))
        .thenReturn(successful(ApplicationUpdateSuccessful))

      val result: Future[Result] = underTest.activateIpAllowlist(anApplicationWithoutIpWhitelist.id)(loggedInRequest)

      status(result) shouldBe OK
      val body: String = contentAsString(result)
      body should include("Your IP allow list is active")
      verify(mockIpAllowlistService).activateIpAllowlist(eqTo(anApplicationWithoutIpWhitelist), eqTo(sessionId))(*)
      verifyIpAllowlistSurveyIsPresent(body)
    }

    "return 403 when a developer tries to access a production app" in new Setup {
      givenApplicationAction(anApplicationWithoutIpWhitelist, givenTheUserIsLoggedInAs(developer))

      val result: Future[Result] = underTest.activateIpAllowlist(anApplicationWithoutIpWhitelist.id)(loggedInRequest)

      status(result) shouldBe FORBIDDEN
    }
  }

  "removeIpAllowlist" should {
    "return the remove IP allowlist page" in new Setup {
      givenApplicationAction(anApplicationWithoutIpWhitelist, givenTheUserIsLoggedInAs(admin))

      val result: Future[Result] = underTest.removeIpAllowlist(anApplicationWithoutIpWhitelist.id)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK
      val body: String = contentAsString(result)
      body should include("Are you sure you want to remove your IP allow list?")
      verifyIpAllowlistSurveyIsPresent(body)
    }

    "return 403 when a developer tries to access a production app" in new Setup {
      givenApplicationAction(anApplicationWithoutIpWhitelist, givenTheUserIsLoggedInAs(developer))

      val result: Future[Result] = underTest.removeIpAllowlist(anApplicationWithoutIpWhitelist.id)(loggedInRequest.withCSRFToken)

      status(result) shouldBe FORBIDDEN
    }
  }

  "removeIpAllowlistAction" should {
    "deactivate the IP allowlist and return the success page" in new Setup {
      givenApplicationAction(anApplicationWithoutIpWhitelist, givenTheUserIsLoggedInAs(admin))
      when(mockIpAllowlistService.deactivateIpAllowlist(eqTo(anApplicationWithoutIpWhitelist), eqTo(sessionId))(*)).thenReturn(successful(ApplicationUpdateSuccessful))

      val result: Future[Result] = underTest.removeIpAllowlistAction(anApplicationWithoutIpWhitelist.id)(loggedInRequest)

      status(result) shouldBe OK
      val body: String = contentAsString(result)
      body should include("IP allow list removed")
      verify(mockIpAllowlistService).deactivateIpAllowlist(eqTo(anApplicationWithoutIpWhitelist), eqTo(sessionId))(*)
      verifyIpAllowlistSurveyIsPresent(body)
    }

    "return 403 when a developer tries to access a production app" in new Setup {
      givenApplicationAction(anApplicationWithoutIpWhitelist, givenTheUserIsLoggedInAs(developer))

      val result: Future[Result] = underTest.removeIpAllowlistAction(anApplicationWithoutIpWhitelist.id)(loggedInRequest)

      status(result) shouldBe FORBIDDEN
    }
  }
}

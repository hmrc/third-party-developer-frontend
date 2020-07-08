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

import domain._
import mocks.service._
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.`given`
import play.api.mvc.{Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.{BAD_REQUEST, FORBIDDEN, OK}
import service.DeskproService
import uk.gov.hmrc.http.HeaderCarrier
import utils.WithLoggedInSession._
import utils.TestApplications
import views.html.ipwhitelist.{ChangeIpWhitelistSuccessView, ChangeIpWhitelistView, ManageIpWhitelistView}

import scala.concurrent.Future.failed

class IpWhitelistSpec extends BaseControllerSpec with TestApplications {

  trait Setup extends ApplicationServiceMock with SessionServiceMock {
    val mockDeskproService = mock[DeskproService]
    val manageIpWhitelistView = app.injector.instanceOf[ManageIpWhitelistView]
    val changeIpWhitelistView = app.injector.instanceOf[ChangeIpWhitelistView]
    val changeIpWhitelistSuccessView = app.injector.instanceOf[ChangeIpWhitelistSuccessView]

    val underTest = new IpWhitelist(
      mockDeskproService,
      applicationServiceMock,
      sessionServiceMock,
      mockErrorHandler,
      mcc,
      cookieSigner,
      manageIpWhitelistView,
      changeIpWhitelistView,
      changeIpWhitelistSuccessView
    )

    val sessionId = "sessionId"
    val loggedInRequest = FakeRequest().withLoggedIn(underTest, implicitly)(sessionId)

    val admin: Developer = Developer("admin@example.com", "Joe", "Bloggs")
    val developer: Developer = Developer("developer@example.com", "John", "Doe")
    def givenTheUserIsLoggedInAs(user: Developer) = {
      val session = Session(sessionId, user, LoggedInState.LOGGED_IN)
      fetchSessionByIdReturns(sessionId, session)
    }

    implicit val hc = HeaderCarrier()

    val anApplicationWithoutIpWhitelist: Application = anApplication(adminEmail = admin.email, developerEmail = developer.email)
    val anApplicationWithIpWhitelist: Application = anApplicationWithoutIpWhitelist.copy(ipWhitelist = Set("1.1.1.0/24"))

    val supportEnquiryFormCaptor: ArgumentCaptor[SupportEnquiryForm] = ArgumentCaptor.forClass(classOf[SupportEnquiryForm])
  }

  "manageIpWhitelist" should {
    "return the manage IP whitelist page for admins" in new Setup {
      givenTheUserIsLoggedInAs(admin)
      givenApplicationExists(anApplicationWithIpWhitelist)

      val result: Result = await(underTest.manageIpWhitelist(anApplicationWithIpWhitelist.id)(loggedInRequest))

      status(result) shouldBe OK
      val body: String = bodyOf(result)
      body should include("Manage whitelisted IPs")
      body should include("API requests can only be made from these whitelisted IPs")
    }

    "return bad request when the application does not have whitelisted IPs" in new Setup {
      givenTheUserIsLoggedInAs(admin)
      givenApplicationExists(anApplicationWithoutIpWhitelist)

      val result: Result = await(underTest.manageIpWhitelist(anApplicationWithoutIpWhitelist.id)(loggedInRequest))

      status(result) shouldBe BAD_REQUEST
    }

    "return the manage IP whitelist page for developers" in new Setup {
      givenTheUserIsLoggedInAs(developer)
      givenApplicationExists(anApplicationWithIpWhitelist)

      val result: Result = await(underTest.manageIpWhitelist(anApplicationWithIpWhitelist.id)(loggedInRequest))

      status(result) shouldBe OK
      val body: String = bodyOf(result)
      body should include("Manage whitelisted IPs")
      body should include("API requests can only be made from these whitelisted IPs")
    }
  }

  "changeIpWhitelist" should {
    "return the change IP whitelist page for admins" in new Setup {
      givenTheUserIsLoggedInAs(admin)
      givenApplicationExists(anApplicationWithIpWhitelist)

      val result: Result = await(underTest.changeIpWhitelist(anApplicationWithIpWhitelist.id)(loggedInRequest.withCSRFToken))

      status(result) shouldBe OK
      val body: String = bodyOf(result)
      body should include("Change whitelisted IPs")
      body should include("Tell us what you want to change")
    }

    "return bad request when the application does not have whitelisted IPs" in new Setup {
      givenTheUserIsLoggedInAs(admin)
      givenApplicationExists(anApplicationWithoutIpWhitelist)

      val result: Result = await(underTest.changeIpWhitelist(anApplicationWithoutIpWhitelist.id)(loggedInRequest.withCSRFToken))

      status(result) shouldBe BAD_REQUEST
    }

    "return forbidden for developers" in new Setup {
      givenTheUserIsLoggedInAs(developer)
      givenApplicationExists(anApplicationWithIpWhitelist)

      val result: Result = await(underTest.changeIpWhitelist(anApplicationWithIpWhitelist.id)(loggedInRequest.withCSRFToken))

      status(result) shouldBe FORBIDDEN
    }
  }

  "changeIpWhitelistAction" should {
    "send deskpro ticket for admins" in new Setup {
      givenTheUserIsLoggedInAs(admin)
      givenApplicationExists(anApplicationWithIpWhitelist)
      given(mockDeskproService.submitSupportEnquiry(supportEnquiryFormCaptor.capture())(any[Request[AnyRef]], any[HeaderCarrier])).willReturn(TicketCreated)
      val expectedComments = "add 1.1.2.0/24 to the whitelist"

      val result: Result = await(underTest.changeIpWhitelistAction(anApplicationWithIpWhitelist.id)(loggedInRequest.withCSRFToken
        .withFormUrlEncodedBody("description" -> expectedComments)))

      status(result) shouldBe OK
      val body: String = bodyOf(result)
      body should include("Request received")
      body should include("You requested to change your whitelisted IP addresses.")
      supportEnquiryFormCaptor.getValue.fullname shouldBe "Joe Bloggs"
      supportEnquiryFormCaptor.getValue.email shouldBe admin.email
      supportEnquiryFormCaptor.getValue.comments shouldBe expectedComments
    }

    "propagate exception from deskpro" in new Setup {
      givenTheUserIsLoggedInAs(admin)
      givenApplicationExists(anApplicationWithIpWhitelist)
      given(mockDeskproService.submitSupportEnquiry(any[SupportEnquiryForm])(any[Request[AnyRef]], any[HeaderCarrier]))
        .willReturn(failed(new DeskproTicketCreationFailed("unexpected error")))

      val exception: DeskproTicketCreationFailed = intercept[DeskproTicketCreationFailed] {
        await(underTest.changeIpWhitelistAction(anApplicationWithIpWhitelist.id)(loggedInRequest.withCSRFToken
          .withFormUrlEncodedBody("description" -> "add 1.1.2.0/24 to the whitelist")))
      }

      exception.getMessage shouldBe "Failed to create deskpro ticket: unexpected error"
    }

    "return bad request when the application does not have whitelisted IPs" in new Setup {
      givenTheUserIsLoggedInAs(admin)
      givenApplicationExists(anApplicationWithoutIpWhitelist)

      val result: Result = await(underTest.changeIpWhitelistAction(anApplicationWithoutIpWhitelist.id)(loggedInRequest.withCSRFToken
        .withFormUrlEncodedBody("description" -> "add 1.1.2.0/24 to the whitelist")))

      status(result) shouldBe BAD_REQUEST
    }

    "return forbidden for developers" in new Setup {
      givenTheUserIsLoggedInAs(developer)
      givenApplicationExists(anApplicationWithIpWhitelist)

      val result: Result = await(underTest.changeIpWhitelistAction(anApplicationWithIpWhitelist.id)(loggedInRequest.withCSRFToken
        .withFormUrlEncodedBody("description" -> "add 1.1.2.0/24 to the whitelist")))

      status(result) shouldBe FORBIDDEN
    }

    "return bad request when the change description is missing" in new Setup {
      givenTheUserIsLoggedInAs(admin)
      givenApplicationExists(anApplicationWithIpWhitelist)

      val result: Result = await(underTest.changeIpWhitelistAction(anApplicationWithIpWhitelist.id)(loggedInRequest.withCSRFToken))

      status(result) shouldBe BAD_REQUEST
    }

    "return bad request when the change description is empty" in new Setup {
      givenTheUserIsLoggedInAs(admin)
      givenApplicationExists(anApplicationWithIpWhitelist)

      val result: Result = await(underTest.changeIpWhitelistAction(anApplicationWithIpWhitelist.id)(loggedInRequest.withCSRFToken
        .withFormUrlEncodedBody("description" -> "")))

      status(result) shouldBe BAD_REQUEST
    }

    "return bad request when the change description is too long" in new Setup {
      givenTheUserIsLoggedInAs(admin)
      givenApplicationExists(anApplicationWithIpWhitelist)

      val result: Result = await(underTest.changeIpWhitelistAction(anApplicationWithIpWhitelist.id)(loggedInRequest.withCSRFToken
        .withFormUrlEncodedBody("description" -> "a" * 3001)))

      status(result) shouldBe BAD_REQUEST
    }
  }
}

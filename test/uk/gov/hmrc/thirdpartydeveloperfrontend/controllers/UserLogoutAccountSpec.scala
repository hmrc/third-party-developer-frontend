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

package uk.gov.hmrc.thirdpartydeveloperfrontend.controllers

import scala.concurrent.ExecutionContext.Implicits.global

import play.api.mvc.AnyContent
import play.api.test.FakeRequest
import play.api.test.Helpers._

import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ErrorHandler
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.{ApplicationService, DeskproService}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithCSRFAddToken

class UserLogoutAccountSpec
    extends BaseControllerSpec
    with WithCSRFAddToken {

  trait Setup {

    val underTest = new UserLogoutAccount(
      mock[DeskproService],
      sessionServiceMock,
      mock[ApplicationService],
      mock[ErrorHandler],
      mcc,
      cookieSigner
    )

    DestroySession.succeedsWith(devSession.sessionId)

    val notLoggedInRequestWithCsrfToken = FakeRequest()
      .withSession(sessionParams: _*)
  }

  "logging out" should {

    "display the logout confirmation page when the user calls logout" in new Setup {
      val request = loggedInDevRequest
      val result  = underTest.logout()(request)

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some("/feedback/devhub")
    }

    "display the logout confirmation page when a user that is not signed in attempts to log out" in new Setup {
      val request = FakeRequest()
      val result  = underTest.logout()(request)

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some("/feedback/devhub")
    }

    "destroy session on logout" in new Setup {
      implicit val request: FakeRequest[AnyContent] = loggedInDevRequest.withSession("access_uri" -> "https://www.example.com")
      val result                                    = await(underTest.logout()(request))

      verify(underTest.sessionService, atLeastOnce).destroy(eqTo(devSession.sessionId))(*)
      result.session.data shouldBe Map.empty
    }
  }
}

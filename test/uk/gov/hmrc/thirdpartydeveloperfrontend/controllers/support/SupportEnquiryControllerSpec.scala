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

package uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.support

import scala.concurrent.ExecutionContext.Implicits.global

import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider

import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.{LoggedInState, UserSession, UserSessionId}
import uk.gov.hmrc.apiplatform.modules.tpd.test.builders.UserBuilder
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ErrorHandler
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.BaseControllerSpec
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service.SessionServiceMock
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithCSRFAddToken
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithLoggedInSession._

class SupportEnquiryControllerSpec extends BaseControllerSpec with WithCSRFAddToken with UserBuilder with LocalUserIdTracker {

  trait Setup extends SessionServiceMock {

    val underTest = new SupportEnquiryController(
      mcc,
      cookieSigner,
      sessionServiceMock,
      mock[ErrorHandler]
    )

    val sessionParams: Seq[(String, String)] = Seq("csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken)
    val developer                            = buildTrackedUser(emailAddress = "thirdpartydeveloper@example.com".toLaxEmail)
    val sessionId                            = UserSessionId.random
  }

  trait IsLoggedIn {
    self: Setup =>

    val request = FakeRequest()
      .withLoggedIn(underTest, implicitly)(sessionId)
      .withSession(sessionParams: _*)

    fetchSessionByIdReturns(sessionId, UserSession(sessionId, LoggedInState.LOGGED_IN, developer))
  }

  trait NotLoggedIn {
    self: Setup =>

    val request = FakeRequest()
      .withSession(sessionParams: _*)

    fetchSessionByIdReturnsNone(sessionId)
  }

  trait IsPartLoggedInEnablingMFA {
    self: Setup =>

    val request = FakeRequest()
      .withLoggedIn(underTest, implicitly)(sessionId)
      .withSession(sessionParams: _*)

    fetchSessionByIdReturns(sessionId, UserSession(sessionId, LoggedInState.PART_LOGGED_IN_ENABLING_MFA, developer))
  }

  "SupportEnquiryController" when {

    "invoking supportEnquiryPage for old support" should {
      "redirect to new devhub support frontend" in new Setup with IsLoggedIn {
        val result = addToken(underTest.supportEnquiryPage())(request)

        status(result) shouldBe MOVED_PERMANENTLY
        redirectLocation(result) shouldBe Some("/devhub-support/")
      }
    }
  }

}

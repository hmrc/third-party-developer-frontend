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

package uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.noapplications

import scala.concurrent.ExecutionContext.Implicits.global

import views.helper.EnvironmentNameService
import views.html.noapplications.{NoApplicationsChoiceView, StartUsingRestApisView}

import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.test.Helpers._

import uk.gov.hmrc.apiplatform.modules.tpd.test.builders.UserBuilder
import uk.gov.hmrc.apiplatform.modules.tpd.test.data.SampleUserSession
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.SampleApplication
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ErrorHandler
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers._
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithCSRFAddToken
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithLoggedInSession._

class NoApplicationsSpec
    extends BaseControllerSpec
    with ApplicationActionServiceMock
    with SampleUserSession
    with SampleApplication
    with SubscriptionTestSugar
    with SubscriptionTestHelper
    with WithCSRFAddToken
    with UserBuilder
    with LocalUserIdTracker {

  trait Setup extends SessionServiceMock {
    val noApplicationsChoiceView                                = app.injector.instanceOf[NoApplicationsChoiceView]
    val startUsingRestApisView                                  = app.injector.instanceOf[StartUsingRestApisView]
    implicit val environmentNameService: EnvironmentNameService = new EnvironmentNameService(appConfig)

    val noApplicationsController = new NoApplications(
      mock[ErrorHandler],
      sessionServiceMock,
      cookieSigner,
      startUsingRestApisView,
      noApplicationsChoiceView,
      mcc
    )

    fetchSessionByIdReturns(sessionId, userSession)
    updateUserFlowSessionsReturnsSuccessfully(sessionId)

    val loggedInRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
      .withLoggedIn(noApplicationsController, implicitly)(sessionId)
      .withSession(sessionParams: _*).withCSRFToken

    val partLoggedInRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
      .withLoggedIn(noApplicationsController, implicitly)(partLoggedInSession.sessionId)
      .withSession(sessionParams: _*)
  }

  "noApplications" when {

    "noApplicationsPage" should {
      "return the no applications Page when the user logged in" in new Setup {

        private val result = noApplicationsController.noApplicationsPage()(loggedInRequest)

        status(result) shouldBe OK
        contentAsString(result) should include(userSession.developer.displayedName)
        contentAsString(result) should include("Sign out")
        contentAsString(result) should include("Using the Developer Hub")
        contentAsString(result) should not include "Sign in"
      }

      "return to the login page when the user is not logged in" in new Setup {
        val request = FakeRequest()

        private val result = noApplicationsController.noApplicationsPage()(request)

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("/developer/login")
      }
    }

    "noApplicationsAction" should {

      "redirect to email preferences when 'get-emails' choice posted" in new Setup {
        val request = loggedInRequest.withFormUrlEncodedBody("choice" -> "get-emails")

        private val result = noApplicationsController.noApplicationsAction()(request)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("/developer/profile/email-preferences")
      }

      "redirect to start using rest apis when 'use-apis' choice posted" in new Setup {
        val request = loggedInRequest.withFormUrlEncodedBody("choice" -> "use-apis")

        private val result = noApplicationsController.noApplicationsAction()(request)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("/developer/no-applications-start")
      }

      "return Using Developer Hub page with errors when the form is invalid" in new Setup {
        val request = loggedInRequest

        private val result = noApplicationsController.noApplicationsAction()(request)
        status(result) shouldBe BAD_REQUEST
        contentAsString(result) should include("Please select an option")
      }

      "return to the login page when the user is not logged in" in new Setup {
        val request = FakeRequest()

        private val result = noApplicationsController.noApplicationsAction()(request)

        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("/developer/login")
      }
    }

  }

}

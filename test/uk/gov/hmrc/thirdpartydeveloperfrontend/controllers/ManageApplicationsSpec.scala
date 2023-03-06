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

import java.time.LocalDateTime
import java.util.UUID.randomUUID
import scala.concurrent.ExecutionContext.Implicits.global

import views.helper.EnvironmentNameService
import views.html._

import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider

import uk.gov.hmrc.apiplatform.modules.uplift.services.mocks.UpliftLogicMock
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.{DeveloperBuilder, _}
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ErrorHandler
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers.ApplicationSummary
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.connectors.ApmConnectorMockModule
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithLoggedInSession._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{LocalUserIdTracker, WithCSRFAddToken}
import uk.gov.hmrc.apiplatform.modules.submissions.services.mocks.SubmissionServiceMockModule

class ManageApplicationsSpec
    extends BaseControllerSpec
    with ApplicationActionServiceMock
    with SampleSession
    with SampleApplication
    with SubscriptionTestHelperSugar
    with WithCSRFAddToken
    with DeveloperBuilder
    with LocalUserIdTracker {

  val tokens = ApplicationToken(List(aClientSecret(), aClientSecret()), "token")

  private val sessionParams = Seq("csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken)

  trait Setup extends UpliftLogicMock with AppsByTeamMemberServiceMock with ApplicationServiceMock with ApmConnectorMockModule with SessionServiceMock
      with TermsOfUseInvitationServiceMockModule with SubmissionServiceMockModule {
    val manageApplicationsView = app.injector.instanceOf[ManageApplicationsView]

    implicit val environmentNameService = new EnvironmentNameService(appConfig)

    val manageApplicationsController = new ManageApplications(
      mock[ErrorHandler],
      sessionServiceMock,
      cookieSigner,
      appsByTeamMemberServiceMock,
      upliftLogicMock,
      manageApplicationsView,
      mcc,
      TermsOfUseInvitationServiceMock.aMock,
      SubmissionServiceMock.aMock
    )

    fetchSessionByIdReturns(sessionId, session)
    updateUserFlowSessionsReturnsSuccessfully(sessionId)

    val loggedInRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
      .withLoggedIn(manageApplicationsController, implicitly)(sessionId)
      .withSession(sessionParams: _*)

    val partLoggedInRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
      .withLoggedIn(manageApplicationsController, implicitly)(partLoggedInSessionId)
      .withSession(sessionParams: _*)
  }

  "manageApps" should {

    "return the manage Applications page with the user logged in" in new Setup {
      val prodSummary = ApplicationSummary.from(sampleApp, loggedInDeveloper.developer.userId)
      aUsersUplfitableAndNotUpliftableAppsReturns(List.empty, List.empty, List.empty)
      fetchProductionSummariesByTeamMemberReturns(List(prodSummary))

      TermsOfUseInvitationServiceMock.FetchTermsOfUseInvitation.thenReturnNone()

      SubmissionServiceMock.FetchLatestSubmission.thenReturnsNone()

      private val result = manageApplicationsController.manageApps()(loggedInRequest)

      status(result) shouldBe OK
      contentAsString(result) should include(loggedInDeveloper.displayedName)
      contentAsString(result) should include("Sign out")
      contentAsString(result) should include("App name 1")
      contentAsString(result) should not include "Sign in"
    }

    "redirect to the no Applications page when the user logged in and no applications returned for user" in new Setup {
      aUsersUplfitableAndNotUpliftableAppsReturns(List.empty, List.empty, List.empty)
      fetchProductionSummariesByTeamMemberReturns(List.empty)

      TermsOfUseInvitationServiceMock.FetchTermsOfUseInvitation.thenReturnNone()

      private val result = manageApplicationsController.manageApps()(loggedInRequest)

      status(result) shouldBe SEE_OTHER
      headers(result).get("LOCATION").getOrElse("") shouldBe "/developer/no-applications"
    }

    "return to the login page when the user is not logged in" in new Setup {
      val request = FakeRequest()

      private val result = manageApplicationsController.manageApps()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/login")
    }
  }

  private def aClientSecret() = ClientSecret(randomUUID.toString, randomUUID.toString, LocalDateTime.now())
}

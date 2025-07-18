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

import java.util.UUID.randomUUID
import scala.concurrent.ExecutionContext.Implicits.global

import views.helper.EnvironmentNameService
import views.html._

import play.api.test.FakeRequest
import play.api.test.Helpers._

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.{ClientSecret, ClientSecretResponse}
import uk.gov.hmrc.apiplatform.modules.submissions.services.mocks.SubmissionServiceMockModule
import uk.gov.hmrc.apiplatform.modules.uplift.services.mocks.UpliftLogicMock
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ErrorHandler
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers.ApplicationSummary
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithCSRFAddToken

class ManageApplicationsSpec
    extends BaseControllerSpec
    with WithCSRFAddToken {

  val tokens = ApplicationToken(List(aClientSecret(), aClientSecret()), "token")

  trait Setup
      extends UpliftLogicMock
      with ApplicationActionServiceMock
      with AppsByTeamMemberServiceMock
      with ApplicationServiceMock
      with TermsOfUseInvitationServiceMockModule
      with SubmissionServiceMockModule {

    val manageApplicationsView = app.injector.instanceOf[ManageApplicationsView]

    implicit val environmentNameService: EnvironmentNameService = new EnvironmentNameService(appConfig)

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

    val sessionId   = adminSession.sessionId
    val userSession = adminSession
  }

  "manageApps" should {

    "return the manage Applications page with the user logged in" in new Setup {
      val prodSummary = ApplicationSummary.from(standardApp, userSession.developer.userId)
      aUsersUplfitableAndNotUpliftableAppsReturns(List.empty, List.empty, List.empty)
      fetchProductionSummariesByTeamMemberReturns(List(prodSummary))

      TermsOfUseInvitationServiceMock.FetchTermsOfUseInvitation.thenReturnNone()

      SubmissionServiceMock.FetchLatestSubmission.thenReturnsNone()

      private val result = manageApplicationsController.manageApps()(loggedInAdminRequest)

      status(result) shouldBe OK
      contentAsString(result) should include(userSession.developer.displayedName)
      contentAsString(result) should include("Sign out")
      contentAsString(result) should include(standardApp.name.value)
      contentAsString(result) should not include "Sign in"
    }

    "redirect to the no Applications page when the user logged in and no applications returned for user" in new Setup {
      aUsersUplfitableAndNotUpliftableAppsReturns(List.empty, List.empty, List.empty)
      fetchProductionSummariesByTeamMemberReturns(List.empty)

      TermsOfUseInvitationServiceMock.FetchTermsOfUseInvitation.thenReturnNone()

      private val result = manageApplicationsController.manageApps()(loggedInAdminRequest)

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

  private def aClientSecret() = ClientSecretResponse(ClientSecret.Id.random, randomUUID.toString, instant)
}

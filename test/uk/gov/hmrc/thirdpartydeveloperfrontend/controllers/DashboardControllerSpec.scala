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

import views.helper.EnvironmentNameService
import views.html._

import play.api.test.FakeRequest
import play.api.test.Helpers._

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.{ApplicationWithCollaboratorsFixtures, CollaboratorData}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.OrganisationId
import uk.gov.hmrc.apiplatform.modules.organisations.domain.models.{Member, Organisation, OrganisationName}
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ErrorHandler
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers.ApplicationSummary
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithCSRFAddToken

class DashboardControllerSpec
    extends BaseControllerSpec
    with WithCSRFAddToken {

  trait Setup
      extends DashboardServiceMockModule
      with ApplicationWithCollaboratorsFixtures {

    val dashboardView = app.injector.instanceOf[DashboardView]

    implicit val environmentNameService: EnvironmentNameService = new EnvironmentNameService(appConfig)

    val dashboardController = new DashboardController(
      mock[ErrorHandler],
      sessionServiceMock,
      cookieSigner,
      dashboardView,
      mcc,
      DashboardServiceMock.aMock
    )

    val sessionId   = adminSession.sessionId
    val userSession = adminSession

    val userId       = CollaboratorData.Administrator.one.userId
    val orgId        = OrganisationId.random
    val organisation = Organisation(orgId, OrganisationName("My org"), Organisation.OrganisationType.UkLimitedCompany, instant, Set(Member(userId)))

  }

  "get dashboard page" should {

    "return the dashboard page with the user logged in" in new Setup {
      val prodSummary = ApplicationSummary.from(standardApp, userSession.developer.userId)
      val apps        = Seq(prodSummary)
      val orgs        = Seq(organisation)

      DashboardServiceMock.FetchApplicationList.thenReturn(apps)
      DashboardServiceMock.FetchOrganisationsByUserId.thenReturn(orgs)

      private val result = dashboardController.home()(loggedInAdminRequest)

      status(result) shouldBe OK
      contentAsString(result) should include(userSession.developer.displayedName)
      contentAsString(result) should include("Your organisations")
      contentAsString(result) should include(organisation.organisationName.value)
      contentAsString(result) should include("Sign out")
      contentAsString(result) should include(standardApp.name.value)
      contentAsString(result) should not include "Sign in"
    }

    "return the dashboard page without the orgs tile if the user has no orgs" in new Setup {
      val prodSummary = ApplicationSummary.from(standardApp, userSession.developer.userId)
      val apps        = Seq(prodSummary)
      val orgs        = Seq.empty

      DashboardServiceMock.FetchApplicationList.thenReturn(apps)
      DashboardServiceMock.FetchOrganisationsByUserId.thenReturn(orgs)

      private val result = dashboardController.home()(loggedInAdminRequest)

      status(result) shouldBe OK
      contentAsString(result) should include(userSession.developer.displayedName)
      contentAsString(result) shouldNot include("Your organisations")
      contentAsString(result) should include("Sign out")
      contentAsString(result) should include(standardApp.name.value)
      contentAsString(result) should not include "Sign in"
    }

    "return to the login page when the user is not logged in" in new Setup {
      val request = FakeRequest()

      private val result = dashboardController.home()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/login")
    }
  }
}

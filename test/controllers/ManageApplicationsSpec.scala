/*
 * Copyright 2022 HM Revenue & Customs
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

import java.util.UUID.randomUUID

import builder.DeveloperBuilder
import config.ErrorHandler
import domain.models.applications._
import mocks.service._
import org.joda.time.DateTimeZone
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider
import uk.gov.hmrc.time.DateTimeUtils
import utils.WithCSRFAddToken
import utils.WithLoggedInSession._
import views.helper.EnvironmentNameService
import views.html._
import domain.models.controllers.ApplicationSummary

import scala.concurrent.ExecutionContext.Implicits.global
import utils.LocalUserIdTracker
import mocks.connector.ApmConnectorMockModule
import builder._
import modules.uplift.services.mocks.UpliftLogicMock

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

  trait Setup extends UpliftLogicMock with AppsByTeamMemberServiceMock with ApplicationServiceMock with ApmConnectorMockModule with SessionServiceMock {
    val addApplicationSubordinateEmptyNestView = app.injector.instanceOf[AddApplicationSubordinateEmptyNestView]
    val manageApplicationsView = app.injector.instanceOf[ManageApplicationsView]

    implicit val environmentNameService = new EnvironmentNameService(appConfig)

    val manageApplicationsController = new ManageApplications(
      mock[ErrorHandler],
      sessionServiceMock,
      cookieSigner,

      appsByTeamMemberServiceMock,
      upliftLogicMock,
      
      manageApplicationsView,
      addApplicationSubordinateEmptyNestView,

      mcc
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
      aUsersUplfitableAndNotUpliftableAppsReturns(List.empty, List.empty)
      fetchProductionSummariesByTeamMemberReturns(List(prodSummary))

      private val result = manageApplicationsController.manageApps()(loggedInRequest)

      status(result) shouldBe OK
      contentAsString(result) should include(loggedInDeveloper.displayedName)
      contentAsString(result) should include("Sign out")
      contentAsString(result) should include("App name 1")
      contentAsString(result) should not include "Sign in"
    }

    "return to the login page when the user is not logged in" in new Setup {
      val request = FakeRequest()

      private val result = manageApplicationsController.manageApps()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/login")
    }
  }

  private def aClientSecret() = ClientSecret(randomUUID.toString, randomUUID.toString, DateTimeUtils.now.withZone(DateTimeZone.getDefault))
}

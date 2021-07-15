/*
 * Copyright 2021 HM Revenue & Customs
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

import builder.DeveloperBuilder
import config.ErrorHandler
import domain.models.applications._
import domain.models.developers.{DeveloperSession, LoggedInState, Session}
import mocks.service._
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.test.Helpers._
import service.AuditService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.time.DateTimeUtils
import utils._
import utils.WithLoggedInSession._
import views.helper.EnvironmentNameService
import views.html._

import scala.concurrent.ExecutionContext.Implicits.global
import utils.LocalUserIdTracker
import domain.models.controllers.SandboxApplicationSummary
import domain.models.apidefinitions.AccessType
import builder.ApplicationBuilder
class AddApplicationProductionSwitchSpec
    extends BaseControllerSpec 
    with SubscriptionTestHelperSugar 
    with WithCSRFAddToken 
    with DeveloperBuilder
    with LocalUserIdTracker
    with ApplicationBuilder {

  val developer = buildDeveloper()
  val sessionId = "sessionId"
  val session = Session(sessionId, developer, LoggedInState.LOGGED_IN)

  val loggedInUser = DeveloperSession(session)

  val partLoggedInSessionId = "partLoggedInSessionId"
  val partLoggedInSession = Session(partLoggedInSessionId, developer, LoggedInState.PART_LOGGED_IN_ENABLING_MFA)

  val collaborator: Collaborator = loggedInUser.email.asAdministratorCollaborator

  val appCreatedOn = DateTimeUtils.now.minusDays(1)
  val appLastAccess = appCreatedOn

  val sandboxAppSummaries = (1 to 5).map(_ => buildApplication(loggedInUser.email)).map(SandboxApplicationSummary.from(_, loggedInUser.email))

  trait Setup extends ApplicationServiceMock with ApplicationActionServiceMock with SessionServiceMock with EmailPreferencesServiceMock {
    val addApplicationSubordinateEmptyNestView = app.injector.instanceOf[AddApplicationSubordinateEmptyNestView]
    val manageApplicationsView = app.injector.instanceOf[ManageApplicationsView]
    val accessTokenSwitchView = app.injector.instanceOf[AccessTokenSwitchView]
    val usingPrivilegedApplicationCredentialsView = app.injector.instanceOf[UsingPrivilegedApplicationCredentialsView]
    val tenDaysWarningView = app.injector.instanceOf[TenDaysWarningView]
    val addApplicationStartSubordinateView = app.injector.instanceOf[AddApplicationStartSubordinateView]
    val addApplicationStartPrincipalView = app.injector.instanceOf[AddApplicationStartPrincipalView]
    val addApplicationSubordinateSuccessView = app.injector.instanceOf[AddApplicationSubordinateSuccessView]
    val addApplicationNameView = app.injector.instanceOf[AddApplicationNameView]
    val chooseApplicationToUpliftView = app.injector.instanceOf[ChooseApplicationToUpliftView]

    implicit val environmentNameService = new EnvironmentNameService(appConfig)

    val underTest = new AddApplication(
      mock[ErrorHandler],
      applicationServiceMock,
      applicationActionServiceMock,
      emailPreferencesServiceMock,
      sessionServiceMock,
      mock[AuditService],
      mcc,
      cookieSigner,
      addApplicationSubordinateEmptyNestView,
      manageApplicationsView,
      accessTokenSwitchView,
      usingPrivilegedApplicationCredentialsView,
      tenDaysWarningView,
      addApplicationStartSubordinateView,
      addApplicationStartPrincipalView,
      addApplicationSubordinateSuccessView,
      addApplicationNameView,
      chooseApplicationToUpliftView
    )

    val hc = HeaderCarrier()

    fetchSessionByIdReturns(sessionId, session)
    updateUserFlowSessionsReturnsSuccessfully(sessionId)

    fetchSessionByIdReturns(partLoggedInSessionId, partLoggedInSession)

    val loggedInRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
      .withLoggedIn(underTest, implicitly)(sessionId)
      .withCSRFToken

    val partLoggedInRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
      .withLoggedIn(underTest, implicitly)(partLoggedInSessionId)

    when(appConfig.nameOfPrincipalEnvironment).thenReturn("Production")
    when(appConfig.nameOfSubordinateEnvironment).thenReturn("Sandbox")

  }

  "addApplicationProductionSwitch" should {
    "return bad request when no apps are upliftable" in new Setup {
      fetchSandoxSummariesByTeamMemberReturns(Seq.empty[SandboxApplicationSummary])

      intercept[IllegalStateException] {
        await(underTest.addApplicationProductionSwitch()(loggedInRequest))
      }.getMessage() shouldBe "Should not be requesting with this data"
    }
    
    "go to next stage in journey when one app is upliftable and no other apps are present" in new Setup {
      val summaries = sandboxAppSummaries.take(1)
      fetchSandoxSummariesByTeamMemberReturns(summaries)
      identifyUpliftableSandboxAppIdsReturns(summaries.map(_.id).toSet)
      
      val result = underTest.addApplicationProductionSwitch()(loggedInRequest)

      status(result) shouldBe OK

      // Gone to addApplicationPage
      // TODO - will go to check page once ready
      contentAsString(result) should include("What&#x27;s the name of your application?")
    }
    
    "return ok when all apps are upliftable" in new Setup {
      val summaries = sandboxAppSummaries
      fetchSandoxSummariesByTeamMemberReturns(summaries)
      identifyUpliftableSandboxAppIdsReturns(summaries.map(_.id).toSet)
      
      val result = underTest.addApplicationProductionSwitch()(loggedInRequest)

      status(result) shouldBe OK

      contentAsString(result) should include("Which application do you want production credentials for?")
      summaries.map { summary =>
        contentAsString(result) should include(summary.name)
      }
    }
    
    "return ok when some apps are upliftable" in new Setup {
      val summaries = sandboxAppSummaries
      fetchSandoxSummariesByTeamMemberReturns(summaries)
      identifyUpliftableSandboxAppIdsReturns(summaries.drop(1).map(_.id).toSet)
      
      val result = underTest.addApplicationProductionSwitch()(loggedInRequest)

      status(result) shouldBe OK

      contentAsString(result) should include("Which application do you want production credentials for?")
      summaries.drop(1).map { summary =>
        contentAsString(result) should include(summary.name)
      }
      summaries.take(1).map { summary =>
        contentAsString(result) should not include(summary.name)
      }
    }
  }
}

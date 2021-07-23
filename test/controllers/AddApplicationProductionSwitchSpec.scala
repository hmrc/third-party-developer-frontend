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
import builder.ApplicationBuilder
import scala.concurrent.Future
import play.api.mvc.Result
import mocks.connector.ApmConnectorMockModule

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

  trait Setup extends UpliftDataServiceMock with AppsByTeamMemberServiceMock with ApplicationServiceMock with ApmConnectorMockModule with ApplicationActionServiceMock with SessionServiceMock with EmailPreferencesServiceMock {
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
      ApmConnectorMock.aMock,
      sessionServiceMock,
      mock[AuditService],
      upliftDataServiceMock,
      mcc,
      cookieSigner,
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

    def shouldShowWhichAppMessage()(implicit results: Future[Result]) = {
      contentAsString(results) should include("Which application do you want production credentials for?")
    }

    def shouldShowAppNamesFor(summaries: Seq[SandboxApplicationSummary])(implicit results: Future[Result]) = {
      summaries.map { summary =>
        contentAsString(results) should include(summary.name)
      }
    }

    def shouldNotShowAppNamesFor(summaries: Seq[SandboxApplicationSummary])(implicit results: Future[Result]) = {
      summaries.map { summary =>
        contentAsString(results) should not include(summary.name)
      }
    }

    def shouldShowMessageAboutNotNeedingProdCreds()(implicit results: Future[Result]) = {
      contentAsString(results) should include("You do not need production credentials")
    }
    def shouldNotShowMessageAboutNotNeedingProdCreds()(implicit results: Future[Result]) = {
      contentAsString(results) should not include("You do not need production credentials")
    }
  }

  "addApplicationProductionSwitch" should {
    "return bad request when no apps are upliftable" in new Setup {
      getUpliftDataReturns(Nil, false)

      val result = underTest.addApplicationProductionSwitch()(loggedInRequest)

      status(result) shouldBe BAD_REQUEST
    }
    
    "go to next stage in journey when one app is upliftable and no other apps are present" in new Setup {
      val summaries = sandboxAppSummaries.take(1)
      getUpliftDataReturns(summaries, false)

      val prodAppId = ApplicationId.random
      ApmConnectorMock.UpliftApplication.willReturn(prodAppId)

      val result = underTest.addApplicationProductionSwitch()(loggedInRequest)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).value shouldBe controllers.checkpages.routes.ApplicationCheck.requestCheckPage(prodAppId).toString()
    }
    
    "return ok when all apps are upliftable" in new Setup {
      val summaries = sandboxAppSummaries
      getUpliftDataReturns(summaries, false)
      identifyUpliftableSandboxAppIdsReturns(summaries.map(_.id).toSet)
      
      implicit val result = underTest.addApplicationProductionSwitch()(loggedInRequest)

      status(result) shouldBe OK

      shouldShowWhichAppMessage()
      shouldShowAppNamesFor(summaries)
      shouldNotShowMessageAboutNotNeedingProdCreds()
    }
    
    "return ok when one app is upliftable out of 5" in new Setup {
      val summaries = sandboxAppSummaries
      val upliftable = summaries.take(1)
      val notUpliftable = summaries.drop(1)

      getUpliftDataReturns(upliftable, true)
      
      implicit val result = underTest.addApplicationProductionSwitch()(loggedInRequest)

      status(result) shouldBe OK

      shouldShowWhichAppMessage()
      shouldShowAppNamesFor(upliftable)
      shouldNotShowAppNamesFor(notUpliftable)
      shouldShowMessageAboutNotNeedingProdCreds()
    }

    "return ok when some apps are upliftable" in new Setup {
      val summaries = sandboxAppSummaries
      val upliftable = summaries.drop(1)
      val notUpliftable = summaries.take(1)

      getUpliftDataReturns(upliftable,false)
      
      implicit val result = underTest.addApplicationProductionSwitch()(loggedInRequest)

      status(result) shouldBe OK

      shouldShowWhichAppMessage()
      shouldShowAppNamesFor(upliftable)
      shouldNotShowAppNamesFor(notUpliftable)
      // TODO - work out why
      // shouldShowMessageAboutNotNeedingProdCreds()
    }
    "return ok when some apps are upliftable after showing fluff" in new Setup {
      val summaries = sandboxAppSummaries
      val upliftable = summaries.drop(1)
      val notUpliftable = summaries.take(1)

      getUpliftDataReturns(upliftable,true)
      
      implicit val result = underTest.addApplicationProductionSwitch()(loggedInRequest)

      status(result) shouldBe OK

      shouldShowMessageAboutNotNeedingProdCreds()
    }
  }
}

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

import java.time.temporal.ChronoUnit.DAYS
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import views.helper.EnvironmentNameService
import views.html._

import play.api.mvc.Result
import play.api.test.Helpers._
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.{ApplicationName, ApplicationWithCollaboratorsFixtures}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.ApplicationId
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.UserSessionId
import uk.gov.hmrc.apiplatform.modules.uplift.domain.models.GetProductionCredentialsFlow
import uk.gov.hmrc.apiplatform.modules.uplift.services.GetProductionCredentialsFlowService
import uk.gov.hmrc.apiplatform.modules.uplift.services.mocks.UpliftLogicMock
import uk.gov.hmrc.apiplatform.modules.uplift.views.html.BeforeYouStartView
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ErrorHandler
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.addapplication.AddApplication
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers.ApplicationSummary
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service._
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.AuditService
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils._

class AddApplicationProductionSwitchSpec
    extends BaseControllerSpec
    with SubscriptionTestSugar
    with WithCSRFAddToken
    with ApplicationWithCollaboratorsFixtures
    with FixedClock {

  val appCreatedOn  = instant.minus(1, DAYS)
  val appLastAccess = appCreatedOn

  val sandboxAppSummaries = (1 to 5).map { i =>
    standardApp
      .inSandbox()
      .withId(ApplicationId.random)
      .withName(ApplicationName(s"App $i"))
  }
    .map(ApplicationSummary.from(_, devUser.userId)).toList

  trait Setup
      extends UpliftLogicMock
      with AppsByTeamMemberServiceMock
      with ApplicationServiceMock
      with ApplicationActionServiceMock
      with EmailPreferencesServiceMock {
    val accessTokenSwitchView                     = app.injector.instanceOf[AccessTokenSwitchView]
    val usingPrivilegedApplicationCredentialsView = app.injector.instanceOf[UsingPrivilegedApplicationCredentialsView]
    val addApplicationStartSubordinateView        = app.injector.instanceOf[AddApplicationStartSubordinateView]
    val addApplicationSubordinateSuccessView      = app.injector.instanceOf[AddApplicationSubordinateSuccessView]
    val addApplicationNameView                    = app.injector.instanceOf[AddApplicationNameView]
    val chooseApplicationToUpliftView             = app.injector.instanceOf[ChooseApplicationToUpliftView]

    val beforeYouStartView: BeforeYouStartView = app.injector.instanceOf[BeforeYouStartView]

    val flowServiceMock = mock[GetProductionCredentialsFlowService]

    implicit val environmentNameService: EnvironmentNameService = new EnvironmentNameService(appConfig)

    val underTest = new AddApplication(
      mock[ErrorHandler],
      applicationServiceMock,
      applicationActionServiceMock,
      emailPreferencesServiceMock,
      sessionServiceMock,
      mock[AuditService],
      upliftLogicMock,
      mcc,
      cookieSigner,
      accessTokenSwitchView,
      usingPrivilegedApplicationCredentialsView,
      addApplicationStartSubordinateView,
      addApplicationSubordinateSuccessView,
      addApplicationNameView,
      chooseApplicationToUpliftView,
      beforeYouStartView,
      flowServiceMock
    )
    val hc        = HeaderCarrier()

    when(appConfig.nameOfPrincipalEnvironment).thenReturn("Production")
    when(appConfig.nameOfSubordinateEnvironment).thenReturn("Sandbox")

    def shouldShowWhichAppMessage()(implicit results: Future[Result]) = {
      withClue("shouldShowWhichAppMessage")(contentAsString(results) should include("Which application do you want production credentials for?"))
    }

    def shouldShowAppNamesFor(summaries: Seq[ApplicationSummary])(implicit results: Future[Result]) = {
      summaries.map { summary =>
        withClue(s"shouldShowAppNamesFor[${summary.name.value}]")(contentAsString(results) should include(summary.name.value))
      }
    }

    def shouldNotShowAppNamesFor(summaries: Seq[ApplicationSummary])(implicit results: Future[Result]) = {
      summaries.map { summary =>
        withClue(s"shouldNotShowAppNamesFor: ${summary.name.value}")(contentAsString(results) should not include (summary.name.value))
      }
    }

    def shouldShowMessageAboutNotNeedingProdCreds()(implicit results: Future[Result]) = {
      withClue("shouldShowMessageAboutNotNeedingProdCreds")(contentAsString(results) should include("You do not need production credentials"))
    }

    def shouldNotShowMessageAboutNotNeedingProdCreds()(implicit results: Future[Result]) = {
      withClue("shouldNotShowMessageAboutNotNeedingProdCreds")(contentAsString(results) should not include ("You do not need production credentials"))
    }
  }

  "addApplicationProductionSwitch" should {
    "return bad request when no apps are upliftable" in new Setup {
      aUsersUplfitableAndNotUpliftableAppsReturns(Nil, List.empty, List.empty)
      when(flowServiceMock.resetFlow(*)).thenReturn(Future.successful(GetProductionCredentialsFlow(UserSessionId.random, None, None)))

      val result = underTest.addApplicationProductionSwitch()(loggedInDevRequest)

      status(result) shouldBe BAD_REQUEST
    }

    "go to next stage in journey when one app is upliftable and no other apps are present" in new Setup {
      val summaries = sandboxAppSummaries.take(1)
      aUsersUplfitableAndNotUpliftableAppsReturns(summaries, summaries.map(_.id), List.empty)
      when(flowServiceMock.storeApiSubscriptions(*, *)).thenReturn(Future.successful(GetProductionCredentialsFlow(UserSessionId.random, None, None)))
      when(flowServiceMock.resetFlow(*)).thenReturn(Future.successful(GetProductionCredentialsFlow(UserSessionId.random, None, None)))

      val result = underTest.addApplicationProductionSwitch()(loggedInDevRequest)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).value shouldBe uk.gov.hmrc.apiplatform.modules.uplift.controllers.routes.UpliftJourneyController.beforeYouStart(
        summaries.head.id
      ).toString
    }

    "return ok when all apps are upliftable" in new Setup {
      val summaries = sandboxAppSummaries
      aUsersUplfitableAndNotUpliftableAppsReturns(summaries, summaries.map(_.id), List.empty)

      when(flowServiceMock.resetFlow(*)).thenReturn(Future.successful(GetProductionCredentialsFlow(UserSessionId.random, None, None)))
      implicit val result: Future[Result] = underTest.addApplicationProductionSwitch()(loggedInDevRequest.withCSRFToken)

      status(result) shouldBe OK

      shouldShowWhichAppMessage()
      shouldShowAppNamesFor(summaries)
      shouldNotShowMessageAboutNotNeedingProdCreds()
    }

    "return ok when some apps are upliftable" in new Setup {
      val summaries     = sandboxAppSummaries
      val upliftable    = summaries.drop(1)
      val notUpliftable = summaries.take(1)

      aUsersUplfitableAndNotUpliftableAppsReturns(summaries, upliftable.map(_.id), notUpliftable.map(_.id))

      when(flowServiceMock.resetFlow(*)).thenReturn(Future.successful(GetProductionCredentialsFlow(UserSessionId.random, None, None)))
      implicit val result: Future[Result] = underTest.addApplicationProductionSwitch()(loggedInDevRequest.withCSRFToken)

      status(result) shouldBe OK

      shouldShowWhichAppMessage()
      shouldShowAppNamesFor(upliftable)
      shouldNotShowAppNamesFor(notUpliftable)
      // TODO - work out why
      // shouldShowMessageAboutNotNeedingProdCreds()
    }

    "return ok when some apps are upliftable after showing fluff" in new Setup {
      val summaries     = sandboxAppSummaries
      val upliftable    = summaries.drop(1)
      val notUpliftable = summaries.take(1)

      aUsersUplfitableAndNotUpliftableAppsReturns(summaries, upliftable.map(_.id), notUpliftable.map(_.id))
      when(flowServiceMock.resetFlow(*)).thenReturn(Future.successful(GetProductionCredentialsFlow(UserSessionId.random, None, None)))

      implicit val result: Future[Result] = underTest.addApplicationProductionSwitch()(loggedInDevRequest.withCSRFToken)

      status(result) shouldBe OK

      shouldShowMessageAboutNotNeedingProdCreds()
    }
  }
}

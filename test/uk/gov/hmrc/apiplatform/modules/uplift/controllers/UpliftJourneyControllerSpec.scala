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

package uk.gov.hmrc.apiplatform.modules.uplift.controllers

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import org.jsoup.Jsoup
import views.html.checkpages.applicationcheck.UnauthorisedAppDetailsView

import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.submissions.SubmissionsTestData
import uk.gov.hmrc.apiplatform.modules.submissions.services.mocks.SubmissionServiceMockModule
import uk.gov.hmrc.apiplatform.modules.uplift.domain.models._
import uk.gov.hmrc.apiplatform.modules.uplift.services.mocks._
import uk.gov.hmrc.apiplatform.modules.uplift.views.html._
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder._
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.{On, UpliftJourneyConfig}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.{BaseControllerSpec, SubscriptionTestHelperSugar}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.{ApplicationId, ApplicationState, ApplicationWithSubscriptionData, SellResellOrDistribute, Environment}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.{DeveloperSession, LoggedInState, Session}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.{ApiCategory, ApiData, VersionData}
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.connectors.ApmConnectorMockModule
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service.{ApplicationActionServiceMock, ApplicationServiceMock, SessionServiceMock, TermsOfUseInvitationServiceMockModule}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithLoggedInSession._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{LocalUserIdTracker, WithCSRFAddToken}

class UpliftJourneyControllerSpec extends BaseControllerSpec
    with SampleSession
    with SampleApplication
    with SubscriptionTestHelperSugar
    with SubmissionsTestData
    with WithCSRFAddToken
    with SubscriptionsBuilder
    with DeveloperBuilder
    with LocalUserIdTracker {

  trait Setup
      extends ApplicationServiceMock
      with SubmissionServiceMockModule
      with TermsOfUseInvitationServiceMockModule
      with ApplicationActionServiceMock
      with ApmConnectorMockModule
      with GetProductionCredentialsFlowServiceMockModule
      with UpliftJourneyServiceMockModule
      with SessionServiceMock {

    def titleOf(result: Future[Result]) = {
      val titleRegEx = """<title[^>]*>(.*)</title>""".r
      val title      = titleRegEx.findFirstMatchIn(contentAsString(result)).map(_.group(1))
      title.isDefined shouldBe true
      title.get
    }

    implicit val hc = HeaderCarrier()

    val confirmApisView                    = app.injector.instanceOf[ConfirmApisView]
    val turnOffApisMasterView              = app.injector.instanceOf[TurnOffApisMasterView]
    val sellResellOrDistributeSoftwareView = app.injector.instanceOf[SellResellOrDistributeSoftwareView]
    val weWillCheckYourAnswersView         = app.injector.instanceOf[WeWillCheckYourAnswersView]
    val beforeYouStartView                 = app.injector.instanceOf[BeforeYouStartView]
    val unauthorisedAppDetailsView         = app.injector.instanceOf[UnauthorisedAppDetailsView]

    val mockUpliftJourneyConfig     = mock[UpliftJourneyConfig]
    val sr20UpliftJourneySwitchMock = new UpliftJourneySwitch(mockUpliftJourneyConfig)

    val controller = new UpliftJourneyController(
      mockErrorHandler,
      sessionServiceMock,
      applicationActionServiceMock,
      applicationServiceMock,
      SubmissionServiceMock.aMock,
      TermsOfUseInvitationServiceMock.aMock,
      UpliftJourneyServiceMock.aMock,
      mcc,
      cookieSigner,
      confirmApisView,
      turnOffApisMasterView,
      ApmConnectorMock.aMock,
      GPCFlowServiceMock.aMock,
      sellResellOrDistributeSoftwareView,
      weWillCheckYourAnswersView,
      beforeYouStartView,
      unauthorisedAppDetailsView,
      sr20UpliftJourneySwitchMock
    )

    val appName: String = "app"
    val apiVersion      = ApiVersion("version")

    val developer = buildDeveloper()
    val sessionId = "sessionId"
    val session   = Session(sessionId, developer, LoggedInState.LOGGED_IN)

    val loggedInDeveloper = DeveloperSession(session)
    val testingApp        = sampleApp.copy(state = ApplicationState.testing, deployedTo = Environment.SANDBOX)

    fetchSessionByIdReturns(sessionId, session)
    updateUserFlowSessionsReturnsSuccessfully(sessionId)

    val sessionParams    = Seq("csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken)
    val loggedOutRequest = FakeRequest().withSession(sessionParams: _*)
    val loggedInRequest  = FakeRequest().withLoggedIn(controller, implicitly)(sessionId).withSession(sessionParams: _*)

    val apiIdentifier1 = ApiIdentifier(ApiContext("test-api-context-1"), ApiVersion("1.0"))
    val apiIdentifier2 = ApiIdentifier(ApiContext("test-api-context-2"), ApiVersion("1.0"))

    val emptyFields = emptySubscriptionFieldsWrapper(appId, clientId, apiIdentifier1.context, apiIdentifier1.version)

    val testAPISubscriptionStatus1 = APISubscriptionStatus(
      "test-api-1",
      "api-example-microservice",
      apiIdentifier1.context,
      ApiVersionDefinition(apiIdentifier1.version, APIStatus.STABLE),
      subscribed = true,
      requiresTrust = false,
      fields = emptyFields
    )

    val testAPISubscriptionStatus2 = APISubscriptionStatus(
      "test-api-2",
      "api-example-microservice",
      apiIdentifier2.context,
      ApiVersionDefinition(apiIdentifier2.version, APIStatus.STABLE),
      subscribed = true,
      requiresTrust = false,
      fields = emptyFields
    )

    val singleApi: Map[ApiContext, ApiData] = Map(
      ApiContext("test-api-context-1") ->
        ApiData(
          "test-api-context-1",
          "test-api-context-1",
          true,
          Map(ApiVersion("1.0") ->
            VersionData(APIStatus.STABLE, APIAccess(APIAccessType.PUBLIC))),
          List(ApiCategory.EXAMPLE)
        )
    )

    val multipleApis: Map[ApiContext, ApiData] = Map(
      ApiContext("test-api-context-1") ->
        ApiData(
          "test-api-context-1",
          "test-api-context-1",
          true,
          Map(ApiVersion("1.0") ->
            VersionData(APIStatus.STABLE, APIAccess(APIAccessType.PUBLIC))),
          List(ApiCategory.EXAMPLE)
        ),
      ApiContext("test-api-context-2") ->
        ApiData(
          "test-api-context-2",
          "test-api-context-2",
          true,
          Map(ApiVersion("1.0") ->
            VersionData(APIStatus.STABLE, APIAccess(APIAccessType.PUBLIC))),
          List(ApiCategory.EXAMPLE)
        )
    )

    fetchByApplicationIdReturns(appId, testingApp)
    givenApplicationAction(
      ApplicationWithSubscriptionData(testingApp, asSubscriptions(List(testAPISubscriptionStatus1)), asFields(List.empty)),
      loggedInDeveloper,
      List(testAPISubscriptionStatus1)
    )

    ApmConnectorMock.FetchAllApis.willReturn(singleApi)
  }

  "confirmApiSubscription" should {

    "render the confirm apis view containing 1 upliftable api as there is only 1 upliftable api available to the application" in new Setup {

      UpliftJourneyServiceMock.ApiSubscriptionData.thenReturns(Set("test-api-1 - 1.0"), false)

      private val result = controller.confirmApiSubscriptionsPage(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK

      contentAsString(result) should include("test-api-1 - 1.0")
    }

    "render the confirm apis view without the 'Change my API subscriptions' link as there is only 1 upliftable api available to the application" in new Setup {

      UpliftJourneyServiceMock.ApiSubscriptionData.thenReturns(Set("test-api-1 - 1.0"), false)

      private val result = controller.confirmApiSubscriptionsPage(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK

      contentAsString(result) should not include ("Change my API subscriptions")
    }

    "render the confirm apis view with the 'Change my API subscriptions' link as there is more than 1 upliftable api available to the application" in new Setup {

      UpliftJourneyServiceMock.ApiSubscriptionData.thenReturns(Set("test-api-context-1", "test-api-context-2"), true)

      private val result = controller.confirmApiSubscriptionsPage(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK

      contentAsString(result) should include("Change my API subscriptions")
    }

    "The selected apis are saved on the 'Turn off API subscriptions you don’t need' view and 'save and continue' clicked" in new Setup {

      val apiIdentifiers = Set(
        ApiIdentifier(ApiContext("test-api-context-1"), ApiVersion("1.0"))
      )

      UpliftJourneyServiceMock.ConfirmAndUplift.thenReturns(appId)

      ApmConnectorMock.FetchUpliftableSubscriptions.willReturn(apiIdentifiers)
      GPCFlowServiceMock.StoreApiSubscriptions.thenReturns(GetProductionCredentialsFlow("", None, None))

      private val result = controller.saveApiSubscriptionsSubmit(appId)(loggedInRequest.withCSRFToken.withFormUrlEncodedBody(
        "test_api_context_1-1_0-subscribed" -> "true"
      ))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/applications/${appId.value}/confirm-subscriptions")
    }

    "An error screen is shown when all APIs are turned off on the 'Turn off API subscriptions you don’t need' view and 'save and continue' clicked" in new Setup {

      ApmConnectorMock.FetchUpliftableSubscriptions.willReturn(Set(apiIdentifier1))

      val testFlow = ApiSubscriptions(Map(apiIdentifier1 -> true))
      GPCFlowServiceMock.FetchFlow.thenReturns(GetProductionCredentialsFlow("", None, Some(testFlow)))
      GPCFlowServiceMock.StoreApiSubscriptions.thenReturns(GetProductionCredentialsFlow("", None, None))

      private val result = controller.saveApiSubscriptionsSubmit(appId)(loggedInRequest.withCSRFToken.withFormUrlEncodedBody(
        "test_api_context_1-1_0-subscribed" -> "false"
      ))

      status(result) shouldBe OK
      contentAsString(result) should include("You need at least 1 API subscription")
    }

    "The selected apis are saved when 'save and continue' clicked" in new Setup {
      when(mockUpliftJourneyConfig.status).thenReturn(On)
      UpliftJourneyServiceMock.ConfirmAndUplift.thenReturns(appId)

      private val result = controller.confirmApiSubscriptionsAction(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/submissions/application/${appId.value}/production-credentials-checklist")
    }

    "The selected apis are not save when 'save and continue' clicked but uplift fails" in new Setup {

      UpliftJourneyServiceMock.ConfirmAndUplift.thenLeft("bang")

      private val result = controller.confirmApiSubscriptionsAction(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe BAD_REQUEST
    }
  }

  "changeApiSubscriptions" should {

    "initially render the 'change api subscrptions view'" in new Setup {
      UpliftJourneyServiceMock.ChangeApiSubscriptions.thenReturns(sampleSubscriptions)

      private val result = controller.changeApiSubscriptions(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK

      contentAsString(result) should include("Turn off API subscriptions you don’t need")
    }

  }

  "sellResellOrDistributeYourSoftware" should {

    "initially render the 'sell resell or distribute your software view' with choices unselected" in new Setup {
      GPCFlowServiceMock.FindSellResellOrDistribute.thenReturnsNone

      private val result = controller.sellResellOrDistributeYourSoftware(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK

      titleOf(result) shouldBe "Will you sell, resell or distribute your software? - HMRC Developer Hub - GOV.UK"

      contentAsString(result) should include("Will you sell, resell or distribute your software?")
    }

    "render the 'sell resell or distribute your software view' with the answer 'Yes' selected" in new Setup {
      GPCFlowServiceMock.FindSellResellOrDistribute.thenReturnsYes

      private val result = controller.sellResellOrDistributeYourSoftware(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK

      contentAsString(result) should include("Will you sell, resell or distribute your software?")

      val document = Jsoup.parse(contentAsString(result))
      document.getElementById("answer") shouldNot be(null)
      document.getElementById("answer").hasAttr("checked") shouldBe true
    }

    "render the 'sell resell or distribute your software view' with the answer 'No' selected" in new Setup {
      GPCFlowServiceMock.FindSellResellOrDistribute.thenReturnsNo

      private val result = controller.sellResellOrDistributeYourSoftware(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK

      contentAsString(result) should include("Will you sell, resell or distribute your software?")

      val document = Jsoup.parse(contentAsString(result))
      document.getElementById("distribute-question-no") shouldNot be(null)
      document.getElementById("distribute-question-no").hasAttr("checked") shouldBe true
    }

    "render the 'sell resell or distribute your software view' with an error when no selection has been made" in new Setup {

      GPCFlowServiceMock.FindSellResellOrDistribute.thenReturnsNone()

      private val result = controller.sellResellOrDistributeYourSoftwareAction(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe BAD_REQUEST

      titleOf(result) shouldBe "Error: Will you sell, resell or distribute your software? - HMRC Developer Hub - GOV.UK"

      contentAsString(result) should include("Will you sell, resell or distribute your software?")
      contentAsString(result) should include("Select yes if you sell, resell or distribute your software")
    }

    "store the answer 'Yes' from the 'sell resell or distribute your software view' and redirect to next page" in new Setup {

      val testSellResellOrDistribute = SellResellOrDistribute("Yes")

      GPCFlowServiceMock.StoreSellResellOrDistribute.thenReturns(testSellResellOrDistribute, GetProductionCredentialsFlow("", Some(testSellResellOrDistribute), None))
      UpliftJourneyServiceMock.StoreDefaultSubscriptionsInFlow.thenReturns()

      private val result = controller.sellResellOrDistributeYourSoftwareAction(appId)(loggedInRequest.withCSRFToken.withFormUrlEncodedBody(
        "answer" -> testSellResellOrDistribute.answer
      ))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/applications/myAppId/confirm-subscriptions")
    }

    "store the answer 'No' from the 'sell resell or distribute your software view' and redirect to next page" in new Setup {

      val testSellResellOrDistribute = SellResellOrDistribute("No")

      GPCFlowServiceMock.StoreSellResellOrDistribute.thenReturns(testSellResellOrDistribute, GetProductionCredentialsFlow("", Some(testSellResellOrDistribute), None))
      UpliftJourneyServiceMock.StoreDefaultSubscriptionsInFlow.thenReturns()

      private val result = controller.sellResellOrDistributeYourSoftwareAction(appId)(loggedInRequest.withCSRFToken.withFormUrlEncodedBody(
        "answer" -> testSellResellOrDistribute.answer
      ))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/applications/myAppId/confirm-subscriptions")
    }

    "store the answer 'Yes' from the 'sell resell or distribute your software view' and redirect to questionnaire when application is Production" in new Setup {

      val testSellResellOrDistribute = SellResellOrDistribute("Yes")
      val prodAppId                  = ApplicationId.random
      val prodApp                    = sampleApp.copy(id = prodAppId)
      fetchByApplicationIdReturns(prodAppId, prodApp)
      givenApplicationAction(
        ApplicationWithSubscriptionData(prodApp, asSubscriptions(List(testAPISubscriptionStatus1)), asFields(List.empty)),
        loggedInDeveloper,
        List(testAPISubscriptionStatus1)
      )
      UpliftJourneyServiceMock.CreateNewSubmission.thenReturns(aSubmission)
      GPCFlowServiceMock.StoreSellResellOrDistribute.thenReturns(testSellResellOrDistribute, GetProductionCredentialsFlow("", Some(testSellResellOrDistribute), None))
      UpliftJourneyServiceMock.StoreDefaultSubscriptionsInFlow.thenReturns()

      private val result = controller.sellResellOrDistributeYourSoftwareAction(prodAppId)(loggedInRequest.withCSRFToken.withFormUrlEncodedBody(
        "answer" -> testSellResellOrDistribute.answer
      ))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/submissions/application/${prodAppId.value}/production-credentials-checklist")
    }
  }

  "weWillCheckYourAnswers" should {

    "render the 'we will check your answers' page" in new Setup {
      private val result = controller.weWillCheckYourAnswers(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK

      titleOf(result) shouldBe "We will check your answers - HMRC Developer Hub - GOV.UK"

      contentAsString(result) should include("We will check your answers")
    }
  }

  "beforeYouStart" should {

    "render the 'before you start' page" in new Setup {
      private val result = controller.beforeYouStart(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK

      titleOf(result) shouldBe "Before you start - HMRC Developer Hub - GOV.UK"

      contentAsString(result) should include("Before you start")
      contentAsString(result) should include("You have 6 months to complete your request")
    }
  }

  "agreeNewTermsOfUse" should {

    "render the before you start page if no existing submission found" in new Setup {

      TermsOfUseInvitationServiceMock.FetchTermsOfUseInvitation.thenReturn
      SubmissionServiceMock.FetchLatestSubmission.thenReturnsNone

      private val result = controller.agreeNewTermsOfUse(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK

      titleOf(result) shouldBe "Before you start - HMRC Developer Hub - GOV.UK"

      contentAsString(result) should include("Before you start")
    }

    "render the before you start page if an existing submission found" in new Setup {

      TermsOfUseInvitationServiceMock.FetchTermsOfUseInvitation.thenReturn
      SubmissionServiceMock.FetchLatestSubmission.thenReturns(aSubmission)

      private val result = controller.agreeNewTermsOfUse(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/submissions/application/${appId.value}/production-credentials-checklist")
    }

    "return bad request if not invited" in new Setup {

      TermsOfUseInvitationServiceMock.FetchTermsOfUseInvitation.thenReturnNone

      private val result = controller.agreeNewTermsOfUse(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe BAD_REQUEST
      contentAsString(result) should include("This application has not been invited to complete the new terms of use")
    }
  }
}

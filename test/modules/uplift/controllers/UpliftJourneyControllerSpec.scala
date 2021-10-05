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

package modules.uplift.controllers

import builder._
import controllers.{BaseControllerSpec, SubscriptionTestHelperSugar}
import domain.models.apidefinitions._
import domain.models.applications.ApplicationWithSubscriptionData
import modules.uplift.domain.models._
import domain.models.developers.{DeveloperSession, LoggedInState, Session}
import domain.models.subscriptions.{ApiCategory, ApiData, VersionData}
import mocks.connector.ApmConnectorMockModule
import mocks.service.{ApplicationActionServiceMock, ApplicationServiceMock, SessionServiceMock}
import org.jsoup.Jsoup
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider
import uk.gov.hmrc.http.HeaderCarrier
import utils.WithLoggedInSession._
import utils.{LocalUserIdTracker, WithCSRFAddToken}
import views.html.upliftJourney.{ConfirmApisView, ProductionCredentialsChecklistView, ResponsibleIndividualView, SellResellOrDistributeSoftwareView, TurnOffApisMasterView}

import scala.concurrent.ExecutionContext.Implicits.global
import modules.uplift.services._
import controllers.SubscriptionTestHelperSugar


class UpliftJourneyControllerSpec extends BaseControllerSpec
                with SampleSession
                with SampleApplication
                with SubscriptionTestHelperSugar
                with WithCSRFAddToken
                with SubscriptionsBuilder
                with DeveloperBuilder
                with LocalUserIdTracker {

  trait Setup
    extends ApplicationServiceMock 
    with ApplicationActionServiceMock 
    with ApmConnectorMockModule 
    with GetProductionCredentialsFlowServiceMockModule
    with UpliftJourneyServiceMockModule
    with SessionServiceMock {

    implicit val hc = HeaderCarrier()

    val confirmApisView = app.injector.instanceOf[ConfirmApisView]
    val turnOffApisMasterView = app.injector.instanceOf[TurnOffApisMasterView]
    val responsibleIndividualView = app.injector.instanceOf[ResponsibleIndividualView]
    val sellResellOrDistributeSoftwareView = app.injector.instanceOf[SellResellOrDistributeSoftwareView]
    val productionCredentialsChecklistView = app.injector.instanceOf[ProductionCredentialsChecklistView]

    val controller = new UpliftJourneyController(
      mockErrorHandler,
      sessionServiceMock,
      applicationActionServiceMock,
      applicationServiceMock,
      UpliftJourneyServiceMock.aMock,
      mcc,
      cookieSigner,
      confirmApisView,
      turnOffApisMasterView,
      ApmConnectorMock.aMock,
      responsibleIndividualView,
      GPCFlowServiceMock.aMock,
      sellResellOrDistributeSoftwareView,
      productionCredentialsChecklistView
    )

    val appName: String = "app"
    val apiVersion = ApiVersion("version")

    val developer = buildDeveloper()
    val sessionId = "sessionId"
    val session = Session(sessionId, developer, LoggedInState.LOGGED_IN)

    val loggedInDeveloper = DeveloperSession(session)

    fetchSessionByIdReturns(sessionId, session)
    updateUserFlowSessionsReturnsSuccessfully(sessionId)

    val sessionParams = Seq("csrfToken" -> app.injector.instanceOf[TokenProvider].generateToken)
    val loggedOutRequest = FakeRequest().withSession(sessionParams: _*)
    val loggedInRequest = FakeRequest().withLoggedIn(controller, implicitly)(sessionId).withSession(sessionParams: _*)

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

    val singleApi: Map[ApiContext,ApiData] = Map(
      ApiContext("test-api-context-1") ->
        ApiData("test-api-context-1", "test-api-context-1", true, Map(ApiVersion("1.0") ->
          VersionData(APIStatus.STABLE, APIAccess(APIAccessType.PUBLIC))), List(ApiCategory.EXAMPLE))
    )

    val multipleApis: Map[ApiContext,ApiData] = Map(
      ApiContext("test-api-context-1") ->
        ApiData("test-api-context-1", "test-api-context-1", true, Map(ApiVersion("1.0") ->
          VersionData(APIStatus.STABLE, APIAccess(APIAccessType.PUBLIC))), List(ApiCategory.EXAMPLE)),
      ApiContext("test-api-context-2") ->
        ApiData("test-api-context-2", "test-api-context-2", true, Map(ApiVersion("1.0") ->
          VersionData(APIStatus.STABLE, APIAccess(APIAccessType.PUBLIC))), List(ApiCategory.EXAMPLE))
    )

    fetchByApplicationIdReturns(appId, sampleApp)
    givenApplicationAction(ApplicationWithSubscriptionData(sampleApp,
      asSubscriptions(List(testAPISubscriptionStatus1)),
      asFields(List.empty)),
      loggedInDeveloper,
      List(testAPISubscriptionStatus1))

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

      contentAsString(result) should not include("Change my API subscriptions")
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
      GPCFlowServiceMock.StoreApiSubscriptions.thenReturns(GetProductionCredentialsFlow("", None, None, None))

      private val result = controller.saveApiSubscriptionsSubmit(appId)(loggedInRequest.withCSRFToken.withFormUrlEncodedBody(
        "test_api_context_1-1_0-subscribed" -> "true")
      )

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/applications/${appId.value}/confirm-subscriptions")
    }

    "An error screen is shown when all APIs are turned off on the 'Turn off API subscriptions you don’t need' view and 'save and continue' clicked" in new Setup {

      ApmConnectorMock.FetchUpliftableSubscriptions.willReturn(Set(apiIdentifier1))

      val testFlow = ApiSubscriptions(Map(apiIdentifier1 -> true))
      GPCFlowServiceMock.FetchFlow.thenReturns(GetProductionCredentialsFlow("", None, None, Some(testFlow)))
      GPCFlowServiceMock.StoreApiSubscriptions.thenReturns(GetProductionCredentialsFlow("", None, None, None))

      private val result = controller.saveApiSubscriptionsSubmit(appId)(loggedInRequest.withCSRFToken.withFormUrlEncodedBody(
        "test_api_context_1-1_0-subscribed" -> "false")
      )

      status(result) shouldBe OK
      contentAsString(result) should include("You need at least 1 API subscription")
    }

    "The selected apis are saved when 'save and continue' clicked" in new Setup {

      UpliftJourneyServiceMock.ConfirmAndUplift.thenReturns(appId)

      private val result = controller.confirmApiSubscriptionsAction(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/applications/${appId.value}/production-credentials-checklist")
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

  "responsibleIndividual" should {

    "initially render the 'responsible individual view' unpopulated" in new Setup {

      UpliftJourneyServiceMock.ResponsibleIndividual.thenReturnsNone()

      private val result = controller.responsibleIndividual(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK

      contentAsString(result) should include("Provide details for a responsible individual in your organisation")
      contentAsString(result) shouldNot include("test full name")
      contentAsString(result) shouldNot include("test email address")
    }

    "render the 'responsible individual view' populated with a responsible individual" in new Setup {

      UpliftJourneyServiceMock.ResponsibleIndividual.thenReturns(ResponsibleIndividual("test full name", "test email address"))

      private val result = controller.responsibleIndividual(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK

      contentAsString(result) should include("Provide details for a responsible individual in your organisation")
      contentAsString(result) should include("test full name")
      contentAsString(result) should include("test email address")
    }

    "render the 'responsible individual view' with errors when fullName and emailAddress are missing" in new Setup {

      UpliftJourneyServiceMock.ResponsibleIndividual.thenReturnsNone()

      private val result = controller.responsibleIndividualAction(appId)(loggedInRequest.withCSRFToken.withFormUrlEncodedBody(
        "fullName" -> "",
        "emailAddress" -> ""
      ))

      status(result) shouldBe BAD_REQUEST

      contentAsString(result) should include("Provide details for a responsible individual in your organisation")
      contentAsString(result) should include("Provide a full name")
      contentAsString(result) should include("Provide an email address")
    }

    "render the 'responsible individual view' with errors when fullName is missing" in new Setup {

      UpliftJourneyServiceMock.ResponsibleIndividual.thenReturnsNone()

      private val result = controller.responsibleIndividualAction(appId)(loggedInRequest.withCSRFToken.withFormUrlEncodedBody(
        "fullName" -> "",
        "emailAddress" -> "test.user@example.com"
      ))

      status(result) shouldBe BAD_REQUEST

      contentAsString(result) should include("Provide details for a responsible individual in your organisation")
      contentAsString(result) should include("Provide a full name")
      contentAsString(result) shouldNot include("Provide an email address")
    }

    "render the 'responsible individual view' with errors when emailAddress is missing" in new Setup {

      ApmConnectorMock.FetchUpliftableSubscriptions.willReturn(Set(apiIdentifier1))

      private val result = controller.responsibleIndividualAction(appId)(loggedInRequest.withCSRFToken.withFormUrlEncodedBody(
        "fullName" -> "test user",
        "emailAddress" -> ""
      ))

      status(result) shouldBe BAD_REQUEST

      contentAsString(result) should include("Provide details for a responsible individual in your organisation")
      contentAsString(result) shouldNot include("Provide a full name")
      contentAsString(result) should include("Provide an email address")
    }

    "render the 'responsible individual view' with errors when emailAddress is not valid" in new Setup {

      ApmConnectorMock.FetchUpliftableSubscriptions.willReturn(Set(apiIdentifier1))

      private val result = controller.responsibleIndividualAction(appId)(loggedInRequest.withCSRFToken.withFormUrlEncodedBody(
        "fullName" -> "test user",
        "emailAddress" -> "invalidemailaddress"
      ))

      status(result) shouldBe BAD_REQUEST

      contentAsString(result) should include("Provide details for a responsible individual in your organisation")
      contentAsString(result) shouldNot include("Provide a full name")
      contentAsString(result) should include("Provide a valid email address")
    }

    "store the full name and email address from the 'responsible individual view' and redirect to next page" in new Setup {

      val testResponsibleIndividual = ResponsibleIndividual("test user", "test.user@example.com")
      GPCFlowServiceMock.StoreResponsibleIndividual.thenReturns(testResponsibleIndividual, GetProductionCredentialsFlow("", Some(testResponsibleIndividual), None, None))
      ApmConnectorMock.FetchUpliftableSubscriptions.willReturn(Set(apiIdentifier1))

      private val result = controller.responsibleIndividualAction(appId)(loggedInRequest.withCSRFToken.withFormUrlEncodedBody(
        "fullName" -> testResponsibleIndividual.fullName,
        "emailAddress" -> testResponsibleIndividual.emailAddress
      ))

      status(result) shouldBe SEE_OTHER
    }
  }

  "sellResellOrDistributeYourSoftware" should {

    "initially render the 'sell resell or distribute your software view' with choices unselected" in new Setup {
      UpliftJourneyServiceMock.SellResellOrDistribute.thenReturnsNone()

      private val result = controller.sellResellOrDistributeYourSoftware(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK

      contentAsString(result) should include("Will you sell, resell or distribute your software?")
    }

    "render the 'sell resell or distribute your software view' with the answer 'Yes' selected" in new Setup {
      UpliftJourneyServiceMock.SellResellOrDistribute.thenReturns(SellResellOrDistribute("Yes"))

      private val result = controller.sellResellOrDistributeYourSoftware(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK

      contentAsString(result) should include("Will you sell, resell or distribute your software?")

      val document = Jsoup.parse(contentAsString(result))
      document.getElementById("distribute-question-yes") shouldNot be(null)
      document.getElementById("distribute-question-yes").hasAttr("checked") shouldBe true
    }

    "render the 'sell resell or distribute your software view' with the answer 'No' selected" in new Setup {
      UpliftJourneyServiceMock.SellResellOrDistribute.thenReturns(SellResellOrDistribute("No"))

      private val result = controller.sellResellOrDistributeYourSoftware(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK

      contentAsString(result) should include("Will you sell, resell or distribute your software?")

      val document = Jsoup.parse(contentAsString(result))
      document.getElementById("distribute-question-no") shouldNot be(null)
      document.getElementById("distribute-question-no").hasAttr("checked") shouldBe true
    }

    "render the 'sell resell or distribute your software view' with an error when no selection has been made" in new Setup {

      UpliftJourneyServiceMock.SellResellOrDistribute.thenReturnsNone()

      private val result = controller.sellResellOrDistributeYourSoftwareAction(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe BAD_REQUEST

      contentAsString(result) should include("Will you sell, resell or distribute your software?")
      contentAsString(result) should include("Tell us if you will sell, resell or distribute your software")
    }

    "store the answer 'Yes' from the 'sell resell or distribute your software view' and redirect to next page" in new Setup {

      val testSellResellOrDistribute = SellResellOrDistribute("Yes")

      GPCFlowServiceMock.StoreSellResellOrDistribute.thenReturns(testSellResellOrDistribute,GetProductionCredentialsFlow("", None, Some(testSellResellOrDistribute), None))
      UpliftJourneyServiceMock.StoreDefaultSubscriptionsInFlow.thenReturns()

      private val result = controller.sellResellOrDistributeYourSoftwareAction(appId)(loggedInRequest.withCSRFToken.withFormUrlEncodedBody(
        "answer" -> testSellResellOrDistribute.answer
      ))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/applications/myAppId/confirm-subscriptions")
    }

    "store the answer 'No' from the 'sell resell or distribute your software view' and redirect to next page" in new Setup {

      val testSellResellOrDistribute = SellResellOrDistribute("No")
      
      GPCFlowServiceMock.StoreSellResellOrDistribute.thenReturns(testSellResellOrDistribute,GetProductionCredentialsFlow("", None, Some(testSellResellOrDistribute), None))
      UpliftJourneyServiceMock.StoreDefaultSubscriptionsInFlow.thenReturns()

      private val result = controller.sellResellOrDistributeYourSoftwareAction(appId)(loggedInRequest.withCSRFToken.withFormUrlEncodedBody(
        "answer" -> testSellResellOrDistribute.answer
      ))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/developer/applications/myAppId/confirm-subscriptions")
    }
  }
}

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

import builder._
import domain.models.apidefinitions._
import domain.models.applications.{Application, ApplicationState, ApplicationWithSubscriptionData, Environment}
import domain.models.applicationuplift.{ApiSubscriptions, ResponsibleIndividual, SellResellOrDistribute}
import domain.models.developers.{DeveloperSession, LoggedInState, Session}
import domain.models.subscriptions.{ApiCategory, ApiData, VersionData}
import mocks.connector.ApmConnectorMockModule
import mocks.service.{ApplicationActionServiceMock, ApplicationServiceMock, SessionServiceMock}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF.TokenProvider
import service.{GetProductionCredentialsFlow, GetProductionCredentialsFlowService}
import uk.gov.hmrc.http.HeaderCarrier
import utils.WithLoggedInSession._
import utils.{LocalUserIdTracker, WithCSRFAddToken}
import views.html.upliftJourney.{ConfirmApisView, ResponsibleIndividualView, SellResellOrDistributeSoftwareView, TurnOffApisMasterView}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.jsoup.Jsoup

class SR20Spec extends BaseControllerSpec
                with SampleSession
                with SampleApplication
                with SubscriptionTestHelperSugar
                with WithCSRFAddToken
                with SubscriptionsBuilder
                with DeveloperBuilder
                with LocalUserIdTracker {

  trait Setup extends ApplicationServiceMock with ApplicationActionServiceMock with ApmConnectorMockModule with SessionServiceMock {

    implicit val hc = HeaderCarrier()

    val confirmApisView = app.injector.instanceOf[ConfirmApisView]
    val turnOffApisMasterView = app.injector.instanceOf[TurnOffApisMasterView]
    val responsibleIndividualView = app.injector.instanceOf[ResponsibleIndividualView]
    val sellResellOrDistributeSoftwareView = app.injector.instanceOf[SellResellOrDistributeSoftwareView]

    val flowServiceMock = mock[GetProductionCredentialsFlowService]

    val controller = new SR20(
      mockErrorHandler,
      sessionServiceMock,
      applicationActionServiceMock,
      applicationServiceMock,
      mcc,
      cookieSigner,
      confirmApisView,
      turnOffApisMasterView,
      ApmConnectorMock.aMock,
      responsibleIndividualView,
      flowServiceMock,
      sellResellOrDistributeSoftwareView
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

    val sandboxApp: Application = sampleApp.copy(deployedTo = Environment.SANDBOX, state = ApplicationState.testing)

    fetchByApplicationIdReturns(appId, sandboxApp)
    givenApplicationAction(ApplicationWithSubscriptionData(sandboxApp,
      asSubscriptions(List(testAPISubscriptionStatus1)),
      asFields(List.empty)),
      loggedInDeveloper,
      List(testAPISubscriptionStatus1))

    ApmConnectorMock.FetchAllApis.willReturn(singleApi)
  }

  "confirmApiSubscription" should {

    "render the confirm apis view containing 1 upliftable api as there is only 1 upliftable api available to the application" in new Setup {

      val testFlow = ApiSubscriptions(Map(apiIdentifier1 -> true))

      when(flowServiceMock.fetchFlow(*)).thenReturn(Future.successful(GetProductionCredentialsFlow("", None, None,
        Some(testFlow))))

      ApmConnectorMock.FetchUpliftableSubscriptions.willReturn(Set(apiIdentifier1))

      private val result = controller.confirmApiSubscriptionsPage(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK

      contentAsString(result) should include("test-api-1 - 1.0")
    }

    "render the confirm apis view without the 'Change my API subscriptions' link as there is only 1 upliftable api available to the application" in new Setup {

      val testFlow = ApiSubscriptions(Map(apiIdentifier1 -> true))

      when(flowServiceMock.fetchFlow(*)).thenReturn(Future.successful(GetProductionCredentialsFlow("", None, None,
        Some(testFlow))))

      ApmConnectorMock.FetchUpliftableSubscriptions.willReturn(Set(apiIdentifier1))

      private val result = controller.confirmApiSubscriptionsPage(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK

      contentAsString(result) should not include("Change my API subscriptions")
    }

    "render the confirm apis view with the 'Change my API subscriptions' link as there is more than 1 upliftable api available to the application" in new Setup {

      val testFlow = ApiSubscriptions(Map(apiIdentifier1 -> true, apiIdentifier2 -> true))

      when(flowServiceMock.fetchFlow(*)).thenReturn(Future.successful(GetProductionCredentialsFlow("", None, None,
        Some(testFlow))))

      val apiIdentifiers = Set(
        ApiIdentifier(ApiContext("test-api-context-1"), ApiVersion("1.0")),
        ApiIdentifier(ApiContext("test-api-context-2"), ApiVersion("1.0"))
      )
      ApmConnectorMock.FetchUpliftableSubscriptions.willReturn(apiIdentifiers)

      private val result = controller.confirmApiSubscriptionsPage(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK

      contentAsString(result) should include("Change my API subscriptions")
    }
  }

  "responsibleIndividual" should {

    "initially render the 'responsible individual view' unpopulated" in new Setup {

      when(flowServiceMock.fetchFlow(*)).thenReturn(Future.successful(GetProductionCredentialsFlow("", None, None, None)))
      ApmConnectorMock.FetchUpliftableSubscriptions.willReturn(Set(apiIdentifier1))

      private val result = controller.responsibleIndividual(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK

      contentAsString(result) should include("Provide details for a responsible individual in your organisation")
      contentAsString(result) shouldNot include("test full name")
      contentAsString(result) shouldNot include("test email address")
    }

    "render the 'responsible individual view' populated with a responsible individual" in new Setup {

      when(flowServiceMock.fetchFlow(*)).thenReturn(
        Future.successful(GetProductionCredentialsFlow("",
          Some(ResponsibleIndividual("test full name", "test email address")), None, None)))

      ApmConnectorMock.FetchUpliftableSubscriptions.willReturn(Set(apiIdentifier1))

      private val result = controller.responsibleIndividual(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK

      contentAsString(result) should include("Provide details for a responsible individual in your organisation")
      contentAsString(result) should include("test full name")
      contentAsString(result) should include("test email address")
    }

    "render the 'responsible individual view' with errors when fullName and emailAddress are missing" in new Setup {

        ApmConnectorMock.FetchUpliftableSubscriptions.willReturn(Set(apiIdentifier1))

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

        ApmConnectorMock.FetchUpliftableSubscriptions.willReturn(Set(apiIdentifier1))

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

      when(flowServiceMock.storeResponsibleIndividual(*, *)).thenReturn(Future.successful(GetProductionCredentialsFlow("", None, None, None)))

      ApmConnectorMock.FetchUpliftableSubscriptions.willReturn(Set(apiIdentifier1))

      private val result = controller.responsibleIndividualAction(appId)(loggedInRequest.withCSRFToken.withFormUrlEncodedBody(
        "fullName" -> testResponsibleIndividual.fullName,
        "emailAddress" -> testResponsibleIndividual.emailAddress
      ))

      status(result) shouldBe SEE_OTHER

      verify(flowServiceMock).storeResponsibleIndividual(eqTo(testResponsibleIndividual), any[DeveloperSession])
    }
  }

  "sellResellOrDistributeYourSoftware" should {

    "initially render the 'sell resell or distribute your software view' with choices unselected" in new Setup {

      when(flowServiceMock.fetchFlow(*)).thenReturn(Future.successful(GetProductionCredentialsFlow("", None, None, None)))

      private val result = controller.sellResellOrDistributeYourSoftware(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe OK

      contentAsString(result) should include("Will you sell, resell or distribute your software?")
    }

   "render the 'sell resell or distribute your software view' with the answer 'Yes' selected" in new Setup {

     when(flowServiceMock.fetchFlow(*)).thenReturn(Future.successful(GetProductionCredentialsFlow("", None, Some(SellResellOrDistribute("Yes")), None)))

     private val result = controller.sellResellOrDistributeYourSoftware(appId)(loggedInRequest.withCSRFToken)

     status(result) shouldBe OK

     contentAsString(result) should include("Will you sell, resell or distribute your software?")

     val document = Jsoup.parse(contentAsString(result))
     document.getElementById("distribute-question-yes") shouldNot be(null)
     document.getElementById("distribute-question-yes").hasAttr("checked") shouldBe true
   }

   "render the 'sell resell or distribute your software view' with the answer 'No' selected" in new Setup {

     when(flowServiceMock.fetchFlow(*)).thenReturn(Future.successful(GetProductionCredentialsFlow("", None, Some(SellResellOrDistribute("No")), None)))

     private val result = controller.sellResellOrDistributeYourSoftware(appId)(loggedInRequest.withCSRFToken)

     status(result) shouldBe OK

     contentAsString(result) should include("Will you sell, resell or distribute your software?")

     val document = Jsoup.parse(contentAsString(result))
     document.getElementById("distribute-question-no") shouldNot be(null)
     document.getElementById("distribute-question-no").hasAttr("checked") shouldBe true
   }

    "render the 'sell resell or distribute your software view' with an error when no selection has been made" in new Setup {

      ApmConnectorMock.FetchUpliftableSubscriptions.willReturn(Set(apiIdentifier1))

      private val result = controller.sellResellOrDistributeYourSoftwareAction(appId)(loggedInRequest.withCSRFToken)

      status(result) shouldBe BAD_REQUEST

      contentAsString(result) should include("Will you sell, resell or distribute your software?")
      contentAsString(result) should include("Tell us if you will sell, resell or distribute your software")
    }

    "store the answer 'Yes' from the 'sell resell or distribute your software view' and redirect to next page" in new Setup {

      val testSellResellOrDistribute = SellResellOrDistribute("Yes")

      when(flowServiceMock.storeSellResellOrDistribute(*, *)).thenReturn(Future.successful(GetProductionCredentialsFlow("", None, None, None)))
      when(flowServiceMock.storeApiSubscriptions(*, *)).thenReturn(Future.successful(GetProductionCredentialsFlow("", None, None, None)))

      ApmConnectorMock.FetchUpliftableSubscriptions.willReturn(Set(apiIdentifier1))

      private val result = controller.sellResellOrDistributeYourSoftwareAction(appId)(loggedInRequest.withCSRFToken.withFormUrlEncodedBody(
        "answer" -> testSellResellOrDistribute.answer
      ))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/applications/myAppId/confirm-subscriptions")

      verify(flowServiceMock).storeSellResellOrDistribute(eqTo(testSellResellOrDistribute), any[DeveloperSession])
    }

    "store the answer 'No' from the 'sell resell or distribute your software view' and redirect to next page" in new Setup {

      val testSellResellOrDistribute = SellResellOrDistribute("No")

      when(flowServiceMock.storeSellResellOrDistribute(*, *)).thenReturn(Future.successful(GetProductionCredentialsFlow("", None, None, None)))
      when(flowServiceMock.storeApiSubscriptions(*, *)).thenReturn(Future.successful(GetProductionCredentialsFlow("", None, None, None)))

      ApmConnectorMock.FetchUpliftableSubscriptions.willReturn(Set(apiIdentifier1))

      private val result = controller.sellResellOrDistributeYourSoftwareAction(appId)(loggedInRequest.withCSRFToken.withFormUrlEncodedBody(
        "answer" -> testSellResellOrDistribute.answer
      ))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(s"/developer/applications/myAppId/confirm-subscriptions")

      verify(flowServiceMock).storeSellResellOrDistribute(eqTo(testSellResellOrDistribute), any[DeveloperSession])
    }
  }
}

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

package uk.gov.hmrc.apiplatform.modules.uplift.services

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.successful

import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.apis.domain.models._
import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models.SellResellOrDistribute
import uk.gov.hmrc.apiplatform.modules.applications.common.domain.models.FullName
import uk.gov.hmrc.apiplatform.modules.applications.submissions.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.apiplatform.modules.submissions.SubmissionsTestData
import uk.gov.hmrc.apiplatform.modules.submissions.connectors.ThirdPartyApplicationSubmissionsConnector
import uk.gov.hmrc.apiplatform.modules.tpd.sessions.domain.models.{DeveloperSession, LoggedInState, Session, UserSessionId}
import uk.gov.hmrc.apiplatform.modules.uplift.domain.models._
import uk.gov.hmrc.apiplatform.modules.uplift.services.mocks._
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder._
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.SubscriptionTestHelperSugar
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions._
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.connectors.ApmConnectorMockModule
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service.{ApplicationActionServiceMock, ApplicationServiceMock, SessionServiceMock}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{AsyncHmrcSpec, LocalUserIdTracker}

class UpliftJourneyServiceSpec
    extends AsyncHmrcSpec
    with SampleSession
    with SampleApplication
    with SubmissionsTestData
    with SubscriptionTestHelperSugar
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

    val sandboxAppId = ApplicationId.random
    val prodAppId    = ApplicationId.random

    implicit val hc: HeaderCarrier = HeaderCarrier()

    val mockSubmissionsConnector: ThirdPartyApplicationSubmissionsConnector = mock[ThirdPartyApplicationSubmissionsConnector]

    val underTest = new UpliftJourneyService(
      GPCFlowServiceMock.aMock,
      ApplicationServiceMock.applicationServiceMock,
      ApmConnectorMock.aMock,
      mockSubmissionsConnector
    )

    val appName: String = "app"
    val apiVersion      = ApiVersionNbr("version")

    val developer = buildDeveloper()
    val sessionId = UserSessionId.random
    val session   = Session(sessionId, developer, LoggedInState.LOGGED_IN)

    val loggedInDeveloper = DeveloperSession(session)

    val apiIdentifier1 = ApiIdentifier(ApiContext("test-api-context-1"), ApiVersionNbr("1.0"))
    val apiIdentifier2 = ApiIdentifier(ApiContext("test-api-context-2"), ApiVersionNbr("1.0"))

    val emptyFields = emptySubscriptionFieldsWrapper(appId, clientId, apiIdentifier1.context, apiIdentifier1.versionNbr)

    val testAPISubscriptionStatus1 = APISubscriptionStatus(
      "test-api-1",
      ServiceName("api-example-microservice"),
      apiIdentifier1.context,
      ApiVersion(apiIdentifier1.versionNbr, ApiStatus.STABLE, ApiAccess.PUBLIC, List.empty),
      subscribed = true,
      requiresTrust = false,
      fields = emptyFields
    )

    val testAPISubscriptionStatus2 = APISubscriptionStatus(
      "test-api-2",
      ServiceName("api-example-microservice"),
      apiIdentifier2.context,
      ApiVersion(apiIdentifier2.versionNbr, ApiStatus.STABLE, ApiAccess.PUBLIC, List.empty),
      subscribed = true,
      requiresTrust = false,
      fields = emptyFields
    )

    val testAPISubscriptionStatus3 = APISubscriptionStatus(
      "test-api-3",
      ServiceName("api-example-microservice"),
      ApiContext("test-api-context-3"),
      ApiVersion(apiIdentifier2.versionNbr, ApiStatus.STABLE, ApiAccess.PUBLIC, List.empty),
      subscribed = true,
      requiresTrust = false,
      fields = emptyFields
    )

    val singleApi: List[ApiDefinition] = List(
      ApiDefinition(
        serviceName = ServiceName("test-api-context-1"),
        serviceBaseUrl = "http://serviceBaseUrl",
        name = "test-api-context-1",
        description = "Description",
        context = ApiContext("test-api-context-1"),
        versions = Map(ApiVersionNbr("1.0") ->
          ApiVersion(ApiVersionNbr("1.0"), ApiStatus.STABLE, ApiAccess.PUBLIC, List.empty)),
        isTestSupport = false,
        categories = List(ApiCategory.EXAMPLE)
      )
    )

    val multipleApis: List[ApiDefinition] = List(
      ApiDefinition(
        serviceName = ServiceName("test-api-context-1"),
        serviceBaseUrl = "http://serviceBaseUrl",
        name = "test-api-context-1",
        description = "Description",
        context = ApiContext("test-api-context-1"),
        versions = Map(ApiVersionNbr("1.0") ->
          ApiVersion(ApiVersionNbr("1.0"), ApiStatus.STABLE, ApiAccess.PUBLIC, List.empty)),
        isTestSupport = false,
        categories = List(ApiCategory.EXAMPLE)
      ),
      ApiDefinition(
        serviceName = ServiceName("test-api-context-2"),
        serviceBaseUrl = "http://serviceBaseUrl",
        name = "test-api-context-2",
        description = "Description",
        context = ApiContext("test-api-context-2"),
        versions = Map(ApiVersionNbr("1.0") ->
          ApiVersion(ApiVersionNbr("1.0"), ApiStatus.STABLE, ApiAccess.PUBLIC, List.empty)),
        isTestSupport = false,
        categories = List(ApiCategory.EXAMPLE)
      )
    )

    def toIdentifiers(apis: List[ApiDefinition]): Set[ApiIdentifier] =
      apis.flatMap {
        definition => definition.versions.keySet.map(version => ApiIdentifier(definition.context, version))
      }.toSet

    ApmConnectorMock.FetchAllApis.willReturn(singleApi)

    val aResponsibleIndividual      = ResponsibleIndividual(FullName("test full name"), "test email address".toLaxEmail)
    val sellResellOrDistribute      = SellResellOrDistribute("Yes")
    val doNotSellResellOrDistribute = SellResellOrDistribute("No")
    val aListOfSubscriptions        = ApiSubscriptions(toIdentifiers(multipleApis).map(id => id -> true).toMap)
    val aSingleSubscriptions        = ApiSubscriptions(toIdentifiers(singleApi).map(id => id -> true).toMap)
  }

  "confirmAndUplift" should {
    "return the new app id when everything is good" in new Setup {
      val productionAppId = ApplicationId.random
      GPCFlowServiceMock.FetchFlow.thenReturns(GetProductionCredentialsFlow(UserSessionId.random, Some(sellResellOrDistribute), Some(aListOfSubscriptions)))
      ApmConnectorMock.FetchUpliftableSubscriptions.willReturn(Set(apiIdentifier1))
      ApmConnectorMock.UpliftApplicationV2.willReturn(productionAppId)

      private val result = await(underTest.confirmAndUplift(sandboxAppId, loggedInDeveloper))

      result.isRight shouldBe true
      result shouldBe Right(productionAppId)
    }

    "fail when missing sell resell..." in new Setup {
      GPCFlowServiceMock.FetchFlow.thenReturns(GetProductionCredentialsFlow(UserSessionId.random, None, None))

      private val result = await(underTest.confirmAndUplift(sandboxAppId, loggedInDeveloper))

      result.left.value shouldBe "No sell or resell or distribute set"
    }

    "fail when missing subscriptions" in new Setup {
      GPCFlowServiceMock.FetchFlow.thenReturns(GetProductionCredentialsFlow(UserSessionId.random, Some(sellResellOrDistribute), None))

      private val result = await(underTest.confirmAndUplift(sandboxAppId, loggedInDeveloper))

      result.left.value shouldBe "No subscriptions set"
    }

    "fail when no upliftable apis found" in new Setup {
      GPCFlowServiceMock.FetchFlow.thenReturns(GetProductionCredentialsFlow(UserSessionId.random, Some(sellResellOrDistribute), Some(aListOfSubscriptions)))
      ApmConnectorMock.FetchUpliftableSubscriptions.willReturn(Set())

      private val result = await(underTest.confirmAndUplift(sandboxAppId, loggedInDeveloper))

      result.left.value shouldBe "No apis found to subscribe to"
    }
  }

  "apiSubscriptionData" should {
    "returns the names of apis when flow has selected them ignoring any that are not upliftable" in new Setup {
      GPCFlowServiceMock.FetchFlow.thenReturns(GetProductionCredentialsFlow(UserSessionId.random, Some(sellResellOrDistribute), Some(aListOfSubscriptions)))
      ApmConnectorMock.FetchUpliftableSubscriptions.willReturn(Set(apiIdentifier1, apiIdentifier2))

      private val result =
        await(underTest.apiSubscriptionData(sandboxAppId, loggedInDeveloper, List(testAPISubscriptionStatus1, testAPISubscriptionStatus2, testAPISubscriptionStatus3)))

      result match {
        case (names, flag) =>
          names.size shouldBe 2
          names.head shouldBe "test-api-1 - 1.0"
          flag shouldBe true
      }
    }

    "returns the name of selected api ignoring any that are not upliftable" in new Setup {
      GPCFlowServiceMock.FetchFlow.thenReturns(GetProductionCredentialsFlow(UserSessionId.random, Some(sellResellOrDistribute), Some(aSingleSubscriptions)))
      ApmConnectorMock.FetchUpliftableSubscriptions.willReturn(Set(apiIdentifier1, apiIdentifier2))

      private val result =
        await(underTest.apiSubscriptionData(sandboxAppId, loggedInDeveloper, List(testAPISubscriptionStatus1, testAPISubscriptionStatus2, testAPISubscriptionStatus3)))

      result match {
        case (names, flag) =>
          names.size shouldBe 1
          names.head shouldBe "test-api-1 - 1.0"
          flag shouldBe true
      }
    }

    "returns the name of selected api and false when there is only one upliftable api" in new Setup {
      GPCFlowServiceMock.FetchFlow.thenReturns(GetProductionCredentialsFlow(UserSessionId.random, Some(sellResellOrDistribute), Some(aSingleSubscriptions)))
      ApmConnectorMock.FetchUpliftableSubscriptions.willReturn(Set(apiIdentifier1))

      private val result =
        await(underTest.apiSubscriptionData(sandboxAppId, loggedInDeveloper, List(testAPISubscriptionStatus1, testAPISubscriptionStatus2, testAPISubscriptionStatus3)))

      result match {
        case (names, flag) =>
          names.size shouldBe 1
          names.head shouldBe "test-api-1 - 1.0"
          flag shouldBe false
      }
    }
  }

  "createNewSubmission" should {
    "return the new submission when everything is good" in new Setup {
      val productionAppId = ApplicationId.random
      GPCFlowServiceMock.FetchFlow.thenReturns(GetProductionCredentialsFlow(UserSessionId.random, Some(sellResellOrDistribute), Some(aListOfSubscriptions)))
      ApplicationServiceMock.updateApplicationSuccessful()
      when(mockSubmissionsConnector.createSubmission(*[ApplicationId], *[LaxEmailAddress])(*)).thenReturn(successful(Some(aSubmission)))

      private val result = await(underTest.createNewSubmission(productionAppId, sampleApp, loggedInDeveloper))

      result.isRight shouldBe true
      result shouldBe Right(aSubmission)
    }
  }
}

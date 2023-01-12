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

import uk.gov.hmrc.thirdpartydeveloperfrontend.builder._
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.SubscriptionTestHelperSugar
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions._
import uk.gov.hmrc.apiplatform.modules.uplift.domain.models._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.{DeveloperSession, LoggedInState, Session}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.{ApiCategory, ApiData, VersionData}
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.connectors.ApmConnectorMockModule
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service.{ApplicationActionServiceMock, ApplicationServiceMock, SessionServiceMock}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.AsyncHmrcSpec
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.ApplicationId
import uk.gov.hmrc.apiplatform.modules.uplift.services.mocks._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.SellResellOrDistribute
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.ResponsibleIndividual

class UpliftJourneyServiceSpec
    extends AsyncHmrcSpec
    with SampleSession
    with SampleApplication
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

    implicit val hc = HeaderCarrier()

    val underTest = new UpliftJourneyService(
      GPCFlowServiceMock.aMock,
      ApmConnectorMock.aMock
    )

    val appName: String = "app"
    val apiVersion      = ApiVersion("version")

    val developer = buildDeveloper()
    val sessionId = "sessionId"
    val session   = Session(sessionId, developer, LoggedInState.LOGGED_IN)

    val loggedInDeveloper = DeveloperSession(session)

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

    val testAPISubscriptionStatus3 = APISubscriptionStatus(
      "test-api-3",
      "api-example-microservice",
      ApiContext("test-api-context-3"),
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

    def toIdentifiers(apis: Map[ApiContext, ApiData]): Set[ApiIdentifier] =
      apis.flatMap {
        case (context, data) => data.versions.keySet.map(version => ApiIdentifier(context, version))
      }.toSet

    ApmConnectorMock.FetchAllApis.willReturn(singleApi)

    val aResponsibleIndividual      = ResponsibleIndividual(ResponsibleIndividual.Name("test full name"), ResponsibleIndividual.EmailAddress("test email address"))
    val sellResellOrDistribute      = SellResellOrDistribute("Yes")
    val doNotSellResellOrDistribute = SellResellOrDistribute("No")
    val aListOfSubscriptions        = ApiSubscriptions(toIdentifiers(multipleApis).map(id => id -> true).toMap)
    val aSingleSubscriptions        = ApiSubscriptions(toIdentifiers(singleApi).map(id => id -> true).toMap)
  }

  "confirmAndUplift" should {
    "return the new app id when everything is good" in new Setup {
      val productionAppId = ApplicationId.random
      GPCFlowServiceMock.FetchFlow.thenReturns(GetProductionCredentialsFlow("", Some(sellResellOrDistribute), Some(aListOfSubscriptions)))
      ApmConnectorMock.FetchUpliftableSubscriptions.willReturn(Set(apiIdentifier1))
      ApmConnectorMock.UpliftApplicationV1.willReturn(productionAppId)

      private val result = await(underTest.confirmAndUplift(sandboxAppId, loggedInDeveloper, false))

      result.right.value shouldBe productionAppId
    }

    "fail when missing sell resell..." in new Setup {
      GPCFlowServiceMock.FetchFlow.thenReturns(GetProductionCredentialsFlow("", None, None))

      private val result = await(underTest.confirmAndUplift(sandboxAppId, loggedInDeveloper, true))

      result.left.value shouldBe "No sell or resell or distribute set"
    }

    "fail when missing subscriptions" in new Setup {
      GPCFlowServiceMock.FetchFlow.thenReturns(GetProductionCredentialsFlow("", Some(sellResellOrDistribute), None))

      private val result = await(underTest.confirmAndUplift(sandboxAppId, loggedInDeveloper, true))

      result.left.value shouldBe "No subscriptions set"
    }

    "fail when no upliftable apis found" in new Setup {
      GPCFlowServiceMock.FetchFlow.thenReturns(GetProductionCredentialsFlow("", Some(sellResellOrDistribute), Some(aListOfSubscriptions)))
      ApmConnectorMock.FetchUpliftableSubscriptions.willReturn(Set())

      private val result = await(underTest.confirmAndUplift(sandboxAppId, loggedInDeveloper, false))

      result.left.value shouldBe "No apis found to subscribe to"
    }
  }

  "apiSubscriptionData" should {
    "returns the names of apis when flow has selected them ignoring any that are not upliftable" in new Setup {
      GPCFlowServiceMock.FetchFlow.thenReturns(GetProductionCredentialsFlow("", Some(sellResellOrDistribute), Some(aListOfSubscriptions)))
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
      GPCFlowServiceMock.FetchFlow.thenReturns(GetProductionCredentialsFlow("", Some(sellResellOrDistribute), Some(aSingleSubscriptions)))
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
      GPCFlowServiceMock.FetchFlow.thenReturns(GetProductionCredentialsFlow("", Some(sellResellOrDistribute), Some(aSingleSubscriptions)))
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
}

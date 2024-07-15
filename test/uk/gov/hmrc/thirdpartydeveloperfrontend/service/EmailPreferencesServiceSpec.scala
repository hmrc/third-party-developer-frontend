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

package uk.gov.hmrc.thirdpartydeveloperfrontend.service

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.apis.domain.models.{ApiCategory, ServiceName}
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.apiplatform.modules.tpd.core.domain.models.User
import uk.gov.hmrc.apiplatform.modules.tpd.emailpreferences.domain.models.{EmailPreferences, EmailTopic, TaxRegimeInterests}
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models._
import uk.gov.hmrc.apiplatform.modules.uplift.services.mocks.FlowRepositoryMockModule
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.DeveloperBuilder
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.{ApmConnector, ThirdPartyDeveloperConnector}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.CombinedApiTestDataHelper
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.CombinedApi
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.emailpreferences.APICategoryDisplayDetails
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.flows.{EmailPreferencesFlowV2, FlowType, NewApplicationEmailPreferencesFlowV2}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{AsyncHmrcSpec, LocalUserIdTracker}
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock

class EmailPreferencesServiceSpec extends AsyncHmrcSpec {

  trait SetUp extends DeveloperBuilder with LocalUserIdTracker with FixedClock with CombinedApiTestDataHelper with FlowRepositoryMockModule {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val emailPreferences                       = EmailPreferences(List(TaxRegimeInterests("CATEGORY_1", Set("api1", "api2"))), Set(EmailTopic.TECHNICAL))
    val developer: User                   = buildDeveloper()
    val developerWithEmailPrefences: User = developer.copy(emailPreferences = emailPreferences)
    val sessionId                              = UserSessionId.random
    val session: UserSession                   = UserSession(sessionId, LoggedInState.LOGGED_IN, developerWithEmailPrefences)
    val sessionNoEMailPrefences: UserSession   = UserSession(sessionId, LoggedInState.LOGGED_IN, developer)
    val loggedInDeveloper: DeveloperSession    = DeveloperSession(session)
    val applicationId                          = ApplicationId.random

    val mockThirdPartyDeveloperConnector = mock[ThirdPartyDeveloperConnector]
    val mockApmConnector                 = mock[ApmConnector]
    val underTest                        = new EmailPreferencesService(mockApmConnector, mockThirdPartyDeveloperConnector, FlowRepositoryMock.aMock)

  }

  "EmailPreferences" when {
    "removeEmailPreferences" should {
      "return true when connector is called correctly and true" in new SetUp {
        val userId = UserId.random

        when(mockThirdPartyDeveloperConnector.removeEmailPreferences(*[UserId])(*)).thenReturn(Future.successful(true))
        val result = await(underTest.removeEmailPreferences(userId))
        result shouldBe true
      }
    }

    "updateEmailPreferences" should {
      "return true when connector is called correctly and true" in new SetUp {
        when(mockThirdPartyDeveloperConnector.updateEmailPreferences(*[UserId], *)(*)).thenReturn(Future.successful(true))
        val userId             = UserId.random
        val expectedFlowObject = EmailPreferencesFlowV2(sessionId, Set("CATEGORY_1"), Map("CATEGORY_1" -> Set("api1", "api2")), Set("TECHNICAL"), List.empty)

        val result = await(underTest.updateEmailPreferences(userId, expectedFlowObject))

        result shouldBe true
        verify(mockThirdPartyDeveloperConnector).updateEmailPreferences(eqTo(userId), eqTo(expectedFlowObject.toEmailPreferences))(*)
      }
    }

    "fetchEmailPreferencesFlow" should {
      "call the flow repository correctly and return flow when repository returns data" in new SetUp {
        val flowObject = EmailPreferencesFlowV2(sessionId, Set("category1", "category1"), Map("category1" -> Set("api1", "api2")), Set("TECHNICAL"), List.empty)
        FlowRepositoryMock.FetchBySessionIdAndFlowType.thenReturn[EmailPreferencesFlowV2](sessionId)(flowObject)
        val result     = await(underTest.fetchEmailPreferencesFlow(loggedInDeveloper))
        result shouldBe flowObject
        FlowRepositoryMock.FetchBySessionIdAndFlowType.verifyCalledWith[EmailPreferencesFlowV2](sessionId)
      }

      "call the flow repository correctly and create a new flow object when nothing returned" in new SetUp {
        val expectedFlowObject = EmailPreferencesFlowV2(sessionId, Set.empty, Map.empty, Set.empty, List.empty)
        FlowRepositoryMock.FetchBySessionIdAndFlowType.thenReturnNothing[EmailPreferencesFlowV2](sessionId)
        val result             = await(underTest.fetchEmailPreferencesFlow(loggedInDeveloper.copy(sessionNoEMailPrefences)))

        result shouldBe expectedFlowObject
        FlowRepositoryMock.FetchBySessionIdAndFlowType.verifyCalledWith[EmailPreferencesFlowV2](sessionId)
      }

      "call the flow repository correctly and copy existing email preferences to flow object when nothing in cache" in new SetUp {
        val expectedFlowObject = EmailPreferencesFlowV2(sessionId, Set("CATEGORY_1"), Map("CATEGORY_1" -> Set("api1", "api2")), Set("TECHNICAL"), List.empty)
        FlowRepositoryMock.FetchBySessionIdAndFlowType.thenReturnNothing[EmailPreferencesFlowV2](sessionId)
        val result             = await(underTest.fetchEmailPreferencesFlow(loggedInDeveloper))

        result shouldBe expectedFlowObject
        FlowRepositoryMock.FetchBySessionIdAndFlowType.verifyCalledWith[EmailPreferencesFlowV2](sessionId)
      }
    }

    "fetchNewApplicationEmailPreferencesFlow" should {
      "call the flow repository correctly and return flow when repository returns data" in new SetUp {
        val flowObject =
          NewApplicationEmailPreferencesFlowV2(sessionId, emailPreferences, applicationId, Set(combinedApi("Test Api Definition")), Set.empty, Set("TECHNICAL"))
        FlowRepositoryMock.FetchBySessionIdAndFlowType.thenReturn[NewApplicationEmailPreferencesFlowV2](sessionId)(flowObject)
        val result     = await(underTest.fetchNewApplicationEmailPreferencesFlow(loggedInDeveloper, applicationId))
        result shouldBe flowObject
        FlowRepositoryMock.FetchBySessionIdAndFlowType.verifyCalledWith[NewApplicationEmailPreferencesFlowV2](sessionId)
      }

      "call the flow repository correctly and create a new flow object when nothing returned" in new SetUp {
        val expectedFlowObject =
          NewApplicationEmailPreferencesFlowV2(sessionId, emailPreferences, applicationId, Set.empty, Set.empty, Set("TECHNICAL"))
        FlowRepositoryMock.FetchBySessionIdAndFlowType.thenReturnNothing[NewApplicationEmailPreferencesFlowV2](sessionId)
        val result             = await(underTest.fetchNewApplicationEmailPreferencesFlow(loggedInDeveloper, applicationId))

        result shouldBe expectedFlowObject
        FlowRepositoryMock.FetchBySessionIdAndFlowType.verifyCalledWith[NewApplicationEmailPreferencesFlowV2](sessionId)
      }
    }

    "deleteFlowBySessionId" should {
      "call flowRepository correctly" in new SetUp {
        FlowRepositoryMock.DeleteBySessionIdAndFlowType.thenReturnSuccess(sessionId, FlowType.EMAIL_PREFERENCES_V2)
        val result = await(underTest.deleteFlow(sessionId, FlowType.EMAIL_PREFERENCES_V2))

        result shouldBe true
        FlowRepositoryMock.DeleteBySessionIdAndFlowType.verifyCalledWith(sessionId, FlowType.EMAIL_PREFERENCES_V2)
      }
    }

    "fetchAllAPICategoryDetails" should {
      val categoryDisplayDetails = APICategoryDisplayDetails("SELF_ASSESSMENT", ApiCategory.SELF_ASSESSMENT.displayText)

      "return all APICategoryDetails objects from the library type ApiCategory" in new SetUp {
        val result = await(underTest.fetchAllAPICategoryDetails())

        result.size should be(ApiCategory.values.size)
        result.find(_.category == categoryDisplayDetails.category) should be(Some(categoryDisplayDetails))
      }
    }

    "fetchAPIDetails" should {
      val apiServiceName1 = ServiceName("service-1")
      val apiServiceName2 = ServiceName("service-2")

      val apiDetails1 = mock[CombinedApi]
      val apiDetails2 = mock[CombinedApi]

      "return details of APIs by serviceName" in new SetUp {
        when(mockApmConnector.fetchCombinedApi(eqTo(apiServiceName1))(*)).thenReturn(Future.successful(Right(apiDetails1)))
        when(mockApmConnector.fetchCombinedApi(eqTo(apiServiceName2))(*)).thenReturn(Future.successful(Right(apiDetails2)))

        val result = await(underTest.fetchAPIDetails(Set(apiServiceName1, apiServiceName2)))

        result.size should be(2)
        result should contain theSameElementsAs List(apiDetails1, apiDetails2)

        verify(mockApmConnector).fetchCombinedApi(eqTo(apiServiceName1))(*)
        verify(mockApmConnector).fetchCombinedApi(eqTo(apiServiceName2))(*)
      }
    }

    "updateNewApplicationSelectedApis" should {
      "persist changes to flow object" in new SetUp {
        val api1Name = ServiceName("first-api")
        val api1     = mock[CombinedApi]
        val api2Name = ServiceName("second-api")
        val api2     = mock[CombinedApi]

        val existingFlowObject =
          NewApplicationEmailPreferencesFlowV2(sessionId, EmailPreferences.noPreferences, applicationId, Set(api1, api2), Set.empty, Set.empty)
        val expectedFlowObject = existingFlowObject.copy(selectedApis = Set(api1, api2))

        when(mockApmConnector.fetchCombinedApi(api1Name)).thenReturn(Future.successful(Right(api1)))
        when(mockApmConnector.fetchCombinedApi(api2Name)).thenReturn(Future.successful(Right(api2)))

        FlowRepositoryMock.FetchBySessionIdAndFlowType.thenReturn[NewApplicationEmailPreferencesFlowV2](sessionId)(existingFlowObject)
        FlowRepositoryMock.SaveFlow.thenReturnSuccess[NewApplicationEmailPreferencesFlowV2]

        val result = await(underTest.updateNewApplicationSelectedApis(loggedInDeveloper, applicationId, Set(api1Name, api2Name)))

        result should be(expectedFlowObject)
      }
    }
  }
}

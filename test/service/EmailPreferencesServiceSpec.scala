/*
 * Copyright 2020 HM Revenue & Customs
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

package service

import builder.DeveloperBuilder
import connectors.{ApmConnector, ThirdPartyDeveloperConnector}
import domain.models.apidefinitions.ExtendedApiDefinitionTestDataHelper
import domain.models.applications.ApplicationId
import domain.models.developers.{Developer, DeveloperSession, LoggedInState, Session}
import domain.models.emailpreferences.{APICategoryDetails, EmailPreferences, EmailTopic, TaxRegimeInterests}
import domain.models.flows.{EmailPreferencesFlow, FlowType, NewApplicationEmailPreferencesFlow}
import repositories.FlowRepository
import uk.gov.hmrc.http.HeaderCarrier
import utils.AsyncHmrcSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import domain.models.connectors.ExtendedApiDefinition
import domain.models.developers.UserId

class EmailPreferencesServiceSpec extends AsyncHmrcSpec {
  trait SetUp extends ExtendedApiDefinitionTestDataHelper with DeveloperBuilder {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val emailPreferences = EmailPreferences(List(TaxRegimeInterests("CATEGORY_1", Set("api1", "api2"))), Set(EmailTopic.TECHNICAL))
    val developer: Developer = buildDeveloper()
    val developerWithEmailPrefences: Developer = developer.copy(emailPreferences = emailPreferences)
    val sessionId = "sessionId"
    val session: Session = Session(sessionId, developerWithEmailPrefences, LoggedInState.LOGGED_IN)
    val sessionNoEMailPrefences: Session = Session(sessionId, developer, LoggedInState.LOGGED_IN)
    val loggedInDeveloper: DeveloperSession = DeveloperSession(session)
    val applicationId = ApplicationId.random

    val mockFlowRepository = mock[FlowRepository]
    val mockThirdPartyDeveloperConnector = mock[ThirdPartyDeveloperConnector]
    val mockApmConnector = mock[ApmConnector]
    val underTest = new EmailPreferencesService(mockApmConnector, mockThirdPartyDeveloperConnector, mockFlowRepository)

  }

  "EmailPreferences" when {
    "removeEmailPreferences" should {
      "return true when connector is called correctly and true" in new SetUp {
        when(mockThirdPartyDeveloperConnector.removeEmailPreferences(*)(*)).thenReturn(Future.successful(true))
        val result = await(underTest.removeEmailPreferences("someEmail"))
        result shouldBe true
      }
    }

    "updateEmailPreferences" should {
      "return true when connector is called correctly and true" in new SetUp {
        when(mockThirdPartyDeveloperConnector.updateEmailPreferences(*[UserId], *)(*)).thenReturn(Future.successful(true))
        val userId = UserId.random
        val expectedFlowObject = EmailPreferencesFlow(sessionId, Set("CATEGORY_1"), Map("CATEGORY_1" -> Set("api1", "api2")), Set("TECHNICAL"), Seq.empty)

        val result = await(underTest.updateEmailPreferences(userId, expectedFlowObject))

        result shouldBe true
        verify(mockThirdPartyDeveloperConnector).updateEmailPreferences(eqTo(userId), eqTo(expectedFlowObject.toEmailPreferences))(*)
      }
    }

    "fetchEmailPreferencesFlow" should {
      "call the flow repository correctly and return flow when repository returns data" in new SetUp {
        val flowObject = EmailPreferencesFlow(sessionId, Set("category1", "category1"), Map("category1" -> Set("api1", "api2")), Set("TECHNICAL"), Seq.empty)
        when(mockFlowRepository.fetchBySessionIdAndFlowType[EmailPreferencesFlow](eqTo(sessionId), eqTo(FlowType.EMAIL_PREFERENCES))(*)).thenReturn(Future.successful(Some(flowObject)))
        val result = await(underTest.fetchEmailPreferencesFlow(loggedInDeveloper))
        result shouldBe flowObject
        verify(mockFlowRepository).fetchBySessionIdAndFlowType(eqTo(sessionId), eqTo(FlowType.EMAIL_PREFERENCES))(*)
      }

      "call the flow repository correctly and create a new flow object when nothing returned" in new SetUp {
        val expectedFlowObject = EmailPreferencesFlow(sessionId, Set.empty, Map.empty, Set.empty, Seq.empty)
        when(mockFlowRepository.fetchBySessionIdAndFlowType(eqTo(sessionId), eqTo(FlowType.EMAIL_PREFERENCES))(*)).thenReturn(Future.successful(None))
        val result = await(underTest.fetchEmailPreferencesFlow(loggedInDeveloper.copy(sessionNoEMailPrefences)))

        result shouldBe expectedFlowObject
        verify(mockFlowRepository).fetchBySessionIdAndFlowType(eqTo(sessionId), eqTo(FlowType.EMAIL_PREFERENCES))(*)
      }

      "call the flow repository correctly and copy existing email preferences to flow object when nothing in cache" in new SetUp {
        val expectedFlowObject = EmailPreferencesFlow(sessionId, Set("CATEGORY_1"), Map("CATEGORY_1" -> Set("api1", "api2")), Set("TECHNICAL"), Seq.empty)
        when(mockFlowRepository.fetchBySessionIdAndFlowType(eqTo(sessionId), eqTo(FlowType.EMAIL_PREFERENCES))(*)).thenReturn(Future.successful(None))
        val result = await(underTest.fetchEmailPreferencesFlow(loggedInDeveloper))

        result shouldBe expectedFlowObject
        verify(mockFlowRepository).fetchBySessionIdAndFlowType(eqTo(sessionId), eqTo(FlowType.EMAIL_PREFERENCES))(*)
      }
    }

    "fetchNewApplicationEmailPreferencesFlow" should {
      "call the flow repository correctly and return flow when repository returns data" in new SetUp {
        val flowObject =
          NewApplicationEmailPreferencesFlow(sessionId, emailPreferences, applicationId, Set(extendedApiDefinition("Test Api Definition")), Set.empty, Set("TECHNICAL"))
        when(mockFlowRepository.fetchBySessionIdAndFlowType[NewApplicationEmailPreferencesFlow](eqTo(sessionId), eqTo(FlowType.NEW_APPLICATION_EMAIL_PREFERENCES))(*)).thenReturn(Future.successful(Some(flowObject)))
        val result = await(underTest.fetchNewApplicationEmailPreferencesFlow(loggedInDeveloper, applicationId))
        result shouldBe flowObject
        verify(mockFlowRepository).fetchBySessionIdAndFlowType(eqTo(sessionId), eqTo(FlowType.NEW_APPLICATION_EMAIL_PREFERENCES))(*)
      }

       "call the flow repository correctly and create a new flow object when nothing returned" in new SetUp {
         val expectedFlowObject =
           NewApplicationEmailPreferencesFlow(sessionId, emailPreferences, applicationId, Set.empty, Set.empty, Set("TECHNICAL"))
         when(mockFlowRepository.fetchBySessionIdAndFlowType(eqTo(sessionId), eqTo(FlowType.NEW_APPLICATION_EMAIL_PREFERENCES))(*)).thenReturn(Future.successful(None))
         val result = await(underTest.fetchNewApplicationEmailPreferencesFlow(loggedInDeveloper, applicationId))

         result shouldBe expectedFlowObject
         verify(mockFlowRepository).fetchBySessionIdAndFlowType(eqTo(sessionId), eqTo(FlowType.NEW_APPLICATION_EMAIL_PREFERENCES))(*)
       }
    }

    "deleteFlowBySessionId" should {
      "call flowRepository correctly" in new SetUp {
        when(mockFlowRepository.deleteBySessionIdAndFlowType(eqTo(sessionId), eqTo(FlowType.EMAIL_PREFERENCES))).thenReturn(Future.successful(true))
        val result = await(underTest.deleteFlow(sessionId, FlowType.EMAIL_PREFERENCES))

        result shouldBe true
        verify(mockFlowRepository).deleteBySessionIdAndFlowType(eqTo(sessionId), eqTo(FlowType.EMAIL_PREFERENCES))
      }
    }

    "fetchAllAPICategoryDetails" should {
      val category1 = mock[APICategoryDetails]
      val category2 = mock[APICategoryDetails]

      "return all APICategoryDetails objects from connector" in new SetUp {
        when(mockApmConnector.fetchAllAPICategories()(*)).thenReturn(Future.successful(Seq(category1, category2)))

        val result = await(underTest.fetchAllAPICategoryDetails())

        result.size should be(2)
        result should contain only(category1, category2)

        verify(mockApmConnector).fetchAllAPICategories()(*)
      }
    }

    "fetchAPIDetails" should {
      val apiServiceName1 = "service-1"
      val apiServiceName2 = "service-2"

      val apiDetails1 = mock[ExtendedApiDefinition]
      val apiDetails2 = mock[ExtendedApiDefinition]

      "return details of APIs by serviceName" in new SetUp {
        when(mockApmConnector.fetchAPIDefinition(eqTo(apiServiceName1))(*)).thenReturn(Future.successful(apiDetails1))
        when(mockApmConnector.fetchAPIDefinition(eqTo(apiServiceName2))(*)).thenReturn(Future.successful(apiDetails2))

        val result = await(underTest.fetchAPIDetails(Set(apiServiceName1, apiServiceName2)))

        result.size should be(2)
        result should contain only(apiDetails1, apiDetails2)

        verify(mockApmConnector).fetchAPIDefinition(eqTo(apiServiceName1))(*)
        verify(mockApmConnector).fetchAPIDefinition(eqTo(apiServiceName2))(*)
      }
    }

    "updateNewApplicationSelectedApis" should {
      "persist changes to flow object" in new SetUp {
        val api1Name = "first-api"
        val api1 = mock[ExtendedApiDefinition]
        val api2Name = "second-api"
        val api2 = mock[ExtendedApiDefinition]

        val existingFlowObject =
          NewApplicationEmailPreferencesFlow(sessionId, EmailPreferences.noPreferences, applicationId, Set(api1, api2), Set.empty, Set.empty)
        val expectedFlowObject = existingFlowObject.copy(selectedApis = Set(api1, api2))

        when(mockApmConnector.fetchAPIDefinition(api1Name)).thenReturn(Future.successful(api1))
        when(mockApmConnector.fetchAPIDefinition(api2Name)).thenReturn(Future.successful(api2))

        when(mockFlowRepository.fetchBySessionIdAndFlowType[NewApplicationEmailPreferencesFlow](eqTo(sessionId), eqTo(FlowType.NEW_APPLICATION_EMAIL_PREFERENCES))(*))
          .thenReturn(Future.successful(Some(existingFlowObject)))

        when(mockFlowRepository.saveFlow(eqTo(expectedFlowObject))(*)).thenReturn(Future.successful(expectedFlowObject))

        val result = await(underTest.updateNewApplicationSelectedApis(loggedInDeveloper, applicationId, Set(api1Name, api2Name)))

        result should be (expectedFlowObject)
      }
    }
  }
}

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

import connectors.ThirdPartyDeveloperConnector
import domain.models.developers.{Developer, DeveloperSession, LoggedInState, Session}
import domain.models.flows.{EmailPreferencesFlow, FlowType}
import model.{EmailTopic, TaxRegimeInterests}
import repositories.FlowRepository
import utils.AsyncHmrcSpec
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class EmailPreferencesServiceSpec extends AsyncHmrcSpec {

  val emailPreferences = model.EmailPreferences(List(TaxRegimeInterests("CATEGORY_1", Set("api1", "api2"))), Set(EmailTopic.TECHNICAL))
  val developer: Developer = Developer("third.party.developer@example.com", "John", "Doe")
  val developerWithEmailPrefences: Developer = developer.copy(emailPreferences = emailPreferences)
  val sessionId = "sessionId"
  val session: Session = Session(sessionId, developerWithEmailPrefences, LoggedInState.LOGGED_IN)
  val sessionNoEMailPrefences: Session = Session(sessionId, developer, LoggedInState.LOGGED_IN)
  val loggedInDeveloper: DeveloperSession = DeveloperSession(session)
    
 trait SetUp {
  implicit val hc: HeaderCarrier = HeaderCarrier()
   val mockFlowRepository = mock[FlowRepository]
  val mockThirdPartyDeveloperConnector = mock[ThirdPartyDeveloperConnector]
  val underTest = new EmailPreferencesService(mockThirdPartyDeveloperConnector, mockFlowRepository)

 }

  "EmailPreferences" when {

    "removeEmail Preferences" should {
      "return true when connector is called correctly and true" in new SetUp {
        when(mockThirdPartyDeveloperConnector.removeEmailPreferences(*)(*)).thenReturn(Future.successful(true))
        val result = await(underTest.removeEmailPreferences("someEmail"))
        result shouldBe true
      }
    }

    "fetchFlowBySessionId" should {

      "call the flow repository correctly and return flow when repository returns data" in new SetUp {
        val flowObject = EmailPreferencesFlow(sessionId, Set("category1", "category1"), Map("category1" -> Set("api1", "api2")), Set("TECHNICAL"))
        when(mockFlowRepository.fetchBySessionIdAndFlowType[EmailPreferencesFlow](eqTo(sessionId), eqTo(FlowType.EMAIL_PREFERENCES))(*)).thenReturn(Future.successful(Some(flowObject)))
        val result = await(underTest.fetchFlowBySessionId(loggedInDeveloper))
        result shouldBe flowObject
        verify(mockFlowRepository).fetchBySessionIdAndFlowType(eqTo(sessionId), eqTo(FlowType.EMAIL_PREFERENCES))(*)
      }

      "call the flow repository correctly and create a new flow object when nothing returned" in new SetUp {
        val expectedFlowObject = EmailPreferencesFlow(sessionId, Set.empty, Map.empty, Set.empty)
        when(mockFlowRepository.fetchBySessionIdAndFlowType(eqTo(sessionId), eqTo(FlowType.EMAIL_PREFERENCES))(*)).thenReturn(Future.successful(None))
        val result = await(underTest.fetchFlowBySessionId(loggedInDeveloper.copy(sessionNoEMailPrefences)))

        result shouldBe expectedFlowObject
        verify(mockFlowRepository).fetchBySessionIdAndFlowType(eqTo(sessionId), eqTo(FlowType.EMAIL_PREFERENCES))(*)
      }


      "call the flow repository correctly and copy existing email preferences to flow object when nothing in cache" in new SetUp {
        val expectedFlowObject = EmailPreferencesFlow(sessionId, Set("CATEGORY_1"), Map("CATEGORY_1" -> Set("api1", "api2")), Set("TECHNICAL"))
        when(mockFlowRepository.fetchBySessionIdAndFlowType(eqTo(sessionId), eqTo(FlowType.EMAIL_PREFERENCES))(*)).thenReturn(Future.successful(None))
        val result = await(underTest.fetchFlowBySessionId(loggedInDeveloper))

        result shouldBe expectedFlowObject
        verify(mockFlowRepository).fetchBySessionIdAndFlowType(eqTo(sessionId), eqTo(FlowType.EMAIL_PREFERENCES))(*)
      }
    }
  }
  
}

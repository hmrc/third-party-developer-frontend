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

package modules.submissions.services

import connectors.DeskproConnector
import domain.models.applications.ApplicationId
import domain.models.developers.DeveloperSession
import connectors.ThirdPartyApplicationProductionConnector
import uk.gov.hmrc.http.HeaderCarrier
import utils.AsyncHmrcSpec
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.successful
import utils.TestApplications
import utils.CollaboratorTracker
import utils.LocalUserIdTracker
import domain.models.connectors.TicketCreated
import domain.models.HasSuceeded
import domain.ApplicationNotFound
import domain.ApplicationAlreadyExists

class RequestProductionCredentialsSpec extends AsyncHmrcSpec 
  with CollaboratorTracker 
  with LocalUserIdTracker
  with TestApplications  {

  trait Setup {
    implicit val hc = HeaderCarrier()
    val applicationId = ApplicationId.random
    val mockProductionApplicationConnector: ThirdPartyApplicationProductionConnector = mock[ThirdPartyApplicationProductionConnector]

    val email: String = "test@example.com"
    val developerSession = mock[DeveloperSession]
    when(developerSession.email).thenReturn(email)

    val mockDeskproConnector = mock[DeskproConnector]
    val underTest = new RequestProductionCredentials(mockProductionApplicationConnector, mockDeskproConnector)
  }

  "requestProductionCredentials" should {
    "successfully create a ticket" in new Setup {
      val app = anApplication(developerEmail = email)
      when(mockProductionApplicationConnector.requestApproval(eqTo(applicationId), eqTo(email))(*)).thenReturn(successful(app))
      when(mockDeskproConnector.createTicket(*)(*)).thenReturn(successful(TicketCreated))
      val result = await(underTest.requestProductionCredentials(applicationId, developerSession))
      
      result shouldBe app
      verify(mockDeskproConnector).createTicket(*)(*)
    }

    "fails to create a ticket if the application is not found" in new Setup {
      when(mockProductionApplicationConnector.requestApproval(eqTo(applicationId), eqTo(email))(*)).thenThrow(new ApplicationNotFound())
      
      intercept[ApplicationNotFound] {
        await(underTest.requestProductionCredentials(applicationId, developerSession))
      }
      verify(mockDeskproConnector, never).createTicket(*)(*)
    }

    "fails to create a ticket if application already exists" in new Setup {
      when(mockProductionApplicationConnector.requestApproval(eqTo(applicationId), eqTo(email))(*)).thenThrow(new ApplicationAlreadyExists())
      
      intercept[ApplicationAlreadyExists] {
        await(underTest.requestProductionCredentials(applicationId, developerSession))
      }
      verify(mockDeskproConnector, never).createTicket(*)(*)
    }

  }
}
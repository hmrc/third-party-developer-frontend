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

package uk.gov.hmrc.apiplatform.modules.submissions.services

import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.DeskproConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.ApplicationId
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.DeveloperSession
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.AsyncHmrcSpec
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.successful
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.TestApplications
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.CollaboratorTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.TicketCreated
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.ApplicationNotFound
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.ApplicationAlreadyExists
import uk.gov.hmrc.apiplatform.modules.submissions.connectors.ThirdPartyApplicationSubmissionsConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.DeskproTicket
import org.mockito.captor.ArgCaptor

class RequestProductionCredentialsSpec extends AsyncHmrcSpec
    with CollaboratorTracker
    with LocalUserIdTracker
    with TestApplications {

  trait Setup {
    implicit val hc                                                         = HeaderCarrier()
    val applicationId                                                       = ApplicationId.random
    val mockSubmissionsConnector: ThirdPartyApplicationSubmissionsConnector = mock[ThirdPartyApplicationSubmissionsConnector]

    val email: String    = "test@example.com"
    val name: String     = "bob example"
    val developerSession = mock[DeveloperSession]
    when(developerSession.email).thenReturn(email)
    when(developerSession.displayedName).thenReturn(name)

    val mockDeskproConnector = mock[DeskproConnector]
    val underTest            = new RequestProductionCredentials(mockSubmissionsConnector, mockDeskproConnector)
  }

  "requestProductionCredentials" should {
    "successfully create a ticket if requester is responsible individual" in new Setup {
      val app    = anApplication(developerEmail = email)
      when(mockSubmissionsConnector.requestApproval(eqTo(applicationId), eqTo(name), eqTo(email))(*)).thenReturn(successful(Right(app)))
      when(mockDeskproConnector.createTicket(*)(*)).thenReturn(successful(TicketCreated))
      val result = await(underTest.requestProductionCredentials(applicationId, developerSession, true))

      result.right.value shouldBe app

      val ticketCapture = ArgCaptor[DeskproTicket]
      verify(mockDeskproConnector).createTicket(ticketCapture.capture)(*)
      ticketCapture.value.subject shouldBe "New application submitted for checking"
    }

    "not create a ticket if requester is not responsible individual" in new Setup {
      val app    = anApplication(developerEmail = email)
      when(mockSubmissionsConnector.requestApproval(eqTo(applicationId), eqTo(name), eqTo(email))(*)).thenReturn(successful(Right(app)))
      val result = await(underTest.requestProductionCredentials(applicationId, developerSession, false))

      result.right.value shouldBe app

      verify(mockDeskproConnector, never).createTicket(*)(*)
    }

    "fails to create a ticket if the application is not found" in new Setup {
      when(mockSubmissionsConnector.requestApproval(eqTo(applicationId), eqTo(name), eqTo(email))(*)).thenThrow(new ApplicationNotFound())

      intercept[ApplicationNotFound] {
        await(underTest.requestProductionCredentials(applicationId, developerSession, true))
      }
      verify(mockDeskproConnector, never).createTicket(*)(*)
    }

    "fails to create a ticket if application already exists" in new Setup {
      when(mockSubmissionsConnector.requestApproval(eqTo(applicationId), eqTo(name), eqTo(email))(*)).thenThrow(new ApplicationAlreadyExists())

      intercept[ApplicationAlreadyExists] {
        await(underTest.requestProductionCredentials(applicationId, developerSession, true))
      }
      verify(mockDeskproConnector, never).createTicket(*)(*)
    }

  }
}

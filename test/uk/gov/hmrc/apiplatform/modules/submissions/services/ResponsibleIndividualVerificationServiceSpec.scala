/*
 * Copyright 2022 HM Revenue & Customs
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
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models.{ResponsibleIndividualVerification, ResponsibleIndividualVerificationId}
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models.Submission

class ResponsibleIndividualVerificationServiceSpec extends AsyncHmrcSpec 
  with CollaboratorTracker 
  with LocalUserIdTracker
  with TestApplications  {

  trait Setup {
    implicit val hc = HeaderCarrier()
    val applicationId = ApplicationId.random
    val mockSubmissionsConnector: ThirdPartyApplicationSubmissionsConnector = mock[ThirdPartyApplicationSubmissionsConnector]

    val code = "12345678"
    val riVerification = ResponsibleIndividualVerification(ResponsibleIndividualVerificationId(code), ApplicationId.random, "App name", Submission.Id.random, 0)

    val mockDeskproConnector = mock[DeskproConnector]
    val underTest = new ResponsibleIndividualVerificationService(mockSubmissionsConnector, mockDeskproConnector)
  }

  "fetchResponsibleIndividualVerification" should {
    "successfully return a riVerification record" in new Setup {
      when(mockSubmissionsConnector.fetchResponsibleIndividualVerification(eqTo(code))(*)).thenReturn(successful(Some(riVerification)))
      
      val result = await(underTest.fetchResponsibleIndividualVerification(code))
      
      result.isDefined shouldBe true
      result.get shouldBe riVerification
    }

    "return 'None' where no riVerification record found" in new Setup {
      when(mockSubmissionsConnector.fetchResponsibleIndividualVerification(eqTo(code))(*)).thenReturn(successful(None))
      
      val result = await(underTest.fetchResponsibleIndividualVerification(code))
      
      result.isDefined shouldBe false
    }
  }

  "verifyResponsibleIndividual" should {
    "successfully return a riVerification record for accept" in new Setup {
      when(mockSubmissionsConnector.responsibleIndividualAccept(eqTo(code))(*)).thenReturn(successful(Right(riVerification)))
      when(mockDeskproConnector.createTicket(*)(*)).thenReturn(successful(TicketCreated))
      
      val result = await(underTest.verifyResponsibleIndividual(code, true))
      
      result shouldBe 'Right
      result.right.value shouldBe riVerification
      val ticketCapture = ArgCaptor[DeskproTicket]
      verify(mockDeskproConnector).createTicket(ticketCapture.capture)(*)
      ticketCapture.value.subject shouldBe "New application submitted for checking"
    }

    "successfully return a riVerification record for decline" in new Setup {
      when(mockSubmissionsConnector.responsibleIndividualDecline(eqTo(code))(*)).thenReturn(successful(Right(riVerification)))
      
      val result = await(underTest.verifyResponsibleIndividual(code, false))
      
      result shouldBe 'Right
      result.right.value shouldBe riVerification
      verify(mockDeskproConnector, never).createTicket(*)(*)
    }
  }
}
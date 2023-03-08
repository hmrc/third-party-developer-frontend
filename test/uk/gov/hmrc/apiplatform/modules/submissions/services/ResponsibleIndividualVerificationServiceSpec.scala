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

import java.time.{LocalDateTime, ZoneOffset}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.successful

import org.mockito.captor.ArgCaptor

import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.submissions.connectors.ThirdPartyApplicationSubmissionsConnector
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models.{
  ResponsibleIndividualToUVerification,
  ResponsibleIndividualUpdateVerification,
  ResponsibleIndividualVerificationId,
  ResponsibleIndividualVerificationState,
  Submission
}
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.DeskproConnector
import uk.gov.hmrc.apiplatform.modules.applications.domain.models.ApplicationId
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.{ResponsibleIndividual}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.{DeskproTicket, TicketCreated}
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service.ApplicationServiceMock
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{AsyncHmrcSpec, CollaboratorTracker, LocalUserIdTracker, TestApplications}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax

class ResponsibleIndividualVerificationServiceSpec extends AsyncHmrcSpec
    with CollaboratorTracker
    with LocalUserIdTracker
    with ApplicationServiceMock
    with TestApplications {

  trait Setup {
    implicit val hc   = HeaderCarrier()
    val applicationId = ApplicationId.random
    val application   = aStandardPendingResponsibleIndividualVerificationApplication()
    val code          = "12345678"

    val riVerification        = ResponsibleIndividualToUVerification(
      ResponsibleIndividualVerificationId(code),
      applicationId,
      Submission.Id.random,
      0,
      "App name",
      LocalDateTime.now(ZoneOffset.UTC),
      ResponsibleIndividualVerificationState.INITIAL
    )
    val responsibleIndividual = ResponsibleIndividual.build("bob example", "bob@example.com".toLaxEmail)

    val mockSubmissionsConnector: ThirdPartyApplicationSubmissionsConnector = mock[ThirdPartyApplicationSubmissionsConnector]
    val mockDeskproConnector                                                = mock[DeskproConnector]
    val underTest                                                           = new ResponsibleIndividualVerificationService(mockSubmissionsConnector, ApplicationServiceMock.applicationServiceMock, mockDeskproConnector)
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

  "accept" should {
    "successfully return a riVerification record for accept and create a deskpro ticket with correct details" in new Setup {
      when(mockSubmissionsConnector.fetchResponsibleIndividualVerification(eqTo(code))(*)).thenReturn(successful(Some(riVerification)))
      ApplicationServiceMock.fetchByApplicationIdReturns(applicationId, application)
      ApplicationServiceMock.acceptResponsibleIndividualVerification(applicationId, code)
      when(mockDeskproConnector.createTicket(*[ResponsibleIndividualVerificationId], *)(*)).thenReturn(successful(TicketCreated))

      val result = await(underTest.accept(code))

      result shouldBe 'Right
      result.right.value shouldBe riVerification

      val ticketCapture = ArgCaptor[DeskproTicket]
      verify(mockDeskproConnector).createTicket(eqTo(riVerification.id), ticketCapture.capture)(*)
      val deskproTicket = ticketCapture.value
      deskproTicket.subject shouldBe "New application submitted for checking"
      deskproTicket.name shouldBe application.state.requestedByName.get
      deskproTicket.email.text shouldBe application.state.requestedByEmailAddress.get
      deskproTicket.message should include(riVerification.applicationName)
      deskproTicket.referrer should include(s"/application/${riVerification.applicationId.value}/check-answers")
    }

    "successfully return a riVerification record for accept but don't create a deskpro ticket for an update" in new Setup {
      val riUpdateVerification = ResponsibleIndividualUpdateVerification(
        ResponsibleIndividualVerificationId(code),
        applicationId,
        Submission.Id.random,
        0,
        "App name",
        LocalDateTime.now(ZoneOffset.UTC),
        responsibleIndividual,
        "Mr Admin",
        "admin@example.com".toLaxEmail,
        ResponsibleIndividualVerificationState.INITIAL
      )

      when(mockSubmissionsConnector.fetchResponsibleIndividualVerification(eqTo(code))(*)).thenReturn(successful(Some(riUpdateVerification)))
      ApplicationServiceMock.fetchByApplicationIdReturns(applicationId, application)
      ApplicationServiceMock.acceptResponsibleIndividualVerification(applicationId, code)

      val result = await(underTest.accept(code))

      result shouldBe 'Right
      result.right.value shouldBe riUpdateVerification
      verify(mockDeskproConnector, never).createTicket(*[ResponsibleIndividualVerificationId], *)(*)
    }
  }

  "decline" should {
    "successfully return a riVerification record for decline" in new Setup {
      when(mockSubmissionsConnector.fetchResponsibleIndividualVerification(eqTo(code))(*)).thenReturn(successful(Some(riVerification)))
      ApplicationServiceMock.declineResponsibleIndividualVerification(applicationId, code)

      val result = await(underTest.decline(code))

      result shouldBe 'Right
      result.right.value shouldBe riVerification
      verify(mockDeskproConnector, never).createTicket(*[ResponsibleIndividualVerificationId], *)(*)
    }
  }
}

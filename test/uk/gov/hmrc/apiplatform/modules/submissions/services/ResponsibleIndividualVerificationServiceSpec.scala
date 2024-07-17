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

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.successful

import org.mockito.captor.ArgCaptor

import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.applications.common.domain.models.FullName
import uk.gov.hmrc.apiplatform.modules.applications.submissions.domain.models.{SubmissionId, _}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.ApplicationId
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.submissions.SubmissionsTestData
import uk.gov.hmrc.apiplatform.modules.submissions.connectors.ThirdPartyApplicationSubmissionsConnector
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models._
import uk.gov.hmrc.apiplatform.modules.tpd.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.DeskproConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.{DeskproTicket, TicketCreated}
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service.ApplicationServiceMock
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{AsyncHmrcSpec, CollaboratorTracker, TestApplications}

class ResponsibleIndividualVerificationServiceSpec extends AsyncHmrcSpec
    with CollaboratorTracker
    with LocalUserIdTracker
    with ApplicationServiceMock
    with TestApplications
    with SubmissionsTestData {

  trait Setup {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    val applicationId              = ApplicationId.random
    val application                = aStandardPendingResponsibleIndividualVerificationApplication()
    val code                       = "12345678"
    val requesterName              = "Mr Submitter"
    val requesterEmail             = "submitter@example.com".toLaxEmail

    val riVerification        = ResponsibleIndividualToUVerification(
      ResponsibleIndividualVerificationId(code),
      applicationId,
      SubmissionId.random,
      0,
      "App name",
      instant,
      ResponsibleIndividualVerificationState.INITIAL
    )
    val responsibleIndividual = ResponsibleIndividual(FullName("bob example"), "bob@example.com".toLaxEmail)

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
      when(mockSubmissionsConnector.fetchLatestSubmission(eqTo(applicationId))(*)).thenReturn(successful(Some(aSubmission)))
      ApplicationServiceMock.fetchByApplicationIdReturns(applicationId, application)
      ApplicationServiceMock.acceptResponsibleIndividualVerification(applicationId, code)
      when(mockDeskproConnector.createTicket(*[ResponsibleIndividualVerificationId], *)(*)).thenReturn(successful(TicketCreated))

      val result = await(underTest.accept(code))

      result.isRight shouldBe true
      result shouldBe Right(riVerification)

      val ticketCapture = ArgCaptor[DeskproTicket]
      verify(mockDeskproConnector).createTicket(eqTo(riVerification.id), ticketCapture.capture)(*)
      val deskproTicket = ticketCapture.value
      deskproTicket.subject shouldBe "New application submitted for checking"
      deskproTicket.name shouldBe application.state.requestedByName.get
      deskproTicket.email.text shouldBe application.state.requestedByEmailAddress.get
      deskproTicket.message should include(riVerification.applicationName)
      deskproTicket.message should include("submitted the following application for production use on the Developer Hub")
      deskproTicket.referrer should include(s"/application/${riVerification.applicationId.value}/check-answers")
    }

    "successfully return a riVerification record for accept and create a deskpro ticket for a terms of use uplift" in new Setup {
      val riVerificationUplift = ResponsibleIndividualTouUpliftVerification(
        ResponsibleIndividualVerificationId(code),
        applicationId,
        SubmissionId.random,
        0,
        "App name",
        instant,
        requesterName,
        requesterEmail,
        ResponsibleIndividualVerificationState.INITIAL
      )

      when(mockSubmissionsConnector.fetchResponsibleIndividualVerification(eqTo(code))(*)).thenReturn(successful(Some(riVerificationUplift)))
      when(mockSubmissionsConnector.fetchLatestSubmission(eqTo(applicationId))(*)).thenReturn(successful(Some(aSubmission)))
      ApplicationServiceMock.fetchByApplicationIdReturns(applicationId, application)
      ApplicationServiceMock.acceptResponsibleIndividualVerification(applicationId, code)
      when(mockDeskproConnector.createTicket(*[ResponsibleIndividualVerificationId], *)(*)).thenReturn(successful(TicketCreated))

      val result = await(underTest.accept(code))

      result.isRight shouldBe true
      result shouldBe Right(riVerificationUplift)

      val ticketCapture = ArgCaptor[DeskproTicket]
      verify(mockDeskproConnector).createTicket(eqTo(riVerificationUplift.id), ticketCapture.capture)(*)
      val deskproTicket = ticketCapture.value
      deskproTicket.subject shouldBe "Terms of use uplift application submitted for checking"
      deskproTicket.name shouldBe requesterName
      deskproTicket.email shouldBe requesterEmail
      deskproTicket.message should include(riVerificationUplift.applicationName)
      deskproTicket.message should include("has submitted a Terms of Use application that has warnings or fails")
      deskproTicket.referrer should include("https://admin.tax.service.gov.uk/api-gatekeeper/terms-of-use")
    }

    "successfully return a riVerification record for accept but don't create a deskpro ticket for a terms of use uplift where the submission status is passed" in new Setup {
      val riVerificationUplift = ResponsibleIndividualTouUpliftVerification(
        ResponsibleIndividualVerificationId(code),
        applicationId,
        SubmissionId.random,
        0,
        "App name",
        instant,
        requesterName,
        requesterEmail,
        ResponsibleIndividualVerificationState.INITIAL
      )

      when(mockSubmissionsConnector.fetchResponsibleIndividualVerification(eqTo(code))(*)).thenReturn(successful(Some(riVerificationUplift)))
      when(mockSubmissionsConnector.fetchLatestSubmission(eqTo(applicationId))(*)).thenReturn(successful(Some(grantedSubmission)))
      ApplicationServiceMock.fetchByApplicationIdReturns(applicationId, application)
      ApplicationServiceMock.acceptResponsibleIndividualVerification(applicationId, code)
      when(mockDeskproConnector.createTicket(*[ResponsibleIndividualVerificationId], *)(*)).thenReturn(successful(TicketCreated))

      val result = await(underTest.accept(code))

      result.isRight shouldBe true
      result shouldBe Right(riVerificationUplift)
      verify(mockDeskproConnector, never).createTicket(*[ResponsibleIndividualVerificationId], *)(*)
    }

    "successfully return a riVerification record for accept but don't create a deskpro ticket for an update" in new Setup {
      val riUpdateVerification = ResponsibleIndividualUpdateVerification(
        ResponsibleIndividualVerificationId(code),
        applicationId,
        SubmissionId.random,
        0,
        "App name",
        instant,
        responsibleIndividual,
        "Mr Admin",
        "admin@example.com".toLaxEmail,
        ResponsibleIndividualVerificationState.INITIAL
      )

      when(mockSubmissionsConnector.fetchResponsibleIndividualVerification(eqTo(code))(*)).thenReturn(successful(Some(riUpdateVerification)))
      when(mockSubmissionsConnector.fetchLatestSubmission(eqTo(applicationId))(*)).thenReturn(successful(None))
      ApplicationServiceMock.fetchByApplicationIdReturns(applicationId, application)
      ApplicationServiceMock.acceptResponsibleIndividualVerification(applicationId, code)

      val result = await(underTest.accept(code))

      result.isRight shouldBe true
      result shouldBe Right(riUpdateVerification)
      verify(mockDeskproConnector, never).createTicket(*[ResponsibleIndividualVerificationId], *)(*)
    }
  }

  "decline" should {
    "successfully return a riVerification record for decline" in new Setup {
      when(mockSubmissionsConnector.fetchResponsibleIndividualVerification(eqTo(code))(*)).thenReturn(successful(Some(riVerification)))
      ApplicationServiceMock.declineResponsibleIndividualVerification(applicationId, code)

      val result = await(underTest.decline(code))

      result.isRight shouldBe true
      result shouldBe Right(riVerification)
      verify(mockDeskproConnector, never).createTicket(*[ResponsibleIndividualVerificationId], *)(*)
    }
  }
}

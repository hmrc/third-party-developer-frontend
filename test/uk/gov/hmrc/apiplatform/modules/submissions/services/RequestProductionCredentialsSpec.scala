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

import uk.gov.hmrc.apiplatform.modules.commands.applications.domain.models.CommandFailures
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{ApplicationId, UserId}
import uk.gov.hmrc.apiplatform.modules.submissions.SubmissionsTestData
import uk.gov.hmrc.apiplatform.modules.submissions.connectors.ThirdPartyApplicationSubmissionsConnector
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models.{ErrorDetails, ResponsibleIndividualVerificationId}
import uk.gov.hmrc.apiplatform.modules.tpd.core.domain.models.User
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.UserSession
import uk.gov.hmrc.apiplatform.modules.tpd.test.data.UserTestData
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.DeskproConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.{DeskproTicket, TicketCreated}
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.connectors.{ApmConnectorMockModule, ApplicationCommandConnectorMockModule}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{AsyncHmrcSpec, CollaboratorTracker, TestApplications}

class RequestProductionCredentialsSpec extends AsyncHmrcSpec
    with CollaboratorTracker
    with LocalUserIdTracker
    with TestApplications
    with SubmissionsTestData
    with UserTestData {

  trait Setup extends ApmConnectorMockModule with ApplicationCommandConnectorMockModule {
    implicit val hc: HeaderCarrier                                          = HeaderCarrier()
    val applicationId                                                       = ApplicationId.random
    val mockSubmissionsConnector: ThirdPartyApplicationSubmissionsConnector = mock[ThirdPartyApplicationSubmissionsConnector]

    val userId: UserId    = UserId.random
    val email             = "test@example.com".toLaxEmail
    val firstName: String = "bob"
    val lastName: String  = "example"
    val name: String      = s"$firstName $lastName"
    val developer: User   = standardDeveloper

    val userSession: UserSession = mock[UserSession]

    val devInSession: User = mock[User]

    when(userSession.developer).thenReturn(devInSession)
    when(devInSession.email).thenReturn(email)
    when(devInSession.displayedName).thenReturn(name)

    val mockDeskproConnector = mock[DeskproConnector]
    val underTest            = new RequestProductionCredentials(ApmConnectorMock.aMock, mockSubmissionsConnector, ApplicationCommandConnectorMock.aMock, mockDeskproConnector, clock)
  }

  "requestProductionCredentials" should {
    "successfully create a ticket if requester is responsible individual" in new Setup {
      val app             = anApplication(appId = applicationId, developerEmail = email)
      val appAfterCommand = app.copy(name = "New app name")
      ApplicationCommandConnectorMock.Dispatch.thenReturnsSuccess(appAfterCommand)
      when(mockSubmissionsConnector.fetchLatestSubmission(eqTo(applicationId))(*)).thenReturn(successful(Some(aSubmission)))
      when(mockDeskproConnector.createTicket(*[Option[UserId]], *)(*)).thenReturn(successful(TicketCreated))
      val result          = await(underTest.requestProductionCredentials(app, userSession, true, false))

      result.isRight shouldBe true
      result shouldBe Right(appAfterCommand)

      val ticketCapture = ArgCaptor[DeskproTicket]
      verify(mockDeskproConnector).createTicket(*[Option[UserId]], ticketCapture.capture)(*)
      ticketCapture.value.subject shouldBe "New application submitted for checking"
      ticketCapture.value.name shouldBe name
      ticketCapture.value.email shouldBe email
      ticketCapture.value.message should include(appAfterCommand.name)
      ticketCapture.value.message should include("submitted the following application for production use on the Developer Hub")
      ticketCapture.value.referrer should include(s"/application/${app.id.value}/check-answers")
    }

    "successfully create a ticket if terms of use uplift and requester is responsible individual" in new Setup {
      val app    = anApplication(appId = applicationId, developerEmail = email)
      ApplicationCommandConnectorMock.Dispatch.thenReturnsSuccess(app)
      when(mockSubmissionsConnector.fetchLatestSubmission(eqTo(applicationId))(*)).thenReturn(successful(Some(aSubmission)))
      when(mockDeskproConnector.createTicket(*[Option[UserId]], *)(*)).thenReturn(successful(TicketCreated))
      val result = await(underTest.requestProductionCredentials(app, userSession, true, true))

      result.isRight shouldBe true
      result shouldBe Right(app)

      val ticketCapture = ArgCaptor[DeskproTicket]
      verify(mockDeskproConnector).createTicket(*[Option[UserId]], ticketCapture.capture)(*)
      ticketCapture.value.subject shouldBe "Terms of use uplift application submitted for checking"
      ticketCapture.value.name shouldBe name
      ticketCapture.value.email shouldBe email
      ticketCapture.value.message should include(app.name)
      ticketCapture.value.message should include("has submitted a Terms of Use application that has warnings or fails")
      ticketCapture.value.referrer should include("https://admin.tax.service.gov.uk/api-gatekeeper/terms-of-use")
    }

    "not create a ticket if terms of use uplift and requester is responsible individual but submission is passed" in new Setup {
      val app    = anApplication(appId = applicationId, developerEmail = email)
      ApplicationCommandConnectorMock.Dispatch.thenReturnsSuccess(app)
      when(mockSubmissionsConnector.fetchLatestSubmission(eqTo(applicationId))(*)).thenReturn(successful(Some(grantedSubmission)))
      val result = await(underTest.requestProductionCredentials(app, userSession, true, true))

      result.isRight shouldBe true
      result shouldBe Right(app)

      verify(mockDeskproConnector, never).createTicket(*[ResponsibleIndividualVerificationId], *)(*)
    }

    "not create a ticket if requester is not responsible individual" in new Setup {
      val app    = anApplication(appId = applicationId, developerEmail = email)
      ApplicationCommandConnectorMock.Dispatch.thenReturnsSuccess(app)
      when(mockSubmissionsConnector.fetchLatestSubmission(eqTo(applicationId))(*)).thenReturn(successful(Some(aSubmission)))
      val result = await(underTest.requestProductionCredentials(app, userSession, false, false))

      result.isRight shouldBe true
      result shouldBe Right(app)

      verify(mockDeskproConnector, never).createTicket(*[ResponsibleIndividualVerificationId], *)(*)
    }

    "fails to create a ticket if the application is not in the correct state" in new Setup {
      val app = anApplication(appId = applicationId, developerEmail = email)
      ApplicationCommandConnectorMock.Dispatch.thenFailsWith(CommandFailures.GenericFailure("App is not in TESTING state"))

      val result = await(underTest.requestProductionCredentials(app, userSession, true, false))
      result.isLeft shouldBe true
      result.left.value shouldBe ErrorDetails("submitSubmission001", s"Submission failed - App is not in TESTING state")

      verify(mockDeskproConnector, never).createTicket(*[ResponsibleIndividualVerificationId], *)(*)
    }

    "fails to create a ticket if the submission is not found" in new Setup {
      val app = anApplication(appId = applicationId, developerEmail = email)
      ApplicationCommandConnectorMock.Dispatch.thenReturnsSuccess(app)
      when(mockSubmissionsConnector.fetchLatestSubmission(eqTo(applicationId))(*)).thenReturn(successful(None))

      val result = await(underTest.requestProductionCredentials(app, userSession, true, false))

      result.isLeft shouldBe true
      result.left.value shouldBe ErrorDetails("submitSubmission001", s"No submission record found for ${applicationId}")

      verify(mockDeskproConnector, never).createTicket(*[ResponsibleIndividualVerificationId], *)(*)
    }

    "fails to create a ticket if application already exists" in new Setup {
      val app = anApplication(appId = applicationId, developerEmail = email)
      ApplicationCommandConnectorMock.Dispatch.thenFailsWith(CommandFailures.DuplicateApplicationName("New app name"))

      val result = await(underTest.requestProductionCredentials(app, userSession, true, false))
      result.isLeft shouldBe true
      result.left.value shouldBe ErrorDetails("submitSubmission001", s"Submission failed - An application already exists for the name 'New app name' ")

      verify(mockDeskproConnector, never).createTicket(*[ResponsibleIndividualVerificationId], *)(*)
    }

    "fails to create a ticket if application name is invalid" in new Setup {
      val app = anApplication(appId = applicationId, developerEmail = email)
      ApplicationCommandConnectorMock.Dispatch.thenFailsWith(CommandFailures.InvalidApplicationName("Rude word name"))

      val result = await(underTest.requestProductionCredentials(app, userSession, true, false))
      result.isLeft shouldBe true
      result.left.value shouldBe ErrorDetails("submitSubmission001", s"Submission failed - The application name 'Rude word name' contains words that are prohibited")

      verify(mockDeskproConnector, never).createTicket(*[ResponsibleIndividualVerificationId], *)(*)
    }
  }
}

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

package uk.gov.hmrc.apiplatform.modules.applications.services

import scala.concurrent.ExecutionContext.Implicits.global

import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models._
import uk.gov.hmrc.apiplatform.modules.commands.applications.domain.models.{ApplicationCommands, DispatchSuccessResult}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils._
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors._
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.connectors._
import uk.gov.hmrc.thirdpartydeveloperfrontend.testdata._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{AsyncHmrcSpec, _}

class CollaboratorServiceSpec
    extends AsyncHmrcSpec
    with CommonUserFixtures
    with ApplicationWithCollaboratorsFixtures
    with FixedClock {

  trait Setup
      extends ThirdPartyDeveloperConnectorMockModule
      with ApmConnectorMockModule
      with ApmConnectorCommandModuleMockModule {

    implicit val hc: HeaderCarrier = HeaderCarrier()

    val mockApmConnector: ApmConnector = mock[ApmConnector]

    val collaboratorService = new CollaboratorService(
      ApmConnectorCommandModuleMock.aMock,
      TPDMock.aMock,
      FixedClock.clock
    )

  }

  "add teamMember" should {
    "add teamMember successful" in new Setup {
      TPDMock.FetchByEmails.returnsEmptySeq()
      TPDMock.GetOrCreateUser.succeedsWith(devAsCollaborator.userId)

      val mockResponse = mock[ApplicationWithCollaborators]

      ApmConnectorCommandModuleMock.Dispatch.thenReturnsSuccess(mockResponse)

      val result = await(collaboratorService.addTeamMember(standardApp, devEmail, Collaborator.Roles.DEVELOPER, adminEmail))
      result.isRight shouldBe true

      inside(result) {
        case Right(DispatchSuccessResult(response)) =>
          response shouldBe mockResponse
          inside(ApmConnectorCommandModuleMock.Dispatch.verifyCommand()) {
            case ApplicationCommands.AddCollaborator(actor, collaborator, _) =>
              actor shouldBe Actors.AppCollaborator(adminEmail)
              collaborator shouldBe devAsCollaborator
          }
      }
    }
  }
  "remove teamMember" should {
    "remove teamMember successfully from production" in new Setup {
      TPDMock.FetchByEmails.returnsEmptySeq()

      val mockResponse = mock[ApplicationWithCollaborators]

      ApmConnectorCommandModuleMock.Dispatch.thenReturnsSuccess(mockResponse) // .thenReturnsSuccessFor(command)(productionApplication)

      val result = await(collaboratorService.removeTeamMember(standardApp, devEmail, adminEmail))
      result.isRight shouldBe true

      inside(result) {
        case Right(DispatchSuccessResult(response)) =>
          response shouldBe mockResponse
          inside(ApmConnectorCommandModuleMock.Dispatch.verifyCommand()) {
            case ApplicationCommands.RemoveCollaborator(actor, collaborator, _) =>
              actor shouldBe Actors.AppCollaborator(adminEmail)
              collaborator shouldBe devAsCollaborator
          }
      }
    }

    "remove teamMember determines admins to email" in new Setup with CollaboratorTracker with LocalUserIdTracker {
      val verifiedAdmin      = "verified@example.com".toLaxEmail.asAdministratorCollaborator
      val unverifiedAdmin    = "unverified@example.com".toLaxEmail.asAdministratorCollaborator
      val removerAdmin       = "admin.email@example.com".toLaxEmail.asAdministratorCollaborator
      val verifiedDeveloper  = "developer@example.com".toLaxEmail.asDeveloperCollaborator
      val teamMemberToRemove = "to.remove@example.com".toLaxEmail.asAdministratorCollaborator
      val application        = standardApp.withCollaborators(verifiedAdmin, unverifiedAdmin, removerAdmin, verifiedDeveloper, teamMemberToRemove)

      val notExcluded = Set(verifiedAdmin, unverifiedAdmin).map(_.emailAddress)

      TPDMock.FetchByEmails.returnsSuccessFor(notExcluded)(Seq(
        adminUser.copy(email = verifiedAdmin.emailAddress),
        adminUser.copy(email = unverifiedAdmin.emailAddress, verified = false)
      ))
      val mockResponse = mock[ApplicationWithCollaborators]

      ApmConnectorCommandModuleMock.Dispatch.thenReturnsSuccess(mockResponse) // .thenReturnsSuccessFor(command)(productionApplication)

      val result = await(collaboratorService.removeTeamMember(application, teamMemberToRemove.emailAddress, removerAdmin.emailAddress))
      result.isRight shouldBe true

      ApmConnectorCommandModuleMock.Dispatch.verifyAdminsToEmail() shouldBe Set(verifiedAdmin.emailAddress)
    }

    "determineOtherAdmins" should {
      "exclude the actor and the removed" in new Setup with CollaboratorTracker with LocalUserIdTracker {
        val verifiedAdmin      = "verified@example.com".toLaxEmail.asAdministratorCollaborator
        val unverifiedAdmin    = "unverified@example.com".toLaxEmail.asAdministratorCollaborator
        val removerAdmin       = "admin.email@example.com".toLaxEmail.asAdministratorCollaborator
        val verifiedDeveloper  = "developer@example.com".toLaxEmail.asDeveloperCollaborator
        val teamMemberToRemove = "to.remove@example.com".toLaxEmail.asAdministratorCollaborator

        val collaborators = Set(verifiedAdmin, unverifiedAdmin, removerAdmin, verifiedDeveloper, teamMemberToRemove)
        val exclusions    = Set(removerAdmin, teamMemberToRemove).map(_.emailAddress)

        val result = collaboratorService.determineOtherAdmins(collaborators, exclusions)

        result shouldBe Set(verifiedAdmin, unverifiedAdmin).map(_.emailAddress)
      }
    }
  }

  "getCollaboratorUsers" should {
    "return the User instances relating to the supplied Collaborator instances" in new Setup with CollaboratorTracker with LocalUserIdTracker{
      val adminCollaborator = ("admin-email@example.com".toLaxEmail.asAdministratorCollaborator)
      val developerCollaborator = ("dev-email@example.com".toLaxEmail.asAdministratorCollaborator)
      val collaborators = Set[Collaborator](adminCollaborator, developerCollaborator)
      val adminUserFromCollaborator = adminUser.copy(email = adminCollaborator.emailAddress)
      val developerUserFromCollaborator = devUser.copy(email = adminCollaborator.emailAddress, verified = true)

      TPDMock.FetchByEmails.returnsSuccessFor(collaborators.map(_.emailAddress))(Seq(
        adminUserFromCollaborator, developerUserFromCollaborator
      ))
      val result = await(collaboratorService.getCollaboratorUsers(collaborators))

      result shouldBe List(adminUserFromCollaborator, developerUserFromCollaborator)
    }
  }
}

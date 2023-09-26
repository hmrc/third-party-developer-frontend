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

import java.time.{LocalDateTime, Period, ZoneOffset}
import java.util.UUID.randomUUID
import scala.concurrent.ExecutionContext.Implicits.global

import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ApiStatus
import uk.gov.hmrc.apiplatform.modules.applications.domain.models.Collaborator
import uk.gov.hmrc.apiplatform.modules.commands.applications.domain.models.{ApplicationCommands, DispatchSuccessResult}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder._
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.User
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.VersionSubscription
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.connectors._
import uk.gov.hmrc.thirdpartydeveloperfrontend.testdata.CollaboratorsTestData
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{AsyncHmrcSpec, LocalUserIdTracker}

class CollaboratorServiceSpec extends AsyncHmrcSpec
    with SubscriptionsBuilder
    with ApplicationBuilder
    with LocalUserIdTracker
    with DeveloperSessionBuilder
    with DeveloperTestData
    with CollaboratorsTestData {

  val versionOne  = ApiVersionNbr("1.0")
  val versionTwo  = ApiVersionNbr("2.0")
  val grantLength = Period.ofDays(547)

  trait Setup
      extends FixedClock
      with ThirdPartyDeveloperConnectorMockModule
      with ApplicationCommandConnectorMockModule {

    implicit val hc: HeaderCarrier = HeaderCarrier()

    val mockDeskproConnector: DeskproConnector = mock[DeskproConnector]
    val mockApmConnector: ApmConnector         = mock[ApmConnector]

    val collaboratorService = new CollaboratorService(
      mockApmConnector,
      ApplicationCommandConnectorMock.aMock,
      TPDMock.aMock,
      FixedClock.clock
    )
  }

  def version(version: ApiVersionNbr, status: ApiStatus, subscribed: Boolean): VersionSubscription =
    VersionSubscription(ApiVersionDefinition(version, status), subscribed)

  val productionApplicationId = ApplicationId.random
  val productionClientId      = ClientId(s"client-id-${randomUUID().toString}")

  val productionApplication: Application =
    Application(
      productionApplicationId,
      productionClientId,
      "name",
      LocalDateTime.now(ZoneOffset.UTC),
      Some(LocalDateTime.now(ZoneOffset.UTC)),
      None,
      grantLength,
      Environment.PRODUCTION,
      Some("description"),
      mixOfAllTypesOfCollaborators
    )

  "add teamMember" should {
    "add teamMember successful" in new Setup {
      TPDMock.FetchByEmails.returnsEmptySeq()
      TPDMock.GetOrCreateUser.succeedsWith(developerAsCollaborator.userId)

      val mockResponse = mock[Application]

      ApplicationCommandConnectorMock.Dispatch.thenReturnsSuccess(mockResponse)

      val result = await(collaboratorService.addTeamMember(productionApplication, developerEmail, Collaborator.Roles.DEVELOPER, administratorEmail))
      result.isRight shouldBe true

      inside(result) {
        case Right(DispatchSuccessResult(response)) =>
          response shouldBe mockResponse
          inside(ApplicationCommandConnectorMock.Dispatch.verifyCommand()) {
            case ApplicationCommands.AddCollaborator(actor, collaborator, _) =>
              actor shouldBe Actors.AppCollaborator(administratorEmail)
              collaborator shouldBe developerAsCollaborator
          }
      }
    }
  }
  "remove teamMember" should {
    "remove teamMember successfully from production" in new Setup {
      TPDMock.FetchByEmails.returnsEmptySeq()

      val mockResponse = mock[Application]

      ApplicationCommandConnectorMock.Dispatch.thenReturnsSuccess(mockResponse) // .thenReturnsSuccessFor(command)(productionApplication)

      val result = await(collaboratorService.removeTeamMember(productionApplication, developerEmail, administratorEmail))
      result.isRight shouldBe true

      inside(result) {
        case Right(DispatchSuccessResult(response)) =>
          response shouldBe mockResponse
          inside(ApplicationCommandConnectorMock.Dispatch.verifyCommand()) {
            case ApplicationCommands.RemoveCollaborator(actor, collaborator, _) =>
              actor shouldBe Actors.AppCollaborator(administratorEmail)
              collaborator shouldBe developerAsCollaborator
          }
      }
    }

    "remove teamMember determines admins to email" in new Setup {
      import cats.syntax.option._
      val verifiedAdmin      = "verified@example.com".toLaxEmail.asAdministratorCollaborator
      val unverifiedAdmin    = "unverified@example.com".toLaxEmail.asAdministratorCollaborator
      val removerAdmin       = "admin.email@example.com".toLaxEmail.asAdministratorCollaborator
      val verifiedDeveloper  = "developer@example.com".toLaxEmail.asDeveloperCollaborator
      val teamMemberToRemove = "to.remove@example.com".toLaxEmail.asAdministratorCollaborator
      val application        = productionApplication.copy(collaborators = Set(verifiedAdmin, unverifiedAdmin, removerAdmin, verifiedDeveloper, teamMemberToRemove))

      val notExcluded = Set(verifiedAdmin, unverifiedAdmin).map(_.emailAddress)

      TPDMock.FetchByEmails.returnsSuccessFor(notExcluded)(Seq(User(verifiedAdmin.emailAddress, true.some), User(unverifiedAdmin.emailAddress, false.some)))
      val mockResponse = mock[Application]

      ApplicationCommandConnectorMock.Dispatch.thenReturnsSuccess(mockResponse) // .thenReturnsSuccessFor(command)(productionApplication)

      val result = await(collaboratorService.removeTeamMember(application, teamMemberToRemove.emailAddress, removerAdmin.emailAddress))
      result.isRight shouldBe true

      ApplicationCommandConnectorMock.Dispatch.verifyAdminsToEmail() shouldBe Set(verifiedAdmin.emailAddress)
    }

    "determineOtherAdmins" should {
      "exclude the actor and the removed" in new Setup {
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
}

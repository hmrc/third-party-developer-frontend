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
import uk.gov.hmrc.apiplatform.modules.apis.domain.models._
import uk.gov.hmrc.apiplatform.modules.applications.domain.models.{ApplicationId, ClientId}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.connectors.ThirdPartyApplicationConnectorMockModule
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder._
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.VersionSubscription
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{AsyncHmrcSpec, FixedClock, LocalUserIdTracker}

import java.time.{LocalDateTime, Period, ZoneOffset}
import java.util.UUID.randomUUID
import scala.concurrent.Future
import scala.concurrent.Future.{successful, failed}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.User
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.BridgedConnector
import uk.gov.hmrc.apiplatform.modules.commands.applications.domain.models.RemoveCollaborator
import uk.gov.hmrc.apiplatform.modules.applications.domain.models.Collaborators
import uk.gov.hmrc.apiplatform.modules.common.domain.models.Actors
import uk.gov.hmrc.apiplatform.modules.commands.applications.domain.models.DispatchSuccessResult
import uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.connectors.ThirdPartyDeveloperConnectorMockModule
import scala.annotation.bridge

class CollaboratorServiceSpec extends AsyncHmrcSpec
    with SubscriptionsBuilder
    with ApplicationBuilder
    with LocalUserIdTracker
    with DeveloperSessionBuilder
    with DeveloperTestData {

  val versionOne  = ApiVersion("1.0")
  val versionTwo  = ApiVersion("2.0")
  val grantLength = Period.ofDays(547)

  trait Setup 
      extends FixedClock
      with ThirdPartyDeveloperConnectorMockModule with ThirdPartyApplicationConnectorMockModule {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    private val mockAppConfig = mock[ApplicationConfig]


    val mockDeskproConnector: DeskproConnector                   = mock[DeskproConnector]
    val mockApmConnector: ApmConnector                           = mock[ApmConnector]
    
    val collaboratorService = new CollaboratorService(
      mockApmConnector,
      BridgedAppConnector.connector,
      TPDMock.aMock
    )
   }

  def version(version: ApiVersion, status: APIStatus, subscribed: Boolean): VersionSubscription =
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
      Set()
    )
 


  "remove teamMember" should {
    val email         = "john.bloggs@example.com".toLaxEmail
    val admin         = "admin@example.com".toLaxEmail
    val adminsToEmail = Set.empty[LaxEmailAddress]
    val collaboratorToRemove  = email.asDeveloperCollaborator

    "remove teamMember successfully from production" in new Setup {

      TPDMock.FetchByEmails.returnsEmptySeq


      val command = RemoveCollaborator(Actors.AppCollaborator(admin), collaboratorToRemove, LocalDateTime.now())

      when(PrincipalAppConnector.aMock.dispatch(productionApplicationId, command, adminsToEmail))
        .thenReturn(successful(Right(DispatchSuccessResult(productionApplication))))
      
      await(collaboratorService.removeTeamMember(productionApplication, email, admin)) shouldBe ApplicationUpdateSuccessful
    }


  //   "include correct set of admins to email" in new Setup {

  //     private val verifiedAdmin      = "verified@example.com".toLaxEmail.asAdministratorCollaborator
  //     private val unverifiedAdmin    = "unverified@example.com".toLaxEmail.asAdministratorCollaborator
  //     private val removerAdmin       = "admin.email@example.com".toLaxEmail.asAdministratorCollaborator
  //     private val verifiedDeveloper  = "developer@example.com".toLaxEmail.asDeveloperCollaborator
  //     private val teamMemberToRemove = "to.remove@example.com".toLaxEmail.asAdministratorCollaborator

  //     val nonRemoverAdmins = Seq(
  //       User("verified@example.com".toLaxEmail, Some(true)),
  //       User("unverified@example.com".toLaxEmail, Some(false))
  //     )

  //     private val application = productionApplication.copy(collaborators = Set(verifiedAdmin, unverifiedAdmin, removerAdmin, verifiedDeveloper, teamMemberToRemove))

  //     private val response = ApplicationUpdateSuccessful

  //     when(mockDeveloperConnector.fetchByEmails(eqTo(Set("verified@example.com".toLaxEmail, "unverified@example.com".toLaxEmail)))(*))
  //       .thenReturn(successful(nonRemoverAdmins))
  //     theProductionConnectorthenReturnTheApplication(productionApplicationId, application)
  //     when(mockProductionApplicationConnector.removeTeamMember(*[ApplicationId], *[LaxEmailAddress], *[LaxEmailAddress], *[Set[LaxEmailAddress]])(*)).thenReturn(successful(response))

  //     await(collaboratorService.removeTeamMember(application, teamMemberToRemove.emailAddress, removerAdmin.emailAddress)) shouldBe response
  //     verify(mockProductionApplicationConnector).removeTeamMember(
  //       eqTo(productionApplicationId),
  //       eqTo(teamMemberToRemove.emailAddress),
  //       eqTo(removerAdmin.emailAddress),
  //       eqTo(Set(verifiedAdmin.emailAddress))
  //     )(*)
  //   }
  }
}

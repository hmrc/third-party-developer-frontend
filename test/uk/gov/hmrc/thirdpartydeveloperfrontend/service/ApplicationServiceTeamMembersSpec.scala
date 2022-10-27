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

package uk.gov.hmrc.thirdpartydeveloperfrontend.service

import uk.gov.hmrc.thirdpartydeveloperfrontend.domain._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.{DeskproTicket, TicketCreated}
import uk.gov.hmrc.play.audit.http.connector.AuditResult.Success

import scala.concurrent.Future.{failed, successful}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.User

class ApplicationServiceTeamMembersSpec extends ApplicationServiceCommonSetup {

  trait Setup extends CommonSetup

  "remove teamMember" should {
    val email = "john.bloggs@example.com"
    val admin = "admin@example.com"
    val adminsToEmail = Set.empty[String]

    "remove teamMember successfully from production" in new Setup {
      when(mockDeveloperConnector.fetchByEmails(*)(*)).thenReturn(successful(Seq.empty))
      theProductionConnectorthenReturnTheApplication(productionApplicationId, productionApplication)
      when(mockProductionApplicationConnector.removeTeamMember(productionApplicationId, email, admin, adminsToEmail))
        .thenReturn(successful(ApplicationUpdateSuccessful))
      await(applicationService.removeTeamMember(productionApplication, email, admin)) shouldBe ApplicationUpdateSuccessful
    }

    "propagate ApplicationNeedsAdmin from connector from production" in new Setup {
      when(mockDeveloperConnector.fetchByEmails(*)(*)).thenReturn(successful(Seq.empty))
      theProductionConnectorthenReturnTheApplication(productionApplicationId, productionApplication)
      when(mockProductionApplicationConnector.removeTeamMember(productionApplicationId, email, admin, adminsToEmail))
        .thenReturn(failed(new ApplicationNeedsAdmin))
      intercept[ApplicationNeedsAdmin](await(applicationService.removeTeamMember(productionApplication, email, admin)))
    }

    "propagate ApplicationNotFound from connector from production" in new Setup {
      when(mockDeveloperConnector.fetchByEmails(*)(*)).thenReturn(successful(Seq.empty))
      theProductionConnectorthenReturnTheApplication(productionApplicationId, productionApplication)
      when(mockProductionApplicationConnector.removeTeamMember(productionApplicationId, email, admin, adminsToEmail))
        .thenReturn(failed(new ApplicationNotFound))
      intercept[ApplicationNotFound](await(applicationService.removeTeamMember(productionApplication, email, admin)))
    }

    "remove teamMember successfully from sandbox" in new Setup {
      when(mockDeveloperConnector.fetchByEmails(*)(*)).thenReturn(successful(Seq.empty))
      theSandboxConnectorthenReturnTheApplication(sandboxApplicationId, sandboxApplication)
      when(mockSandboxApplicationConnector.removeTeamMember(sandboxApplicationId, email, admin, adminsToEmail))
        .thenReturn(successful(ApplicationUpdateSuccessful))
      await(applicationService.removeTeamMember(sandboxApplication, email, admin)) shouldBe ApplicationUpdateSuccessful
    }

    "propagate ApplicationNeedsAdmin from connector from sandbox" in new Setup {
      when(mockDeveloperConnector.fetchByEmails(*)(*)).thenReturn(successful(Seq.empty))
      theSandboxConnectorthenReturnTheApplication(sandboxApplicationId, sandboxApplication)
      when(mockSandboxApplicationConnector.removeTeamMember(sandboxApplicationId, email, admin, adminsToEmail))
        .thenReturn(failed(new ApplicationNeedsAdmin))
      intercept[ApplicationNeedsAdmin](await(applicationService.removeTeamMember(sandboxApplication, email, admin)))
    }

    "propagate ApplicationNotFound from connector from sandbox" in new Setup {
      when(mockDeveloperConnector.fetchByEmails(*)(*)).thenReturn(successful(Seq.empty))
      theSandboxConnectorthenReturnTheApplication(sandboxApplicationId, sandboxApplication)
      when(mockSandboxApplicationConnector.removeTeamMember(sandboxApplicationId, email, admin, adminsToEmail))
        .thenReturn(failed(new ApplicationNotFound))
      intercept[ApplicationNotFound](await(applicationService.removeTeamMember(sandboxApplication, email, admin)))
    }

    "include correct set of admins to email" in new Setup {

      private val verifiedAdmin = "verified@example.com".asAdministratorCollaborator
      private val unverifiedAdmin = "unverified@example.com".asAdministratorCollaborator
      private val removerAdmin = "admin.email@example.com".asAdministratorCollaborator
      private val verifiedDeveloper = "developer@example.com".asDeveloperCollaborator
      private val teamMemberToRemove = "to.remove@example.com".asAdministratorCollaborator

      val nonRemoverAdmins = Seq(
        User("verified@example.com", Some(true)),
        User("unverified@example.com", Some(false))
      )

      private val application = productionApplication.copy(collaborators = Set(verifiedAdmin, unverifiedAdmin, removerAdmin, verifiedDeveloper, teamMemberToRemove))

      private val response = ApplicationUpdateSuccessful

      when(mockDeveloperConnector.fetchByEmails(eqTo(Set("verified@example.com", "unverified@example.com")))(*))
        .thenReturn(successful(nonRemoverAdmins))
      theProductionConnectorthenReturnTheApplication(productionApplicationId, application)
      when(mockProductionApplicationConnector.removeTeamMember(*[ApplicationId], *, *, *)(*)).thenReturn(successful(response))

      await(applicationService.removeTeamMember(application, teamMemberToRemove.emailAddress, removerAdmin.emailAddress)) shouldBe response
      verify(mockProductionApplicationConnector).removeTeamMember(
        eqTo(productionApplicationId),
        eqTo(teamMemberToRemove.emailAddress),
        eqTo(removerAdmin.emailAddress),
        eqTo(Set(verifiedAdmin.emailAddress))
      )(*)
    }
  }

  "request delete developer" should {
    val developerName = "Testy McTester"
    val developerEmail = "testy@example.com"

    "correctly create a deskpro ticket and audit record" in new Setup {
      when(mockDeskproConnector.createTicket(any[DeskproTicket])(eqTo(hc)))
        .thenReturn(successful(TicketCreated))
      when(mockAuditService.audit(any[AuditAction], any[Map[String, String]])(eqTo(hc)))
        .thenReturn(successful(Success))

      await(applicationService.requestDeveloperAccountDeletion(developerName, developerEmail))

      verify(mockDeskproConnector, times(1)).createTicket(any[DeskproTicket])(eqTo(hc))
      verify(mockAuditService, times(1)).audit(any[AuditAction], any[Map[String, String]])(eqTo(hc))
    }
  }
}

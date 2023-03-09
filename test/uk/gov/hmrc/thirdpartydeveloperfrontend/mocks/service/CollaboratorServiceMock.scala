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

package uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service

import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import uk.gov.hmrc.apiplatform.modules.applications.services.CollaboratorService
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Application

import cats.data.NonEmptyList
import cats.syntax.all._
import cats.instances.future._
import uk.gov.hmrc.apiplatform.modules.commands.applications.domain.models.DispatchSuccessResult
import uk.gov.hmrc.apiplatform.modules.commands.applications.domain.models.CommandFailure
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import uk.gov.hmrc.apiplatform.modules.applications.domain.models.Collaborator
import uk.gov.hmrc.apiplatform.modules.applications.domain.models.ApplicationId
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.TeamMemberAlreadyExists
import scala.concurrent.Future.failed
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.ApplicationNotFound

trait CollaboratorServiceMockModule extends MockitoSugar with ArgumentMatchersSugar {

  type Err = NonEmptyList[CommandFailure]

  trait AbstractCollaboratorServiceMock {
    def aMock: CollaboratorService

    object AddTeamMember {
      def succeeds() =
        when(aMock.addTeamMember(*[ApplicationId], *[LaxEmailAddress], *[Collaborator.Role], *[LaxEmailAddress])(*))
          .thenReturn(().pure[Future])

      def teamMemberAlreadyExists() =
        when(aMock.addTeamMember(*[ApplicationId], *[LaxEmailAddress], *[Collaborator.Role], *[LaxEmailAddress])(*))
          .thenReturn(failed(new TeamMemberAlreadyExists))

      def applicationNotFound() =
        when(aMock.addTeamMember(*[ApplicationId], *[LaxEmailAddress], *[Collaborator.Role], *[LaxEmailAddress])(*))
        .thenReturn(failed(new ApplicationNotFound))
        
      def verifyCalledFor(appId: ApplicationId, newEmail: LaxEmailAddress, newRole: Collaborator.Role, requestingEmail: LaxEmailAddress) =
         verify(aMock, atLeastOnce).addTeamMember(eqTo(appId), eqTo(newEmail), eqTo(newRole), eqTo(requestingEmail))(*)

      def verifyNeverCalled() =
        verify(aMock, never).addTeamMember(*[ApplicationId], *[LaxEmailAddress], *[Collaborator.Role], *[LaxEmailAddress])(*)
    }

    object RemoveTeamMember {
      def succeeds(app: Application) =
        when(aMock.removeTeamMember(*, *[LaxEmailAddress], *[LaxEmailAddress])(*))
          .thenReturn(DispatchSuccessResult(app).asRight[Err].pure[Future])        

      def thenReturnsSuccessFor(requestingEmail: LaxEmailAddress)(app: Application) =
        when(aMock.removeTeamMember(*, *[LaxEmailAddress], eqTo(requestingEmail))(*))
          .thenReturn(DispatchSuccessResult(app).asRight[Err].pure[Future])        

     def verifyCalledFor(app: Application, emailToRemove: LaxEmailAddress, requestingEmail: LaxEmailAddress) =
         verify(aMock, atLeastOnce).removeTeamMember(eqTo(app), eqTo(emailToRemove), eqTo(requestingEmail))(*)

      def verifyNeverCalled() =
        verify(aMock, never).removeTeamMember(*, *[LaxEmailAddress], *[LaxEmailAddress])(*)
    }
  }

  object CollaboratorServiceMock extends AbstractCollaboratorServiceMock {
    val aMock = mock[CollaboratorService]
  }
}


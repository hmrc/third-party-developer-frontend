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

import scala.concurrent.ExecutionContext.Implicits.global

import org.mockito.{ArgumentMatchersSugar, MockitoSugar}

import uk.gov.hmrc.apiplatform.modules.applications.domain.models.Collaborator
import uk.gov.hmrc.apiplatform.modules.applications.services.CollaboratorService
import uk.gov.hmrc.apiplatform.modules.commands.applications.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Application

trait CollaboratorServiceMockModule extends MockitoSugar with ArgumentMatchersSugar {

  trait AbstractCollaboratorServiceMock {
    import Types._

    def aMock: CollaboratorService

    object AddTeamMember {

      def succeeds() =
        when(aMock.addTeamMember(*, *[LaxEmailAddress], *[Collaborator.Role], *[LaxEmailAddress])(*))
          .thenReturn(DispatchSuccessResult(mock[Application]).asSuccess)

      def teamMemberAlreadyExists() =
        when(aMock.addTeamMember(*, *[LaxEmailAddress], *[Collaborator.Role], *[LaxEmailAddress])(*))
          .thenReturn(CommandFailures.CollaboratorAlreadyExistsOnApp.asFailure)

      def applicationNotFound() =
        when(aMock.addTeamMember(*, *[LaxEmailAddress], *[Collaborator.Role], *[LaxEmailAddress])(*))
          .thenReturn(CommandFailures.ApplicationNotFound.asFailure)

      def verifyCalledFor(newEmail: LaxEmailAddress, newRole: Collaborator.Role, requestingEmail: LaxEmailAddress) =
        verify(aMock, atLeastOnce).addTeamMember(*, eqTo(newEmail), eqTo(newRole), eqTo(requestingEmail))(*)

      def verifyNeverCalled() =
        verify(aMock, never).addTeamMember(*, *[LaxEmailAddress], *[Collaborator.Role], *[LaxEmailAddress])(*)
    }

    object RemoveTeamMember {

      def succeeds(app: Application) =
        when(aMock.removeTeamMember(*, *[LaxEmailAddress], *[LaxEmailAddress])(*)).thenReturn(DispatchSuccessResult(app).asSuccess)

      def thenReturnsSuccessFor(requestingEmail: LaxEmailAddress)(app: Application) =
        when(aMock.removeTeamMember(*, *[LaxEmailAddress], eqTo(requestingEmail))(*)).thenReturn(DispatchSuccessResult(app).asSuccess)

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

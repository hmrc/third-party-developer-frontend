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

package uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.connectors

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.successful

import org.mockito.captor.ArgCaptor
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.ApplicationWithCollaborators
import uk.gov.hmrc.apiplatform.modules.commands.applications.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{ApplicationId, LaxEmailAddress}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.ApplicationUpdateSuccessful

trait ApmConnectorCommandModuleMockModule extends MockitoSugar with ArgumentMatchersSugar {
  self: ApmConnectorMockModule =>

  object ApmConnectorCommandModuleMock {
    val aMock = ApmConnectorMock.aMock

    val CHT = new CommandHandlerTypes[DispatchSuccessResult] {}

    import CHT.Implicits._

    object DispatchWithThrow {

      def thenReturnsSuccess(app: ApplicationWithCollaborators) = {
        when(aMock.dispatchWithThrow(*[ApplicationId], *, *)(*)).thenReturn(successful(ApplicationUpdateSuccessful))
      }
    }

    object Dispatch {

      def thenReturnsSuccess(app: ApplicationWithCollaborators) = {
        when(aMock.dispatch(*[ApplicationId], *, *)(*)).thenReturn(DispatchSuccessResult(app).asSuccess)
      }

      def thenReturnsSuccessFor(command: ApplicationCommand)(app: ApplicationWithCollaborators) = {
        when(aMock.dispatch(*[ApplicationId], eqTo(command), *)(*)).thenReturn(DispatchSuccessResult(app).asSuccess)
      }

      def thenFailsWith(fail: CommandFailure) = {
        when(aMock.dispatch(*[ApplicationId], *, *)(*)).thenReturn(fail.asFailure)
      }

      def verifyAdminsToEmail() = {
        val captor = ArgCaptor[Set[LaxEmailAddress]]
        verify(aMock).dispatch(*[ApplicationId], *, captor)(*)
        captor.value
      }

      def verifyCommand() = {
        val captor = ArgCaptor[ApplicationCommand]
        verify(aMock).dispatch(*[ApplicationId], captor, *)(*)
        captor.value
      }

      def verifyNeverCalled() = {
        verify(aMock, never).dispatch(*[ApplicationId], *, *)(*)
      }
    }
  }
}

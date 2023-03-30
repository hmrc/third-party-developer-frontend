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
import scala.concurrent.Future

import cats.data.NonEmptyList
import cats.instances.future._
import cats.syntax.all._
import org.mockito.captor.ArgCaptor
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}

import uk.gov.hmrc.apiplatform.modules.applications.domain.models.ApplicationId
import uk.gov.hmrc.apiplatform.modules.commands.applications.domain.models.{ApplicationCommand, CommandFailure, DispatchSuccessResult}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.{ApplicationCommandConnector}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Application

trait ApplicationCommandConnectorMockModule extends MockitoSugar with ArgumentMatchersSugar {

  type Err = NonEmptyList[CommandFailure]

  object ApplicationCommandConnectorMock {
    val aMock: ApplicationCommandConnector = mock[ApplicationCommandConnector]

    object Dispatch {

      def thenReturnsSuccess(app: Application) {
        when(aMock.dispatch(*[ApplicationId], *, *)(*))
          .thenReturn(DispatchSuccessResult(app).asRight[Err].pure[Future])
      }

      def thenReturnsSuccessFor(command: ApplicationCommand)(app: Application) {
        when(aMock.dispatch(*[ApplicationId], eqTo(command), *)(*))
          .thenReturn(DispatchSuccessResult(app).asRight[Err].pure[Future])
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
    }
  }
}
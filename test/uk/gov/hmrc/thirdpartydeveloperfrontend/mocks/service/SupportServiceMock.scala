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

import scala.concurrent.Future.successful

import org.mockito.{ArgumentMatchersSugar, MockitoSugar}

import uk.gov.hmrc.http.HttpException

import uk.gov.hmrc.apiplatform.modules.apis.domain.models.{ApiDefinition, ServiceName}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.Support
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.flows.SupportFlow
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.SupportService

trait SupportServiceMockModule extends MockitoSugar with ArgumentMatchersSugar {

  trait AbstractSupportServiceMock {
    def aMock: SupportService

    object FetchAllPublicApis {

      def succeeds(apis: List[ApiDefinition]) =
        when(aMock.fetchAllPublicApis(*)(*)).thenReturn(successful(apis))
    }

    object GetSupportFlow {
      def succeeds(flow: SupportFlow = SupportFlow("sessionId", Support.UsingAnApi.id)) = when(aMock.getSupportFlow(*)).thenReturn(successful(flow))
    }

    object SubmitTicket {
      def succeeds(flow: SupportFlow = SupportFlow("sessionId", Support.UsingAnApi.id)) = when(aMock.submitTicket(*, *)(*)).thenReturn(successful(flow))
    }

    object CreateSupportFlow {
      def succeeds(flow: SupportFlow = SupportFlow("sessionId", Support.MakingAnApiCall.id)) = when(aMock.createFlow(*, *)).thenReturn(successful(flow))
    }

    object UpdateApiChoice {

      def succeeds(flow: SupportFlow = SupportFlow("sessionId", Support.MakingAnApiCall.id)) =
        when(aMock.updateApiChoice(*, *[ServiceName], *)(*)).thenReturn(successful(Right(flow)))
      def fails()                                                                            = when(aMock.updateApiChoice(*, *[ServiceName], *)(*)).thenReturn(successful(Left(new HttpException("", 400))))
    }

    object ClearApiChoice {
      def succeeds(flow: SupportFlow = SupportFlow("sessionId", Support.MakingAnApiCall.id)) = when(aMock.clearApiChoice(*)).thenReturn(successful(Right(flow)))
      def fails()                                                                            = when(aMock.clearApiChoice(*)).thenReturn(successful(Left(new HttpException("", 400))))
    }
  }

  object SupportServiceMock extends AbstractSupportServiceMock {
    val aMock: SupportService = mock[SupportService]
  }
}

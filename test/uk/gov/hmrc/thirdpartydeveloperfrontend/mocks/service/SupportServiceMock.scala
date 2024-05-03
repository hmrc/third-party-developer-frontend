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

import uk.gov.hmrc.apiplatform.modules.apis.domain.models.{ApiDefinition}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.support.{ApplyForPrivateApiAccessForm, SupportData, SupportDetailsForm}
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
      def succeeds(flow: SupportFlow = SupportFlow("sessionId", SupportData.UsingAnApi.id)) = when(aMock.getSupportFlow(*)).thenReturn(successful(flow))
    }

    object SubmitTicket { // TODO - sort two calls

      def succeeds(flow: SupportFlow = SupportFlow("sessionId", SupportData.UsingAnApi.id)) = {
        when(aMock.submitTicket(*, *[ApplyForPrivateApiAccessForm])(*)).thenReturn(successful(flow))
        when(aMock.submitTicket(*, *[SupportDetailsForm])(*)).thenReturn(successful(flow))
      }
    }

    object UpdateWithDelta {
      def succeeds(flow: SupportFlow = SupportFlow("sessionId", SupportData.MakingAnApiCall.id)) = when(aMock.updateWithDelta(*)(*)).thenReturn(successful(flow))
    }
    
    object CreateSupportFlow {
      def succeeds(flow: SupportFlow = SupportFlow("sessionId", SupportData.MakingAnApiCall.id)) = when(aMock.createFlow(*, *)).thenReturn(successful(flow))
    }

  }

  object SupportServiceMock extends AbstractSupportServiceMock {
    val aMock: SupportService = mock[SupportService]
  }
}

/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.apiplatform.modules.uplift.services.mocks

import org.mockito.MockitoSugar
import org.mockito.ArgumentMatchersSugar
import scala.concurrent.Future.successful
import uk.gov.hmrc.apiplatform.modules.uplift.domain.models._
import uk.gov.hmrc.thirdpartydeveloperfrontend.repositories.FlowRepository
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.flows.FlowType
import play.api.libs.json.OFormat

trait FlowRepositoryMockModule extends MockitoSugar with ArgumentMatchersSugar {
  protected trait BaseFlowRepositoryMock {
    def aMock: FlowRepository

    object FetchBySessionIdAndFlowType {
      def thenReturn(flow: GetProductionCredentialsFlow) = when(aMock.fetchBySessionIdAndFlowType[GetProductionCredentialsFlow](*, eqTo(FlowType.GET_PRODUCTION_CREDENTIALS))(*)).thenReturn(successful(Some(flow)))
      def thenReturnNothing = when(aMock.fetchBySessionIdAndFlowType[GetProductionCredentialsFlow](*, eqTo(FlowType.GET_PRODUCTION_CREDENTIALS))(*)).thenReturn(successful(None))
    }

    object SaveFlow {
      def thenReturnsSuccess = when(aMock.saveFlow(*[GetProductionCredentialsFlow])).thenAnswer((f: GetProductionCredentialsFlow) => successful(f))
    }

    object DeleteBySessionIdAndFlowType {
      def thenReturnsSuccess = when(aMock.deleteBySessionIdAndFlowType(*, eqTo(FlowType.GET_PRODUCTION_CREDENTIALS))).thenReturn(successful(true))
    }
  }
  
  object FlowRepositoryMock extends BaseFlowRepositoryMock {
    val aMock = mock[FlowRepository](withSettings.lenient())
  }
}
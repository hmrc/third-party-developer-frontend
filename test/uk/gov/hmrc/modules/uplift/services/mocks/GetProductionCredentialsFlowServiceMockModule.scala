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

package uk.gov.hmrc.modules.uplift.services.mocks

import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.mockito.verification.VerificationMode
import scala.concurrent.Future.{successful, failed}
import uk.gov.hmrc.modules.uplift.domain.models._
import uk.gov.hmrc.modules.uplift.services.GetProductionCredentialsFlowService

trait GetProductionCredentialsFlowServiceMockModule extends MockitoSugar with ArgumentMatchersSugar {

  protected trait BaseGetProductionCredentialsFlowServiceMock {
    def aMock: GetProductionCredentialsFlowService

    def verify = MockitoSugar.verify(aMock)

    def verify(mode: VerificationMode) = MockitoSugar.verify(aMock, mode)

    def verifyZeroInteractions() = MockitoSugar.verifyZeroInteractions(aMock)

    object FetchFlow {
      def thenReturns(out: GetProductionCredentialsFlow) =
        when(aMock.fetchFlow(*)).thenReturn(successful(out))
        
      def thenFail(ex: Exception) = {
        when(aMock.fetchFlow(*)).thenReturn(failed(ex))
      }
    }

    object StoreResponsibleIndividual {
      def thenReturns(ri: ResponsibleIndividual, out: GetProductionCredentialsFlow) =
        when(aMock.storeResponsibleIndividual(eqTo(ri), *)).thenReturn(successful(out))
        
      def thenFail(ex: Exception) = {
        when(aMock.fetchFlow(*)).thenReturn(failed(ex))
      }
    }

     
    object FindResponsibleIndividual {
      def thenReturns(out: ResponsibleIndividual) = 
        when(aMock.findResponsibleIndividual(*)).thenReturn(successful(Some(out)))
        
      def thenReturnsNone() = 
        when(aMock.findResponsibleIndividual(*)).thenReturn(successful(None))
    }

    object StoreSellResellOrDistribute {
      def thenReturns(in: SellResellOrDistribute, out: GetProductionCredentialsFlow) = {
        when(aMock.storeSellResellOrDistribute(eqTo(in), *)).thenReturn(successful(out))
      }

      def thenFail(ex: Exception) = {
        when(aMock.storeSellResellOrDistribute(*, *)).thenReturn(failed(ex))
      }
    }

    object FindSellResellOrDistribute {
      def thenReturnsYes() = 
        when(aMock.findSellResellOrDistribute(*)).thenReturn(successful(Some(SellResellOrDistribute("Yes"))))
        
      def thenReturnsNo() = 
        when(aMock.findSellResellOrDistribute(*)).thenReturn(successful(Some(SellResellOrDistribute("No"))))
        
      def thenReturnsNone() = 
        when(aMock.findSellResellOrDistribute(*)).thenReturn(successful(None))
    }

    object StoreApiSubscriptions {
      def thenReturns(out: GetProductionCredentialsFlow) =
        when(aMock.storeApiSubscriptions(*, *)).thenReturn(successful(out))
        
      def thenFail(ex: Exception) = {
        when(aMock.storeApiSubscriptions(*,*)).thenReturn(failed(ex))
      }

    }
  }

  object GPCFlowServiceMock extends BaseGetProductionCredentialsFlowServiceMock {
    val aMock = mock[GetProductionCredentialsFlowService](withSettings.lenient())
  }
}

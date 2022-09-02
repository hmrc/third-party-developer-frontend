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
import org.mockito.verification.VerificationMode
import scala.concurrent.Future.successful
import uk.gov.hmrc.apiplatform.modules.uplift.domain.models._
import uk.gov.hmrc.thirdpartydeveloperfrontend.repositories.FlowRepository
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.flows.FlowType
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.flows.Flow
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.Session

trait FlowRepositoryMockModule extends MockitoSugar with ArgumentMatchersSugar {
  protected trait BaseFlowRepositoryMock {
    def aMock: FlowRepository

    def verify = MockitoSugar.verify(aMock)

    def verifyZeroInteractions() = MockitoSugar.verifyZeroInteractions(aMock)

    object FetchBySessionIdAndFlowType {
      def thenReturn[A <: Flow](flow: A) = when(aMock.fetchBySessionIdAndFlowType[A](*)(*,*)).thenReturn(successful(Some(flow)))
      def thenReturn[A <: Flow](sessionId: String)(flow: A) = when(aMock.fetchBySessionIdAndFlowType[A](eqTo(sessionId))(*,*)).thenReturn(successful(Some(flow)))

      def thenReturnNothing[A <: Flow] = when(aMock.fetchBySessionIdAndFlowType[A](*)(*,*)).thenReturn(successful(None))
      def thenReturnNothing[A <: Flow](sessionId: String) = when(aMock.fetchBySessionIdAndFlowType[A](eqTo(sessionId))(*,*)).thenReturn(successful(None))

      def verifyCalledWith[A <: Flow](sessionId: String) = verify.fetchBySessionIdAndFlowType[A](eqTo(sessionId))(*,*)
    }

    object SaveFlow {
      def thenReturnSuccess[A <: Flow] = when(aMock.saveFlow[A](*)).thenAnswer((flow: A) => successful(flow))

      def verifyCalledWith[A <: Flow](flow: A) = verify.saveFlow[A](eqTo(flow))
    }

    object DeleteBySessionIdAndFlowType {
      def thenReturnSuccess(sessionId: String, flowType: FlowType) = when(aMock.deleteBySessionIdAndFlowType(eqTo(sessionId), eqTo(flowType))).thenReturn(successful(true))

     def verifyCalledWith(sessionId: String, flowType: FlowType) = verify.deleteBySessionIdAndFlowType(eqTo(sessionId), eqTo(flowType))
    }
  }
  
  object FlowRepositoryMock extends BaseFlowRepositoryMock {
    val aMock = mock[FlowRepository](withSettings.lenient())
  }
}
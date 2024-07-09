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

package uk.gov.hmrc.apiplatform.modules.uplift.services.mocks

import scala.concurrent.Future.successful
import scala.reflect.runtime.universe._

import org.mockito.quality.Strictness
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}

import uk.gov.hmrc.apiplatform.modules.tpd.core.domain.models.SessionId
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.flows.{Flow, FlowType}
import uk.gov.hmrc.thirdpartydeveloperfrontend.repositories.FlowRepository

trait FlowRepositoryMockModule extends MockitoSugar with ArgumentMatchersSugar {

  protected trait BaseFlowRepositoryMock {
    def aMock: FlowRepository

    def verify = MockitoSugar.verify(aMock)

    def verifyZeroInteractions() = MockitoSugar.verifyZeroInteractions(aMock)

    object FetchBySessionIdAndFlowType {
      def thenReturn[A <: Flow](flow: A)(implicit tt: TypeTag[A]) = when(aMock.fetchBySessionIdAndFlowType[A](*)(eqTo(tt), *)).thenReturn(successful(Some(flow)))

      def thenReturn[A <: Flow](sessionId: A#Type)(flow: A)(implicit tt: TypeTag[A]) =
        when(aMock.fetchBySessionIdAndFlowType[A](eqTo(sessionId))(eqTo(tt), *)).thenReturn(successful(Some(flow)))

      def thenReturnNothing[A <: Flow](implicit tt: TypeTag[A]) = when(aMock.fetchBySessionIdAndFlowType[A](*)(eqTo(tt), *)).thenReturn(successful(None))

      def thenReturnNothing[A <: Flow](sessionId: A#Type)(implicit tt: TypeTag[A]) =
        when(aMock.fetchBySessionIdAndFlowType[A](eqTo(sessionId))(eqTo(tt), *)).thenReturn(successful(None))

      def verifyCalledWith[A <: Flow](sessionId: A#Type)(implicit tt: TypeTag[A]) = verify.fetchBySessionIdAndFlowType[A](eqTo(sessionId))(eqTo(tt), *)
    }

    object SaveFlow {
      def thenReturnSuccess[A <: Flow] = when(aMock.saveFlow[A](*)).thenAnswer((flow: A) => successful(flow))

      def verifyCalledWith[A <: Flow](flow: A) = verify.saveFlow[A](eqTo(flow))
    }

    object DeleteBySessionIdAndFlowType {
      def thenReturnSuccess(sessionId: SessionId, flowType: FlowType) = when(aMock.deleteBySessionIdAndFlowType(eqTo(sessionId), eqTo(flowType))).thenReturn(successful(true))

      def verifyCalledWith(sessionId: SessionId, flowType: FlowType) = verify.deleteBySessionIdAndFlowType(eqTo(sessionId), eqTo(flowType))
    }
  }

  object FlowRepositoryMock extends BaseFlowRepositoryMock {
    val aMock = mock[FlowRepository](withSettings.strictness(Strictness.LENIENT))
  }
}

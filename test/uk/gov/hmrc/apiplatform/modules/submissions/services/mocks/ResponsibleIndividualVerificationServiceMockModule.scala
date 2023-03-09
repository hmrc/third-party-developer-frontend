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

package uk.gov.hmrc.apiplatform.modules.submissions.services.mocks

import scala.concurrent.Future.successful

import org.mockito.quality.Strictness
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}

import uk.gov.hmrc.apiplatform.modules.submissions.domain.models._
import uk.gov.hmrc.apiplatform.modules.submissions.services.ResponsibleIndividualVerificationService

trait ResponsibleIndividualVerificationServiceMockModule extends MockitoSugar with ArgumentMatchersSugar {

  protected trait BaseResponsibleIndividualVerificationServiceMock {
    def aMock: ResponsibleIndividualVerificationService

    object FetchResponsibleIndividualVerification {

      def thenReturns(out: ResponsibleIndividualVerification) =
        when(aMock.fetchResponsibleIndividualVerification(*)(*)).thenReturn(successful(Some(out)))

      def thenReturnsNone() = {
        when(aMock.fetchResponsibleIndividualVerification(*)(*)).thenReturn(successful(None))
      }
    }

    object Accept {

      def thenReturns(out: ResponsibleIndividualVerification) =
        when(aMock.accept(*)(*)).thenReturn(successful(Right(out)))

      def thenReturnFailure() = {
        when(aMock.accept(*)(*)).thenReturn(successful(Left(ErrorDetails("code", "nope"))))
      }
    }

    object Decline {

      def thenReturns(out: ResponsibleIndividualVerification) =
        when(aMock.decline(*)(*)).thenReturn(successful(Right(out)))

      def thenReturnFailure() = {
        when(aMock.decline(*)(*)).thenReturn(successful(Left(ErrorDetails("code", "nope"))))
      }
    }
  }

  object ResponsibleIndividualVerificationServiceMock extends BaseResponsibleIndividualVerificationServiceMock {
    val aMock = mock[ResponsibleIndividualVerificationService](withSettings.strictness(Strictness.LENIENT))
  }
}

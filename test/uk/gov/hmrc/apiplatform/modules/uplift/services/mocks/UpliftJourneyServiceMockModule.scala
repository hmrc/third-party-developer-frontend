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

import org.mockito.verification.VerificationMode
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}

import uk.gov.hmrc.apiplatform.modules.submissions.domain.models.Submission
import uk.gov.hmrc.apiplatform.modules.uplift.domain.models._
import uk.gov.hmrc.apiplatform.modules.uplift.services.UpliftJourneyService
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.APISubscriptionStatus
import uk.gov.hmrc.apiplatform.modules.applications.domain.models.ApplicationId
import org.mockito.quality.Strictness

trait UpliftJourneyServiceMockModule extends MockitoSugar with ArgumentMatchersSugar {

  protected trait BaseUpliftJourneyServiceMock {
    def aMock: UpliftJourneyService

    def verify = MockitoSugar.verify(aMock)

    def verify(mode: VerificationMode) = MockitoSugar.verify(aMock, mode)

    def verifyZeroInteractions() = MockitoSugar.verifyZeroInteractions(aMock)

    object ApiSubscriptionData {

      def thenReturns(in: Set[String], bool: Boolean) =
        when(aMock.apiSubscriptionData(*[ApplicationId], *, *)(*)).thenReturn(successful((in, bool)))
    }

    object StoreDefaultSubscriptionsInFlow {

      def thenReturns() =
        when(aMock.storeDefaultSubscriptionsInFlow(*[ApplicationId], *)(*)).thenReturn(successful(mock[ApiSubscriptions]))
    }

    object ConfirmAndUplift {
      def thenReturns(appId: ApplicationId) = when(aMock.confirmAndUplift(*[ApplicationId], *, *)(*)).thenReturn(successful(Right(appId)))

      def thenLeft(err: String) = when(aMock.confirmAndUplift(*[ApplicationId], *, *)(*)).thenReturn(successful(Left(err)))
    }

    object ChangeApiSubscriptions {
      def thenReturns(out: List[APISubscriptionStatus]) = when(aMock.changeApiSubscriptions(*[ApplicationId], *, *)(*)).thenReturn(successful(out))
    }

    object CreateNewSubmission {
      def thenReturns(out: Submission) = when(aMock.createNewSubmission(*[ApplicationId], *, *)(*)).thenReturn(successful(Right(out)))
    }
  }

  object UpliftJourneyServiceMock extends BaseUpliftJourneyServiceMock {
    val aMock = mock[UpliftJourneyService](withSettings.strictness(Strictness.LENIENT))
  }
}

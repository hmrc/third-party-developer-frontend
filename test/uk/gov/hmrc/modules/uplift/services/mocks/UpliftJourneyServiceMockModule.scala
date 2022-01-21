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

import org.mockito.MockitoSugar
import org.mockito.ArgumentMatchersSugar
import org.mockito.verification.VerificationMode
import scala.concurrent.Future.successful
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.ApplicationId
import uk.gov.hmrc.modules.uplift.domain.models._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.APISubscriptionStatus
import uk.gov.hmrc.modules.uplift.services.UpliftJourneyService

trait UpliftJourneyServiceMockModule extends MockitoSugar with ArgumentMatchersSugar {
  protected trait BaseUpliftJourneyServiceMock {
    def aMock: UpliftJourneyService

    def verify = MockitoSugar.verify(aMock)

    def verify(mode: VerificationMode) = MockitoSugar.verify(aMock, mode)

    def verifyZeroInteractions() = MockitoSugar.verifyZeroInteractions(aMock)

    object ApiSubscriptionData {
    
      def thenReturns(in: Set[String], bool: Boolean) =
        when(aMock.apiSubscriptionData(*[ApplicationId], *, *)(*)).thenReturn(successful((in,bool)))
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
  }
  
  object UpliftJourneyServiceMock extends BaseUpliftJourneyServiceMock {
    val aMock = mock[UpliftJourneyService](withSettings.lenient())
  }
}
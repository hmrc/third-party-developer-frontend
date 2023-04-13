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

import org.mockito.MockitoSugar
import org.mockito.ArgumentMatchersSugar
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.SubscriptionsService
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ApiIdentifier
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import scala.concurrent.Future.successful
import uk.gov.hmrc.apiplatform.modules.commands.applications.domain.models._
import uk.gov.hmrc.apiplatform.modules.applications.domain.models.ApplicationId
import scala.concurrent.ExecutionContext.Implicits.global

class SubscriptionsServiceMockModule extends MockitoSugar with ArgumentMatchersSugar {

  trait AbstractSubscriptionsServiceMock {
    import Types._

    def aMock: SubscriptionsService

    object SubscribeToApi {
      def succeeds(app: Application, apiIdentifier: ApiIdentifier) =
        when(aMock.subscribeToApi(eqTo(app), eqTo(apiIdentifier), *)(*)).thenReturn(DispatchSuccessResult(app).asSuccess)

      def succeeds() = {
        val mockApp = mock[Application]
        when(aMock.subscribeToApi(*, *, *)(*)).thenReturn(DispatchSuccessResult(mockApp).asSuccess)
      }
    }

    object UnsubscribeFromApi {
      def succeeds(app: Application, apiIdentifier: ApiIdentifier) =
        when(aMock.unsubscribeFromApi(eqTo(app), eqTo(apiIdentifier), *)(*)).thenReturn(DispatchSuccessResult(app).asSuccess)
    }

    object IsSubscribedToApi {
      def isTrue(appId: ApplicationId, apiIdentifier: ApiIdentifier) =
        when(aMock.isSubscribedToApi(eqTo(appId), eqTo(apiIdentifier))(*)).thenReturn(successful(true))
  
      def isFalse(appId: ApplicationId, apiIdentifier: ApiIdentifier) =
        when(aMock.isSubscribedToApi(eqTo(appId), eqTo(apiIdentifier))(*)).thenReturn(successful(false))
    }
  }

  object SubscriptionsServiceMock extends AbstractSubscriptionsServiceMock {
    val aMock = mock[SubscriptionsService]
  }
  
}

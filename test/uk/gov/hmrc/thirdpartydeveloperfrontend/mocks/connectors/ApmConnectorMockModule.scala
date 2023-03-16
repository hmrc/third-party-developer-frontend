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

package uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.connectors

import scala.concurrent.Future.{failed, successful}

import org.mockito.{ArgumentMatchersSugar, MockitoSugar}

import uk.gov.hmrc.apiplatform.modules.apis.domain.models.{ApiContext, ApiIdentifier}
import uk.gov.hmrc.apiplatform.modules.applications.domain.models.ApplicationId
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ApmConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.ApiData

trait ApmConnectorMockModule extends MockitoSugar with ArgumentMatchersSugar {

  object ApmConnectorMock {
    val aMock = mock[ApmConnector]

    object UpliftApplicationV1 {

      def willReturn(newAppId: ApplicationId) =
        when(aMock.upliftApplicationV1(*[ApplicationId], *)(*)).thenReturn(successful(newAppId))

      def willFailWith(exception: Exception) =
        when(aMock.upliftApplicationV1(*[ApplicationId], *)(*)).thenReturn(failed(exception))
    }

    object UpliftApplicationV2 {

      def willReturn(newAppId: ApplicationId) =
        when(aMock.upliftApplicationV2(*[ApplicationId], *)(*)).thenReturn(successful(newAppId))

      def willFailWith(exception: Exception) =
        when(aMock.upliftApplicationV2(*[ApplicationId], *)(*)).thenReturn(failed(exception))
    }

    object FetchAllApis {

      def willReturn(apis: Map[ApiContext, ApiData]) =
        when(aMock.fetchAllApis(*)(*)).thenReturn(successful(apis))

      def willFailWith(exception: Exception) =
        when(aMock.fetchAllApis(*)(*)).thenReturn(failed(exception))
    }

    object FetchUpliftableSubscriptions {

      def willReturn(apiIds: Set[ApiIdentifier]) =
        when(aMock.fetchUpliftableSubscriptions(*[ApplicationId])(*)).thenReturn(successful(apiIds))

      def willFailWith(exception: Exception) =
        when(aMock.fetchUpliftableSubscriptions(*)(*)).thenReturn(failed(exception))
    }
  }
}
